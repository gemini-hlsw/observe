// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import observe.engine.Result.PartialVal
import observe.model.NsSubexposure
import observe.model.enums.NsAction

import gmos.GmosController.GmosSite

package object gmos {
  type GmosSouthController[F[_]] = GmosController[F, GmosSite.South.type]

  type GmosNorthController[F[_]] = GmosController[F, GmosSite.North.type]

  import lucuma.core.util.Enumerated

  sealed trait NSPartial extends PartialVal {
    def ongoingAction: NsAction
    def sub: NsSubexposure
  }
  object NSPartial {
    def unapply(s: NSPartial): Option[(NsAction, NsSubexposure)] =
      Some((s.ongoingAction, s.sub))

    case class NSStart(sub: NsSubexposure)            extends NSPartial {
      override val ongoingAction: NsAction = NsAction.Start
    }
    case class NSTCSNodStart(sub: NsSubexposure)      extends NSPartial {
      override val ongoingAction: NsAction = NsAction.NodStart
    }
    case class NSTCSNodComplete(sub: NsSubexposure)   extends NSPartial {
      override val ongoingAction: NsAction = NsAction.NodComplete
    }
    case class NsSubexposureStart(sub: NsSubexposure) extends NSPartial {
      override val ongoingAction: NsAction = NsAction.StageObserveStart
    }
    case class NsSubexposureEnd(sub: NsSubexposure)   extends NSPartial {
      override val ongoingAction: NsAction = NsAction.StageObserveComplete
    }
    case class NSComplete(sub: NsSubexposure)         extends NSPartial {
      override val ongoingAction: NsAction = NsAction.Done
    }

    case object NSContinue  extends InternalPartialVal
    case object NSSubPaused extends InternalPartialVal
    case object NSFinalObs  extends InternalPartialVal

  }

  enum NSObserveCommand(val tag: String) derives Enumerated {
    case StopGracefully extends NSObserveCommand("StopGracefully")

    case StopImmediately extends NSObserveCommand("StopImmediately")

    case AbortGracefully extends NSObserveCommand("AbortGracefully")

    case AbortImmediately extends NSObserveCommand("AbortImmediately")

    case PauseGracefully extends NSObserveCommand("PauseGracefully")

    case PauseImmediately extends NSObserveCommand("PauseImmediately")
  }

}
