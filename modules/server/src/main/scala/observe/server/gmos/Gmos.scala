// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import scala.concurrent.duration.*
import cats._
import cats.data.Kleisli
import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.{GmosAdc, GmosEOffsetting, GmosGratingOrder, GmosRoi, Site}
import org.typelevel.log4cats.Logger
import lucuma.core.math.Wavelength
import observe.model.GmosParameters.*
import observe.model.dhs.ImageFileId
import observe.model.enums.Guiding
import observe.model.enums.Instrument
import observe.model.enums.NodAndShuffleStage
import observe.model.enums.NodAndShuffleStage.*
import observe.model.enums.ObserveCommandResult
import observe.server.*
import observe.server.gmos.Gmos.SiteSpecifics
import observe.server.gmos.GmosController.Config.NSConfig
import observe.server.gmos.GmosController.Config.*
import observe.server.keywords.DhsInstrument
import observe.server.keywords.KeywordsClient
import shapeless.tag
import cats.effect.{Ref, Temporal}
import lucuma.core.model.sequence.DynamicConfig
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.Execution.{Config => OdbConfig}
import observe.common.ObsQueriesGQL.ObsQuery.{Data, GmosInstrumentConfig, GmosReadout, GmosSite, GmosStatic, InsConfig, SeqStep, Sequence}
import observe.model.Observation
import observe.server.SequenceGen.StepGen
import observe.server.StepType.ExclusiveDarkOrBias
import observe.server.gmos.GmosController.Config

import scala.jdk.DurationConverters.*

