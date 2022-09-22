// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import observe.model.ImageFileId
import observe.model.enums.ObservationStage

trait ProgressLabel:
  protected def renderLabel(
    fileId:          ImageFileId,
    remainingMillis: Option[Int],
    stopping:        Boolean,
    paused:          Boolean,
    stage:           ObservationStage
  ): String = {
    val durationStr = remainingMillis.foldMap { millis =>
      val remainingSecs = millis / 1000
      val remainingStr  = if (remainingSecs > 1) s"$remainingSecs seconds" else "1 second"
      s" - $remainingStr left"
    }
    val stageStr    =
      stage match {
        case ObservationStage.Preparing  => "Preparing".some
        case ObservationStage.ReadingOut => "Reading out...".some
        case _                           => None
      }

    if (paused) s"$fileId - Paused$durationStr"
    else if (stopping) s"$fileId - Stopping - Reading out..."
    else
      stageStr match {
        case Some(stage) => s"$fileId - $stage"
        case _           =>
          remainingMillis.fold(fileId.value) { millis =>
            if (millis > 0) s"${fileId.value}$durationStr" else s"${fileId.value} - Reading out..."
          }
      }
  }
