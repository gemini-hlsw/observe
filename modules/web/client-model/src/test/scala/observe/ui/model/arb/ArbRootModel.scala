// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.arb

import eu.timepit.refined.scalacheck.string.given
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.math.arb.ArbRefined.given
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.util.arb.ArbGid.arbGid
import lucuma.core.util.arb.ArbGid.given
import lucuma.core.util.arb.ArbUid.given
import lucuma.ui.sso.UserVault
import lucuma.ui.sso.arb.ArbUserVault.given
import observe.model.Conditions
import observe.model.ExecutionState
import observe.model.Observer
import observe.model.Operator
import observe.model.StepProgress
import observe.model.arb.ArbExecutionState.given
import observe.model.arb.ArbStepProgress.given
import observe.model.arb.ObserveModelArbitraries.given
import observe.ui.model.LoadedObservation
import observe.ui.model.RootModelData
import observe.ui.model.arb.ArbLoadedObservation.given
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbRootModel:
  // Make sure a known Observation.Id is generated somewhere.
  given Arbitrary[Observation.Id] = Arbitrary:
    Gen.oneOf(Gen.const(StandardObsId), arbGid[Observation.Id].arbitrary)

  given Arbitrary[RootModelData] = Arbitrary:
    for
      uv   <- arbitrary[Option[UserVault]]
      nto  <- arbitrary[Option[LoadedObservation]]
      dtos <- arbitrary[List[LoadedObservation]]
      se   <- arbitrary[Map[Observation.Id, ExecutionState]]
      sp   <- arbitrary[Map[Observation.Id, StepProgress]]
      uss  <- arbitrary[Map[Observation.Id, Step.Id]]
      cs   <- arbitrary[Conditions]
      obs  <- arbitrary[Option[Observer]]
      op   <- arbitrary[Option[Operator]]
      usm  <- arbitrary[Option[NonEmptyString]]
      log  <- arbitrary[List[NonEmptyString]]
    yield RootModelData(uv, nto, dtos, se, sp, uss, cs, obs, op, usm, log)

  given Cogen[RootModelData] = Cogen[
    (
      Option[UserVault],
      Option[LoadedObservation],
      List[LoadedObservation],
      List[(Observation.Id, ExecutionState)],
      List[(Observation.Id, Step.Id)],
      Conditions,
      Option[Observer],
      Option[Operator],
      Option[NonEmptyString],
      List[NonEmptyString]
    )
  ].contramap: x =>
    (x.userVault,
     x.nighttimeObservation,
     x.daytimeObservations,
     x.executionState.toList,
     x.userSelectedStep.toList,
     x.conditions,
     x.observer,
     x.operator,
     x.userSelectionMessage,
     x.log
    )

object ArbRootModel extends ArbRootModel