abstract class Gmos[F[_]: Temporal: Logger, T <: GmosSite](
  val controller: GmosController[F, T],
  ss:             SiteSpecifics[T],
  nsCmdR:         Ref[F, Option[NSObserveCommand]],
  obsType: StepType,
  staticCfg: GmosStatic[T],
  dynamicCfg: GmosInstrumentConfig[T]
)(configTypes:    GmosController.Config[T])
    extends DhsInstrument[F]
    with InstrumentSystem[F] {

  import InstrumentSystem._

  override val contributorName: String = "gmosdc"

  override val keywordsClient: KeywordsClient[F] = this

  val nsCmdRef: Ref[F, Option[NSObserveCommand]] = nsCmdR

  val nsCount: F[Int] = controller.nsCount

  override def observeTimeout: FiniteDuration = 110.seconds

  override def observeControl: InstrumentSystem.CompleteControl[F] =
    if (isNodAndShuffle(staticCfg))
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

  protected def fpuFromFPUnit(
    n: Option[T#BuiltInFpu],
    m: Option[String]
  ): Option[GmosFPU] =
    n.map(configTypes.BuiltInFPU).orElse(m.map(GmosController.Config.CustomMaskFPU))

  private def calcDisperser(
    grt:   Option[T#Grating],
    order: Option[DisperserOrder],
    wl:    Option[Wavelength]
  ): configTypes.GmosDisperser =
    grt
      .flatMap { disp =>
        // Workaround for missing order: Use order 1 as default
        val o = order.getOrElse(GmosGratingOrder.One)

        if (o === GmosGratingOrder.Zero)
          configTypes.GmosDisperser.Order0(disp).some
        else
          wl.map(w => configTypes.GmosDisperser.OrderN(disp, o, w))
      }
      .getOrElse(configTypes.GmosDisperser.Mirror.asRight)

  private def ccConfigFromSequenceConfig: Config.CCConfig = {
    val isDarkOrBias = obsType === StepType.DarkOrBias || obsType === ExclusiveDarkOrBias || obsType === StepType.DarkOrBiasNS

    if(isDarkOrBias) Config.DarkOrBias
    else configTypes.StandardCCConfig(
      ss.extractFilter(dynamicCfg),
      calcDisperser(ss.extractDisperser(dynamicCfg), dynamicCfg.gratingConfig.map(_.order), dynamicCfg.gratingConfig.map(_.wavelength)),
      fpuFromFPUnit(ss.extractFPU(dynamicCfg), ss.extractCustomFPU(dynamicCfg)),
      ss.extractStageMode(staticCfg),
      dynamicCfg.dtax,
      GmosAdc.Follow,
      staticCfg.nodAndShuffle.map(_.eOffset).getOrElse(GmosEOffsetting.Off)
    )
  }

  private def fromSequenceConfig: GmosController.GmosConfig[T] = {
    new GmosController.GmosConfig[T](configTypes)(
      ccConfigFromSequenceConfig,
      dcConfigFromSequenceConfig(nsConfig),
      nsConfig
    )
  }

  override def observe: Kleisli[F, ImageFileId, ObserveCommandResult] =
    Kleisli { fileId =>
      controller.observe(fileId, dynamicCfg.exposure.toScala)
    }

  override def instrumentActions: InstrumentActions[F] =
    new GmosInstrumentActions(this)

  override def notifyObserveEnd: F[Unit] =
    controller.endObserve

  override def notifyObserveStart: F[Unit] = Applicative[F].unit

  override def configure: F[ConfigResult] =
      controller.applyConfig(fromSequenceConfig)
        .as(ConfigResult(this))

  override def calcObserveTime: Duration = dynamicCfg.exposure.toScala / nsConfig.exposureDivider

  override def observeProgress(total: Duration, elapsed: ElapsedTime): fs2.Stream[F, Progress] =
    controller
      .observeProgress(total, elapsed)

  def isNodAndShuffle(s: GmosStatic[T]): Boolean = s.nodAndShuffle.isDefined

  def nsConfig: NSConfig = staticCfg.nodAndShuffle.map { n =>
    NSConfig.NodAndShuffle(tag[NsCyclesI][Int](n.shuffleCycles),
      tag[NsRowsI][Int](n.shuffleOffset),
      Vector(NSPosition(NodAndShuffleStage.StageA, n.posA, Guiding.Guide), NSPosition(NodAndShuffleStage.StageB, n.posB, Guiding.Guide)),
      dynamicCfg.exposure.toScala
    )
  }.getOrElse(NSConfig.NoNodAndShuffle)

  private def shutterStateObserveType(obsType: StepType): ShutterState = obsType match {
    case StepType.DarkOrBias | StepType.ExclusiveDarkOrBias | StepType.DarkOrBiasNS => ShutterState.CloseShutter
    case _                    => ShutterState.UnsetShutter
  }

  private def exposureTime(
    d: GmosInstrumentConfig[T],
    nsConfig: NSConfig
  ): Duration = d.exposure.toScala / nsConfig.exposureDivider

  def dcConfigFromSequenceConfig(
    nsConfig: NSConfig
  ): DCConfig = DCConfig(
    exposureTime(dynamicCfg, nsConfig),
    shutterStateObserveType(obsType),
    CCDReadout(dynamicCfg.readout.ampReadMode, dynamicCfg.readout.ampGain, dynamicCfg.readout.ampCount, calcGainSetting(dynamicCfg.readout)),
    CCDBinning(dynamicCfg.readout.xBin, dynamicCfg.readout.yBin),
    extractROIs
  )

  def calcGainSetting(r: GmosReadout): Double = (r.ampReadMode, r.ampGain) match {
    case _ => 2.0
  }

  def nsCmdRef: F[Ref[F, Option[NSObserveCommand]]] = Ref.of(none)

  def extractROIs: RegionsOfInterest = RegionsOfInterest.fromOCS(dynamicCfg.roi, List.empty)

  def calcStepType(
    stdType: StepType,
    instrument: Instrument,
    s: GmosStatic[T], d: GmosInstrumentConfig[T],
  ): Either[ObserveFailure, StepType] = {
    if (s.nodAndShuffle.isDefined) {
      stdType match {
        case StepType.ExclusiveDarkOrBias(_) => StepType.DarkOrBiasNS(instrument).asRight
        case StepType.CelestialObject(_)     => StepType.NodAndShuffle(instrument).asRight
        case st                              => ObserveFailure.Unexpected(s"N&S is not supported for steps of type $st").asLeft
      }
    } else {
      stdType.asRight
    }
  }


}

object Gmos {

  def rowsToShuffle(stage: NodAndShuffleStage, rows: Int): Int =
    if (stage === StageA) 0 else rows

  trait SiteSpecifics[T <: GmosSite] {
    final val FPU_CUSTOM_MASK = "fpuCustomMask"

    def extractFilter(d: GmosInstrumentConfig[T]): Option[T#Filter]

    def extractDisperser(d: GmosInstrumentConfig[T]): Option[T#Grating]

    def extractFPU(d: GmosInstrumentConfig[T]): Option[T#BuiltInFpu]

    def extractStageMode(s: GmosStatic[T]): T#StageMode

    def extractCustomFPU(d: GmosInstrumentConfig[T]): Option[String] = none
  }

  class GmosTranslator[F[_]: Applicative](
                                           site:      Site,
                                           systemss:  Systems[F],
                                           gmosNsCmd: Ref[F, Option[NSObserveCommand]]
                                         ) extends SeqTranslate[F] {
    override def sequence(sequence: Data.Observation)(using tio: Temporal[F]): F[Option[Either[List[Throwable], SequenceGen[F]]]] =
      sequence.execution.config match {
        case OdbConfig.GmosNorthExecutionConfig(_, staticN, acquisitionN, scienceN) => buildSequence[F, GmosSite.North](staticN, acquisitionN, scienceN).some.pure[F]
        case OdbConfig.GmosSouthExecutionConfig(_, staticS, acquisitionS, scienceS) => buildSequence[F, GmosSite.South](staticS, acquisitionS, scienceS).some.pure[F]
        case _ => none[Either[List[Throwable], SequenceGen[F]]].pure[F]
      }

    override def stopObserve(seqId: Observation.Id, graceful: Boolean)(using tio: Temporal[F]): EngineState[F] => Option[fs2.Stream[F, EventType[F]]] = ???

    override def abortObserve(seqId: Observation.Id)(using tio: Temporal[F]): EngineState[F] => Option[fs2.Stream[F, EventType[F]]] = ???

    override def pauseObserve(seqId: Observation.Id, graceful: Boolean)(using tio: Temporal[F]): EngineState[F] => Option[fs2.Stream[F, EventType[F]]] = ???

    override def resumePaused(seqId: Observation.Id)(using tio: Temporal[F]): EngineState[F] => Option[fs2.Stream[F, EventType[F]]] = ???

    private def buildSequence[F[_], T <: GmosSite](sequence: Data.Observation, inst: Instrument, staticCfg: GmosStatic[T], acquisition: Sequence[InsConfig.Gmos[T]], science: Sequence[InsConfig.Gmos[T]]): Either[List[Throwable], SequenceGen[F]] = {
      val steps = (acquisition.nextAtom.toList ++ acquisition.possibleFuture ++ science.nextAtom.toList ++ science.possibleFuture).flatMap(_.steps).map(x => buildStep[F, T](staticCfg, x))

      SequenceGen(sequence.id, sequence.id.toString(), sequence.title, inst, steps).asRight
    }

    private def buildStep[F[_], T <: GmosSite](staticCfg: GmosStatic[T], step: SeqStep[InsConfig.Gmos[T]]): StepGen[F] = {
      Gmos()
    }
  }
}
