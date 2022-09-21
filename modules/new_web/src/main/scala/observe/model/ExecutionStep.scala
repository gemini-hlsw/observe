// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats._
import cats.syntax.all._
import lucuma.core.util.Enumerated
import monocle.Lens
import monocle.Optional
import monocle.Prism
import monocle.Focus
import monocle.macros.GenPrism
import lucuma.core.model.sequence.Step
import observe.model.enums.*
import cats.derived.*
import lucuma.core.util.Display

// TODO Can we unify with lucuma.core.model.sequence.Step?
sealed trait ExecutionStep derives Eq:
  def id: Step.Id
  def config: ExecutionStepConfig
  def status: StepState
  def breakpoint: Boolean
  def skip: Boolean
  def fileId: Option[ImageFileId]

object ExecutionStep:
  extension [A](l: Lens[A, Boolean]) def negate: A => A = l.modify(!_)

  given Display[ExecutionStep] = Display.byShortName(s =>
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
  )

  val standardStepP: Prism[ExecutionStep, StandardStep] =
    GenPrism[ExecutionStep, StandardStep]

  val nsStepP: Prism[ExecutionStep, NodAndShuffleStep] =
    GenPrism[ExecutionStep, NodAndShuffleStep]

  val status: Lens[ExecutionStep, StepState] =
    Lens[ExecutionStep, StepState] {
      _.status
    } { n => a =>
      a match
        case s: StandardStep      => StandardStep.status.replace(n)(s)
        case s: NodAndShuffleStep => NodAndShuffleStep.status.replace(n)(s)
    }

  val config: Lens[ExecutionStep, ExecutionStepConfig] =
    Lens[ExecutionStep, ExecutionStepConfig] {
      _.config
    } { n => a =>
      a match
        case s: StandardStep      => StandardStep.config.replace(n)(s)
        case s: NodAndShuffleStep => NodAndShuffleStep.config.replace(n)(s)
    }

  val id: Lens[ExecutionStep, Step.Id] =
    Lens[ExecutionStep, Step.Id] {
      _.id
    } { n => a =>
      a match
        case s: StandardStep      => StandardStep.id.replace(n)(s)
        case s: NodAndShuffleStep => NodAndShuffleStep.id.replace(n)(s)
    }

  val skip: Lens[ExecutionStep, Boolean] =
    Lens[ExecutionStep, Boolean] {
      _.skip
    } { n => a =>
      a match
        case s: StandardStep      => StandardStep.skip.replace(n)(s)
        case s: NodAndShuffleStep => NodAndShuffleStep.skip.replace(n)(s)
    }

  val breakpoint: Lens[ExecutionStep, Boolean] =
    Lens[ExecutionStep, Boolean] {
      _.breakpoint
    } { n => a =>
      a match
        case s: StandardStep      => StandardStep.breakpoint.replace(n)(s)
        case s: NodAndShuffleStep => NodAndShuffleStep.breakpoint.replace(n)(s)
    }

  val observeStatus: Optional[ExecutionStep, ActionStatus] =
    Optional[ExecutionStep, ActionStatus] {
      case s: StandardStep      => s.observeStatus.some
      case s: NodAndShuffleStep => s.nsStatus.observing.some
    } { n => a =>
      a match
        case s: StandardStep      => StandardStep.observeStatus.replace(n)(s)
        case s: NodAndShuffleStep =>
          NodAndShuffleStep.nsStatus.andThen(NodAndShuffleStatus.observing).replace(n)(s)
    }

  val configStatus: Optional[ExecutionStep, List[(Resource, ActionStatus)]] =
    Optional[ExecutionStep, List[(Resource, ActionStatus)]] {
      case s: StandardStep      => s.configStatus.some
      case s: NodAndShuffleStep => s.configStatus.some
    } { n => a =>
      a match
        case s: StandardStep      => StandardStep.configStatus.replace(n)(s)
        case s: NodAndShuffleStep => NodAndShuffleStep.configStatus.replace(n)(s)
    }

  extension (s: ExecutionStep)
    def flipBreakpoint: ExecutionStep =
      s match
        case st: StandardStep      => StandardStep.breakpoint.negate(st)
        case st: NodAndShuffleStep => NodAndShuffleStep.breakpoint.negate(st)

    def flipSkip: ExecutionStep =
      s match
        case st: StandardStep      => StandardStep.skip.negate(st)
        case st: NodAndShuffleStep => NodAndShuffleStep.skip.negate(st)

    def file: Option[String] = None

    def canSetBreakpoint(steps: List[ExecutionStep]): Boolean =
      s.status.canSetBreakpoint && steps
        .dropWhile(_.status.isFinished)
        .drop(1)
        .exists(_.id === s.id)

    def canSetSkipmark: Boolean = s.status.canSetSkipmark

    def hasError: Boolean = s.status.hasError

    def isRunning: Boolean = s.status.isRunning

    def runningOrComplete: Boolean = s.status.runningOrComplete

    def isObserving: Boolean =
      s match
        case x: StandardStep      => x.observeStatus === ActionStatus.Running
        case x: NodAndShuffleStep => x.nsStatus.observing === ActionStatus.Running

    def isObservePaused: Boolean =
      s match
        case x: StandardStep      => x.observeStatus === ActionStatus.Paused
        case x: NodAndShuffleStep => x.nsStatus.observing === ActionStatus.Paused

    def isConfiguring: Boolean =
      s match
        case x: StandardStep      => x.configStatus.count(_._2 === ActionStatus.Running) > 0
        case x: NodAndShuffleStep => x.configStatus.count(_._2 === ActionStatus.Running) > 0

    def isFinished: Boolean = s.status.isFinished

    def wasSkipped: Boolean = s.status.wasSkipped

    def canConfigure: Boolean = s.status.canConfigure

    def isMultiLevel: Boolean =
      s match
        case _: NodAndShuffleStep => true
        case _                    => false

