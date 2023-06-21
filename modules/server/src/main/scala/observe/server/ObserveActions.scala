// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import scala.concurrent.duration.*
import cats.*
import cats.effect.MonadThrow
import cats.syntax.all.*
import fs2.Stream
import org.typelevel.log4cats.Logger
import observe.engine.*
import observe.model.{Observation, StepId}
import observe.model.dhs.*
import observe.model.enums.ObserveCommandResult
import InstrumentSystem.*
import squants.time.Time
import squants.time.TimeConversions.*
import cats.effect.Temporal
import eu.timepit.refined.types.numeric.PosInt
import lucuma.schemas.ObservationDB.Scalars.VisitId
//import lucuma.schemas.ObservationDB.Enums.SequenceType

/**
 * Methods usedd to generate observation related actions
 */
trait ObserveActions {

  private def info[F[_]: Logger](msg: => String): F[Unit] = Logger[F].info(msg)

  /**
   * Actions to perform when an observe is aborted
   */
  def abortTail[F[_]: MonadThrow](
    odb:         OdbProxy[F],
    visitId:     Option[VisitId],
    obsIdName:   Observation.IdName,
    imageFileId: ImageFileId
  ): F[Result] = visitId
    .map(
      odb
        .obsAbort(_, obsIdName, imageFileId)
        .ensure(
          ObserveFailure
            .Unexpected("Unable to send ObservationAborted message to ODB.")
        )(identity)
    )
    .getOrElse(Applicative[F].unit)
    .as(Result.OKAborted(Response.Aborted(imageFileId)))

  /**
   * Send the datasetStart command to the odb
   */
  private def sendDataStart[F[_]: MonadThrow](
    odb:          OdbProxy[F],
    visitId:      VisitId,
    obsIdName:    Observation.IdName,
    stepId:       StepId,
    datasetIndex: PosInt,
    fileId:       ImageFileId
  ): F[Unit] =
    odb
      .datasetStart(visitId, obsIdName, stepId, datasetIndex, fileId)
      .ensure(
        ObserveFailure.Unexpected("Unable to send DataStart message to ODB.")
      )(identity)
      .void

  /**
   * Send the datasetEnd command to the odb
   */
  private def sendDataEnd[F[_]: MonadThrow](
    odb:          OdbProxy[F],
    visitId:      VisitId,
    obsIdName:    Observation.IdName,
    stepId:       StepId,
    datasetIndex: PosInt,
    fileId:       ImageFileId
  ): F[Unit] =
    odb
      .datasetComplete(visitId, obsIdName, stepId, datasetIndex, fileId)
      .ensure(
        ObserveFailure.Unexpected("Unable to send DataEnd message to ODB.")
      )(identity)
      .void

  /**
   * Standard progress stream for an observation
   */
  def observationProgressStream[F[_]](
    env: ObserveEnvironment[F]
  ): Stream[F, Result] = env.inst.observeProgress(env.inst.calcObserveTime, ElapsedTime(0.0.seconds)).map(Result.Partial(_))

  /**
   * Tell each subsystem that an observe will start
   */
  def notifyObserveStart[F[_]: Applicative](
    env: ObserveEnvironment[F]
  ): F[Unit] =
    env.otherSys.traverse_(_.notifyObserveStart)

  /**
   * Tell each subsystem that an observe will end Unlike observe start we also tell the instrumetn
   * about it
   */
  def notifyObserveEnd[F[_]: Applicative](env: ObserveEnvironment[F]): F[Unit] =
    (env.inst +: env.otherSys).traverse_(_.notifyObserveEnd)

  /**
   * Close the image, telling either DHS or GDS as it correspond
   */
  def closeImage[F[_]](id: ImageFileId, env: ObserveEnvironment[F]): F[Unit] =
    env.inst.keywordsClient.closeImage(id)

