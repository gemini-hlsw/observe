// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.effect.IO
import lucuma.core.util.arb.ArbEnumerated._
import observe.model.Observation
import observe.model.arb.ArbObservationId._
import org.scalacheck.Arbitrary._
import org.scalacheck.{ Arbitrary, Cogen }
import observe.model.BatchCommandState
import observe.model.enum.Instrument
import observe.model.{ Conditions, Operator }
import observe.model.ObserveModelArbitraries._

trait ObserveServerArbitraries {

  implicit val selectedCoGen: Cogen[Map[Instrument, Observation.Id]] =
    Cogen[List[(Instrument, Observation.Id)]].contramap(_.toList)
  implicit val engineStateArb: Arbitrary[EngineState[IO]]            = Arbitrary {
    for {
      q <- arbitrary[ExecutionQueues]
      s <- arbitrary[Map[Instrument, Observation.Id]]
      c <- arbitrary[Conditions]
      o <- arbitrary[Option[Operator]]
    } yield EngineState.default[IO].copy(queues = q, selected = s, conditions = c, operator = o)
  }

  implicit val executionQueueArb: Arbitrary[ExecutionQueue] = Arbitrary {
    for {
      n <- arbitrary[String]
      s <- arbitrary[BatchCommandState]
      q <- arbitrary[List[Observation.Id]]
    } yield ExecutionQueue(n, s, q)
  }

  implicit val executionQueueCogen: Cogen[ExecutionQueue] =
    Cogen[(String, BatchCommandState, List[Observation.Id])]
      .contramap(x => (x.name, x.cmdState, x.queue))

}
