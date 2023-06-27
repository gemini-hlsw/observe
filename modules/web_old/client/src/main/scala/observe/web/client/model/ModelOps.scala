// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats.Show
import cats.data.NonEmptyList
import cats.syntax.all.*
import lucuma.core.enums.Site
import mouse.all.booleanSyntaxMouse
import observe.model.{ SequenceState, SequenceView, Step, StepId, StepState }
import observe.model.enums.Instrument
import observe.model.enums.Resource

/**
 * Contains useful operations for the observe model
 */
object ModelOps {

  given Show[SequenceState] =
    Show.show[SequenceState] {
      case SequenceState.Completed        => "Complete"
      case SequenceState.Running(true, _) => "Pausing..."
      case SequenceState.Running(_, _)    => "Running"
      case SequenceState.Idle             => "Idle"
      case SequenceState.Aborted          => "Aborted"
      case SequenceState.Failed(_)        => s"Error at step "
    }

  given Show[Step] = Show.show[Step] { s =>
    s.status match {
      case StepState.Pending                      => "Pending"
      case StepState.Completed                    => "Done"
      case StepState.Skipped                      => "Skipped"
      case StepState.Failed(msg)                  => msg
      case StepState.Running if s.isObserving     => "Observing..."
      case StepState.Running if s.isObservePaused => "Exposure paused"
      case StepState.Running if s.isConfiguring   => "Configuring..."
      case StepState.Running                      => "Running..."
      case StepState.Paused                       => "Paused"
      case StepState.Aborted                      => "Aborted"
    }
  }

  given Show[Resource] = Show.show[Resource] {
    case Resource.TCS    => "TCS"
    case Resource.Gcal   => "GCAL"
    case Resource.Gems   => "GeMS"
    case Resource.Altair => "Altair"
    case Resource.P1     => "P1"
    case Resource.OI     => "OI"
    case i: Instrument   => i.show
  }

  extension(s: SequenceView) {

    def allStepsDone: Boolean = s.steps.forall(_.status === StepState.Completed)

    def flipSkipMarkAtStep(step: Step): SequenceView =
      s.copy(steps = s.steps.collect {
        case st if st.id === step.id => Step.skip.modify(!_)(st)
        case st                      => st
      })

    def flipBreakpointAtStep(step: Step): SequenceView =
      s.copy(steps = s.steps.collect {
        case st if st.id === step.id => Step.breakpoint.modify(!_)(st)
        case st                      => st
      })

    def nextStepToRun: Option[StepId] = s.steps.find(s => !s.isFinished && !s.skip).map(_.id)

    def nextStepToRunIndex: Option[Int] =
      s.steps.indexWhere(s => !s.isFinished && !s.skip).some.flatMap(x => (x >= 0).option(x))

    def isPartiallyExecuted: Boolean = s.steps.exists(_.isFinished)

  }

  extension(s: Site) {

    def instruments: NonEmptyList[Instrument] =
      s match {
        case Site.GN => Instrument.gnInstruments
        case Site.GS => Instrument.gsInstruments
      }
  }

  sealed trait InstrumentProperties

  object InstrumentProperties {
    case object Exposure      extends InstrumentProperties
    case object Filter        extends InstrumentProperties
    case object Disperser     extends InstrumentProperties
    case object Offsets       extends InstrumentProperties
    case object FPU           extends InstrumentProperties
    case object ObservingMode extends InstrumentProperties
    case object Camera        extends InstrumentProperties
    case object Decker        extends InstrumentProperties
    case object ImagingMirror extends InstrumentProperties
    case object ReadMode      extends InstrumentProperties
  }

  extension(i: Instrument) {

    def displayItems: Set[InstrumentProperties] =
      i match {
        case Instrument.F2    =>
          Set(InstrumentProperties.Exposure,
              InstrumentProperties.Filter,
              InstrumentProperties.Offsets,
              InstrumentProperties.FPU
          )
        case Instrument.Nifs  =>
          Set(
            InstrumentProperties.Exposure,
            InstrumentProperties.Filter,
            InstrumentProperties.Offsets,
            InstrumentProperties.Disperser,
            InstrumentProperties.FPU,
            InstrumentProperties.ImagingMirror
          )
        case Instrument.GmosS =>
          Set(InstrumentProperties.Exposure,
              InstrumentProperties.Filter,
              InstrumentProperties.Offsets,
              InstrumentProperties.Disperser,
              InstrumentProperties.FPU
          )
        case Instrument.GmosN =>
          Set(InstrumentProperties.Exposure,
              InstrumentProperties.Filter,
              InstrumentProperties.Offsets,
              InstrumentProperties.Disperser,
              InstrumentProperties.FPU
          )
        case Instrument.Gnirs =>
          Set(
            InstrumentProperties.Exposure,
            InstrumentProperties.Filter,
            InstrumentProperties.Offsets,
            InstrumentProperties.Disperser,
            InstrumentProperties.Decker,
            InstrumentProperties.FPU
          )
        case Instrument.Gpi   =>
          Set(InstrumentProperties.Exposure,
              InstrumentProperties.Filter,
              InstrumentProperties.ObservingMode,
              InstrumentProperties.Disperser
          )
        case Instrument.Niri  =>
          Set(InstrumentProperties.Exposure,
              InstrumentProperties.Offsets,
              InstrumentProperties.Filter,
              InstrumentProperties.Camera
          )
        case Instrument.Gsaoi =>
          Set(InstrumentProperties.Exposure,
              InstrumentProperties.Offsets,
              InstrumentProperties.Filter,
              InstrumentProperties.ReadMode
          )
        case Instrument.Ghost => Set.empty
        case _                =>
          Set(InstrumentProperties.Exposure,
              InstrumentProperties.Filter,
              InstrumentProperties.Offsets,
              InstrumentProperties.FPU
          )
      }
  }

}
