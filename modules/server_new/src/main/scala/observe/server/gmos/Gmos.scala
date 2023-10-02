// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.*
import cats.data.Kleisli
import cats.effect.Ref
import cats.effect.Temporal
import cats.syntax.all.*
import eu.timepit.refined.api.Refined.*
import lucuma.core.enums.GmosAdc
import lucuma.core.enums.GmosEOffsetting
import lucuma.core.enums.GmosGratingOrder
import lucuma.core.enums.GmosRoi
import lucuma.core.enums.MosPreImaging
import lucuma.core.enums.ObserveClass
import lucuma.core.math.Wavelength
import lucuma.core.model.sequence
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.GmosCcdMode
import lucuma.core.model.sequence.gmos.GmosNodAndShuffle
import lucuma.core.model.sequence.gmos.StaticConfig
import lucuma.core.util.TimeSpan
import monocle.Getter
import observe.model.GmosParameters.*
import observe.model.dhs.ImageFileId
import observe.model.enums.Guiding
import observe.model.enums.Instrument
import observe.model.enums.NodAndShuffleStage
import observe.model.enums.NodAndShuffleStage.*
import observe.model.enums.ObserveCommandResult
import observe.server.StepType
import observe.server.StepType.ExclusiveDarkOrBias
import observe.server._
import observe.server.gmos
import observe.server.gmos.GmosController.Config
import observe.server.gmos.GmosController.Config.*
import observe.server.gmos.GmosController.GmosSite
import observe.server.gmos.NSObserveCommand
import observe.server.keywords.DhsInstrument
import observe.server.keywords.KeywordsClient
import org.typelevel.log4cats.Logger

import java.time.temporal.ChronoUnit

abstract class Gmos[F[_]: Temporal: Logger, T <: GmosSite](
  val controller: GmosController[F, T],
  nsCmdR:         Ref[F, Option[NSObserveCommand]],
  val config:     GmosController.GmosConfig[T]
) extends DhsInstrument[F]
    with InstrumentSystem[F] {

  import InstrumentSystem._

  override val contributorName: String = "gmosdc"

  override val keywordsClient: KeywordsClient[F] = this

  val nsCmdRef: Ref[F, Option[NSObserveCommand]] = nsCmdR

  val nsCount: F[Int] = controller.nsCount

  override def observeTimeout: TimeSpan =
    TimeSpan.unsafeFromDuration(110, ChronoUnit.SECONDS)

  override def observeControl: InstrumentSystem.CompleteControl[F] =
    if (isNodAndShuffle)
      CompleteControl(
        StopObserveCmd(stopNS),
        AbortObserveCmd(abortNS),
        PauseObserveCmd(pauseNS),
        ContinuePausedCmd(controller.resumePaused),
        StopPausedCmd(controller.stopPaused),
        AbortPausedCmd(controller.abortPaused)
      )
    else
      CompleteControl(
        StopObserveCmd(_ => controller.stopObserve),
        AbortObserveCmd(controller.abortObserve),
        PauseObserveCmd(_ => controller.pauseObserve),
        ContinuePausedCmd(controller.resumePaused),
        StopPausedCmd(controller.stopPaused),
        AbortPausedCmd(controller.abortPaused)
      )

  private def stopNS(gracefully: Boolean): F[Unit] =
    if (gracefully)
      nsCmdRef.set(NSObserveCommand.StopGracefully.some)
    else
      nsCmdRef.set(NSObserveCommand.StopImmediately.some) *> controller.stopObserve

  private def abortNS: F[Unit] =
    nsCmdRef.set(NSObserveCommand.AbortImmediately.some) *> controller.abortObserve

  private def pauseNS(gracefully: Boolean): F[Unit] =
    if (gracefully)
      nsCmdRef.set(NSObserveCommand.PauseGracefully.some)
    else
      nsCmdRef.set(NSObserveCommand.PauseImmediately.some) *> controller.pauseObserve

  override def observe: Kleisli[F, ImageFileId, ObserveCommandResult] =
    Kleisli { fileId =>
      controller.observe(fileId, calcObserveTime)
    }

  override def instrumentActions: InstrumentActions[F] =
    new GmosInstrumentActions(this)

  override def notifyObserveEnd: F[Unit] =
    controller.endObserve

  override def notifyObserveStart: F[Unit] = Applicative[F].unit

  override def configure: F[ConfigResult[F]] =
    controller
      .applyConfig(config)
      .as(ConfigResult(this))

  override def calcObserveTime: TimeSpan =
    config.dc.t /| config.ns.exposureDivider.value

  override def observeProgress(total: TimeSpan, elapsed: ElapsedTime): fs2.Stream[F, Progress] =
    controller.observeProgress(total, elapsed)

  def isNodAndShuffle: Boolean = config.ns match
    case NsConfig.NoNodAndShuffle => false
    case _                        => true

}

