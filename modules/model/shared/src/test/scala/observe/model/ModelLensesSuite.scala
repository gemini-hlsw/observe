// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import eu.timepit.refined.cats.given
import eu.timepit.refined.scalacheck.string.given
import lucuma.core.math.Axis
import lucuma.core.math.arb.ArbAngle.given
import lucuma.core.math.arb.ArbOffset.given
import lucuma.core.math.arb.ArbRefined.given
import lucuma.core.optics.laws.discipline.FormatTests
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbUid.given
import monocle.law.discipline.*
import observe.model.arb.ObserveModelArbitraries.given
import observe.model.arb.all.{*, given}
import observe.model.enums.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Test as ScalaCheckTest

class ModelLensesSuite extends munit.DisciplineSuite with ModelLenses {
  override def scalaCheckTestParameters = ScalaCheckTest.Parameters.default.withMaxSize(10)

  checkAll("event observer name lens", LensTests(obsNameL))
  checkAll("each step traversal", TraversalTests(eachStepT))
  checkAll("observation steps lens", LensTests(obsStepsL))
  checkAll("each view traversal", TraversalTests(eachViewT))
  checkAll("sequence queue lens", LensTests(sessionQueueL))

  checkAll("step type prism", PrismTests(stringToStepTypeP))

  checkAll("param guiding prism", PrismTests(stringToGuidingP))

  checkAll("StandardStep", PrismTests(ObserveStep.standardStepP))
  checkAll("NodAndShuffleStep", PrismTests(ObserveStep.nsStepP))
  checkAll("ObserveStep.status", LensTests(ObserveStep.status))
  checkAll("ObserveStep.id", LensTests(ObserveStep.id))
  checkAll("ObserveStep.breakpoint", LensTests(ObserveStep.breakpoint))
  checkAll("ObserveStep.observeStatus", OptionalTests(ObserveStep.observeStatus))
  checkAll("ObserveStep.configStatus", OptionalTests(ObserveStep.configStatus))
  checkAll("signedPFormat", FormatTests(signedComponentFormat[Axis.P]).formatWith(stringsOffsets))
  checkAll("signedQFormat", FormatTests(signedComponentFormat[Axis.Q]).formatWith(stringsOffsets))
  checkAll("signedArcsecFormat", FormatTests(signedArcsecFormat).formatWith(stringsOffsets))
}
