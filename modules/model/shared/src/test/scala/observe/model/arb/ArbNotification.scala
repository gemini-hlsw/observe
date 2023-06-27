// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Gen
import org.scalacheck.Cogen
import observe.model.StepId
import observe.model.Notification
import observe.model.Notification.{*, given}
import observe.model.Observation
import observe.model.enums.Instrument
import observe.model.enums.Resource

trait ArbNotification {
  import ArbObservationIdName.given

  given rcArb: Arbitrary[ResourceConflict] = Arbitrary[ResourceConflict] {
    for {
      idName <- arbitrary[Observation.IdName]
    } yield ResourceConflict(idName)
  }

  given rcCogen: Cogen[ResourceConflict] =
    Cogen[Observation.IdName].contramap(_.sidName)

  given rfArb: Arbitrary[RequestFailed] = Arbitrary[RequestFailed] {
    arbitrary[List[String]].map(RequestFailed.apply)
  }

  given rfCogen: Cogen[RequestFailed] =
    Cogen[List[String]].contramap(_.msgs)

  given inArb: Arbitrary[InstrumentInUse] = Arbitrary[InstrumentInUse] {
    for {
      id <- arbitrary[Observation.IdName]
      i  <- arbitrary[Instrument]
    } yield InstrumentInUse(id, i)
  }

  given inCogen: Cogen[InstrumentInUse] =
    Cogen[(Observation.IdName, Instrument)].contramap(x => (x.sidName, x.ins))

  given subsArb: Arbitrary[SubsystemBusy] = Arbitrary[SubsystemBusy] {
    for {
      id <- arbitrary[Observation.IdName]
      i  <- arbitrary[StepId]
      r  <- arbitrary[Resource]
    } yield SubsystemBusy(id, i, r)
  }

  given subsCogen: Cogen[SubsystemBusy] =
    Cogen[(Observation.IdName, StepId, Resource)].contramap(x => (x.sidName, x.stepId, x.resource))

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
