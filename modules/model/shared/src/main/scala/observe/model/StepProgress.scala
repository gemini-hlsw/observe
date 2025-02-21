// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.syntax.*
import lucuma.core.model.sequence.Step
import lucuma.core.util.Enumerated
import lucuma.core.util.TimeSpan
import lucuma.odb.json.time.transport.given
import monocle.Prism
import monocle.macros.GenPrism

enum StepProgress(val isNs: Boolean) derives Eq:
  def stepId: Step.Id
  def total: TimeSpan
  def remaining: TimeSpan
  def stage: ObserveStage

  def isExposure: Boolean = stage === ObserveStage.Exposure

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

  given Encoder[StepProgress] = Encoder.instance:
    case r @ Regular(_, _, _, _)           => r.asJson
    case ns @ NodAndShuffle(_, _, _, _, _) => ns.asJson

  given Decoder[StepProgress] =
    List[Decoder[StepProgress]](
      Decoder[NodAndShuffle].widen, // Must come first, since it has extra field.
      Decoder[Regular].widen
    ).reduceLeft(_ or _)

  object Regular:
    given Encoder.AsObject[Regular] = Encoder.AsObject.instance: op =>
      JsonObject(
        "stepId"    -> op.stepId.asJson,
        "total"     -> op.total.asJson,
        "remaining" -> op.remaining.asJson,
        "stage"     -> op.stage.asJson
      )

    given Decoder[Regular] = Decoder.instance: c =>
      for
        stepId    <- c.downField("stepId").as[Step.Id]
        total     <- c.downField("total").as[TimeSpan]
        remaining <- c.downField("remaining").as[TimeSpan]
        stage     <- c.downField("stage").as[ObserveStage]
      yield Regular(stepId, total, remaining, stage)

  object NodAndShuffle:
    given Encoder.AsObject[NodAndShuffle] = Encoder.AsObject.instance: op =>
      JsonObject(
        "stepId"    -> op.stepId.asJson,
        "total"     -> op.total.asJson,
        "remaining" -> op.remaining.asJson,
        "stage"     -> op.stage.asJson,
        "sub"       -> op.sub.asJson
      )

    given Decoder[NodAndShuffle] = Decoder.instance: c =>
      for // Check this differentiating value "sub" first, fail fast.
        sub       <- c.downField("sub").as[NsSubexposure]
        stepId    <- c.downField("stepId").as[Step.Id]
        total     <- c.downField("total").as[TimeSpan]
        remaining <- c.downField("remaining").as[TimeSpan]
        stage     <- c.downField("stage").as[ObserveStage]
      yield NodAndShuffle(stepId, total, remaining, stage, sub)
