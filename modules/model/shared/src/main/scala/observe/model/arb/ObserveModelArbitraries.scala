// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import cats.syntax.all.*
import eu.timepit.refined.scalacheck.numeric.given
import eu.timepit.refined.scalacheck.string.given
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.math.arb.ArbQuantity.given
import lucuma.core.math.arb.ArbRefined.given
import lucuma.core.model.CloudExtinction
import lucuma.core.model.ImageQuality
import lucuma.core.model.User
import lucuma.core.model.arb.ArbUser.given
import lucuma.core.model.sequence.Step
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbGid.given
import lucuma.core.util.arb.ArbNewType.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.*
import observe.model.arb.all.given
import observe.model.enums.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

import scala.collection.immutable.SortedMap

trait ObserveModelArbitraries {
  private val maxListSize = 2

  given Arbitrary[Conditions] = Arbitrary[Conditions] {
    for {
      ce <- arbitrary[Option[CloudExtinction]]
      iq <- arbitrary[Option[ImageQuality]]
      sb <- arbitrary[Option[SkyBackground]]
      wv <- arbitrary[Option[WaterVapor]]
    } yield Conditions(ce, iq, sb, wv)
  }

  given Arbitrary[Resource | Instrument] =
    Arbitrary:
      Gen.oneOf(arbitrary[Resource], arbitrary[Instrument])

  given Cogen[Resource | Instrument] =
    Cogen[Either[Resource, Instrument]].contramap:
      case r: Resource   => r.asLeft
      case i: Instrument => i.asRight

  // N.B. We don't want to auto derive this to limit the size of the lists for performance reasons
  given sequencesQueueArb[A](using arb: Arbitrary[A]): Arbitrary[SequencesQueue[A]] =
    Arbitrary {
      for {
        b <- Gen.listOfN[A](maxListSize, arb.arbitrary)
        c <- arbitrary[Conditions]
        o <- arbitrary[Option[Operator]]
        // We are already testing serialization of conditions and Strings
        // Let's reduce the test space by only testing the list of items
      } yield SequencesQueue(Map.empty, c, o, SortedMap.empty, b)
    }

  given Arbitrary[ActionType] = Arbitrary[ActionType] {
    for {
      c <- arbitrary[Resource].map(ActionType.Configure.apply)
      a <- Gen.oneOf(ActionType.Observe, ActionType.Undefined)
      b <- Gen.oneOf(c, a)
    } yield b
  }

  given Arbitrary[SequenceMetadata] = Arbitrary[SequenceMetadata] {
    for {
      i <- arbitrary[Instrument]
      o <- arbitrary[Option[Observer]]
      n <- Gen.alphaStr.suchThat(_.nonEmpty).map(NonEmptyString.unsafeFrom)
    } yield SequenceMetadata(i, o, n)
  }

  given Arbitrary[SequenceState.Running] = Arbitrary[SequenceState.Running] {
    for {
      u  <- arbitrary[Boolean]
      i  <- arbitrary[Boolean]
      w  <- arbitrary[Boolean]
      a  <- arbitrary[Boolean]
      s  <- arbitrary[Boolean]
      ff <- arbitrary[IsFutureFailed]
    } yield SequenceState.Running(u, i, w, a, s, ff)
  }

  given Arbitrary[SequenceState] = Arbitrary[SequenceState] {
    for {
      f <- Gen.oneOf(SequenceState.Completed, SequenceState.Idle)
      r <- arbitrary[SequenceState.Running]
      a <- arbitrary[String].map(SequenceState.Error.apply)
      s <- Gen.oneOf(f, r, a)
    } yield s
  }

  given Arbitrary[SystemOverrides] = Arbitrary[SystemOverrides] {
    for {
      tcs  <- arbitrary[SubsystemEnabled]
      inst <- arbitrary[SubsystemEnabled]
      gcal <- arbitrary[SubsystemEnabled]
      dhs  <- arbitrary[SubsystemEnabled]
    } yield SystemOverrides(tcs, inst, gcal, dhs)
  }

