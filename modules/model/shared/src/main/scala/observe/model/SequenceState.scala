// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
// import lucuma.core.model.sequence.Step
import lucuma.core.util.Display

enum SequenceState(val name: String) derives Eq:
  case Completed           extends SequenceState("Completed")
  case Idle                extends SequenceState("Idle")
  case Running(
    // stepId:       Step.Id,
    // nsState:      Option[NsRunningState],
    userStop:     Boolean,
    internalStop: Boolean
  )                        extends SequenceState("Running")
  case Failed(msg: String) extends SequenceState("Failed")
  case Aborted             extends SequenceState("Aborted")

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
    // def init(stepId: Step.Id, nsState: Option[NsRunningState]): Running =
    //   SequenceState.Running(
    //     stepId = stepId,
    //     nsState = nsState,
    //     userStop = false,
    //     internalStop = false
    //   )

  given Display[SequenceState] = Display.byShortName(_.name)
