// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import observe.engine.Result.PartialVal
import observe.model.dhs.ImageFileId
import observe.model.{NSSubexposure, ObserveStage}

import scala.concurrent.duration.FiniteDuration

// Marker trait for partials that won't result on a client message
trait InternalPartialVal extends PartialVal

final case class FileIdAllocated(fileId: ImageFileId) extends PartialVal
final case class RemainingTime(self: FiniteDuration)  extends AnyVal

sealed trait Progress extends PartialVal with Product with Serializable {
  val total: FiniteDuration
  val remaining: RemainingTime
  def progress: FiniteDuration
  val stage: ObserveStage
}

object Progress {
  extension (a: Progress) {
    def toNSProgress(sub: NSSubexposure): NSProgress =
      NSProgress.fromObsProgress(a, sub)
  }
}

final case class ObsProgress(total: FiniteDuration, remaining: RemainingTime, stage: ObserveStage)
    extends Progress {
  val progress: FiniteDuration = total - remaining.self
}

final case class NSProgress(
  total:     FiniteDuration,
  remaining: RemainingTime,
  stage:     ObserveStage,
  sub:       NSSubexposure
) extends Progress {
  val progress: FiniteDuration = total - remaining.self
}

object NSProgress {
  def fromObsProgress(progress: Progress, sub: NSSubexposure): NSProgress =
    NSProgress(progress.total, progress.remaining, progress.stage, sub)
}
