// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.Applicative
import cats.Functor
import cats.Monad
import cats.data.StateT
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
final case class Handle[F[_], S, E, O](run: StateT[F, S, (O, Stream[F, E])])

object Handle {
  def fromEventStream[F[_]: Monad, S, E](p: Stream[F, E]): Handle[F, S, E, Unit] =
    Handle[F, S, E, Unit]:
      Applicative[StateT[F, S, *]].pure[(Unit, Stream[F, E])](((), p))

  def fromSingleEvent[F[_]: Monad, S, E](f: F[E]): Handle[F, S, E, Unit] =
    fromEventStream(Stream.eval(f))

  def liftF[F[_]: Monad, S, E, O](f: F[O]): Handle[F, S, E, O] =
    fromStateT(StateT.liftF[F, S, O](f))

  def pure[F[_]: Monad, S, E, O](a: O): Handle[F, S, E, O] =
    Handle[F, S, E, O]:
      Applicative[StateT[F, S, *]].pure((a, Stream.empty))

  given [F[_]: Monad, S, E]: Monad[Handle[F, S, E, *]] =
    new Monad[Handle[F, S, E, *]] {
      override def pure[O](a: O): Handle[F, S, E, O] =
        Handle:
          Applicative[StateT[F, S, *]].pure((a, Stream.empty))

      override def flatMap[O, O1](
        fa: Handle[F, S, E, O]
      )(f: O => Handle[F, S, E, O1]): Handle[F, S, E, O1] =
        Handle[F, S, E, O1]:
          fa.run.flatMap: (a, p1) =>
            f(a).run.map: (b, p2) =>
              (b, p1 ++ p2)

      // Kudos to @tpolecat
      def tailRecM[O, O1](a: O)(f: O => Handle[F, S, E, Either[O, O1]]): Handle[F, S, E, O1] = {
        // Construct a StateT that delegates to F's tailRecM
        val st: StateT[F, S, (O1, Stream[F, E])] =
          StateT: s =>
            Monad[F].tailRecM[(S, O), (S, (O1, Stream[F, E]))]((s, a)): (s, a) =>
              f(a).run
                .run(s)
                .map:
                  case (sʹ, (Left(a), _))  => Left((sʹ, a))
                  case (sʹ, (Right(b), u)) => Right((sʹ, (b, u)))

        // Done
        Handle(st)
      }
    }

  // This class adds a method to Handle similar to flatMap, but the Streams resulting from both Handle instances
  // are concatenated in the reverse order.
  extension [F[_]: Monad, S, E, O](self: Handle[F, S, E, O]) {
    def reversedStreamFlatMap[O1](f: O => Handle[F, S, E, O1]): Handle[F, S, E, O1] =
      Handle[F, S, E, O1]:
        self.run.flatMap: (a, p1) =>
          f(a).run.map: (b, p2) =>
            (b, p2 ++ p1)
  }

  inline def fromStateT[F[_]: Functor, S, E, O](s: StateT[F, S, O]): Handle[F, S, E, O] =
    Handle[F, S, E, O](s.map((_, Stream.empty)))

  inline def unit[F[_]: Monad, S, E]: Handle[F, S, E, Unit] =
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
}
