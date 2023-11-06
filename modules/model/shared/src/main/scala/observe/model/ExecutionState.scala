// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
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
  stepResources:   Map[Step.Id, Map[Resource | Instrument, ActionStatus]],
  systemOverrides: SystemOverrides,
  breakpoints:     Set[Step.Id] = Set.empty
) derives Eq,
      Encoder.AsObject,
      Decoder:

  // If there's a running step or resource, the step is considered locked.
  lazy val isLocked: Boolean =
    runningStepId.isDefined || stepResources.exists(
      _._2.exists(r => ActionStatus.LockedStatuses.contains_(r._2))
    )

object ExecutionState:
  val sequenceState: Lens[ExecutionState, SequenceState]                                          = Focus[ExecutionState](_.sequenceState)
  val runningStepId: Lens[ExecutionState, Option[Step.Id]]                                        = Focus[ExecutionState](_.runningStepId)
  val nsState: Lens[ExecutionState, Option[NsRunningState]]                                       = Focus[ExecutionState](_.nsState)
  val stepResources: Lens[ExecutionState, Map[Step.Id, Map[Resource | Instrument, ActionStatus]]] =
    Focus[ExecutionState](_.stepResources)
  val systemOverrides: Lens[ExecutionState, SystemOverrides]                                      =
    Focus[ExecutionState](_.systemOverrides)
  val breakpoints: Lens[ExecutionState, Set[Step.Id]]                                             = Focus[ExecutionState](_.breakpoints)
  val observer: Lens[ExecutionState, Option[Observer]]                                            = Focus[ExecutionState](_.observer)
