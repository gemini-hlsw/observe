// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.syntax.all.*
import java.util.UUID
import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen
import org.scalacheck.Arbitrary.*
import scala.collection.immutable.SortedMap
import squants.time.*
import observe.model.enums.*
import observe.model.events.SingleActionEvent
import observe.model.arb.all.{*, given}

trait ObserveModelArbitraries {

  private val maxListSize = 2

  given Arbitrary[Operator] = Arbitrary[Operator](Gen.alphaStr.map(Operator.apply))

  given Arbitrary[Conditions] = Arbitrary[Conditions] {
    for {
      cc <- arbitrary[CloudCover]
      iq <- arbitrary[ImageQuality]
      sb <- arbitrary[SkyBackground]
      wv <- arbitrary[WaterVapor]
    } yield Conditions(cc, iq, sb, wv)
  }

  // N.B. We don't want to auto derive this to limit the size of the lists for performance reasons
  implicit def sequencesQueueArb[A](using arb: Arbitrary[A]): Arbitrary[SequencesQueue[A]] =
    Arbitrary {
      for {
        b <- Gen.listOfN[A](maxListSize, arb.arbitrary)
        c <- arbitrary[Conditions]
        o <- arbitrary[Option[Operator]]
        // We are already testing serialization of conditions and Strings
        // Let's reduce the test space by only testing the list of items
      } yield SequencesQueue(Map.empty, c, o, SortedMap.empty, b)
    }

  given Arbitrary[QueueId] = Arbitrary {
    arbitrary[UUID].map(QueueId.apply)
  }

  given Cogen[QueueId] =
    Cogen[UUID].contramap(_.self)

  given Arbitrary[ActionType] = Arbitrary[ActionType] {
    for {
      c <- arbitrary[Resource].map(ActionType.Configure.apply)
      a <- Gen.oneOf(ActionType.Observe, ActionType.Undefined)
      b <- Gen.oneOf(c, a)
    } yield b
  }

  given Arbitrary[UserDetails] = Arbitrary[UserDetails] {
    for {
      u <- arbitrary[String]
      n <- arbitrary[String]
    } yield UserDetails(u, n)
  }

  given Arbitrary[Observer]         = Arbitrary[Observer](Gen.alphaStr.map(Observer.apply))
  given Arbitrary[SequenceMetadata] = Arbitrary[SequenceMetadata] {
    for {
      i <- arbitrary[Instrument]
      o <- arbitrary[Option[Observer]]
      n <- Gen.alphaStr
    } yield SequenceMetadata(i, o, n)
  }

  given Arbitrary[SequenceState.Running] = Arbitrary[SequenceState.Running] {
    for {
      u <- arbitrary[Boolean]
      i <- arbitrary[Boolean]
    } yield SequenceState.Running(u, i)
  }

  given Arbitrary[SequenceState] = Arbitrary[SequenceState] {
    for {
      f <- Gen.oneOf(SequenceState.Completed, SequenceState.Idle)
      r <- arbitrary[SequenceState.Running]
      a <- arbitrary[String].map(SequenceState.Failed.apply)
      s <- Gen.oneOf(f, r, a)
    } yield s
  }

  given Arbitrary[SystemOverrides] = Arbitrary[SystemOverrides] {
    for {
      tcs  <- arbitrary[Boolean]
      inst <- arbitrary[Boolean]
      gcal <- arbitrary[Boolean]
      dhs  <- arbitrary[Boolean]
    } yield SystemOverrides(tcs, inst, gcal, dhs)
  }

  given Arbitrary[SequenceView]                 = Arbitrary[SequenceView] {
    for {
      id <- arbitrary[Observation.IdName]
      m  <- arbitrary[SequenceMetadata]
      s  <- arbitrary[SequenceState]
      o  <- arbitrary[SystemOverrides]
      t  <- arbitrary[List[Step]]
      i  <- arbitrary[Option[Int]]
    } yield SequenceView(id, m, s, o, t, i)
  }
  given Arbitrary[SequencesQueue[SequenceView]] = sequencesQueueArb[SequenceView]

  given Cogen[ActionType] =
    Cogen[String].contramap(_.productPrefix)

  given Cogen[Operator] =
    Cogen[String].contramap(_.value)

  given Cogen[Observer] =
    Cogen[String].contramap(_.value)

  given Cogen[SequenceState] =
    Cogen[String].contramap(_.productPrefix)

  given Cogen[UserDetails] =
    Cogen[(String, String)].contramap(u => (u.username, u.displayName))

  given Cogen[SequenceMetadata] =
    Cogen[(Instrument, Option[Observer], String)].contramap(s => (s.instrument, s.observer, s.name))

