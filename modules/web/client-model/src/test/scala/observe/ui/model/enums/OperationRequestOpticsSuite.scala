// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.enums

import monocle.law.discipline.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import observe.model.arb.ArbExecutionState.given
import observe.ui.model.enums.arb.ArbOperationRequest.given
import org.scalacheck.Test

class OperationRequestOpticsSuite extends munit.DisciplineSuite:
  override val scalaCheckTestParameters = Test.Parameters.default.withMaxSize(10)

  checkAll("OperationRequest.pauseState", OptionalTests(OperationRequest.PauseState))
