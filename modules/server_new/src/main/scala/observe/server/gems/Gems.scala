// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gems

import cats.Applicative
import cats.Eq
import cats.MonadThrow
import cats.syntax.all.*
import lucuma.core.enums.*
import lucuma.core.model.AltairConfig
import lucuma.core.model.GemsConfig
import lucuma.core.model.GemsConfig.*
import lucuma.core.util.TimeSpan
import observe.server.gems.Gems.GemsWfsState
import observe.server.tcs.Gaos
import observe.server.tcs.Gaos.PauseCondition
import observe.server.tcs.Gaos.PauseConditionSet
import observe.server.tcs.Gaos.PauseResume
import observe.server.tcs.Gaos.ResumeCondition
import observe.server.tcs.Gaos.ResumeConditionSet
import observe.server.tcs.GuideConfigDb
import observe.server.tcs.GuideConfigState
import org.typelevel.log4cats.Logger

trait Gems[F[_]] extends Gaos[F] {
  val cfg: GemsConfig

  def pauseResume(
    pauseReasons:  PauseConditionSet,
    resumeReasons: ResumeConditionSet
  ): F[PauseResume[F]]

  val stateGetter: GemsWfsState[F]

}

object Gems {

  private class GemsImpl[F[_]: MonadThrow](
    controller:    GemsController[F],
    config:        GemsConfig,
    guideConfigDb: GuideConfigDb[F]
  )(using L: Logger[F])
      extends Gems[F] {

    override val cfg: GemsConfig = config

    override def observe(config: Either[AltairConfig, GemsConfig], expTime: TimeSpan): F[Unit] =
      ().pure[F]

    override def endObserve(config: Either[AltairConfig, GemsConfig]): F[Unit] = ().pure[F]

    override def pauseResume(
      pauseReasons:  PauseConditionSet,
      resumeReasons: ResumeConditionSet
    ): F[PauseResume[F]] =
      guideConfigDb.value.flatMap { g =>
        g.config.gaosGuide match {
          case Some(Right(gemsCfg)) =>
            val filteredPauseReasons  = filterPauseReasons(pauseReasons, g.gemsSkyPaused)
            val filteredResumeReasons = filterResumeReasons(
              resumeReasons,
              g.gemsSkyPaused || filteredPauseReasons.contains(PauseCondition.GaosGuideOff)
            )
            controller
              .pauseResume(filteredPauseReasons, filteredResumeReasons)(combine(gemsCfg, cfg))
              .map(x =>
                PauseResume(
                  x.pause.map(
                    _.flatMap(_ =>
                      guideConfigDb
                        .update(GuideConfigState.gemsSkyPaused.replace(true))
                        .whenA(filteredPauseReasons.contains(PauseCondition.GaosGuideOff))
                    )
                  ),
                  x.resume.map(
                    _.flatMap(_ =>
                      guideConfigDb
                        .update(GuideConfigState.gemsSkyPaused.replace(false))
                        .whenA(filteredResumeReasons.contains(ResumeCondition.GaosGuideOn))
                    )
                  )
                )
              )
          case _                    =>
            // If there is no configuration coming from TCC we just ignore it. This is the case when taking dome flats
            // We check in TcsSouth.scala that it is not an error
            L.info(
              "No GeMS guide configuration from TCC. GeMS control skipped for unguided step."
            ) *>
              PauseResume[F](none, none).pure[F]
        }
      }

    override val stateGetter: GemsWfsState[F] = controller.stateGetter
  }

  // Ignore GaosGuideOff if it was already sent in a previous step
  // TODO: do the same for Filter pause condition (for GSAOI calibrations)
  private def filterPauseReasons(
    pauseReasons: PauseConditionSet,
    isSkyPaused:  Boolean
  ): PauseConditionSet =
    if (isSkyPaused) pauseReasons - PauseCondition.GaosGuideOff
    else pauseReasons

  private def filterResumeReasons(
    resumeReasons: ResumeConditionSet,
    isSkyPaused:   Boolean
  ): ResumeConditionSet =
    if (!isSkyPaused) resumeReasons - ResumeCondition.GaosGuideOn
    else resumeReasons

  // `combine` calculates the final configuration between the configuration coming from the step and the configuration
  // set by the operator.
  private def combine(opConfig: GemsConfig, stepConfig: GemsConfig): GemsConfig =
    GemsOn(
      Cwfs1Usage(opConfig.isCwfs1Used && stepConfig.isCwfs1Used),
      Cwfs2Usage(opConfig.isCwfs2Used && stepConfig.isCwfs2Used),
      Cwfs3Usage(opConfig.isCwfs3Used && stepConfig.isCwfs3Used),
      Odgw1Usage(opConfig.isOdgw1Used && stepConfig.isOdgw1Used),
      Odgw2Usage(opConfig.isOdgw2Used && stepConfig.isOdgw2Used),
      Odgw3Usage(opConfig.isOdgw3Used && stepConfig.isOdgw3Used),
      Odgw4Usage(opConfig.isOdgw4Used && stepConfig.isOdgw4Used),
      P1Usage(opConfig.isP1Used && stepConfig.isP1Used),
      OIUsage(opConfig.isOIUsed && stepConfig.isOIUsed)
    )

