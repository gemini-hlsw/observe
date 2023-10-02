// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import lucuma.core.util.TimeSpan
import observe.engine.Result.PartialVal
import observe.model.NsSubexposure
import observe.model.ObserveStage
import observe.model.dhs.ImageFileId

// Marker trait for partials that won't result on a client message
trait InternalPartialVal extends PartialVal

final case class FileIdAllocated(fileId: ImageFileId) extends PartialVal
final case class RemainingTime(self: TimeSpan)        extends AnyVal

sealed trait Progress extends PartialVal with Product with Serializable {
  val total: TimeSpan
  val remaining: RemainingTime
  def progress: TimeSpan
  val stage: ObserveStage
}

object Progress {
  extension (a: Progress) {
    def toNSProgress(sub: NsSubexposure): NsProgress =
      NsProgress.fromObsProgress(a, sub)
  }
}

final case class ObsProgress(total: TimeSpan, remaining: RemainingTime, stage: ObserveStage)
    extends Progress {
  val progress: TimeSpan = total -| remaining.self
}

final case class NsProgress(
  total:     TimeSpan,
  remaining: RemainingTime,
  stage:     ObserveStage,
  sub:       NsSubexposure
) extends Progress {
  val progress: TimeSpan = total -| remaining.self
}

object NsProgress {
  def fromObsProgress(progress: Progress, sub: NsSubexposure): NsProgress =
    NsProgress(progress.total, progress.remaining, progress.stage, sub)
}
