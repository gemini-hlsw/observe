// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.Eq
import cats.effect.IO
import lucuma.core.model.sequence.Atom
import observe.model.Observation
import monocle.law.discipline.OptionalTests
import org.scalacheck.{Arbitrary, Cogen}
import org.scalacheck.Arbitrary.*
import observe.engine.TestUtil.TestState
import observe.model.ObserveModelArbitraries.given
import observe.model.SequenceState
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*

final class EngineSpec extends munit.DisciplineSuite {

  given Eq[Sequence.State[IO]] = Eq.fromUniversalEquals
  given Eq[TestState]          = Eq.by(x => x.sequences)

  given Arbitrary[Sequence[IO]] = Arbitrary {
    for {
      id  <- arbitrary[Observation.Id]
      aid <- arbitrary[Atom.Id]
    } yield Sequence.sequence(id, aid, List())
  }

  given Arbitrary[Sequence.State[IO]] = Arbitrary {
    for {
      seq <- arbitrary[Sequence[IO]]
      st  <- arbitrary[SequenceState]
    } yield Sequence.State.Final(seq, st)
  }

  given Cogen[Sequence.State[IO]] =
    Cogen[Observation.Id].contramap(_.toSequence.id)

  given Arbitrary[TestState] = Arbitrary {
    for {
      q <- arbitrary[Map[Observation.Id, Sequence.State[IO]]]
    } yield TestState(q)
  }

  checkAll(
    "sequence optional",
    OptionalTests[TestState, Sequence.State[IO], Observation.Id](TestState.sequenceStateIndex)
  )

}
