// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.syntax.all.*
import lucuma.core.util.Enumerated
import monocle.Iso
import monocle.Prism
import monocle.macros.GenPrism
import scala.concurrent.duration.Duration

sealed trait Progress extends Product with Serializable {
  val obsId: Observation.Id
  val stepId: StepId
  val total: Duration
  val remaining: Duration
  val stage: ObserveStage
}

object Progress {

  given Eq[Progress] =
    Eq.instance {
      case (a: ObservationProgress, b: ObservationProgress)     => a === b
      case (a: NSObservationProgress, b: NSObservationProgress) => a === b
      case _                                                    => false
    }

  given Prism[Progress, ObservationProgress] =
    GenPrism[Progress, ObservationProgress]

  given Prism[Progress, NSObservationProgress] =
    GenPrism[Progress, NSObservationProgress]

  given Prism[Progress, Progress] =
    Iso.id[Progress].asPrism
}

final case class ObservationProgress(
  obsId:     Observation.Id,
  stepId:    StepId,
  total:     Duration,
  remaining: Duration,
  stage:     ObserveStage
) extends Progress

object ObservationProgress {

  given Eq[ObservationProgress] =
    Eq.by(x => (x.obsId, x.stepId, x.total, x.remaining, x.stage))

}

final case class NSObservationProgress(
  obsId:     Observation.Id,
  stepId:    StepId,
  total:     Duration,
  remaining: Duration,
  stage:     ObserveStage,
  sub:       NSSubexposure
) extends Progress

object NSObservationProgress {

  given Eq[NSObservationProgress] =
    Eq.by(x => (x.obsId, x.stepId, x.total, x.remaining, x.stage, x.sub))

}

sealed abstract class ObserveStage(val tag: String)

object ObserveStage {

  case object Idle       extends ObserveStage("Idle")
  case object Preparing  extends ObserveStage("Preparing")
  case object Acquiring  extends ObserveStage("Acquiring")
  case object ReadingOut extends ObserveStage("ReadingOut")

  given Enumerated[ObserveStage] =
    Enumerated.from(Idle, Preparing, Acquiring, ReadingOut).withTag(_.tag)

  def fromBooleans(prep: Boolean, acq: Boolean, rdout: Boolean): ObserveStage =
    if (prep) Preparing
    else if (acq) Acquiring
    else if (rdout) ReadingOut
    else Idle

}
