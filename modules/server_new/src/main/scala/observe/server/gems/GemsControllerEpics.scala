// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gems

import cats.effect.Async
import cats.syntax.all.*
import lucuma.core.model.GemsConfig
import lucuma.core.util.TimeSpan
import mouse.boolean.*
import observe.server.gems.Gems.*
import observe.server.gsaoi.GsaoiGuider
import observe.server.tcs.Gaos.PauseCondition
import observe.server.tcs.Gaos.PauseConditionSet
import observe.server.tcs.Gaos.PauseResume
import observe.server.tcs.Gaos.ResumeCondition
import observe.server.tcs.Gaos.ResumeConditionSet
import org.typelevel.log4cats.Logger

import java.time.temporal.ChronoUnit

class GemsControllerEpics[F[_]: Async](
  epicsSys:    GemsEpics[F],
  gsaoiGuider: GsaoiGuider[F]
)(using L: Logger[F])
    extends GemsController[F] {
  import GemsControllerEpics._

  override def pauseResume(pauseReasons: PauseConditionSet, resumeReasons: ResumeConditionSet)(
    cfg: GemsConfig
  ): F[PauseResume[F]] = {
    val r1 = pause(pauseReasons)
    val r2 = resume(resumeReasons)

    PauseResume(
      r1.nonEmpty.option(r1.sequence.void),
      r2.nonEmpty.option(r2.sequence.void)
    ).pure[F]
  }

  import GsaoiGuider.OdgwId._
  override val stateGetter: GemsWfsState[F] = GemsWfsState(
    epicsSys.apd1Active.map(DetectorStateOps.fromBoolean[Cwfs1DetectorState]),
    epicsSys.apd2Active.map(DetectorStateOps.fromBoolean[Cwfs2DetectorState]),
    epicsSys.apd3Active.map(DetectorStateOps.fromBoolean[Cwfs3DetectorState]),
    gsaoiGuider.currentState.map(x =>
      DetectorStateOps.fromBoolean[Odgw1DetectorState](x.isOdgwGuiding(Odgw1))
    ),
    gsaoiGuider.currentState.map(x =>
      DetectorStateOps.fromBoolean[Odgw2DetectorState](x.isOdgwGuiding(Odgw2))
    ),
    gsaoiGuider.currentState.map(x =>
      DetectorStateOps.fromBoolean[Odgw3DetectorState](x.isOdgwGuiding(Odgw3))
    ),
    gsaoiGuider.currentState.map(x =>
      DetectorStateOps.fromBoolean[Odgw4DetectorState](x.isOdgwGuiding(Odgw4))
    )
  )

  private def pause(pauseConditions: PauseConditionSet): Option[F[Unit]] = {
    val unguided = pauseConditions.contains(PauseCondition.GaosGuideOff).option(UnguidedCondition)
    val offset   = pauseConditions.offsetO.as(OffsetCondition)
    val instMove =
      pauseConditions.contains(PauseCondition.InstConfigMove).option(InstrumentCondition)

    val reasons = List(unguided, offset, instMove).flattenOption

    reasons.nonEmpty.option {
      L.debug(s"Send pause command to GeMS, reasons: $reasons") *>
        epicsSys.loopControl.setCommand(PauseCmd) *>
        epicsSys.loopControl.setReasons(reasons.mkString("|")) *>
        epicsSys.loopControl.post(CmdTimeout) *>
        L.debug("Pause command sent to GeMS")
    }

  }

  private def resume(resumeConditions: ResumeConditionSet): Option[F[Unit]] = {
    val unguided = resumeConditions.contains(ResumeCondition.GaosGuideOn).option(UnguidedCondition)
    val offset   = resumeConditions.offsetO.as(OffsetCondition)
    val instMove =
      resumeConditions.contains(ResumeCondition.InstConfigCompleted).option(InstrumentCondition)

    val reasons = List(unguided, offset, instMove).flattenOption

    reasons.nonEmpty.option {
      L.debug(s"Send resume command to GeMS, reasons: $reasons") *>
        epicsSys.loopControl.setCommand(ResumeCmd) *>
        epicsSys.loopControl.setReasons(reasons.mkString("|")) *>
        epicsSys.loopControl.post(CmdTimeout) *>
        epicsSys.waitForStableLoops(LoopStabilizationTimeout) *>
        L.debug("Resume command sent to GeMS")
    }
  }

}

object GemsControllerEpics {

  def apply[F[_]: Async: Logger](
    epicsSys:    => GemsEpics[F],
    gsaoiGuider: GsaoiGuider[F]
  ): GemsController[F] = new GemsControllerEpics[F](epicsSys, gsaoiGuider)

  final case class EpicsGems(
    cwfs1: Cwfs1DetectorState,
    cwfs2: Cwfs2DetectorState,
    cwfs3: Cwfs3DetectorState,
    odgw1: Odgw1DetectorState,
    odgw2: Odgw2DetectorState,
    odgw3: Odgw3DetectorState,
    odgw4: Odgw4DetectorState
  )

  val UnguidedCondition: String   = "Sky"
  val InstrumentCondition: String = "Filter"
  val OffsetCondition: String     = "Dither"

  val PauseCmd: String  = "PAUSE"
  val ResumeCmd: String = "RESUME"

  val CmdTimeout: TimeSpan               =
    TimeSpan.unsafeFromDuration(10, ChronoUnit.SECONDS)
  val LoopStabilizationTimeout: TimeSpan =
    TimeSpan.unsafeFromDuration(30, ChronoUnit.SECONDS)

  val ApdStart: String = "START"
  val ApdStop: String  = "STOP"

}
