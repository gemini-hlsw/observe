// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.util.Enumerated
import lucuma.core.util.TimeSpan
import monocle.Prism
import monocle.macros.GenPrism

enum ObservationProgress(val isNs: Boolean) derives Eq:
  def obsId: Observation.Id
  def stepId: Step.Id
  def total: TimeSpan
  def remaining: TimeSpan
  def stage: ObserveStage

  case Regular(
    obsId:     Observation.Id,
    stepId:    Step.Id,
    total:     TimeSpan,
    remaining: TimeSpan,
    stage:     ObserveStage
  ) extends ObservationProgress(false)

  case NodAndShuffle(
    obsId:     Observation.Id,
    stepId:    Step.Id,
    total:     TimeSpan,
    remaining: TimeSpan,
    stage:     ObserveStage,
    sub:       NsSubexposure
  ) extends ObservationProgress(true)

object ObservationProgress:
  implicit val regular: Prism[ObservationProgress, ObservationProgress.Regular] =
    GenPrism[ObservationProgress, ObservationProgress.Regular]

  implicit val nodAndShuffle: Prism[ObservationProgress, ObservationProgress.NodAndShuffle] =
    GenPrism[ObservationProgress, ObservationProgress.NodAndShuffle]
