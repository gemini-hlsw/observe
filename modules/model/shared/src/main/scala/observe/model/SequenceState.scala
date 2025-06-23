// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.Encoder
import lucuma.core.util.Display
import lucuma.core.util.NewBoolean
import monocle.Focus
import monocle.Lens
import monocle.Prism
import monocle.macros.GenPrism

enum SequenceState(val name: String) derives Eq, Encoder, Decoder:
  case Idle                extends SequenceState("Idle")
  case Running(
    userStop:          SequenceState.HasUserStop,
    internalStop:      SequenceState.HasInternalStop,
    waitingUserPrompt: SequenceState.IsWaitingUserPrompt,
    waitingNextAtom:   SequenceState.IsWaitingNextAtom,
    starting:          SequenceState.IsStarting
  )                        extends SequenceState("Running")
  case Completed           extends SequenceState("Completed")
  case Failed(msg: String) extends SequenceState("Failed")
  case Aborted             extends SequenceState("Aborted")

  def isUserStopRequested: Boolean =
    this match
      case SequenceState.Running(b, _, _, _, _) => b
      case _                                    => false

  def isInternalStopRequested: Boolean =
    this match
      case SequenceState.Running(_, b, _, _, _) => b
      case _                                    => false

  def isStopRequested: Boolean =
    isUserStopRequested || isInternalStopRequested

  def isError: Boolean =
    this match
      case Failed(_) => true
      case _         => false

  def isInProcess: Boolean =
    this =!= SequenceState.Idle

  def isRunning: Boolean =
    this match
      case SequenceState.Running(_, _, _, _, _) => true
      case _                                    => false

  def isWaitingUserPrompt: Boolean =
    this match
      case SequenceState.Running(_, _, waitingUserPrompt, _, _) => waitingUserPrompt
      case _                                                    => false

  // A sequence can be unloaded if it's not running or if it's running but waiting for user prompt.
  def canUnload: Boolean =
    !isRunning || isWaitingUserPrompt

  def isStarting: Boolean =
    this match
      case SequenceState.Running(_, _, _, _, starting) => starting
      case _                                           => false

  def isCompleted: Boolean =
    this === SequenceState.Completed

  def isIdle: Boolean =
    this === SequenceState.Idle || this === SequenceState.Aborted

object SequenceState:
  given Display[SequenceState] = Display.byShortName(_.name)

  val running: Prism[SequenceState, SequenceState.Running] =
    GenPrism[SequenceState, SequenceState.Running]

  object HasUserStop extends NewBoolean { val Yes = True; val No = False }
  type HasUserStop = HasUserStop.Type

  object HasInternalStop extends NewBoolean { val Yes = True; val No = False }
  type HasInternalStop = HasInternalStop.Type

  object IsWaitingUserPrompt extends NewBoolean { val Yes = True; val No = False }
  type IsWaitingUserPrompt = IsWaitingUserPrompt.Type

  object IsWaitingNextAtom extends NewBoolean { val Yes = True; val No = False }
  type IsWaitingNextAtom = IsWaitingNextAtom.Type

  object IsStarting extends NewBoolean { val Yes = True; val No = False }
  type IsStarting = IsStarting.Type

  object Running:
    val Init: Running =
      SequenceState.Running(
        userStop = HasUserStop.No,
        internalStop = HasInternalStop.No,
        waitingUserPrompt = IsWaitingUserPrompt.No,
        waitingNextAtom = IsWaitingNextAtom.No,
        starting = IsStarting.No
      )

    val userStop: Lens[SequenceState.Running, HasUserStop] =
      Focus[SequenceState.Running](_.userStop)

    val internalStop: Lens[SequenceState.Running, HasInternalStop] =
      Focus[SequenceState.Running](_.internalStop)

    val waitingUserPrompt: Lens[SequenceState.Running, IsWaitingUserPrompt] =
      Focus[SequenceState.Running](_.waitingUserPrompt)

    val waitingNextAtom: Lens[SequenceState.Running, IsWaitingNextAtom] =
      Focus[SequenceState.Running](_.waitingNextAtom)

    val starting: Lens[SequenceState.Running, IsStarting] =
      Focus[SequenceState.Running](_.starting)
