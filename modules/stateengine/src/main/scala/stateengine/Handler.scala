// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package stateengine

import cats.{Applicative, Monad}
import cats.data.State
import cats.syntax.all.*
import fs2.Stream
import stateengine.Handler.RetVal

/**
 * Type constructor where all Observe side effect are managed. Handler is a State machine which can
 * produce Streams as output. It is combined with the input stream to run observe engine.
 *
 * Its type parameters are: F: Effect for the Stream, A: Type of the output (usually Unit) V: Type
 * of the events D: Type of the state machine state.
 */
final case class Handler[F[_], D, V, A](run: State[D, RetVal[F, V, A]])

object Handler {
  def fromStream[F[_], D, V](p: Stream[F, V]): Handler[F, D, V, Unit] =
    Handler[F, D, V, Unit](
      Applicative[State[D, *]].pure[RetVal[F, V, Unit]](RetVal((), Some(p)))
    )

  given [F[_], D, V]: Monad[Handler[F, D, V, *]] =
    new Monad[Handler[F, D, V, *]] {
      private def concatOpP(
        op1: Option[Stream[F, V]],
        op2: Option[Stream[F, V]]
      ): Option[Stream[F, V]] = (op1, op2) match {
        case (None, None)         => None
        case (Some(p1), None)     => Some(p1)
        case (None, Some(p2))     => Some(p2)
        case (Some(p1), Some(p2)) => Some(p1 ++ p2)
      }

      override def pure[A](a: A): Handler[F, D, V, A] = Handler.pure(a)

      override def flatMap[A, B](
        fa: Handler[F, D, V, A]
      )(f: A => Handler[F, D, V, B]): Handler[F, D, V, B] = Handler[F, D, V, B](
        fa.run.flatMap { case RetVal(a, op1) =>
          f(a).run.map { case RetVal(b, op2) =>
            RetVal(b, concatOpP(op1, op2))
          }
        }
      )

      def tailRecM[A, B](a: A)(f: A => Handler[F, D, V, Either[A, B]]): Handler[F, D, V, B] = {
        // We don't really care what this type is
        // type Unused = Option[Stream[F, V]]

        // Construct a StateT that delegates to F's tailRecM
        val st: State[D, RetVal[F, V, B]] = a.tailRecM[State[D, *], RetVal[F, V, B]] { x =>
          State[D, Either[A, RetVal[F, V, B]]] { s =>
            f(x).run
              .run(s)
              .map {
                case (sprima, RetVal(Left(a), _))  => (sprima, Left(a))
                case (sprima, RetVal(Right(b), u)) => (sprima, Right(RetVal[F, V, B](b, u)))
              }
              .value
          }
        }

        // Done
        Handler(st)

      }
    }

  // This class adds a method to Handler similar to flatMap, but the Streams resulting from both Handler instances
  // are concatenated in the reverse order.
  extension [F[_]: Monad, D, V, A](self: Handler[F, D, V, A]) {
    private def reverseConcatOpP(
      op1: Option[Stream[F, V]],
      op2: Option[Stream[F, V]]
    ): Option[Stream[F, V]] = (op1, op2) match {
      case (None, None)         => None
      case (Some(p1), None)     => Some(p1)
      case (None, Some(p2))     => Some(p2)
      case (Some(p1), Some(p2)) => Some(p2 ++ p1)
    }

    def reversedStreamFlatMap[B](f: A => Handler[F, D, V, B]): Handler[F, D, V, B] =
      Handler[F, D, V, B](
        self.run.flatMap { case RetVal(a, op1) =>
          f(a).run.map { case RetVal(b, op2) =>
            RetVal(b, reverseConcatOpP(op1, op2))
          }
        }
      )

    def withStream(s: Stream[F, V]): Handler[F, D, V, A] = Handler(
      self.run.map { case RetVal(a, _) =>
        RetVal(a, s.some)
      }
    )
  }

  extension [F[_], D, V, A](self: State[D, A]) {
    def toHandle: Handler[F, D, V, A] = Handler(self.map(RetVal(_, none[Stream[F, V]])))
  }

  def unit[F[_], D, V]: Handler[F, D, V, Unit] =
    Applicative[Handler[F, D, V, *]].unit

  def get[F[_], D, V]: Handler[F, D, V, D] =
    State.get[D].toHandle

  def inspect[F[_], D, V, A](f: D => A): Handler[F, D, V, A] =
    State.inspect[D, A](f).toHandle

  def modify[F[_], D, V](f: D => D): Handler[F, D, V, Unit] =
    State.modify[D](f).toHandle

  def pure[F[_], D, V, A](v: A): Handler[F, D, V, A] = Handler(
    State.pure[D, RetVal[F, V, A]](RetVal(v, none))
  )

  final case class RetVal[F[_], V, +A](v: A, s: Option[Stream[F, V]])

}
