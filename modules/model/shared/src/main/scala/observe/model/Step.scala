// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import cats.syntax.all.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.util.Enumerated
import monocle.Focus
import monocle.Lens
import monocle.Optional
import monocle.Prism
import monocle.macros.GenPrism
import monocle.syntax.all.*
import observe.model.dhs.*
import observe.model.enums.*

sealed trait Step extends Product with Serializable {
  def id: StepId
  def instConfig: DynamicConfig
  def stepConfig: StepConfig
  def status: StepState
  def breakpoint: Breakpoint
  def skip: Boolean
  def fileId: Option[ImageFileId]
}

object Step {
  extension [A](l: Lens[A, Boolean]) {
    def negate: A => A = l.modify(!_)
  }
  extension [A](l: Lens[A, Breakpoint]) {
    def flip: A => A =
      l.modify(b => if (b === Breakpoint.Enabled) Breakpoint.Disabled else Breakpoint.Enabled)
  }
  def standardStepP: Prism[Step, StandardStep] =
    GenPrism[Step, StandardStep]

  def nsStepP: Prism[Step, NodAndShuffleStep] =
    GenPrism[Step, NodAndShuffleStep]

  def status: Lens[Step, StepState] =
    Lens[Step, StepState] {
      _.status
    } { n =>
      {
        case s: StandardStep      => s.focus(_.status).replace(n)
        case s: NodAndShuffleStep => s.focus(_.status).replace(n)
      }
    }

  def id: Lens[Step, StepId] =
    Lens[Step, StepId] {
      _.id
    } { n =>
      {
        case s: StandardStep      => s.focus(_.id).replace(n)
        case s: NodAndShuffleStep => s.focus(_.id).replace(n)
      }
    }

  def instConfig: Lens[Step, DynamicConfig] =
    Lens[Step, DynamicConfig] {
      _.instConfig
    } { d =>
      {
        case s: StandardStep      => s.focus(_.instConfig).replace(d)
        case s: NodAndShuffleStep => s.focus(_.instConfig).replace(d)
      }
    }

  def stepConfig: Lens[Step, StepConfig] =
    Lens[Step, StepConfig] {
      _.stepConfig
    } { d =>
      {
        case s: StandardStep      => s.focus(_.stepConfig).replace(d)
        case s: NodAndShuffleStep => s.focus(_.stepConfig).replace(d)
      }
    }

  def skip: Lens[Step, Boolean] =
    Lens[Step, Boolean] {
      _.skip
    } { n =>
      {
        case s: StandardStep      => s.focus(_.skip).replace(n)
        case s: NodAndShuffleStep => s.focus(_.skip).replace(n)
      }
    }

  def breakpoint: Lens[Step, Breakpoint] =
    Lens[Step, Breakpoint] {
      _.breakpoint
    } { n =>
      {
        case s: StandardStep      => s.focus(_.breakpoint).replace(n)
        case s: NodAndShuffleStep => s.focus(_.breakpoint).replace(n)
      }
    }

  def observeStatus: Optional[Step, ActionStatus] =
    Optional[Step, ActionStatus] {
      case s: StandardStep      => s.observeStatus.some
      case s: NodAndShuffleStep => s.nsStatus.observing.some
    } { n =>
      {
        case s: StandardStep      => s.focus(_.observeStatus).replace(n)
        case s: NodAndShuffleStep => s.focus(_.nsStatus.observing).replace(n)
      }
    }

  def configStatus: Lens[Step, List[(Resource | Instrument, ActionStatus)]] =
    Lens[Step, List[(Resource | Instrument, ActionStatus)]] {
      case s: StandardStep      => s.configStatus
      case s: NodAndShuffleStep => s.configStatus
    } { n =>
      {
        case s: StandardStep      => s.focus(_.configStatus).replace(n)
        case s: NodAndShuffleStep => s.focus(_.configStatus).replace(n)
      }
    }

  given Eq[Step] =
    Eq.instance {
      case (x: StandardStep, y: StandardStep)           =>
        x === y
      case (x: NodAndShuffleStep, y: NodAndShuffleStep) =>
        x === y
      case _                                            =>
        false
    }