object Gmos {

  def rowsToShuffle(stage: NodAndShuffleStage, rows: NsRows): Int =
    if (stage === StageA) 0 else rows.value

  trait ParamGetters[
    T <: GmosSite,
    S <: sequence.gmos.StaticConfig,
    D <: sequence.gmos.DynamicConfig
  ] {
    val exposure: Getter[D, TimeSpan]
    val filter: Getter[D, Option[GmosController.GmosSite.Filter[T]]]
    val grating: Getter[D, Option[GmosController.GmosSite.Grating[T]]]
    val order: Getter[D, Option[GmosController.Config.GratingOrder]]
    val wavelength: Getter[D, Option[Wavelength]]
    val builtinFpu: Getter[D, Option[GmosController.GmosSite.FPU[T]]]
    val customFpu: Getter[D, Option[String]]
    val dtax: Getter[D, GmosController.Config.DTAX]
    val stageMode: Getter[S, GmosController.GmosSite.StageMode[T]]
    val nodAndShuffle: Getter[S, Option[GmosNodAndShuffle]]
    val roi: Getter[D, GmosRoi]
    val readout: Getter[D, GmosCcdMode]
    val isMosPreimaging: Getter[S, MosPreImaging]
  }

  def calcDisperser[T <: GmosSite](
    grt:   Option[GmosController.GmosSite.Grating[T]],
    order: Option[GmosGratingOrder],
    wl:    Option[Wavelength]
  ): GmosController.Config.GmosGrating[T] =
    grt
      .flatMap { disp =>
        // Workaround for missing order: Use order 1 as default
        val o = order.getOrElse(GmosGratingOrder.One)

        if (o === GmosGratingOrder.Zero)
          GmosController.Config.GmosGrating.Order0[T](disp).some
        else
          wl.map(w => GmosController.Config.GmosGrating.OrderN[T](disp, o, w))
      }
      .getOrElse(GmosController.Config.GmosGrating.Mirror())

  def fpuFromFPUnit[T <: GmosSite](
    n: Option[GmosController.GmosSite.FPU[T]],
    m: Option[String]
  ): Option[GmosFPU[T]] =
    n.map(GmosController.Config.BuiltInFPU[T].apply)
      .orElse(m.map(GmosController.Config.CustomMaskFPU[T].apply))

  def exposureTime(
    exp:      TimeSpan,
    nsConfig: NsConfig
  ): TimeSpan = exp /| nsConfig.exposureDivider.value

  def shutterStateObserveType(obsType: StepType): ShutterState = obsType match {
    case StepType.DarkOrBias(_) | StepType.ExclusiveDarkOrBias(_) | StepType.DarkOrBiasNS(_) =>
      ShutterState.CloseShutter
    case _                                                                                   => ShutterState.UnsetShutter
  }

  def calcGainSetting(r: GmosCcdMode): Double = (r.ampReadMode, r.ampGain) match {
    case _ => 2.0
  }

