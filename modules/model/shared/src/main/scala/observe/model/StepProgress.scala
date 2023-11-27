// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.Encoder
import lucuma.core.model.sequence.Step
import lucuma.core.util.Enumerated
import lucuma.core.util.TimeSpan
import monocle.Prism
import monocle.macros.GenPrism

enum StepProgress(val isNs: Boolean) derives Eq, Encoder.AsObject, Decoder:
  def stepId: Step.Id
  def total: TimeSpan
  def remaining: TimeSpan
  def stage: ObserveStage

  case Regular(
    stepId:    Step.Id,
    total:     TimeSpan,
    remaining: TimeSpan,
    stage:     ObserveStage
  ) extends StepProgress(false)

  case NodAndShuffle(
    stepId:    Step.Id,
    total:     TimeSpan,
    remaining: TimeSpan,
    stage:     ObserveStage,
    sub:       NsSubexposure
  ) extends StepProgress(true)

object StepProgress:
  val regular: Prism[StepProgress, StepProgress.Regular] =
    GenPrism[StepProgress, StepProgress.Regular]

  val nodAndShuffle: Prism[StepProgress, StepProgress.NodAndShuffle] =
    GenPrism[StepProgress, StepProgress.NodAndShuffle]
