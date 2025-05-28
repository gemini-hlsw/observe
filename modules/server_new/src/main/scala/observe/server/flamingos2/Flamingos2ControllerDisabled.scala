// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.flamingos2

import cats.Functor
import cats.syntax.all.*
import fs2.Stream
import lucuma.core.util.TimeSpan
import observe.model.dhs.ImageFileId
import observe.model.enums.ObserveCommandResult
import observe.server.Progress
import observe.server.overrideLogMessage
import org.typelevel.log4cats.Logger

class Flamingos2ControllerDisabled[F[_]: Logger: Functor] extends Flamingos2Controller[F] {
  override def applyConfig(config: Flamingos2Controller.Flamingos2Config): F[Unit] =
    overrideLogMessage("Flamingos-2", "applyConfig")

  override def observe(fileId: ImageFileId, expTime: TimeSpan): F[ObserveCommandResult] =
    overrideLogMessage("Flamingos-2", s"observe $fileId").as(ObserveCommandResult.Success)

  override def endObserve: F[Unit] = overrideLogMessage("FLAMINGOS-2", "endObserve")

  override def observeProgress(total: TimeSpan): Stream[F, Progress] = Stream.empty
}
