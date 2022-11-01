// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.util.Enumerated
import monocle.Iso
import monocle.Prism
import monocle.macros.GenPrism
import observe.model.enums.ObservationStage
import org.typelevel.cats.time.given

import java.time.Duration

enum ObservationProgress(
  val obsId:     Observation.Id,
  val obsName:   String,
  val stepId:    Step.Id,
  val total:     Duration,
  val remaining: Duration,
  val stage:     ObservationStage
) derives Eq:
  case Regular(
    override val obsId:     Observation.Id,
    override val obsName:   String,
    override val stepId:    Step.Id,
    override val total:     Duration,
    override val remaining: Duration,
    override val stage:     ObservationStage
  ) extends ObservationProgress(obsId, obsName, stepId, total, remaining, stage)

  case NodAndShuffle(
    override val obsId:     Observation.Id,
    override val obsName:   String,
    override val stepId:    Step.Id,
    override val total:     Duration,
    override val remaining: Duration,
    override val stage:     ObservationStage,
    sub:                    NsSubexposure
  ) extends ObservationProgress(obsId, obsName, stepId, total, remaining, stage)

object ObservationProgress:
  implicit val regular: Prism[ObservationProgress, ObservationProgress.Regular] =
    GenPrism[ObservationProgress, ObservationProgress.Regular]

  implicit val nodAndShuffle: Prism[ObservationProgress, ObservationProgress.NodAndShuffle] =
    GenPrism[ObservationProgress, ObservationProgress.NodAndShuffle]
