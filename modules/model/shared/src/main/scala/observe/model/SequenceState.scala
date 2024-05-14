// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.Encoder
import lucuma.core.util.Display
import monocle.Focus
import monocle.Lens
import monocle.Prism
import monocle.macros.GenPrism

enum SequenceState(val name: String) derives Eq, Encoder, Decoder:
  case Idle                extends SequenceState("Idle")
  case Running(userStop: Boolean, internalStop: Boolean, waitingNextAtom: Boolean)
      extends SequenceState("Running")
  case Completed           extends SequenceState("Completed")
  case Failed(msg: String) extends SequenceState("Failed")
  case Aborted             extends SequenceState("Aborted")

  def userStopRequested: Boolean =
    this match
      case SequenceState.Running(b, _, _) => b
      case _                              => false

  def internalStopRequested: Boolean =
    this match
      case SequenceState.Running(_, b, _) => b
      case _                              => false

  def isError: Boolean =
    this match
      case Failed(_) => true
      case _         => false

  def isInProcess: Boolean =
    this =!= SequenceState.Idle

  def isRunning: Boolean =
    this match
      case SequenceState.Running(_, _, _) => true
      case _                              => false

  def isCompleted: Boolean =
    this === SequenceState.Completed

  def isIdle: Boolean =
    this === SequenceState.Idle || this === SequenceState.Aborted

object SequenceState:
  given Display[SequenceState] = Display.byShortName(_.name)

  val running: Prism[SequenceState, SequenceState.Running] =
    GenPrism[SequenceState, SequenceState.Running]

  object Running:
    val Init: Running =
      SequenceState.Running(userStop = false, internalStop = false, waitingNextAtom = false)

    val userStop: Lens[SequenceState.Running, Boolean] = Focus[SequenceState.Running](_.userStop)

    val internalStop: Lens[SequenceState.Running, Boolean] =
      Focus[SequenceState.Running](_.internalStop)

    val waitingNextAtom: Lens[SequenceState.Running, Boolean] =
      Focus[SequenceState.Running](_.waitingNextAtom)
