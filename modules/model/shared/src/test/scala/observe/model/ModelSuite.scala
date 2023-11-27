// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.kernel.laws.discipline.*
import eu.timepit.refined.cats.given
import io.circe.testing.CodecTests
import io.circe.testing.instances.*
import lucuma.core.util.arb.ArbEnumerated.given
import observe.model.GmosParameters.*
import observe.model.arb.ObserveModelArbitraries.given
import observe.model.arb.all.given
import observe.model.dhs.*
import observe.model.enums.*
import observe.model.events.SingleActionEvent
import squants.time.Time
import squants.time.TimeUnit

/**
 * Tests Model typeclasses
 */
class ModelSuite extends munit.DisciplineSuite:
  checkAll("Eq[SystemName]", EqTests[SystemName].eqv)
  checkAll("Order[Resource]", OrderTests[Resource].order)
  checkAll("Eq[Resource]", EqTests[Resource].eqv)
  checkAll("Eq[List]", EqTests[List[(Resource, ActionStatus)]].eqv)
  checkAll("Eq[Operator]", EqTests[Operator].eqv)
  checkAll("Eq[StepState]", EqTests[StepState].eqv)
  checkAll("Eq[ActionStatus]", EqTests[ActionStatus].eqv)
  checkAll("Eq[Step]", EqTests[Step].eqv)
  checkAll("Eq[StandardStep]", EqTests[StandardStep].eqv)
  checkAll("Eq[NsSubexposure]", EqTests[NsSubexposure].eqv)
  checkAll("Eq[NodAndShuffleStatus]", EqTests[NodAndShuffleStatus].eqv)
  checkAll("Eq[NodAndShuffleStep]", EqTests[NodAndShuffleStep].eqv)
  checkAll("Eq[SequenceState]", EqTests[SequenceState].eqv)
  checkAll("Eq[ActionType]", EqTests[ActionType].eqv)
  checkAll("Eq[SequenceMetadata]", EqTests[SequenceMetadata].eqv)
  checkAll("Eq[SequenceView]", EqTests[SequenceView].eqv)
  checkAll("Eq[SequencesQueue[SequenceView]]", EqTests[SequencesQueue[SequenceView]].eqv)
  checkAll("Eq[StepType]", EqTests[StepType].eqv)
  checkAll("Eq[Guiding]", EqTests[Guiding].eqv)
  checkAll("Eq[FPUMode]", EqTests[FPUMode].eqv)
  checkAll("Eq[Conditions]", EqTests[Conditions].eqv)
  checkAll("Eq[ServerLogLevel]", EqTests[ServerLogLevel].eqv)
  checkAll("Eq[Notification]", EqTests[Notification].eqv)
  checkAll("Eq[UserPrompt]", EqTests[UserPrompt].eqv)
  checkAll("Eq[UserPrompt.TargetCheckOverride]", EqTests[UserPrompt.TargetCheckOverride].eqv)
  // checkAll("Eq[ExecutionQueueView]", EqTests[ExecutionQueueView].eqv)
  checkAll("Eq[StepProgress]", EqTests[StepProgress].eqv)
  checkAll("Eq[ObservationProgress]", EqTests[ObservationProgress].eqv)
  checkAll("Codec[ObservationProgress]", CodecTests[ObservationProgress].codec)
  checkAll("Eq[TimeUnit]", EqTests[TimeUnit].eqv)
  checkAll("Eq[Time]", EqTests[Time].eqv)
  checkAll("Eq[SingleActionOp]", EqTests[SingleActionOp].eqv)
  checkAll("Eq[SingleActionEvent]", EqTests[SingleActionEvent].eqv)
  checkAll("Eq[RunningStep]", EqTests[RunningStep].eqv)
  checkAll("Eq[MountGuideOption]", EqTests[MountGuideOption].eqv)
  checkAll("Eq[ComaOption]", EqTests[ComaOption].eqv)
  checkAll("Eq[TipTiltSource]", EqTests[TipTiltSource].eqv)
  checkAll("Eq[M2GuideConfig]", EqTests[M2GuideConfig].eqv)
  checkAll("Eq[M1Source]", EqTests[M1Source].eqv)
  checkAll("Eq[M1GuideConfig]", EqTests[M1GuideConfig].eqv)
  checkAll("Eq[TelescopeGuideConfig]", EqTests[TelescopeGuideConfig].eqv)
  checkAll("Eq[BatchCommandState]", EqTests[BatchCommandState].eqv)
  checkAll("Eq[ApplyCommandResult]", EqTests[ApplyCommandResult].eqv)
  checkAll("Eq[ObserveCommandResult]", EqTests[ObserveCommandResult].eqv)
  checkAll("Eq[NodAndShuffleStage]", EqTests[NodAndShuffleStage].eqv)
  checkAll("Eq[ImageFileId]", EqTests[ImageFileId].eqv)
  checkAll("Eq[DataId]", EqTests[DataId].eqv)
  checkAll("Eq[NsPairs]", EqTests[NsPairs].eqv)
  checkAll("Eq[NsRows]", EqTests[NsRows].eqv)
  checkAll("Eq[NsAction]", EqTests[NsAction].eqv)
  checkAll("Eq[NsRunningState]", EqTests[NsRunningState].eqv)
