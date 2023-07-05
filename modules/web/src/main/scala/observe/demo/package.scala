// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.demo

import cats.syntax.all.*
import lucuma.core.model.sequence.Step
import observe.model.*
import observe.model.enums.ActionStatus
import observe.model.enums.StepState
import observe.model.enums.SystemName

import java.util.UUID

private val stepConfig = ExecutionStepConfig(
  Map(
    SystemName.Observe    ->
      Parameters(
        Map(
          ParamName("observe:exposureTime") -> ParamValue("300"),
          ParamName("observe:observeType")  -> ParamValue("OBJECT")
        )
      ),
    SystemName.Instrument ->
      Parameters(
        Map(
          ParamName("instrument:disperser")       -> ParamValue("Mirror"),
          ParamName("instrument:disperserLambda") -> ParamValue("550"),
          ParamName("instrument:filter")          -> ParamValue("NONE"),
          ParamName("instrument:fpu")             -> ParamValue("FPU_NONE")
        )
      )
  )
)

private def buildPendingStep(breakpoint: Boolean): ExecutionStep =
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = stepConfig,
    status = StepState.Pending,
    breakpoint = breakpoint,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  )

val DemoExecutionSteps: List[ExecutionStep] = List(
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = stepConfig,
    status = StepState.Skipped,
    breakpoint = false,
    skip = false,
    fileId = none,
    configStatus = List.empty,
    observeStatus = ActionStatus.Pending
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = stepConfig,
    status = StepState.Completed,
    breakpoint = false,
    skip = false,
    fileId = ImageFileId("S20220916S0001").some,
    configStatus = List.empty,
    observeStatus = ActionStatus.Completed
  ),
  StandardStep(
    id = Step.Id.fromUuid(UUID.randomUUID),
    config = stepConfig,
    status = StepState.Running,
    breakpoint = false,
    skip = false,
    fileId = ImageFileId("S20220916S0001").some,
    configStatus = List.empty,
    observeStatus = ActionStatus.Running
  ),
  buildPendingStep(true)
) ++ (1 to 196).map(_ => buildPendingStep(false))

// val DemoSessionQueue: List[SessionQueueRow] =
//   List(
//     SessionQueueRow(
//       Observation.Id.fromLong(133742).get,
//       SequenceState.Running(false, false),
//       Instrument.GmosSouth,
//       "Untitled".some,
//       Observer("Telops").some,
//       "GMOS-S Observation",
//       ObsClass.Nighttime,
//       true,
//       true,
//       none,
//       RunningStep.fromStepId(none, 2, DemoExecutionSteps.length),
//       false
//     )
//   ) ++ (1 to 80).map(_ =>
//     SessionQueueRow(
//       Observation.Id.fromLong(math.abs(Random.nextInt)).get,
//       SequenceState.Idle,
//       Instrument.GmosSouth,
//       "Untitled".some,
//       Observer("Telops").some,
//       "GMOS-S Observation",
//       ObsClass.Nighttime,
//       true,
//       true,
//       none,
//       none,
//       false
//     )
//   )
