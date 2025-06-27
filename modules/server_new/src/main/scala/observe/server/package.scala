// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.MonadError
import cats.MonadThrow
import cats.effect.IO
import cats.effect.std.Queue
import cats.syntax.all.*
import fs2.Stream
import lucuma.core.enums.Breakpoint
import lucuma.core.util.TimeSpan
import observe.model.Conditions
import observe.model.Observer
import observe.model.Operator
import observe.model.QueueId
import observe.model.SystemOverrides
import observe.server.InstrumentSystem.ElapsedTime
import observe.server.SequenceGen.StepGen
import observe.server.engine.Event
import observe.server.engine.Result
import observe.server.engine.Result.PauseContext
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

case class Selected[F[_]](
  gmosSouth:  Option[SequenceData[F]],
  gmosNorth:  Option[SequenceData[F]],
  flamingos2: Option[SequenceData[F]]
)

object Selected        {
  def none[F[_]]: Selected[F] = Selected(None, None, None)
}

case class HeaderExtraData(
  conditions: Conditions,
  operator:   Option[Operator],
  observer:   Option[Observer]
)
object HeaderExtraData {
  val default: HeaderExtraData = HeaderExtraData(Conditions.Default, None, None)
}

case class ObserveContext[F[_]](
  resumePaused: TimeSpan => Stream[F, Result],
  progress:     ElapsedTime => Stream[F, Result],
  stopPaused:   Stream[F, Result],
  abortPaused:  Stream[F, Result],
  expTime:      TimeSpan
) extends PauseContext

type ExecutionQueues = Map[QueueId, ExecutionQueue]

// This is far from ideal but we'll address this in another refactoring
private given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("observe-engine")

type EventQueue[F[_]] = Queue[F, Event[F]]

def toStepList[F[_]](
  seq:         SequenceGen[F],
  overrides:   SystemOverrides,
  headerExtra: HeaderExtraData
): List[(engine.EngineStep[F], Breakpoint)] =
  seq.nextAtom.steps.map(StepGen.generate(_, overrides, headerExtra))

// If f is true continue, otherwise fail
def failUnlessM[F[_]: MonadThrow](f: F[Boolean], err: Exception): F[Unit] =
  f.flatMap {
    MonadError[F, Throwable].raiseError(err).unlessA
  }

def catchObsErrors[F[_]](t: Throwable)(using L: Logger[F]): Stream[F, Result] = t match {
  case e: ObserveFailure =>
    Stream.eval(L.error(e)(s"Observation error: ${ObserveFailure.explain(e)}")) *>
      Stream.emit(Result.Error(ObserveFailure.explain(e)))
  case e: Throwable      =>
    Stream.eval(L.error(e)(s"Observation error: ${e.getMessage}")) *>
      Stream.emit(Result.Error(ObserveFailure.explain(ObserveFailure.ObserveException(e))))
}

def overrideLogMessage[F[_]: Logger](systemName: String, op: String): F[Unit] =
  Logger[F].info(s"System $systemName overridden. Operation $op skipped.")