  given Arbitrary[SequenceView]                 = Arbitrary[SequenceView] {
    for {
      id <- arbitrary[Observation.Id]
      m  <- arbitrary[SequenceMetadata]
      s  <- arbitrary[SequenceState]
      o  <- arbitrary[SystemOverrides]
      st <- arbitrary[SequenceType]
      t  <- arbitrary[List[ObserveStep]]
      i  <- arbitrary[Option[Int]]
      a  <- arbitrary[Map[Step.Id, Map[Resource | Instrument, ActionStatus]]]
    } yield SequenceView(id, m, s, o, st, t, i, a)
  }
  given Arbitrary[SequencesQueue[SequenceView]] = sequencesQueueArb[SequenceView]

  given Cogen[ActionType] =
    Cogen[String].contramap(_.productPrefix)

  given Cogen[SequenceState] =
    Cogen[String].contramap(_.productPrefix)

  given Cogen[SequenceMetadata] =
    Cogen[(Instrument, Option[Observer], String)].contramap(s =>
      (s.instrument, s.observer, s.name.value)
    )

  given Cogen[SystemOverrides] =
    Cogen[(SubsystemEnabled, SubsystemEnabled, SubsystemEnabled, SubsystemEnabled)].contramap(x =>
      (x.isTcsEnabled, x.isInstrumentEnabled, x.isGcalEnabled, x.isDhsEnabled)
    )

  given Cogen[SequenceView] =
    Cogen[
      (
        Observation.Id,
        SequenceMetadata,
        SequenceState,
        SystemOverrides,
        SequenceType,
        List[ObserveStep],
        Option[Int]
      )
    ]
      .contramap(s =>
        (s.obsId, s.metadata, s.status, s.systemOverrides, s.sequenceType, s.steps, s.willStopIn)
      )

  given Cogen[Conditions] =
    Cogen[
      (Option[CloudExtinction], Option[ImageQuality], Option[SkyBackground], Option[WaterVapor])
    ].contramap(c => (c.ce, c.iq, c.sb, c.wv))

  given [A: Cogen]: Cogen[SequencesQueue[A]] =
    Cogen[(Conditions, Option[Operator], List[A])].contramap(s =>
      (s.conditions, s.operator, s.sessionQueue)
    )

  given Arbitrary[BatchCommandState.Run] = Arbitrary {
    for {
      observer <- arbitrary[Observer]
      user     <- arbitrary[User]
      clid     <- arbitrary[ClientId]
    } yield BatchCommandState.Run(observer, user, clid)
  }

  given Arbitrary[BatchCommandState] = Arbitrary(
    Gen.frequency((2, Gen.oneOf(BatchCommandState.Idle, BatchCommandState.Stop)),
                  (1, arbitrary[BatchCommandState.Run])
    )
  )

  given Cogen[BatchCommandState] =
    Cogen[(String, Option[Observer], Option[User], Option[ClientId])]
      .contramap {
        case r @ BatchCommandState.Run(obs, usd, cid) =>
          (r.productPrefix, obs.some, usd.some, cid.some)
        case o                                        => (o.productPrefix, None, None, None)
      }

  given Arbitrary[ExecutionQueueView] =
    Arbitrary {
      for {
        id <- arbitrary[QueueId]
        n  <- arbitrary[String]
        s  <- arbitrary[BatchCommandState]
        xs <- arbitrary[BatchExecState]
        q  <- arbitrary[List[Observation.Id]]
      } yield ExecutionQueueView(id, n, s, xs, q)
    }

  given Cogen[ExecutionQueueView] =
    Cogen[(QueueId, String, BatchCommandState, BatchExecState, List[Observation.Id])]
      .contramap(x => (x.id, x.name, x.cmdState, x.execState, x.queue))
}

object ObserveModelArbitraries extends ObserveModelArbitraries