  def fromConfig[F[_]: MonadThrow: Logger](
    c:             GemsController[F],
    guideConfigDb: GuideConfigDb[F]
  ): Gems[F] = {
    val p1    = none[StepGuideState]
    val oi    = none[StepGuideState]
    val cwfs1 = none[StepGuideState]
    val cwfs2 = none[StepGuideState]
    val cwfs3 = none[StepGuideState]
    val odgw1 = none[StepGuideState]
    val odgw2 = none[StepGuideState]
    val odgw3 = none[StepGuideState]
    val odgw4 = none[StepGuideState]

    new GemsImpl[F](
      c,
      GemsOn(
        Cwfs1Usage(cwfs1.exists(_ === StepGuideState.Enabled)),
        Cwfs2Usage(cwfs2.exists(_ === StepGuideState.Enabled)),
        Cwfs3Usage(cwfs3.exists(_ === StepGuideState.Enabled)),
        Odgw1Usage(odgw1.exists(_ === StepGuideState.Enabled)),
        Odgw2Usage(odgw2.exists(_ === StepGuideState.Enabled)),
        Odgw3Usage(odgw3.exists(_ === StepGuideState.Enabled)),
        Odgw4Usage(odgw4.exists(_ === StepGuideState.Enabled)),
        P1Usage(p1.exists(_ === StepGuideState.Enabled)),
        OIUsage(oi.exists(_ === StepGuideState.Enabled))
      ),
      guideConfigDb
    ): Gems[F]
  }

  trait DetectorStateOps[T] {
    val trueVal: T
    val falseVal: T
  }

  object DetectorStateOps {
    def apply[T](using b: DetectorStateOps[T]): DetectorStateOps[T] = b

    def build[T](t: T, f: T): DetectorStateOps[T] = new DetectorStateOps[T] {
      override val trueVal: T  = t
      override val falseVal: T = f
    }

    def fromBoolean[T: DetectorStateOps](b: Boolean): T =
      if (b) DetectorStateOps[T].trueVal else DetectorStateOps[T].falseVal

    def isActive[T: DetectorStateOps: Eq](v: T): Boolean = v === DetectorStateOps[T].trueVal
  }

  sealed trait Cwfs1DetectorState extends Product with Serializable
  object Cwfs1DetectorState {
    case object On  extends Cwfs1DetectorState
    case object Off extends Cwfs1DetectorState

    given Eq[Cwfs1DetectorState]               = Eq.fromUniversalEquals
    given DetectorStateOps[Cwfs1DetectorState] =
      DetectorStateOps.build(On, Off)
  }

  sealed trait Cwfs2DetectorState extends Product with Serializable
  object Cwfs2DetectorState {
    case object On  extends Cwfs2DetectorState
    case object Off extends Cwfs2DetectorState

    given Eq[Cwfs2DetectorState]               = Eq.fromUniversalEquals
    given DetectorStateOps[Cwfs2DetectorState] =
      DetectorStateOps.build(On, Off)
  }

  sealed trait Cwfs3DetectorState extends Product with Serializable
  object Cwfs3DetectorState {
    case object On  extends Cwfs3DetectorState
    case object Off extends Cwfs3DetectorState

    given Eq[Cwfs3DetectorState]               = Eq.fromUniversalEquals
    given DetectorStateOps[Cwfs3DetectorState] =
      DetectorStateOps.build(On, Off)
  }

  sealed trait Odgw1DetectorState extends Product with Serializable
  object Odgw1DetectorState {
    case object On  extends Odgw1DetectorState
    case object Off extends Odgw1DetectorState

    given Eq[Odgw1DetectorState]               = Eq.fromUniversalEquals
    given DetectorStateOps[Odgw1DetectorState] =
      DetectorStateOps.build(On, Off)
  }

  sealed trait Odgw2DetectorState extends Product with Serializable
  object Odgw2DetectorState {
    case object On  extends Odgw2DetectorState
    case object Off extends Odgw2DetectorState

    given Eq[Odgw2DetectorState]               = Eq.fromUniversalEquals
    given DetectorStateOps[Odgw2DetectorState] =
      DetectorStateOps.build(On, Off)
  }

  sealed trait Odgw3DetectorState extends Product with Serializable
  object Odgw3DetectorState {
    case object On  extends Odgw3DetectorState
    case object Off extends Odgw3DetectorState

    given Eq[Odgw3DetectorState]               = Eq.fromUniversalEquals
    given DetectorStateOps[Odgw3DetectorState] =
      DetectorStateOps.build(On, Off)
  }

  sealed trait Odgw4DetectorState extends Product with Serializable
  object Odgw4DetectorState {
    case object On  extends Odgw4DetectorState
    case object Off extends Odgw4DetectorState

    given Eq[Odgw4DetectorState]               = Eq.fromUniversalEquals
    given DetectorStateOps[Odgw4DetectorState] =
      DetectorStateOps.build(On, Off)
  }

  final case class GemsWfsState[F[_]](
    cwfs1: F[Cwfs1DetectorState],
    cwfs2: F[Cwfs2DetectorState],
    cwfs3: F[Cwfs3DetectorState],
    odgw1: F[Odgw1DetectorState],
    odgw2: F[Odgw2DetectorState],
    odgw3: F[Odgw3DetectorState],
    odgw4: F[Odgw4DetectorState]
  )

  object GemsWfsState {
    def allOff[F[_]: Applicative]: GemsWfsState[F] = GemsWfsState(
      Cwfs1DetectorState.Off.pure[F].widen[Cwfs1DetectorState],
      Cwfs2DetectorState.Off.pure[F].widen[Cwfs2DetectorState],
      Cwfs3DetectorState.Off.pure[F].widen[Cwfs3DetectorState],
      Odgw1DetectorState.Off.pure[F].widen[Odgw1DetectorState],
      Odgw2DetectorState.Off.pure[F].widen[Odgw2DetectorState],
      Odgw3DetectorState.Off.pure[F].widen[Odgw3DetectorState],
      Odgw4DetectorState.Off.pure[F].widen[Odgw4DetectorState]
    )
  }

}
