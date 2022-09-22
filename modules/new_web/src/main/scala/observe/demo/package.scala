// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.demo

import cats.syntax.all.*
import observe.model.*
import observe.ui.model.SessionQueueRow
import observe.model.enums.SequenceState
import observe.ui.model.enums.ObsClass
import lucuma.core.model.Observation
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.Step
import java.util.UUID
import observe.model.enums.StepState
import observe.model.enums.ActionStatus

val DemoSessionQueue: List[SessionQueueRow] =
  List(
    SessionQueueRow(
      Observation.Id.fromLong(133742).get,
      SequenceState.Running(false, false),
      Instrument.GmosSouth,
      "Untitled".some,
      Observer("Telops").some,
      "GMOS-S Observation",
      ObsClass.Nighttime,
      true,
      true,
      none,
      RunningStep.fromInt(none, 0, 20),
      false
    )
  )

val DemoExecutionSteps: List[ExecutionStep] = List(
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Skipped,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Completed,
    breakpoint = false,
    skip = false,
    fileId = ImageFileId("S20220916S0001").some,
    configStatus = List.empty,
    observeStatus = ActionStatus.Completed
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Running,
    breakpoint = false,
    skip = false,
    fileId = ImageFileId("S20220916S0001").some,
    configStatus = List.empty,
    observeStatus = ActionStatus.Running
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Pending,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Pending,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Pending,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Pending,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Pending,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Pending,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Pending,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Pending,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Pending,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Pending,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = ExecutionStepConfig(Map.empty),
    status = StepState.Pending,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  )
)
