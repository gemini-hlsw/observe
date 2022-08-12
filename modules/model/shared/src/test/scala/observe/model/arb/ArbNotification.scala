// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.util.arb.ArbEnumerated._
import lucuma.core.util.arb.ArbGid._
import lucuma.core.util.arb.ArbUid._
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.scalacheck.Cogen
import observe.model.StepId
import observe.model.Notification
import observe.model.Notification._
import observe.model.Observation
import observe.model.enum.Instrument
import observe.model.enum.Resource

trait ArbNotification {
  import ArbObservationIdName._

  implicit val rcArb: Arbitrary[ResourceConflict] = Arbitrary[ResourceConflict] {
    for {
      idName <- arbitrary[Observation.IdName]
    } yield ResourceConflict(idName)
  }

  implicit val rcCogen: Cogen[ResourceConflict] =
    Cogen[Observation.IdName].contramap(_.sidName)

  implicit val rfArb: Arbitrary[RequestFailed] = Arbitrary[RequestFailed] {
    arbitrary[List[String]].map(RequestFailed.apply)
  }

  implicit val rfCogen: Cogen[RequestFailed] =
    Cogen[List[String]].contramap(_.msgs)

  implicit val inArb: Arbitrary[InstrumentInUse] = Arbitrary[InstrumentInUse] {
    for {
      id <- arbitrary[Observation.IdName]
      i  <- arbitrary[Instrument]
    } yield InstrumentInUse(id, i)
  }

  implicit val inCogen: Cogen[InstrumentInUse] =
    Cogen[(Observation.IdName, Instrument)].contramap(x => (x.sidName, x.ins))

  implicit val subsArb: Arbitrary[SubsystemBusy] = Arbitrary[SubsystemBusy] {
    for {
      id <- arbitrary[Observation.IdName]
      i  <- arbitrary[StepId]
      r  <- arbitrary[Resource]
    } yield SubsystemBusy(id, i, r)
  }

  implicit val subsCogen: Cogen[SubsystemBusy] =
    Cogen[(Observation.IdName, StepId, Resource)].contramap(x => (x.sidName, x.stepId, x.resource))

  implicit val notArb: Arbitrary[Notification] = Arbitrary[Notification] {
    for {
      r <- arbitrary[ResourceConflict]
      a <- arbitrary[InstrumentInUse]
      f <- arbitrary[RequestFailed]
      b <- arbitrary[SubsystemBusy]
      s <- Gen.oneOf(r, a, f, b)
    } yield s
  }

  implicit val notCogen: Cogen[Notification] =
    Cogen[Either[ResourceConflict, Either[InstrumentInUse, Either[RequestFailed, SubsystemBusy]]]]
      .contramap {
        case r: ResourceConflict => Left(r)
        case i: InstrumentInUse  => Right(Left(i))
        case f: RequestFailed    => Right(Right(Left(f)))
        case b: SubsystemBusy    => Right(Right(Right(b)))
      }

}

object ArbNotification extends ArbNotification
