// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import cats.syntax.all.*
import lucuma.core.util.Enumerated
import monocle.{Focus, Lens, Optional, Prism}
import monocle.macros.GenPrism
import observe.model.dhs.{*, given}
import observe.model.enums.*

sealed trait Step extends Product with Serializable {
  def id: StepId
  def config: StepConfig
  def status: StepState
  def breakpoint: Boolean
  def skip: Boolean
  def fileId: Option[ImageFileId]
}

object Step {
  extension [A](l: Lens[A, Boolean]) {
    def negate: A => A = l.modify(!_)
  }
  val standardStepP: Prism[Step, StandardStep] =
    GenPrism[Step, StandardStep]

  val nsStepP: Prism[Step, NodAndShuffleStep] =
    GenPrism[Step, NodAndShuffleStep]

  val status: Lens[Step, StepState] =
    Lens[Step, StepState] {
      _.status
    } { n => a =>
      a match {
        case s: StandardStep      => Focus[StandardStep](_.status).replace(n)(s)
        case s: NodAndShuffleStep => Focus[NodAndShuffleStep](_.status).replace(n)(s)
      }
    }

  val config: Lens[Step, StepConfig] =
    Lens[Step, StepConfig] {
      _.config
    } { n => a =>
      a match {
        case s: StandardStep      => Focus[StandardStep](_.config).replace(n)(s)
        case s: NodAndShuffleStep => Focus[NodAndShuffleStep](_.config).replace(n)(s)
      }
    }

  val id: Lens[Step, StepId] =
    Lens[Step, StepId] {
      _.id
    } { n => a =>
      a match {
        case s: StandardStep      => Focus[StandardStep](_.id).replace(n)(s)
        case s: NodAndShuffleStep => Focus[NodAndShuffleStep](_.id).replace(n)(s)
      }
    }

  val skip: Lens[Step, Boolean] =
    Lens[Step, Boolean] {
      _.skip
    } { n => a =>
      a match {
        case s: StandardStep      => Focus[StandardStep](_.skip).replace(n)(s)
        case s: NodAndShuffleStep => Focus[NodAndShuffleStep](_.skip).replace(n)(s)
      }
    }

  val breakpoint: Lens[Step, Boolean] =
    Lens[Step, Boolean] {
      _.breakpoint
    } { n => a =>
      a match {
        case s: StandardStep      => Focus[StandardStep](_.breakpoint).replace(n)(s)
        case s: NodAndShuffleStep => Focus[NodAndShuffleStep](_.breakpoint).replace(n)(s)
      }
    }

  val observeStatus: Optional[Step, ActionStatus] =
    Optional[Step, ActionStatus] {
      case s: StandardStep      => s.observeStatus.some
      case s: NodAndShuffleStep => s.nsStatus.observing.some
    } { n => a =>
      a match {
        case s: StandardStep      => Focus[StandardStep](_.observeStatus).replace(n)(s)
        case s: NodAndShuffleStep =>
          Focus[NodAndShuffleStep](_.nsStatus)
            .andThen(Focus[NodAndShuffleStatus](_.observing))
            .replace(n)(s)
      }
    }

  val configStatus: Optional[Step, List[(Resource, ActionStatus)]] =
    Optional[Step, List[(Resource, ActionStatus)]] {
      case s: StandardStep      => s.configStatus.some
      case s: NodAndShuffleStep => s.configStatus.some
    } { n => a =>
      a match {
        case s: StandardStep      => Focus[StandardStep](_.configStatus).replace(n)(s)
        case s: NodAndShuffleStep => Focus[NodAndShuffleStep](_.configStatus).replace(n)(s)
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
        case st: StandardStep      => Focus[StandardStep](_.breakpoint).negate(st)
        case st: NodAndShuffleStep => Focus[NodAndShuffleStep](_.breakpoint).negate(st)
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

final case class StandardStep(
  override val id:         StepId,
  override val config:     StepConfig,
  override val status:     StepState,
  override val breakpoint: Boolean,
  override val skip:       Boolean,
  override val fileId:     Option[ImageFileId],
  configStatus:            List[(Resource, ActionStatus)],
  observeStatus:           ActionStatus
) extends Step

object StandardStep {
  given Eq[StandardStep] = Eq.by(x =>
    (x.id, x.config, x.status, x.breakpoint, x.skip, x.fileId, x.configStatus, x.observeStatus)
  )
}

// Other kinds of Steps to be defined.
final case class NodAndShuffleStep(
  override val id:         StepId,
  override val config:     StepConfig,
  override val status:     StepState,
  override val breakpoint: Boolean,
  override val skip:       Boolean,
  override val fileId:     Option[ImageFileId],
  configStatus:            List[(Resource, ActionStatus)],
  nsStatus:                NodAndShuffleStatus,
  pendingObserveCmd:       Option[NodAndShuffleStep.PendingObserveCmd]
) extends Step

object NodAndShuffleStep {
  given Eq[NodAndShuffleStep] = Eq.by(x =>
    (x.id, x.config, x.status, x.breakpoint, x.skip, x.fileId, x.configStatus, x.nsStatus)
  )

  sealed abstract class PendingObserveCmd(val tag: String) extends Product with Serializable
  case object PauseGracefully                              extends PendingObserveCmd("PauseGracefully")
  case object StopGracefully                               extends PendingObserveCmd("StopGracefully")

  given Enumerated[PendingObserveCmd] =
    Enumerated.from(PauseGracefully, StopGracefully).withTag(_.tag)

}
