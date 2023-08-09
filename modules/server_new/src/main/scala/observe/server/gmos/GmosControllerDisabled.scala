// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.Applicative
import cats.syntax.all.*
import fs2.Stream
import observe.model.dhs.ImageFileId
import observe.model.enums.ObserveCommandResult
import observe.server.{InstrumentSystem, Progress, overrideLogMessage}
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.FiniteDuration

class GmosControllerDisabled[F[_]: Logger: Applicative, T <: GmosController.GmosSite](name: String)
    extends GmosController[F, T] {
  override def applyConfig(config: GmosController.GmosConfig[T]): F[Unit] =
    overrideLogMessage(name, "applyConfig")

  override def observe(fileId: ImageFileId, expTime: FiniteDuration): F[ObserveCommandResult] =
    overrideLogMessage(name, s"observe $fileId").as(ObserveCommandResult.Success)

  override def endObserve: F[Unit] = overrideLogMessage(name, "endObserve")

  override def stopObserve: F[Unit] = overrideLogMessage(name, "stopObserve")

  override def abortObserve: F[Unit] = overrideLogMessage(name, "abortObserve")

  override def pauseObserve: F[Unit] = overrideLogMessage(name, "pauseObserve")

  override def resumePaused(expTime: FiniteDuration): F[ObserveCommandResult] =
    overrideLogMessage(name, "resumePaused").as(ObserveCommandResult.Success)

  override def stopPaused: F[ObserveCommandResult] =
    overrideLogMessage(name, "stopPaused").as(ObserveCommandResult.Stopped)

  override def abortPaused: F[ObserveCommandResult] =
    overrideLogMessage(name, "abortPaused").as(ObserveCommandResult.Aborted)

  override def observeProgress(
    total:   FiniteDuration,
    elapsed: InstrumentSystem.ElapsedTime
  ): Stream[F, Progress] = Stream.empty

  override def nsCount: F[Int] = 0.pure[F]
}
