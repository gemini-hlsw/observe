// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*

enum SequenceState derives Eq:
  case Completed                                         extends SequenceState
  case Idle                                              extends SequenceState
  case Running(userStop: Boolean, internalStop: Boolean) extends SequenceState
  case Failed(msg: String)                               extends SequenceState
  case Aborted                                           extends SequenceState

  def internalStopRequested: Boolean =
    this match
      case SequenceState.Running(_, b) => b
      case _                           => false

  def isError: Boolean =
    this match
      case Failed(_) => true
      case _         => false

  def isInProcess: Boolean =
    this =!= SequenceState.Idle

  def isRunning: Boolean =
    this match
      case SequenceState.Running(_, _) => true
      case _                           => false

  def isCompleted: Boolean =
    this === SequenceState.Completed

  def isIdle: Boolean =
    this === SequenceState.Idle || this === SequenceState.Aborted

  def userStopRequested: Boolean =
    this match
      case SequenceState.Running(b, _) => b
      case _                           => false

object SequenceState:
  object Running:
    val init: Running =
      SequenceState.Running(userStop = false, internalStop = false)
