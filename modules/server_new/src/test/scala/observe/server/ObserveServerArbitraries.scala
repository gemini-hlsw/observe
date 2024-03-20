// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import lucuma.core.enums.Instrument
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbGid.given
import observe.model.BatchCommandState
import observe.model.Observation
import observe.model.SequenceState
import observe.model.arb.ObserveModelArbitraries.given
import observe.model.enums.Resource
import observe.server.ExecutionQueue.SequenceInQueue
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

trait ObserveServerArbitraries {

  given Cogen[Map[Instrument, Observation.Name]] =
    Cogen[List[(Instrument, Observation.Name)]].contramap(_.toList)

  given Arbitrary[SequenceInQueue] = Arbitrary {
    for {
      o <- arbitrary[Observation.Id]
      i <- arbitrary[Instrument]
      s <- arbitrary[SequenceState]
      r <- arbitrary[Set[Resource | Instrument]]
    } yield SequenceInQueue(o, i, s, r)
  }

  given Cogen[SequenceInQueue] =
    Cogen[(Observation.Id, Instrument, SequenceState, List[Resource | Instrument])]
      .contramap(x => (x.obsId, x.instrument, x.state, x.resources.toList))

  given Arbitrary[ExecutionQueue] = Arbitrary {
    for {
      n <- arbitrary[String]
      s <- arbitrary[BatchCommandState]
      q <- arbitrary[List[SequenceInQueue]]
    } yield ExecutionQueue(n, s, q)
  }

  given Cogen[ExecutionQueue] =
    Cogen[(String, BatchCommandState, List[SequenceInQueue])]
      .contramap(x => (x.name, x.cmdState, x.queue))

}

object ObserveServerArbitraries extends ObserveServerArbitraries