  /**
   * Preamble for observations. It tells the odb, the subsystems send the start headers and finally
   * sends an observe
   */
  def observePreamble[F[_]: Concurrent: Logger](
    fileId: ImageFileId,
    env:    ObserveEnvironment[F]
  ): F[ObserveCommandResult] =
    for {
      _ <- env.ctx.visitId
             .map(sendDataStart(env.odb, _, env.obsIdName, env.stepId, env.datasetIndex, fileId))
             .getOrElse(Applicative[F].unit)
      _ <- notifyObserveStart(env)
      _ <- env.headers(env.ctx).traverse(_.sendBefore(env.obsIdName.id, fileId))
      _ <-
        info(
          s"Start ${env.inst.resource.show} observation ${env.obsIdName.name} with label $fileId"
        )
      r <- env.inst.observe(fileId)
      _ <-
        info(
          s"Completed ${env.inst.resource.show} observation ${env.obsIdName.name} with label $fileId"
        )
    } yield r

  /**
   * End of an observation for a typical instrument It tells the odb and each subsystem and also
   * sends the end observation keywords
   */
  def okTail[F[_]: Concurrent](
    fileId:  ImageFileId,
    stopped: Boolean,
    env:     ObserveEnvironment[F]
  ): F[Result] =
    for {
      _ <- notifyObserveEnd(env)
      _ <- env.headers(env.ctx).reverseIterator.toList.traverse(_.sendAfter(fileId))
      _ <- closeImage(fileId, env)
      _ <- env.ctx.visitId
             .map(sendDataEnd(env.odb, _, env.obsIdName, env.stepId, env.datasetIndex, fileId))
             .getOrElse(Applicative[F].unit)
    } yield
      if (stopped) Result.OKStopped(Response.Observed(fileId))
      else Result.OK(Response.Observed(fileId))

  /**
   * Method to process observe results and act accordingly to the response
   */
  private def observeTail[F[_]: Temporal, S, D](
    fileId: ImageFileId,
    env:    ObserveEnvironment[F]
  )(r:      ObserveCommandResult): Stream[F, Result] =
    Stream.eval(r match {
      case ObserveCommandResult.Success =>
        okTail(fileId, stopped = false, env)
      case ObserveCommandResult.Stopped =>
        okTail(fileId, stopped = true, env)
      case ObserveCommandResult.Aborted =>
        abortTail(env.odb, env.ctx.visitId, env.obsIdName, fileId)
      case ObserveCommandResult.Paused  =>
        val totalTime = env.inst.calcObserveTime
            env.inst.observeControl match {
              case c: CompleteControl[F] =>
                val resumePaused: Duration => Stream[F, Result]    =
                  (remaining: Duration) =>
                    Stream
                      .eval {
                        c.continue
                          .self(remaining)
                      }
                      .flatMap(observeTail(fileId, env))
                val progress: ElapsedTime => Stream[F, Result] =
                  (elapsed: ElapsedTime) =>
                    env.inst
                      .observeProgress(totalTime, elapsed)
                      .map(Result.Partial(_))
                      .widen[Result]
                val stopPaused: Stream[F, Result]              =
                  Stream
                    .eval {
                      c.stopPaused.self
                    }
                    .flatMap(observeTail(fileId, env))
                val abortPaused: Stream[F, Result]             =
                  Stream
                    .eval {
                      c.abortPaused.self
                    }
                    .flatMap(observeTail(fileId, env))

                Result
                  .Paused(
                    ObserveContext[F](
                      resumePaused,
                      progress,
                      stopPaused,
                      abortPaused,
                      totalTime
                    )
                  )
                  .pure[F]
                  .widen[Result]
              case _                     =>
                ObserveFailure
                  .Execution("Observation paused for an instrument that does not support pause")
                  .raiseError[F, Result]
            }
    })

  /**
   * Observe for a typical instrument
   */
  def stdObserve[F[_]: Temporal: Logger](
    fileId: ImageFileId,
    env:    ObserveEnvironment[F]
  ): Stream[F, Result] =
    for {
      result <- Stream.eval(observePreamble(fileId, env))
      ret    <- observeTail(fileId, env)(result)
    } yield ret

}

object ObserveActions extends ObserveActions
