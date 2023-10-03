// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import lucuma.core.model.sequence.Step
import monocle.Focus
import monocle.Lens
import observe.model.enums.ActionStatus
import observe.model.enums.Resource

/**
 * This class concentrates all the execution state that is kept in the server.
 */
case class ExecutionState(
  sequenceState: SequenceState,
  runningStepId: Option[Step.Id],
  nsState:       Option[NsRunningState],
  configStatus:  List[(Resource, ActionStatus)],
  breakpoints:   Set[Step.Id] = Set.empty
) derives Eq

object ExecutionState:
  val sequenceState: Lens[ExecutionState, SequenceState]                 = Focus[ExecutionState](_.sequenceState)
  val runningStepId: Lens[ExecutionState, Option[Step.Id]]               = Focus[ExecutionState](_.runningStepId)
  val nsState: Lens[ExecutionState, Option[NsRunningState]]              = Focus[ExecutionState](_.nsState)
  val configStatus: Lens[ExecutionState, List[(Resource, ActionStatus)]] =
    Focus[ExecutionState](_.configStatus)
  val breakpoints: Lens[ExecutionState, Set[Step.Id]]                    = Focus[ExecutionState](_.breakpoints)
