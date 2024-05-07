// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import io.circe.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.StepConfig
import lucuma.odb.json.offset.transport.given
import lucuma.odb.json.stepconfig.given
import monocle.Focus
import monocle.Lens
import monocle.Optional
import monocle.Prism
import monocle.macros.GenPrism
import monocle.syntax.all.*
import observe.model.dhs.*
import observe.model.enums.*
import observe.model.enums.PendingObserveCmd
// import observe.model.codecs.given
// import lucuma.odb.json.sequence.given
// import lucuma.schemas.odb.given

enum ObserveStep(
  val id:           Step.Id,
  val instConfig:   InstrumentDynamicConfig,
  val stepConfig:   StepConfig,
  val status:       StepState,
  val breakpoint:   Breakpoint,
  val skip:         Boolean,
  val fileId:       Option[ImageFileId],
  val configStatus: List[(Resource | Instrument, ActionStatus)]
) derives Eq,
      Encoder.AsObject,
      Decoder:
  case Standard(
    override val id:           Step.Id,
    override val instConfig:   InstrumentDynamicConfig,
    override val stepConfig:   StepConfig,
    override val status:       StepState,
    override val breakpoint:   Breakpoint,
    override val skip:         Boolean,
    override val fileId:       Option[ImageFileId],
    override val configStatus: List[(Resource | Instrument, ActionStatus)],
    val observeStatus:         ActionStatus
  ) extends ObserveStep(id, instConfig, stepConfig, status, breakpoint, skip, fileId, configStatus)

  case NodAndShuffle(
    override val id:           Step.Id,
    override val instConfig:   InstrumentDynamicConfig,
    override val stepConfig:   StepConfig,
    override val status:       StepState,
    override val breakpoint:   Breakpoint,
    override val skip:         Boolean,
    override val fileId:       Option[ImageFileId],
    override val configStatus: List[(Resource | Instrument, ActionStatus)],
    val nsStatus:              NodAndShuffleStatus,
    val pendingObserveCmd:     Option[PendingObserveCmd]
  ) extends ObserveStep(id, instConfig, stepConfig, status, breakpoint, skip, fileId, configStatus)

