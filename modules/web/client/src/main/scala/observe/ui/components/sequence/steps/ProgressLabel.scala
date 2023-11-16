// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import observe.model.ObserveStage
import observe.model.dhs.ImageFileId

trait ProgressLabel:
  protected def renderLabel(
    fileId:          ImageFileId,
    remainingMillis: Option[Int],
    stopping:        Boolean,
    paused:          Boolean,
    stage:           ObserveStage
  ): String = {
    val durationStr = remainingMillis.foldMap { millis =>
      // s"mm:ss (s s) Remaining"

      val remainingSecs = millis / 1000
      val remainingMins = remainingSecs / 60
      val secsRemainder = remainingSecs % 60
      f"$remainingMins:$secsRemainder%02d ($remainingSecs s) Remaining"
    }
    val stageStr    =
      stage match {
        case ObserveStage.Preparing  => "Preparing".some
        case ObserveStage.ReadingOut => "Reading out...".some
        case _                       => None
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
