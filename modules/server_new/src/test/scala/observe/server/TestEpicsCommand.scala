// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.effect.Ref
import cats.syntax.all.*
import cats.{Applicative, Monad}
import monocle.{Focus, Lens}
import observe.model.enums.ApplyCommandResult

import scala.concurrent.duration.FiniteDuration

object TestEpicsCommand {

  /*
   * Type parameters:
   * A: Type of the recorded events
   * S: State affected by the command.
   *
   * Parameters:
   * ist: initial internal state
   * markL: Lens to the mark flag in the internal state
   * st: Ref to system state
   * out: Ref to events accumulator
   */
  abstract class TestEpicsCommand0[F[_]: Monad, S, A](
    markL: Lens[S, TestEpicsCommand0.State],
    st:    Ref[F, S],
    out:   Ref[F, List[A]]
  ) extends EpicsCommand[F] {
    override def post(timeout: FiniteDuration): F[ApplyCommandResult] = st.get.flatMap { s =>
      if (markL.get(s))
        out.modify(x => (x :+ event(s), ())) *>
          st.modify(s => (markL.replace(false)(cmd(s)), ApplyCommandResult.Completed))
      else
        ApplyCommandResult.Completed.pure[F].widen[ApplyCommandResult]
    }

    override def mark: F[Unit] = st.modify(s => (markL.replace(true)(s), ()))

    protected def event(st: S): A

    protected def cmd(st: S): S
  }

  object TestEpicsCommand0 {
    type State = Boolean
  }

  abstract class TestEpicsCommand1[F[_]: Monad, S, A, U](
    l:   Lens[S, TestEpicsCommand1.State[U]],
    st:  Ref[F, S],
    out: Ref[F, List[A]]
  ) extends TestEpicsCommand0[F, S, A](l.andThen(TestEpicsCommand1.State.mark), st, out) {
    def setParameter1(u: U): F[Unit] =
      st.modify(s => (l.andThen(TestEpicsCommand1.State.param1[U]).replace(u)(s), ())) *> mark
  }

  object TestEpicsCommand1 {
    final case class State[U](mark: Boolean, param1: U)

    object State {
      def mark[U]: Lens[State[U], Boolean] = Focus[State[U]](_.mark)

      def param1[U]: Lens[State[U], U] = Focus[State[U]](_.param1)
    }
  }

  abstract class TestEpicsCommand2[F[_]: Monad, S, A, U, V](
    l:   Lens[S, TestEpicsCommand2.State[U, V]],
    st:  Ref[F, S],
    out: Ref[F, List[A]]
  ) extends TestEpicsCommand0[F, S, A](l.andThen(TestEpicsCommand2.State.mark), st, out) {
    def setParameter1(v: U): F[Unit] =
      st.modify(s => (l.andThen(TestEpicsCommand2.State.param1[U, V]).replace(v)(s), ())) *> mark
    def setParameter2(v: V): F[Unit] =
      st.modify(s => (l.andThen(TestEpicsCommand2.State.param2[U, V]).replace(v)(s), ())) *> mark
  }

  object TestEpicsCommand2 {
    final case class State[U, V](mark: Boolean, param1: U, param2: V)

    object State {
      def mark[U, V]: Lens[State[U, V], Boolean] = Focus[State[U, V]](_.mark)

      def param1[U, V]: Lens[State[U, V], U] = Focus[State[U, V]](_.param1)

      def param2[U, V]: Lens[State[U, V], V] = Focus[State[U, V]](_.param2)
    }
  }

  abstract class TestEpicsCommand3[F[_]: Monad, S, A, U, V, W](
    l:   Lens[S, TestEpicsCommand3.State[U, V, W]],
    st:  Ref[F, S],
    out: Ref[F, List[A]]
  ) extends TestEpicsCommand0[F, S, A](l.andThen(TestEpicsCommand3.State.mark[U, V, W]), st, out) {
    def setParameter1(u: U): F[Unit] =
      st.modify(s => (l.andThen(TestEpicsCommand3.State.param1[U, V, W]).replace(u)(s), ())) *> mark
    def setParameter2(v: V): F[Unit] =
      st.modify(s => (l.andThen(TestEpicsCommand3.State.param2[U, V, W]).replace(v)(s), ())) *> mark
    def setParameter3(w: W): F[Unit] =
      st.modify(s => (l.andThen(TestEpicsCommand3.State.param3[U, V, W]).replace(w)(s), ())) *> mark
  }

  object TestEpicsCommand3 {
    final case class State[U, V, W](mark: Boolean, param1: U, param2: V, param3: W)

    object State {
      def mark[U, V, W]: Lens[State[U, V, W], Boolean] = Focus[State[U, V, W]](_.mark)

      def param1[U, V, W]: Lens[State[U, V, W], U] = Focus[State[U, V, W]](_.param1)

      def param2[U, V, W]: Lens[State[U, V, W], V] = Focus[State[U, V, W]](_.param2)

      def param3[U, V, W]: Lens[State[U, V, W], W] = Focus[State[U, V, W]](_.param3)
    }
  }

  abstract class TestEpicsCommand4[F[_]: Monad, S, A, U, V, W, X](
    l:   Lens[S, TestEpicsCommand4.State[U, V, W, X]],
    st:  Ref[F, S],
    out: Ref[F, List[A]]
  ) extends TestEpicsCommand0[F, S, A](l.andThen(TestEpicsCommand4.State.mark[U, V, W, X]),
                                       st,
                                       out
      ) {
    def setParameter1(u: U): F[Unit] =
      st.modify(s =>
        (l.andThen(TestEpicsCommand4.State.param1[U, V, W, X]).replace(u)(s), ())
      ) *> mark
    def setParameter2(v: V): F[Unit] =
      st.modify(s =>
        (l.andThen(TestEpicsCommand4.State.param2[U, V, W, X]).replace(v)(s), ())
      ) *> mark
    def setParameter3(w: W): F[Unit] =
      st.modify(s =>
        (l.andThen(TestEpicsCommand4.State.param3[U, V, W, X]).replace(w)(s), ())
      ) *> mark
    def setParameter4(x: X): F[Unit] =
      st.modify(s =>
        (l.andThen(TestEpicsCommand4.State.param4[U, V, W, X]).replace(x)(s), ())
      ) *> mark
  }

  object TestEpicsCommand4 {
    final case class State[U, V, W, X](mark: Boolean, param1: U, param2: V, param3: W, param4: X)

    object State {
      def mark[U, V, W, X]: Lens[State[U, V, W, X], Boolean] = Focus[State[U, V, W, X]](_.mark)

      def param1[U, V, W, X]: Lens[State[U, V, W, X], U] = Focus[State[U, V, W, X]](_.param1)

      def param2[U, V, W, X]: Lens[State[U, V, W, X], V] = Focus[State[U, V, W, X]](_.param2)

      def param3[U, V, W, X]: Lens[State[U, V, W, X], W] = Focus[State[U, V, W, X]](_.param3)

      def param4[U, V, W, X]: Lens[State[U, V, W, X], X] = Focus[State[U, V, W, X]](_.param4)
    }
  }

  class DummyCmd[F[_]: Applicative] extends EpicsCommand[F] {
    override def post(timeout: FiniteDuration): F[ApplyCommandResult] =
      ApplyCommandResult.Completed.pure[F].widen[ApplyCommandResult]
    override def mark: F[Unit]                                        = Applicative[F].unit
  }

}