  given Cogen[SystemOverrides] =
    Cogen[(Boolean, Boolean, Boolean, Boolean)].contramap(x =>
      (x.isTcsEnabled, x.isInstrumentEnabled, x.isGcalEnabled, x.isDhsEnabled)
    )

  given Cogen[SequenceView] =
    Cogen[
      (
        Observation.IdName,
        SequenceMetadata,
        SequenceState,
        SystemOverrides,
        List[Step],
        Option[Int]
      )
    ]
      .contramap(s => (s.idName, s.metadata, s.status, s.systemOverrides, s.steps, s.willStopIn))

  given [A: Cogen]: Cogen[SequencesQueue[A]] =
    Cogen[(Conditions, Option[Operator], List[A])].contramap(s =>
      (s.conditions, s.operator, s.sessionQueue)
    )

  given Cogen[Conditions] =
    Cogen[(CloudCover, ImageQuality, SkyBackground, WaterVapor)].contramap(c =>
      (c.cc, c.iq, c.sb, c.wv)
    )

  given Arbitrary[BatchCommandState.Run] = Arbitrary {
    for {
      observer <- arbitrary[Observer]
      user     <- arbitrary[UserDetails]
      clid     <- arbitrary[ClientId]
    } yield BatchCommandState.Run(observer, user, clid)
  }

  given Arbitrary[BatchCommandState] = Arbitrary(
    Gen.frequency((2, Gen.oneOf(BatchCommandState.Idle, BatchCommandState.Stop)),
                  (1, arbitrary[BatchCommandState.Run])
    )
  )

  given Cogen[BatchCommandState] =
    Cogen[(String, Option[Observer], Option[UserDetails], Option[ClientId])]
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

  given Arbitrary[UserLoginRequest] =
    Arbitrary {
      for {
        u <- arbitrary[String]
        p <- arbitrary[String]
      } yield UserLoginRequest(u, p)
    }

  given Cogen[UserLoginRequest] =
    Cogen[(String, String)].contramap(x => (x.username, x.password))

  given Arbitrary[TimeUnit] =
    Arbitrary {
      Gen.oneOf(Nanoseconds, Microseconds, Milliseconds, Seconds, Minutes, Hours, Days)
    }

  given Cogen[TimeUnit] =
    Cogen[String]
      .contramap(_.symbol)

  given Arbitrary[SingleActionOp.Started] =
    Arbitrary {
      for {
        o <- arbitrary[Observation.IdName]
        s <- arbitrary[StepId]
        r <- arbitrary[Resource]
      } yield SingleActionOp.Started(o, s, r)
    }

  given Cogen[SingleActionOp.Started] =
    Cogen[(Observation.IdName, StepId, Resource)]
      .contramap(x => (x.sidName, x.stepId, x.resource))

  given Arbitrary[SingleActionOp.Completed] =
    Arbitrary {
      for {
        o <- arbitrary[Observation.IdName]
        s <- arbitrary[StepId]
        r <- arbitrary[Resource]
      } yield SingleActionOp.Completed(o, s, r)
    }

  given Cogen[SingleActionOp.Completed] =
    Cogen[(Observation.IdName, StepId, Resource)]
      .contramap(x => (x.sidName, x.stepId, x.resource))

  given Arbitrary[SingleActionOp.Error] =
    Arbitrary {
      for {
        o <- arbitrary[Observation.IdName]
        s <- arbitrary[StepId]
        r <- arbitrary[Resource]
        m <- arbitrary[String]
      } yield SingleActionOp.Error(o, s, r, m)
    }

  given Cogen[SingleActionOp.Error] =
    Cogen[(Observation.IdName, StepId, Resource, String)]
      .contramap(x => (x.sidName, x.stepId, x.resource, x.msg))

  given Arbitrary[SingleActionOp] = Arbitrary[SingleActionOp] {
    for {
      s <- arbitrary[SingleActionOp.Started]
      c <- arbitrary[SingleActionOp.Completed]
      e <- arbitrary[SingleActionOp.Error]
      m <- Gen.oneOf(s, c, e)
    } yield m
  }

  given Cogen[SingleActionOp] =
    Cogen[Either[SingleActionOp.Started, Either[SingleActionOp.Completed, SingleActionOp.Error]]]
      .contramap {
        case s: SingleActionOp.Started   => Left(s)
        case c: SingleActionOp.Completed => Right(Left(c))
        case e: SingleActionOp.Error     => Right(Right(e))
      }

  given Arbitrary[SingleActionEvent] =
    Arbitrary {
      for {
        e <- arbitrary[SingleActionOp]
      } yield SingleActionEvent(e)
    }

  given Cogen[SingleActionEvent] =
    Cogen[SingleActionOp]
      .contramap(_.op)
}

object ObserveModelArbitraries extends ObserveModelArbitraries
