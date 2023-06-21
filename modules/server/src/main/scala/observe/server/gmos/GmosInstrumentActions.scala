// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.syntax.all.*
import fs2.Stream
import org.typelevel.log4cats.Logger
import observe.engine.ParallelActions
import observe.engine.Result
import observe.model.NSSubexposure
import observe.model.dhs.*
import observe.model.enums.Guiding
import observe.model.enums.NodAndShuffleStage.*
import observe.model.enums.ObserveCommandResult
import observe.server.InstrumentActions.*
import observe.server.InstrumentSystem.ElapsedTime
import observe.server.ObserveActions.*
import observe.server.*
import observe.server.gmos.GmosController.Config.*
import observe.server.gmos.NSObserveCommand.*
import observe.server.gmos.NSPartial.*
import observe.server.tcs.TcsController.InstrumentOffset
import observe.server.tcs.TcsController.OffsetP
import observe.server.tcs.TcsController.OffsetQ
import shapeless.tag
import squants.space.AngleConversions.*
import cats.effect.{Ref, Temporal}
import observe.common.ObsQueriesGQL.ObsQuery.{GmosInstrumentConfig, GmosSite, GmosStatic}

import scala.concurrent.duration.Duration

/**
 * Gmos needs different actions for N&S
 */
class GmosInstrumentActions[F[_]: Temporal: Logger, A <: GmosSite](
  inst:   Gmos[F, A]
) extends InstrumentActions[F] {
  override def observationProgressStream(
    env: ObserveEnvironment[F]
  ): Stream[F, Result] =
    ObserveActions.observationProgressStream(env)

  // This tail is based on ObserveActions.observeTail
  // But it can understand how to process Partial observations
  // And can eventually return more than one result
  private def observeTail(
    fileId: ImageFileId,
    env:    ObserveEnvironment[F],
    nsCfg:  NSConfig.NodAndShuffle
  )(r:      ObserveCommandResult): F[Result] =
    r match {
      case ObserveCommandResult.Success =>
        okTail(fileId, stopped = false, env)
          .as(Result.Partial(NSFinalObs)) // For normally completed observations send a partial
      case ObserveCommandResult.Stopped =>
        okTail(fileId, stopped = true, env)
      case ObserveCommandResult.Aborted =>
        abortTail(env.odb, env.ctx.visitId, env.obsIdName, fileId)
      case ObserveCommandResult.Paused  =>
            Result
              .Paused(
                ObserveContext(
                  (_: Duration) => resumeObserve(fileId, env, nsCfg),
                  (_: ElapsedTime) => observationProgressStream(env),
                  stopPausedObserve(fileId, env, nsCfg),
                  abortPausedObserve(fileId, env, nsCfg),
                  env.inst.calcObserveTime
                )
              ).pure[F].widen

    }

  private def initialObserve(
    fileId:   ImageFileId,
    env:      ObserveEnvironment[F],
    nsCfg:    NSConfig.NodAndShuffle,
    subExp:   NSSubexposure,
    nsObsCmd: Ref[F, Option[NSObserveCommand]]
  ): F[Result] =
    // Essentially the same as default observation but with a custom tail
    (for {
      result <- observePreamble(fileId, env)
      nsCmd  <- nsObsCmd.get
      ret    <- continueResult(fileId, env, nsCfg, subExp, nsCmd)(result)
    } yield ret).safeResult

  private def lastObserve(
    fileId: ImageFileId,
    env:    ObserveEnvironment[F],
    nsCfg:  NSConfig.NodAndShuffle
  ): F[Result] =
    // the last step completes the observations doing an observeTail
    (for {
      ret <- inst.controller.resumePaused(inst.calcObserveTime)
      t   <- observeTail(fileId, env, nsCfg)(ret)
    } yield t).safeResult

  private def continueResult(
    fileId:    ImageFileId,
    env:       ObserveEnvironment[F],
    nsCfg:     NSConfig.NodAndShuffle,
    subExp:    NSSubexposure,
    nsObsCmd:  Option[NSObserveCommand]
  )(obsResult: ObserveCommandResult): F[Result] =
    (nsObsCmd, obsResult) match {
      case (Some(PauseImmediately), ObserveCommandResult.Paused) |
          (_, ObserveCommandResult.Success) | (_, ObserveCommandResult.Aborted) |
          (_, ObserveCommandResult.Stopped) =>
        observeTail(fileId, env, nsCfg)(obsResult)

      // Pause if this was the last subexposure of a cycle
      case (Some(PauseGracefully), ObserveCommandResult.Paused)
          if subExp.stageIndex === NsSequence.length - 1 =>
        observeTail(fileId, env, nsCfg)(obsResult)

      case (Some(StopImmediately), ObserveCommandResult.Paused) =>
        inst.controller.stopPaused
          .flatMap(observeTail(fileId, env, nsCfg))

      // Stop if this was the last subexposure of a cycle
      case (Some(StopGracefully), ObserveCommandResult.Paused)
          if subExp.stageIndex === NsSequence.length - 1 =>
        inst.controller.stopPaused
          .flatMap(observeTail(fileId, env, nsCfg))

      case (Some(AbortImmediately), ObserveCommandResult.Paused) =>
        inst.controller.abortPaused
          .flatMap(observeTail(fileId, env, nsCfg))

      // Abort if this was the last subexposure of a cycle
      case (Some(AbortGracefully), ObserveCommandResult.Paused)
          if subExp.stageIndex === NsSequence.length - 1 =>
        inst.controller.abortPaused
          .flatMap(observeTail(fileId, env, nsCfg))

      // We reach here only if the result was Paused and no command made it stop/pause/abort
      case _                                                     => Result.Partial(NSContinue).pure[F].widen[Result]

    }

  private def continueObserve(
    fileId:      ImageFileId,
    env:         ObserveEnvironment[F],
    nsCfg:       NSConfig.NodAndShuffle,
    subExp:      NSSubexposure,
    nsObsCmdRef: Ref[F, Option[NSObserveCommand]]
  ): F[Result] = (
    for {
      r     <- inst.controller.resumePaused(inst.calcObserveTime)
      nsCmd <- nsObsCmdRef.get
      x     <- continueResult(fileId, env, nsCfg, subExp, nsCmd)(r)
    } yield x
  ).safeResult

  /**
   * Stream of actions of one sub exposure
   */
  def oneSubExposure(
    fileId:    ImageFileId,
    sub:       NSSubexposure,
    positions: Vector[NSPosition],
    env:       ObserveEnvironment[F],
    nsCfg:     NSConfig.NodAndShuffle,
    nsCmd:     Ref[F, Option[NSObserveCommand]]
  ): Stream[F, Result] = {
    val nsPositionO = positions.find(_.stage === sub.stage)
    // TCS Nod
    (env.getTcs, nsPositionO).mapN { case (tcs, nsPos) =>
      Stream.emit(Result.Partial(NSTCSNodStart(sub))) ++
        Stream.eval(
          tcs
            .nod(
              sub.stage,
              InstrumentOffset(
                tag[OffsetP](nsPos.offset.p.toSignedDoubleRadians.radians),
                tag[OffsetQ](nsPos.offset.q.toSignedDoubleRadians.radians)
              ),
              nsPos.guide === Guiding.Guide
            )
            .as(Result.Partial(NSTCSNodComplete(sub)))
            .widen[Result]
            .safeResult
        )
    }.orEmpty ++
      // Observes for each subexposure
      observationProgressStream(env)
        .mergeHaltR(
          Stream.emit(Result.Partial(NSSubexposureStart(sub))) ++
            (if (sub.firstSubexposure) {
               Stream.eval(initialObserve(fileId, env, nsCfg, sub, nsCmd))
             } else if (sub.lastSubexposure) {
               Stream.eval(lastObserve(fileId, env, nsCfg))
             } else {
               Stream.eval(continueObserve(fileId, env, nsCfg, sub, nsCmd))
             }) ++
            Stream.emit(Result.Partial(NSSubexposureEnd(sub)))
        )
        .handleErrorWith(catchObsErrors[F])
  }

  private def doObserve(
    fileId: ImageFileId,
    env:    ObserveEnvironment[F]
  ): Stream[F, Result] =
    inst.nsConfig match {
      case NSConfig.NoNodAndShuffle                            =>
        Stream.empty
      case c @ NSConfig.NodAndShuffle(cycles, _, positions, _) =>
        val nsZero =
          NSSubexposure
            .subexposures(cycles)
            .headOption
            .getOrElse(NSSubexposure.Zero)
        val nsLast =
          NSSubexposure
            .subexposures(cycles)
            .lastOption
            .getOrElse(NSSubexposure.Zero)

        // Clean NS command Ref
        Stream.eval(inst.nsCmdRef.set(none)) *>
          // Initial notification of N&S Starting
          Stream.emit(Result.Partial(NSStart(nsZero))) ++
          // each subexposure actions
          NSSubexposure
            .subexposures(cycles)
            .map {
              oneSubExposure(fileId, _, positions, env, c, inst.nsCmdRef)
            }
            .reduceOption(_ ++ _)
            .orEmpty ++
          Stream.emit(Result.Partial(NSComplete(nsLast))) ++
          Stream.emit(Result.OK(Response.Observed(fileId)))
    }

  def resumeObserve(
    fileId:   ImageFileId,
    env:      ObserveEnvironment[F],
    nsConfig: NSConfig.NodAndShuffle
  ): Stream[F, Result] = {

    val nsLast =
      NSSubexposure
        .subexposures(nsConfig.cycles)
        .lastOption
        .getOrElse(NSSubexposure.Zero)

    Stream.eval(inst.nsCount).flatMap { cnt =>
      Stream.eval(inst.nsCmdRef.set(none)) *>
        NSSubexposure
          .subexposures(nsConfig.cycles)
          .map {
            oneSubExposure(fileId, _, nsConfig.positions, env, nsConfig, inst.nsCmdRef)
          }
          .drop(cnt)
          .reduceOption(_ ++ _)
          .orEmpty ++
        Stream.emit(Result.Partial(NSComplete(nsLast))) ++
        Stream.emit(Result.OK(Response.Observed(fileId)))
    }
  }

  def stopPausedObserve(
    fileId: ImageFileId,
    env:    ObserveEnvironment[F],
    nsCfg:  NSConfig.NodAndShuffle
  ): Stream[F, Result] = Stream.eval(
    inst.controller.stopPaused.flatMap(observeTail(fileId, env, nsCfg))
  )

  def abortPausedObserve(
    fileId: ImageFileId,
    env:    ObserveEnvironment[F],
    nsCfg:  NSConfig.NodAndShuffle
  ): Stream[F, Result] = Stream.eval(
    inst.controller.abortPaused.flatMap(observeTail(fileId, env, nsCfg))
  )

  def launchObserve(
    env: ObserveEnvironment[F]
  ): Stream[F, Result] =
    Stream
      .eval(FileIdProvider.fileId(env))
      .flatMap { fileId =>
        Stream.emit(Result.Partial(FileIdAllocated(fileId))) ++ doObserve(fileId, env)
      }
      .handleErrorWith(catchObsErrors[F])

  override def observeActions(
    env: ObserveEnvironment[F]
  ): List[ParallelActions[F]] =
    env.stepType match {
      case StepType.NodAndShuffle(i) if i === inst.resource =>
        defaultObserveActions(launchObserve(env))
      case StepType.DarkOrBiasNS(i) if i === inst.resource  =>
        defaultObserveActions(launchObserve(env))

      case _ =>
        // Regular GMOS observations behave as any instrument
        defaultInstrumentActions[F].observeActions(env)
    }

  def runInitialAction(stepType: StepType): Boolean = true

}
