// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.arb

import crystal.Pot
import crystal.arb.given
import eu.timepit.refined.scalacheck.string.given
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.math.arb.ArbRefined.given
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.util.arb.ArbGid.given
import lucuma.core.util.arb.ArbNewType.given
import lucuma.core.util.arb.ArbUid.given
import lucuma.ui.sso.UserVault
import lucuma.ui.sso.arb.ArbUserVault.given
import observe.common.FixedLengthBuffer
import observe.common.arb.ArbFixedLengthBuffer.given
import observe.model.Conditions
import observe.model.ExecutionState
import observe.model.LogMessage
import observe.model.Observer
import observe.model.Operator
import observe.model.StepProgress
import observe.model.arb.ArbExecutionState.given
import observe.model.arb.ArbLogMessage.given
import observe.model.arb.ArbObsRecordedIds.given
import observe.model.arb.ArbStepProgress.given
import observe.model.arb.ObserveModelArbitraries.given
import observe.model.odb.ObsRecordedIds
import observe.ui.model.IsAudioActivated
import observe.ui.model.LoadedObservation
import observe.ui.model.ObsSummary
import observe.ui.model.ObservationRequests
import observe.ui.model.RootModelData
import observe.ui.model.arb.ArbLoadedObservation.given
import observe.ui.model.arb.ArbObsSummary.given
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen

import ArbObservationRequests.given

trait ArbRootModel:
  // Make sure a known Observation.Id is generated somewhere.
  given Arbitrary[Observation.Id] = Arbitrary:
    Gen.oneOf(Gen.const(StandardObsId), arbGid[Observation.Id].arbitrary)

  given Arbitrary[RootModelData] = Arbitrary:
    for
      uv    <- arbitrary[Pot[Option[UserVault]]]
      ros   <- arbitrary[Pot[List[ObsSummary]]]
      los   <- arbitrary[Map[Observation.Id, LoadedObservation]]
      es    <- arbitrary[Map[Observation.Id, ExecutionState]]
      ri    <- arbitrary[ObsRecordedIds]
      sp    <- arbitrary[Map[Observation.Id, StepProgress]]
      uss   <- arbitrary[Map[Observation.Id, Step.Id]]
      or    <- arbitrary[Map[Observation.Id, ObservationRequests]]
      cs    <- arbitrary[Conditions]
      obs   <- arbitrary[Option[Observer]]
      op    <- arbitrary[Option[Operator]]
      usm   <- arbitrary[Option[NonEmptyString]]
      log   <- arbitrary[FixedLengthBuffer[LogMessage]]
      audio <- arbitrary[IsAudioActivated]
    yield RootModelData(
      uv,
      ros,
      los,
      es,
      ri,
      sp,
      uss,
      or,
      cs,
      obs,
      op,
      usm,
      log,
      audio
    )

  given Cogen[RootModelData] = Cogen[
    (
      Pot[Option[UserVault]],
      Pot[List[ObsSummary]],
      List[(Observation.Id, LoadedObservation)],
      List[(Observation.Id, ExecutionState)],
      ObsRecordedIds,
      List[(Observation.Id, StepProgress)],
      List[(Observation.Id, Step.Id)],
      List[(Observation.Id, ObservationRequests)],
      Conditions,
      Option[Observer],
      Option[Operator],
      Option[NonEmptyString],
      FixedLengthBuffer[LogMessage],
      IsAudioActivated
    )
  ].contramap: x =>
    (x.userVault,
     x.readyObservations,
     x.loadedObservations.toList,
     x.executionState.toList,
     x.recordedIds,
     x.obsProgress.toList,
     x.userSelectedStep.toList,
     x.obsRequests.toList,
     x.conditions,
     x.observer,
     x.operator,
     x.userSelectionMessage,
     x.globalLog,
     x.isAudioActivated
    )

object ArbRootModel extends ArbRootModel