object ObserveStep:
  // Derivation doesn't generate instances for subtypes.
  given Eq[Standard]      = Eq.by: x =>
    (x.id, x.instConfig, x.stepConfig, x.status, x.breakpoint, x.skip, x.fileId, x.configStatus)
  given Eq[NodAndShuffle] = Eq.by: x =>
    (x.id,
     x.instConfig,
     x.stepConfig,
     x.status,
     x.breakpoint,
     x.skip,
     x.fileId,
     x.configStatus,
     x.nsStatus,
     x.pendingObserveCmd
    )

  extension [A](l: Lens[A, Boolean]) {
    def negate: A => A = l.modify(!_)
  }

  extension [A](l: Lens[A, Breakpoint]) {
    def flip: A => A =
      l.modify(b => if (b === Breakpoint.Enabled) Breakpoint.Disabled else Breakpoint.Enabled)
  }

  def standardStepP: Prism[ObserveStep, Standard] =
    GenPrism[ObserveStep, Standard]

  def nsStepP: Prism[ObserveStep, NodAndShuffle] =
    GenPrism[ObserveStep, NodAndShuffle]

  def status: Lens[ObserveStep, StepState] =
    Lens[ObserveStep, StepState] {
      _.status
    } { n =>
      {
        case s: Standard      => s.focus(_.status).replace(n)
        case s: NodAndShuffle => s.focus(_.status).replace(n)
      }
    }

  def id: Lens[ObserveStep, Step.Id] =
    Lens[ObserveStep, Step.Id] {
      _.id
    } { n =>
      {
        case s: Standard      => s.focus(_.id).replace(n)
        case s: NodAndShuffle => s.focus(_.id).replace(n)
      }
    }

  def instConfig: Lens[ObserveStep, InstrumentDynamicConfig] =
    Lens[ObserveStep, InstrumentDynamicConfig] {
      _.instConfig
    } { d =>
      {
        case s: Standard      => s.focus(_.instConfig).replace(d)
        case s: NodAndShuffle => s.focus(_.instConfig).replace(d)
      }
    }

  def stepConfig: Lens[ObserveStep, StepConfig] =
    Lens[ObserveStep, StepConfig] {
      _.stepConfig
    } { d =>
      {
        case s: Standard      => s.focus(_.stepConfig).replace(d)
        case s: NodAndShuffle => s.focus(_.stepConfig).replace(d)
      }
    }

  def skip: Lens[ObserveStep, Boolean] =
    Lens[ObserveStep, Boolean] {
      _.skip
    } { n =>
      {
        case s: Standard      => s.focus(_.skip).replace(n)
        case s: NodAndShuffle => s.focus(_.skip).replace(n)
      }
    }

  def breakpoint: Lens[ObserveStep, Breakpoint] =
    Lens[ObserveStep, Breakpoint] {
      _.breakpoint
    } { n =>
      {
        case s: Standard      => s.focus(_.breakpoint).replace(n)
        case s: NodAndShuffle => s.focus(_.breakpoint).replace(n)
      }
    }

  def observeStatus: Optional[ObserveStep, ActionStatus] =
    Optional[ObserveStep, ActionStatus] {
      case s: Standard      => s.observeStatus.some
      case s: NodAndShuffle => s.nsStatus.observing.some
    } { n =>
      {
        case s: Standard      => s.focus(_.observeStatus).replace(n)
        case s: NodAndShuffle => s.focus(_.nsStatus.observing).replace(n)
      }
    }

  def configStatus: Lens[ObserveStep, List[(Resource | Instrument, ActionStatus)]] =
    Lens[ObserveStep, List[(Resource | Instrument, ActionStatus)]] {
      case s: Standard      => s.configStatus
      case s: NodAndShuffle => s.configStatus
    } { n =>
      {
        case s: Standard      => s.focus(_.configStatus).replace(n)
        case s: NodAndShuffle => s.focus(_.configStatus).replace(n)
      }
    }

  extension (s: ObserveStep) {
    def flipBreakpoint: ObserveStep =
      s match
        case st: Standard      => Focus[Standard](_.breakpoint).flip(st)
        case st: NodAndShuffle => Focus[NodAndShuffle](_.breakpoint).flip(st)

    def flipSkip: ObserveStep =
      s match
        case st: Standard      => Focus[Standard](_.skip).negate(st)
        case st: NodAndShuffle => Focus[NodAndShuffle](_.skip).negate(st)

    def file: Option[String] = None

    def canSetBreakpoint(steps: List[ObserveStep]): Boolean =
      s.status.canSetBreakpoint && steps
        .dropWhile(_.status.isFinished)
        .drop(1)
        .exists(_.id === s.id)

    def hasError: Boolean = s.status.hasError

    def isRunning: Boolean = s.status.isRunning

    def runningOrComplete: Boolean = s.status.runningOrComplete

    def isObserving: Boolean =
      s match
        case x: Standard      => x.observeStatus === ActionStatus.Running
        case x: NodAndShuffle => x.nsStatus.observing === ActionStatus.Running

    def isObservePaused: Boolean =
      s match
        case x: Standard      => x.observeStatus === ActionStatus.Paused
        case x: NodAndShuffle => x.nsStatus.observing === ActionStatus.Paused

    def isConfiguring: Boolean =
      s match
        case x: Standard      => x.configStatus.count(_._2 === ActionStatus.Running) > 0
        case x: NodAndShuffle => x.configStatus.count(_._2 === ActionStatus.Running) > 0

    def isFinished: Boolean = s.status.isFinished

    def wasSkipped: Boolean = s.status.wasSkipped

    def canConfigure: Boolean = s.status.canConfigure

    def isMultiLevel: Boolean =
      s match
        case _: NodAndShuffle => true
        case _                => false
  }
