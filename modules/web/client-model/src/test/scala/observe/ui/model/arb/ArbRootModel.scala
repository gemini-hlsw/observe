// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import observe.ui.model.RootModelData
import observe.ui.model.arb.ArbLoadedObservation.given
import lucuma.ui.sso.UserVault
import observe.ui.model.LoadedObservation
import observe.model.ExecutionState
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.ui.sso.arb.ArbUserVault.given
import lucuma.core.model.Observation
import lucuma.core.util.arb.ArbGid.arbGid
import lucuma.core.util.arb.ArbUid.given
import observe.model.arb.ArbExecutionState.given
import lucuma.core.model.sequence.Step
import observe.model.Conditions
import observe.model.arb.ObserveModelArbitraries.given
import observe.model.Observer
import observe.model.Operator
import eu.timepit.refined.scalacheck.string.given
import org.scalacheck.Gen

trait ArbRootModel:
  // Make sure a know Observation.Id is generated somewhere.
  given Arbitrary[Observation.Id] = Arbitrary:
    Gen.oneOf(Gen.const(StandardObsId), arbGid[Observation.Id].arbitrary)

  given Arbitrary[RootModelData] = Arbitrary:
    for
      uv   <- arbitrary[Option[UserVault]]
      nto  <- arbitrary[Option[LoadedObservation]]
      dtos <- arbitrary[List[LoadedObservation]]
      se   <- arbitrary[Map[Observation.Id, ExecutionState]]
      uss  <- arbitrary[Map[Observation.Id, Step.Id]]
      cs   <- arbitrary[Conditions]
      obs  <- arbitrary[Option[Observer]]
      op   <- arbitrary[Option[Operator]]
      usm  <- arbitrary[Option[NonEmptyString]]
      log  <- arbitrary[List[NonEmptyString]]
    yield RootModelData(uv, nto, dtos, se, uss, cs, obs, op, usm, log)

object ArbRootModel extends ArbRootModel
