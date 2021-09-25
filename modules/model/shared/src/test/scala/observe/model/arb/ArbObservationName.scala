// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Arbitrary._
import org.scalacheck.Cogen._

import observe.model.ProgramId
import lucuma.core.math.arb._
import observe.model.Observation
import observe.model.arb.ArbProgramId
import lucuma.core.math.Index
import lucuma.core.optics.syntax.prism._

trait ArbObservationName {

  import ArbIndex._
  import ArbProgramId._

  implicit val arbObservationId: Arbitrary[Observation.Name] =
    Arbitrary {
      for {
        pid <- arbitrary[ProgramId]
        num <- choose[Short](1, 100)
      } yield Observation.Name(pid, Index.fromShort.unsafeGet(num))
    }

  implicit val cogObservationId: Cogen[Observation.Name]     =
    Cogen[(ProgramId, Index)].contramap(oid => (oid.pid, oid.index))

}

object ArbObservationName extends ArbObservationName
