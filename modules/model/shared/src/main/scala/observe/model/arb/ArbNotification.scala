// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.Step
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbGid.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.Notification
import observe.model.Notification.*
import observe.model.Observation
import observe.model.enums.Resource
import observe.model.given_Enumerated_|
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbNotification {

  given rcArb: Arbitrary[ResourceConflict] = Arbitrary[ResourceConflict] {
    for {
      obsId <- arbitrary[Observation.Id]
    } yield ResourceConflict(obsId)
  }

  given rcCogen: Cogen[ResourceConflict] =
    Cogen[Observation.Id].contramap(_.obsId)

  given rfArb: Arbitrary[RequestFailed] = Arbitrary[RequestFailed] {
    arbitrary[List[String]].map(RequestFailed.apply)
  }

  given rfCogen: Cogen[RequestFailed] =
    Cogen[List[String]].contramap(_.msgs)

  given inArb: Arbitrary[InstrumentInUse] = Arbitrary[InstrumentInUse] {
    for {
      id <- arbitrary[Observation.Id]
      i  <- arbitrary[Instrument]
    } yield InstrumentInUse(id, i)
  }

  given inCogen: Cogen[InstrumentInUse] =
    Cogen[(Observation.Id, Instrument)].contramap(x => (x.obsId, x.ins))

  given subsArb: Arbitrary[SubsystemBusy] = Arbitrary[SubsystemBusy] {
    for {
      id <- arbitrary[Observation.Id]
      i  <- arbitrary[Step.Id]
      r  <- arbitrary[Resource]
    } yield SubsystemBusy(id, i, r)
  }

  given subsCogen: Cogen[SubsystemBusy] =
    Cogen[(Observation.Id, Step.Id, Resource | Instrument)].contramap(x =>
      (x.obsId, x.stepId, x.resource)
    )

  given notArb: Arbitrary[Notification] = Arbitrary[Notification] {
    for {
      r <- arbitrary[ResourceConflict]
      a <- arbitrary[InstrumentInUse]
      f <- arbitrary[RequestFailed]
      b <- arbitrary[SubsystemBusy]
      s <- Gen.oneOf(r, a, f, b)
    } yield s
  }

  given notCogen: Cogen[Notification] =
    Cogen[Either[ResourceConflict, Either[InstrumentInUse, Either[RequestFailed, SubsystemBusy]]]]
      .contramap {
        case r: ResourceConflict => Left(r)
        case i: InstrumentInUse  => Right(Left(i))
        case f: RequestFailed    => Right(Right(Left(f)))
        case b: SubsystemBusy    => Right(Right(Right(b)))
      }

}

object ArbNotification extends ArbNotification
