// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.effect.IO
import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*
import observe.model.Observation
import org.scalacheck.Arbitrary.*
import org.scalacheck.{ Arbitrary, Cogen }
import observe.model.BatchCommandState
import observe.model.enums.Instrument
import observe.model.{ Conditions, Operator }
import observe.model.ObserveModelArbitraries.{*, given}

trait ObserveServerArbitraries {

  given Cogen[Map[Instrument, Observation.Name]] =
    Cogen[List[(Instrument, Observation.Name)]].contramap(_.toList)
  given Arbitrary[EngineState[IO]]              = Arbitrary {
    for {
      q <- arbitrary[ExecutionQueues]
      s <- arbitrary[Map[Instrument, Observation.Id]]
      c <- arbitrary[Conditions]
      o <- arbitrary[Option[Operator]]
    } yield EngineState.default[IO].copy(queues = q, selected = s, conditions = c, operator = o)
  }

  given Arbitrary[ExecutionQueue] = Arbitrary {
    for {
      n <- arbitrary[String]
      s <- arbitrary[BatchCommandState]
      q <- arbitrary[List[Observation.Id]]
    } yield ExecutionQueue(n, s, q)
  }

  given Cogen[ExecutionQueue] =
    Cogen[(String, BatchCommandState, List[Observation.Id])]
      .contramap(x => (x.name, x.cmdState, x.queue))

}