  extension (s: Step) {
    def flipBreakpoint: Step =
      s match {
        case st: StandardStep      => Focus[StandardStep](_.breakpoint).flip(st)
        case st: NodAndShuffleStep => Focus[NodAndShuffleStep](_.breakpoint).flip(st)
      }

    def flipSkip: Step =
      s match {
        case st: StandardStep      => Focus[StandardStep](_.skip).negate(st)
        case st: NodAndShuffleStep => Focus[NodAndShuffleStep](_.skip).negate(st)
      }

    def file: Option[String] = None

    def canSetBreakpoint(steps: List[Step]): Boolean =
      s.status.canSetBreakpoint && steps
        .dropWhile(_.status.isFinished)
        .drop(1)
        .exists(_.id === s.id)

    def canSetSkipmark: Boolean = s.status.canSetSkipmark

    def hasError: Boolean = s.status.hasError

    def isRunning: Boolean = s.status.isRunning

    def runningOrComplete: Boolean = s.status.runningOrComplete

    def isObserving: Boolean =
      s match {
        case x: StandardStep      => x.observeStatus === ActionStatus.Running
        case x: NodAndShuffleStep => x.nsStatus.observing === ActionStatus.Running
      }

    def isObservePaused: Boolean =
      s match {
        case x: StandardStep      => x.observeStatus === ActionStatus.Paused
        case x: NodAndShuffleStep => x.nsStatus.observing === ActionStatus.Paused
      }

    def isConfiguring: Boolean =
      s match {
        case x: StandardStep      => x.configStatus.count(_._2 === ActionStatus.Running) > 0
        case x: NodAndShuffleStep => x.configStatus.count(_._2 === ActionStatus.Running) > 0
      }

    def isFinished: Boolean = s.status.isFinished

    def wasSkipped: Boolean = s.status.wasSkipped

    def canConfigure: Boolean = s.status.canConfigure

    def isMultiLevel: Boolean =
      s match {
        case _: NodAndShuffleStep => true
        case _                    => false
      }
  }
}

case class StandardStep(
  id:            StepId,
  instConfig:    DynamicConfig,
  stepConfig:    lucuma.core.model.sequence.StepConfig,
  status:        StepState,
  breakpoint:    Breakpoint,
  skip:          Boolean,
  fileId:        Option[ImageFileId],
  configStatus:  List[(Resource | Instrument, ActionStatus)],
  observeStatus: ActionStatus
) extends Step

object StandardStep {
  given Eq[StandardStep] =
    Eq.by(x =>
      (x.id,
       x.instConfig,
       x.stepConfig,
       x.status,
       x.breakpoint,
       x.skip,
       x.fileId,
       x.configStatus,
       x.observeStatus
      )
    )
}

// Other kinds of Steps to be defined.
case class NodAndShuffleStep(
  id:                StepId,
  instConfig:        DynamicConfig,
  stepConfig:        lucuma.core.model.sequence.StepConfig,
  status:            StepState,
  breakpoint:        Breakpoint,
  skip:              Boolean,
  fileId:            Option[ImageFileId],
  configStatus:      List[(Resource | Instrument, ActionStatus)],
  nsStatus:          NodAndShuffleStatus,
  pendingObserveCmd: Option[NodAndShuffleStep.PendingObserveCmd]
) extends Step

object NodAndShuffleStep {
  given Eq[NodAndShuffleStep] =
    Eq.by(x =>
      (x.id,
       x.instConfig,
       x.stepConfig,
       x.status,
       x.breakpoint,
       x.skip,
       x.fileId,
       x.configStatus,
       x.nsStatus
      )
    )

  sealed abstract class PendingObserveCmd(val tag: String) extends Product with Serializable
  case object PauseGracefully                              extends PendingObserveCmd("PauseGracefully")
  case object StopGracefully                               extends PendingObserveCmd("StopGracefully")

  given Enumerated[PendingObserveCmd] =
    Enumerated.from(PauseGracefully, StopGracefully).withTag(_.tag)

}
