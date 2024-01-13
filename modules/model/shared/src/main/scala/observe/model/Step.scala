// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import cats.syntax.all.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.gmos.DynamicConfig
import observe.model.enums.PendingObserveCmd
import monocle.Focus
import monocle.Lens
import monocle.Optional
import monocle.Prism
import monocle.macros.GenPrism
import monocle.syntax.all.*
import observe.model.dhs.*
import observe.model.enums.*
import cats.Eq
import io.circe.*
import cats.derived.*
import lucuma.odb.json.gmos.given
import lucuma.odb.json.wavelength.transport.given
import lucuma.odb.json.offset.transport.given
import lucuma.odb.json.time.transport.given
import lucuma.odb.json.stepconfig.given

enum Step(
  val id:           StepId,
  val instConfig:   DynamicConfig,
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
    override val id:           StepId,
    override val instConfig:   DynamicConfig,
    override val stepConfig:   StepConfig,
    override val status:       StepState,
    override val breakpoint:   Breakpoint,
    override val skip:         Boolean,
    override val fileId:       Option[ImageFileId],
    override val configStatus: List[(Resource | Instrument, ActionStatus)],
    val observeStatus:         ActionStatus
  ) extends Step(id, instConfig, stepConfig, status, breakpoint, skip, fileId, configStatus)

  case NodAndShuffle(
    override val id:           StepId,
    override val instConfig:   DynamicConfig,
    override val stepConfig:   StepConfig,
    override val status:       StepState,
    override val breakpoint:   Breakpoint,
    override val skip:         Boolean,
    override val fileId:       Option[ImageFileId],
    override val configStatus: List[(Resource | Instrument, ActionStatus)],
    val nsStatus:              NodAndShuffleStatus,
    val pendingObserveCmd:     Option[PendingObserveCmd]
  ) extends Step(id, instConfig, stepConfig, status, breakpoint, skip, fileId, configStatus)

object Step:
  given Eq[Standard]      = summon[Eq[Step]].contramap(identity)
  given Eq[NodAndShuffle] = summon[Eq[Step]].contramap(identity)

  extension [A](l: Lens[A, Boolean]) {
    def negate: A => A = l.modify(!_)
  }
  extension [A](l: Lens[A, Breakpoint]) {
    def flip: A => A =
      l.modify(b => if (b === Breakpoint.Enabled) Breakpoint.Disabled else Breakpoint.Enabled)
  }
  def standardStepP: Prism[Step, Standard] =
    GenPrism[Step, Standard]

  def nsStepP: Prism[Step, NodAndShuffle] =
    GenPrism[Step, NodAndShuffle]

  def status: Lens[Step, StepState] =
    Lens[Step, StepState] {
      _.status
    } { n =>
      {
        case s: Standard      => s.focus(_.status).replace(n)
        case s: NodAndShuffle => s.focus(_.status).replace(n)
      }
    }

  def id: Lens[Step, StepId] =
    Lens[Step, StepId] {
      _.id
    } { n =>
      {
        case s: Standard      => s.focus(_.id).replace(n)
        case s: NodAndShuffle => s.focus(_.id).replace(n)
      }
    }

  def instConfig: Lens[Step, DynamicConfig] =
    Lens[Step, DynamicConfig] {
      _.instConfig
    } { d =>
      {
        case s: Standard      => s.focus(_.instConfig).replace(d)
        case s: NodAndShuffle => s.focus(_.instConfig).replace(d)
      }
    }

  def stepConfig: Lens[Step, StepConfig] =
    Lens[Step, StepConfig] {
      _.stepConfig
    } { d =>
      {
        case s: Standard      => s.focus(_.stepConfig).replace(d)
        case s: NodAndShuffle => s.focus(_.stepConfig).replace(d)
      }
    }

  def skip: Lens[Step, Boolean] =
    Lens[Step, Boolean] {
      _.skip
    } { n =>
      {
        case s: Standard      => s.focus(_.skip).replace(n)
        case s: NodAndShuffle => s.focus(_.skip).replace(n)
      }
    }

  def breakpoint: Lens[Step, Breakpoint] =
    Lens[Step, Breakpoint] {
      _.breakpoint
    } { n =>
      {
        case s: Standard      => s.focus(_.breakpoint).replace(n)
        case s: NodAndShuffle => s.focus(_.breakpoint).replace(n)
      }
    }

  def observeStatus: Optional[Step, ActionStatus] =
    Optional[Step, ActionStatus] {
      case s: Standard      => s.observeStatus.some
      case s: NodAndShuffle => s.nsStatus.observing.some
    } { n =>
      {
        case s: Standard      => s.focus(_.observeStatus).replace(n)
        case s: NodAndShuffle => s.focus(_.nsStatus.observing).replace(n)
      }
    }

  def configStatus: Lens[Step, List[(Resource | Instrument, ActionStatus)]] =
    Lens[Step, List[(Resource | Instrument, ActionStatus)]] {
      case s: Standard      => s.configStatus
      case s: NodAndShuffle => s.configStatus
    } { n =>
      {
        case s: Standard      => s.focus(_.configStatus).replace(n)
        case s: NodAndShuffle => s.focus(_.configStatus).replace(n)
      }
    }

  extension (s: Step) {
    def flipBreakpoint: Step =
      s match
        case st: Standard      => Focus[Standard](_.breakpoint).flip(st)
        case st: NodAndShuffle => Focus[NodAndShuffle](_.breakpoint).flip(st)

    def flipSkip: Step =
      s match
        case st: Standard      => Focus[Standard](_.skip).negate(st)
        case st: NodAndShuffle => Focus[NodAndShuffle](_.skip).negate(st)

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
