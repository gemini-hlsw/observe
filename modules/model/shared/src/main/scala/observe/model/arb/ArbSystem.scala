// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import cats.syntax.all.*
import lucuma.core.enums.Instrument
import lucuma.core.util.arb.ArbEnumerated.given
import observe.model.enums.Resource
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbSystem:
  given Arbitrary[Resource | Instrument] = Arbitrary:
    Gen.oneOf(arbitrary[Resource], arbitrary[Instrument])
  given Cogen[Resource | Instrument]     = Cogen[Either[Resource, Instrument]].contramap:
    case r: Resource   => r.asLeft
    case i: Instrument => i.asRight

object ArbSystem extends ArbSystem
