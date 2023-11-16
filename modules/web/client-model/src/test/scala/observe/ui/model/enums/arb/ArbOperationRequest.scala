// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.enums.arb

import observe.ui.model.enums.OperationRequest
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbOperationRequest:
  given Arbitrary[OperationRequest] = Arbitrary:
    Gen.oneOf(OperationRequest.Idle, OperationRequest.InFlight)

  given Cogen[OperationRequest] =
    Cogen[Boolean].contramap(_ == OperationRequest.InFlight)

object ArbOperationRequest extends ArbOperationRequest
