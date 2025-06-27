// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.engine

import cats.Applicative
import cats.Functor
import cats.Monad
import cats.MonadThrow
import cats.data.StateT
import cats.effect.MonadCancelThrow
import cats.effect.kernel.CancelScope
import cats.effect.kernel.Poll
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.Stream

/**
 * Type constructor where all Observe side effect are managed. Handle is a State machine inside a F,
 * which can produce Streams as output. It is combined with the input stream to run observe engine.
 *
 * Its type parameters are:
 * @tparam F[_]:
 *   Type of the effect
 * @tparam S:
 *   Type of the state machine state.
 * @tparam E:
 *   Type of the events
 * @tparam O:
 *   Type of the output (usually Unit)
 */
opaque type Handle[F[_], S, E, O] = StateT[F, S, (O, Stream[F, E])]

object Handle {
  inline def apply[F[_], S, E, O](stateT: StateT[F, S, (O, Stream[F, E])]): Handle[F, S, E, O] =
    stateT

  extension [F[_], S, E, O](self: Handle[F, S, E, O])
    inline def stateT: StateT[F, S, (O, Stream[F, E])] = self

  def modifyStateEmit[F[_]: MonadThrow, S, E](
    f: S => F[(S, Stream[F, E])]
  ): Handle[F, S, E, Unit] =
    Handle[F, S, E, Unit]:
      StateT: s0 =>
        f(s0).map: (s1, p) =>
          (s1, ((), p))

  inline def modifyStateEmitSingle[F[_]: MonadThrow, S, E](
    f: S => F[(S, E)]
  ): Handle[F, S, E, Unit] =
    modifyStateEmit(s => f(s).map((s, e) => (s, Stream(e))))

  inline def fromEventStream[F[_]: MonadThrow, S, E](f: S => Stream[F, E]): Handle[F, S, E, Unit] =
    modifyStateEmit(s => Applicative[F].pure((s, f(s))))

  inline def fromEventStream[F[_]: MonadThrow, S, E](p: Stream[F, E]): Handle[F, S, E, Unit] =
    fromEventStream(_ => p)

  inline def fromEvents[F[_]: MonadThrow, S, E](f: S => Seq[E]): Handle[F, S, E, Unit] =
    fromEventStream(s => Stream.emits(f(s)))

  inline def fromEvents[F[_]: MonadThrow, S, E](es: E*): Handle[F, S, E, Unit] =
    fromEvents(_ => es)

  inline def fromSingleEventF[F[_]: MonadThrow, S, E](f: F[E]): Handle[F, S, E, Unit] =
    modifyStateEmitSingle(s => f.map((s, _)))

  inline def fromSingleEvent[F[_]: MonadThrow, S, E](e: E): Handle[F, S, E, Unit] =
    fromSingleEventF(e.pure[F])

  inline def liftF[F[_]: MonadThrow, S, E, O](f: F[O]): Handle[F, S, E, O] =
    fromStateT(StateT.liftF[F, S, O](f))

  inline def pure[F[_]: MonadThrow, S, E, O](a: O): Handle[F, S, E, O] =
    Handle[F, S, E, O]:
      Applicative[StateT[F, S, *]].pure((a, Stream.empty))

  inline def fromStateT[F[_]: Functor, S, E, O](s: StateT[F, S, O]): Handle[F, S, E, O] =
    Handle[F, S, E, O](s.map((_, Stream.empty)))

  inline def unit[F[_]: MonadCancelThrow, S, E]: Handle[F, S, E, Unit] =
    Applicative[Handle[F, S, E, *]].unit

  inline def getState[F[_]: Applicative, S, E]: Handle[F, S, E, S] =
    fromStateT(StateT.get[F, S])

  inline def inspectState[F[_]: Applicative, S, E, O](f: S => O): Handle[F, S, E, O] =
    fromStateT(StateT.inspect[F, S, O](f))

  inline def modifyStateF[F[_]: Applicative, S, E, O](f: S => F[(S, O)]): Handle[F, S, E, O] =
    fromStateT(StateT[F, S, O](f))

  inline def modifyState[F[_]: Applicative, S, E, O](f: S => (S, O)): Handle[F, S, E, O] =
    modifyStateF(f(_).pure[F])

