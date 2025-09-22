// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.engine

import cats.FlatMap
import cats.Id
import cats.Monad
import cats.data.StateT
import cats.effect.IO
import cats.effect.kernel.testkit.TestContext
import cats.effect.laws.*
import cats.effect.testkit.TestInstances
import cats.kernel.Eq
import cats.laws.discipline.*
import cats.laws.discipline.arbitrary.*
import cats.laws.discipline.eq.*
import cats.syntax.all.*
import cats.~>
import fs2.Compiler
import observe.server.engine.Handle
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Cogen

class HandleSpec extends munit.DisciplineSuite with TestInstances {
  given [F[_]: Monad, S, E, O](using
    Arbitrary[F[S => F[(S, (O, List[E]))]]]
  ): Arbitrary[Handle[F, S, E, O]] =
    Arbitrary:
      arbitrary[StateT[F, S, (O, List[E])]].map: st =>
        Handle:
          st.map: (o, es) =>
            (o, fs2.Stream.emits(es))

  given [F[_]: FlatMap, S, A](using Cogen[S => F[(S, A)]]): Cogen[StateT[F, S, A]] =
    Cogen[S => F[(S, A)]].contramap(_.run)

  given [F[_]: Monad, S, E, O](using
    Cogen[StateT[F, S, (O, List[E])]]
  )(using fk: F ~> Id): Cogen[Handle[F, S, E, O]] =
    Cogen[StateT[F, S, (O, List[E])]]
      .contramap(_.stateT.map((o, es) => (o, es.translate(fk).compile.toList)))

  given ioFunc: (IO ~> Id) =
    new (IO ~> Id) {
      def apply[A](fa: IO[A]): Id[A] =
        fa.unsafeRunSync()
    }

  // Adapted from https://github.com/typelevel/cats/blob/main/tests/shared/src/test/scala/cats/tests/IndexedStateTSuite.scala
  given [F[_]: FlatMap, S: ExhaustiveCheck, E, O](using
    Eq[F[(S, (O, List[E]))]]
  )(using fk: F ~> Id): Eq[Handle[F, S, E, O]] =
    Eq.by[Handle[F, S, E, O], S => F[(S, (O, List[E]))]](state =>
      s0 =>
        state.stateT.run(s0).map { case (s1, (o, es)) =>
          (s1, (o, es.translate(fk).compile.toList))
        }
    )

  given Eq[Throwable] = Eq.fromUniversalEquals

  given Ticker = Ticker(TestContext())

  checkAll(
    "Handle[Int].MonadErrorLaws",
    MonadErrorTests[Handle[IO, MiniInt, Int, *], Throwable].monadError[MiniInt, Int, String]
  )

  checkAll(
    "Handle[Int].MonadCancelLaws",
    MonadCancelTests[Handle[IO, MiniInt, Int, *], Throwable].monadCancel[MiniInt, Int, String]
  )

}
