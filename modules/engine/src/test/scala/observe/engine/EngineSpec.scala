// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.Eq
import cats.effect.IO
import observe.model.Observation
import monocle.law.discipline.OptionalTests
import org.scalacheck.{Arbitrary, Cogen}
import org.scalacheck.Arbitrary._
import observe.engine.TestUtil.TestState
import observe.model.ObserveModelArbitraries._
import observe.model.SequenceState
import lucuma.core.util.arb.ArbGid._
import lucuma.core.util.arb.ArbUid._

final class EngineSpec extends munit.DisciplineSuite {

  implicit val seqstateEq: Eq[Sequence.State[IO]] = Eq.fromUniversalEquals
  implicit val execstateEq: Eq[TestState]         = Eq.by(x => x.sequences)

  implicit val sequenceArb: Arbitrary[Sequence[IO]] = Arbitrary {
    for {
      id <- arbitrary[Observation.Id]
    } yield Sequence(id, List())
  }

  implicit val sequenceStateArb: Arbitrary[Sequence.State[IO]] = Arbitrary {
    for {
      seq <- arbitrary[Sequence[IO]]
      st  <- arbitrary[SequenceState]
    } yield Sequence.State.Final(seq, st)
  }

  implicit val sequenceStateCogen: Cogen[Sequence.State[IO]] =
    Cogen[Observation.Id].contramap(_.toSequence.id)

  implicit val engineStateArb: Arbitrary[TestState] = Arbitrary {
    for {
      q <- arbitrary[Map[Observation.Id, Sequence.State[IO]]]
    } yield TestState(q)
  }

  checkAll(
    "sequence optional",
    OptionalTests[TestState, Sequence.State[IO], Observation.Id](TestState.sequenceStateIndex)
  )

}
