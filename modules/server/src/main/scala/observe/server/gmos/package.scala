// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import observe.common.ObsQueriesGQL.ObsQuery.GmosSite
import observe.engine.Result.PartialVal
import observe.model.NSSubexposure
import observe.model.enums.NSAction

package object gmos {
  type GmosSouthController[F[_]] = GmosController[F, GmosSite.South]

  type GmosNorthController[F[_]] = GmosController[F, GmosSite.North]
}

package gmos {

  import lucuma.core.util.Enumerated

  sealed trait NSPartial extends PartialVal {
    def ongoingAction: NSAction
    def sub: NSSubexposure
  }
  object NSPartial {
    def unapply(s: NSPartial): Option[(NSAction, NSSubexposure)] =
      Some((s.ongoingAction, s.sub))

    case class NSStart(sub: NSSubexposure)            extends NSPartial {
      override val ongoingAction: NSAction = NSAction.Start
    }
    case class NSTCSNodStart(sub: NSSubexposure)      extends NSPartial {
      override val ongoingAction: NSAction = NSAction.NodStart
    }
    case class NSTCSNodComplete(sub: NSSubexposure)   extends NSPartial {
      override val ongoingAction: NSAction = NSAction.NodComplete
    }
    case class NSSubexposureStart(sub: NSSubexposure) extends NSPartial {
      override val ongoingAction: NSAction = NSAction.StageObserveStart
    }
    case class NSSubexposureEnd(sub: NSSubexposure)   extends NSPartial {
      override val ongoingAction: NSAction = NSAction.StageObserveComplete
    }
    case class NSComplete(sub: NSSubexposure)         extends NSPartial {
      override val ongoingAction: NSAction = NSAction.Done
    }

    case object NSContinue  extends InternalPartialVal
    case object NSSubPaused extends InternalPartialVal
    case object NSFinalObs  extends InternalPartialVal

  }

  sealed abstract class NSObserveCommand(val tag: String) extends Product with Serializable

  object NSObserveCommand {
    case object StopGracefully   extends NSObserveCommand("StopGracefully")
    case object StopImmediately  extends NSObserveCommand("StopImmediately")
    case object AbortGracefully  extends NSObserveCommand("AbortGracefully")
    case object AbortImmediately extends NSObserveCommand("AbortImmediately")
    case object PauseGracefully  extends NSObserveCommand("PauseGracefully")
    case object PauseImmediately extends NSObserveCommand("PauseImmediately")

    given Enumerated[NSObserveCommand] =
      Enumerated
        .from(
          StopGracefully,
          StopImmediately,
          AbortGracefully,
          AbortImmediately,
          PauseGracefully,
          PauseImmediately
        )
        .withTag(_.tag)
  }

}