  inline def modifyState_[F[_]: Applicative, S, E](f: S => S): Handle[F, S, E, Unit] =
    modifyState(s => (f(s), ()))

  given [F[_]: MonadCancelThrow, S, E]: MonadCancelThrow[Handle[F, S, E, *]] =
    // given [F[_]: MonadThrow, S, E]: MonadThrow[Handle[F, S, E, *]] =
    new MonadCancelThrow[Handle[F, S, E, *]] {
      // new MonadThrow[Handle[F, S, E, *]] {

      override def pure[O](a: O): Handle[F, S, E, O] =
        Handle:
          Applicative[StateT[F, S, *]].pure((a, Stream.empty))

      override def flatMap[O, O1](
        fa: Handle[F, S, E, O]
      )(f: O => Handle[F, S, E, O1]): Handle[F, S, E, O1] =
        Handle[F, S, E, O1]:
          fa.stateT.flatMap: (a, p1) =>
            f(a).stateT.map: (b, p2) =>
              (b, p1 ++ p2)

      // Kudos to @tpolecat
      def tailRecM[O, O1](o: O)(f: O => Handle[F, S, E, Either[O, O1]]): Handle[F, S, E, O1] = {
        // Construct a StateT that delegates to F's tailRecM
        val st: StateT[F, S, (O1, Stream[F, E])] =
          StateT: s =>
            Monad[F].tailRecM[(S, (O, Stream[F, E])), (S, (O1, Stream[F, E]))](
              (s, (o, Stream.empty))
            ) { case (s0, (o0, es0)) =>
              f(o0).stateT
                .run(s0)
                .map:
                  case (s1, (Left(o1), es1))  => Left((s1, (o1, es0 ++ es1)))
                  case (s1, (Right(o1), es1)) => Right((s1, (o1, es0 ++ es1)))
            }

        // Done
        Handle(st)
      }

      override def raiseError[A](e: Throwable): Handle[F, S, E, A] =
        Handle.liftF(MonadThrow[F].raiseError(e))

      override def handleErrorWith[A](fa: Handle[F, S, E, A])(
        f: Throwable => Handle[F, S, E, A]
      ): Handle[F, S, E, A] =
        Handle:
          fa.stateT.handleErrorWith(e => f(e).stateT)

      override def rootCancelScope: CancelScope = MonadCancelThrow[F].rootCancelScope

      override def forceR[A, B](fa: Handle[F, S, E, A])(
        fb: Handle[F, S, E, B]
      ): Handle[F, S, E, B] =
        // fa.asInstanceOf[Handle[F, S, E, Unit]].handleError(_ => ()).productR(fb)
        Handle:
          StateT(s => fa.stateT.run(s).forceR(fb.stateT.run(s)))

      override def uncancelable[A](
        body: Poll[Handle[F, S, E, *]] => Handle[F, S, E, A]
      ): Handle[F, S, E, A] =
        Handle:
          StateT: s =>
            MonadCancelThrow[F].uncancelable: poll =>
              def handlePoll: Poll[Handle[F, S, E, *]] =
                new Poll[Handle[F, S, E, *]] {
                  def apply[A](fa: Handle[F, S, E, A]): Handle[F, S, E, A] =
                    Handle:
                      StateT(s0 => poll(fa.stateT.run(s0)))
                }
              body(handlePoll).stateT.run(s)

      override def canceled: Handle[F, S, E, Unit] =
        liftF(MonadCancelThrow[F].canceled)

      override def onCancel[A](
        fa:  Handle[F, S, E, A],
        fin: Handle[F, S, E, Unit]
      ): Handle[F, S, E, A] =
        Handle:
          StateT(s => fa.stateT.run(s).onCancel(fin.stateT.run(s).void))
    }

  // This class adds a method to Handle similar to flatMap, but the Streams resulting from both Handle instances
  // are concatenated in the reverse order.
  extension [F[_]: Monad, S, E, O](self: Handle[F, S, E, O]) {
    def reversedStreamFlatMap[O1](f: O => Handle[F, S, E, O1]): Handle[F, S, E, O1] =
      Handle[F, S, E, O1]:
        self.stateT.flatMap: (a, p1) =>
          f(a).stateT.map: (b, p2) =>
            (b, p2 ++ p1)
  }
}
