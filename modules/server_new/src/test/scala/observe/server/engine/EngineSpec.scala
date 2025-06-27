// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.engine

import cats.Eq
import cats.effect.IO
import lucuma.core.model.sequence.Atom
import lucuma.core.util.arb.ArbGid.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.Observation
import observe.model.SequenceState
import observe.model.arb.ObserveModelArbitraries.given
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

final class EngineSpec extends munit.DisciplineSuite {

  given Eq[Sequence.State[IO]] = Eq.fromUniversalEquals

  given Arbitrary[Sequence[IO]] = Arbitrary {
    for {
      id  <- arbitrary[Observation.Id]
      aid <- arbitrary[Atom.Id]
    } yield Sequence.sequence(id, aid, List(), Breakpoints.empty)
  }

  given Arbitrary[Sequence.State[IO]] = Arbitrary {
    for {
      seq <- arbitrary[Sequence[IO]]
      st  <- arbitrary[SequenceState]
    } yield Sequence.State.Final(seq, st, Breakpoints.empty)
  }

  given Cogen[Sequence.State[IO]] =
    Cogen[Observation.Id].contramap(_.toSequence.id)

}
