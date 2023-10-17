// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import eu.timepit.refined.cats.*
import io.circe.Decoder
import io.circe.Encoder
import io.circe.refined.*
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.Step
import lucuma.core.util.Enumerated
import monocle.Focus
import monocle.Lens
import observe.model.enums.ActionStatus
import observe.model.enums.Resource

/**
 * This class concentrates all the execution state that is kept in the server.
 */
case class ExecutionState(
  sequenceState:   SequenceState,
  observer:        Option[Observer],
  runningStepId:   Option[Step.Id],
  nsState:         Option[NsRunningState],
  configStatus:    Map[Resource | Instrument, ActionStatus],
  systemOverrides: SystemOverrides,
  breakpoints:     Set[Step.Id] = Set.empty
) derives Eq,
      Encoder.AsObject,
      Decoder

object ExecutionState:
  val sequenceState: Lens[ExecutionState, SequenceState]                           = Focus[ExecutionState](_.sequenceState)
  val runningStepId: Lens[ExecutionState, Option[Step.Id]]                         = Focus[ExecutionState](_.runningStepId)
  val nsState: Lens[ExecutionState, Option[NsRunningState]]                        = Focus[ExecutionState](_.nsState)
  val configStatus: Lens[ExecutionState, Map[Resource | Instrument, ActionStatus]] =
    Focus[ExecutionState](_.configStatus)
  val systemOverrides: Lens[ExecutionState, SystemOverrides]                       =
    Focus[ExecutionState](_.systemOverrides)
  val breakpoints: Lens[ExecutionState, Set[Step.Id]]                              = Focus[ExecutionState](_.breakpoints)
  val observer: Lens[ExecutionState, Option[Observer]]                             = Focus[ExecutionState](_.observer)