  def buildConfig[F[
    _
  ]: Temporal: Logger, T <: GmosSite, S <: sequence.gmos.StaticConfig, D <: sequence.gmos.DynamicConfig](
    instrument: Instrument,
    stepType:   StepType,
    staticCfg:  S,
    dynamicCfg: D
  )(using
    getters:    ParamGetters[T, S, D]
  ): GmosController.GmosConfig[T] = {

    def ccConfigFromSequenceConfig: Config.CCConfig[T] = {
      val isDarkOrBias: Boolean = stepType match {
        case StepType.DarkOrBias(_)          => true
        case StepType.DarkOrBiasNS(_)        => true
        case StepType.ExclusiveDarkOrBias(_) => true
        case _                               => false
      }

      if (isDarkOrBias) Config.DarkOrBias[T]()
      else
        GmosController.Config.StandardCCConfig[T](
          getters.filter.get(dynamicCfg),
          calcDisperser[T](
            getters.grating.get(dynamicCfg),
            getters.order.get(dynamicCfg),
            getters.wavelength.get(dynamicCfg)
          ),
          fpuFromFPUnit[T](getters.builtinFpu.get(dynamicCfg), getters.customFpu.get(dynamicCfg)),
          getters.stageMode.get(staticCfg),
          getters.dtax.get(dynamicCfg),
          GmosAdc.Follow,
          getters.nodAndShuffle.get(staticCfg).map(_.eOffset).getOrElse(GmosEOffsetting.Off)
        )
    }

    def extractROIs: RegionsOfInterest =
      RegionsOfInterest.fromOCS(getters.roi.get(dynamicCfg), List.empty)

    def dcConfigFromSequenceConfig(
      nsConfig: NsConfig
    ): DCConfig = DCConfig(
      exposureTime(getters.exposure.get(dynamicCfg), nsConfig),
      shutterStateObserveType(stepType),
      CCDReadout(
        getters.readout.get(dynamicCfg).ampReadMode,
        getters.readout.get(dynamicCfg).ampGain,
        getters.readout.get(dynamicCfg).ampCount,
        calcGainSetting(getters.readout.get(dynamicCfg))
      ),
      CCDBinning(getters.readout.get(dynamicCfg).xBin, getters.readout.get(dynamicCfg).yBin),
      extractROIs
    )

    val nsConfig: NsConfig = getters.nodAndShuffle
      .get(staticCfg)
      .map { n =>
        NsConfig.NodAndShuffle(
          NsCycles(n.shuffleCycles.value),
          NsRows(n.shuffleOffset.value),
          Vector(NSPosition(NodAndShuffleStage.StageA, n.posA, Guiding.Guide),
                 NSPosition(NodAndShuffleStage.StageB, n.posB, Guiding.Guide)
          ),
          getters.exposure.get(dynamicCfg)
        )
      }
      .getOrElse(NsConfig.NoNodAndShuffle)

    GmosController.GmosConfig[T](
      ccConfigFromSequenceConfig,
      dcConfigFromSequenceConfig(nsConfig),
      nsConfig
    )

  }

  final case class GmosStatusGen(ns: NsConfig) extends SequenceGen.StepStatusGen

  def isNodAndShuffle[S <: StaticConfig](
    staticConfig: S,
    l:            Getter[S, Option[GmosNodAndShuffle]]
  ): Boolean =
    l.get(staticConfig).isDefined

  def calcStepType[S <: StaticConfig](
    instrument: Instrument,
    stepCfg:    StepConfig,
    staticCfg:  S,
    obsClass:   ObserveClass,
    l:          Getter[S, Option[GmosNodAndShuffle]]
  ): Either[ObserveFailure, StepType] = {
    val stdType = SeqTranslate.calcStepType(instrument, stepCfg, obsClass)
    if (Gmos.isNodAndShuffle(staticCfg, l)) {
      stdType.flatMap {
        case StepType.DarkOrBias(_)      => StepType.DarkOrBiasNS(instrument).asRight
        case StepType.CelestialObject(_) => StepType.NodAndShuffle(instrument).asRight
        case st                          => ObserveFailure.Unexpected(s"N&S is not supported for steps of type $st").asLeft
      }
    } else {
      stdType.map {
        case StepType.DarkOrBias(inst) => StepType.ExclusiveDarkOrBias(instrument)
        case x                         => x
      }
    }
  }

}