case class StandardStep(
  override val id:         Step.Id,
  override val config:     ExecutionStepConfig,
  override val status:     StepState,
  override val breakpoint: Boolean,
  override val skip:       Boolean,
  override val fileId:     Option[ImageFileId],
  configStatus:            List[(Resource, ActionStatus)],
  observeStatus:           ActionStatus
) extends ExecutionStep

object StandardStep:
  val id: Lens[StandardStep, Step.Id]                                  = Focus[StandardStep](_.id)
  val config: Lens[StandardStep, ExecutionStepConfig]                  = Focus[StandardStep](_.config)
  val status: Lens[StandardStep, StepState]                            = Focus[StandardStep](_.status)
  val breakpoint: Lens[StandardStep, Boolean]                          = Focus[StandardStep](_.breakpoint)
  val skip: Lens[StandardStep, Boolean]                                = Focus[StandardStep](_.skip)
  val fileId: Lens[StandardStep, Option[ImageFileId]]                  = Focus[StandardStep](_.fileId)
  val configStatus: Lens[StandardStep, List[(Resource, ActionStatus)]] =
    Focus[StandardStep](_.configStatus)
  val observeStatus: Lens[StandardStep, ActionStatus]                  = Focus[StandardStep](_.observeStatus)

// Other kinds of Steps to be defined.
case class NodAndShuffleStep(
  override val id:         Step.Id,
  override val config:     ExecutionStepConfig,
  override val status:     StepState,
  override val breakpoint: Boolean,
  override val skip:       Boolean,
  override val fileId:     Option[ImageFileId],
  configStatus:            List[(Resource, ActionStatus)],
  nsStatus:                NodAndShuffleStatus,
  pendingObserveCmd:       Option[NodAndShuffleStep.PendingObserveCmd]
) extends ExecutionStep

object NodAndShuffleStep:
  val id: Lens[NodAndShuffleStep, Step.Id]                                                    = Focus[NodAndShuffleStep](_.id)
  val config: Lens[NodAndShuffleStep, ExecutionStepConfig]                                    = Focus[NodAndShuffleStep](_.config)
  val status: Lens[NodAndShuffleStep, StepState]                                              = Focus[NodAndShuffleStep](_.status)
  val breakpoint: Lens[NodAndShuffleStep, Boolean]                                            = Focus[NodAndShuffleStep](_.breakpoint)
  val skip: Lens[NodAndShuffleStep, Boolean]                                                  = Focus[NodAndShuffleStep](_.skip)
  val fileId: Lens[NodAndShuffleStep, Option[ImageFileId]]                                    = Focus[NodAndShuffleStep](_.fileId)
  val configStatus: Lens[NodAndShuffleStep, List[(Resource, ActionStatus)]]                   =
    Focus[NodAndShuffleStep](_.configStatus)
  val nsStatus: Lens[NodAndShuffleStep, NodAndShuffleStatus]                                  =
    Focus[NodAndShuffleStep](_.nsStatus)
  val pendingObserveCmd: Lens[NodAndShuffleStep, Option[NodAndShuffleStep.PendingObserveCmd]] =
    Focus[NodAndShuffleStep](_.pendingObserveCmd)

  enum PendingObserveCmd derives Eq:
    case PauseGracefully, StopGracefully
