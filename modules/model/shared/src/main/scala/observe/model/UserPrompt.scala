// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.data.NonEmptyList
import cats.derived.*
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.Encoder
import io.circe.syntax.*
import lucuma.core.model.sequence.Step

sealed trait UserPrompt extends Product with Serializable

object UserPrompt {

  sealed trait SeqCheck extends Product with Serializable;

  object SeqCheck:
    given Eq[SeqCheck] = Eq.instance:
      case (a: TargetCheckOverride, b: TargetCheckOverride)               => a === b
      case (a: ObsConditionsCheckOverride, b: ObsConditionsCheckOverride) => a === b
      case _                                                              => false

    given Encoder[SeqCheck] = Encoder.instance:
      case e @ TargetCheckOverride(_)                 => e.asJson
      case e @ ObsConditionsCheckOverride(_, _, _, _) => e.asJson

    given Decoder[SeqCheck] =
      List[Decoder[SeqCheck]](
        Decoder[TargetCheckOverride].widen,
        Decoder[ObsConditionsCheckOverride].widen
      ).reduceLeft(_ or _)

  case class Discrepancy[A](actual: A, required: A)

  object Discrepancy {
    given [A: Eq]: Eq[Discrepancy[A]] = Eq.by(x => (x.actual, x.required))
  }

  case class TargetCheckOverride(self: Discrepancy[String]) extends SeqCheck
      derives Eq,
        Encoder.AsObject,
        Decoder

  // UserPrompt whether to override the observing conditions
  case class ObsConditionsCheckOverride(
    cc: Option[Discrepancy[String]],
    iq: Option[Discrepancy[String]],
    sb: Option[Discrepancy[String]],
    wv: Option[Discrepancy[String]]
  ) extends SeqCheck
      derives Eq,
        Encoder.AsObject,
        Decoder

  given Eq[UserPrompt] =
    Eq.instance { case (a: ChecksOverride, b: ChecksOverride) =>
      a === b
    }

  // UserPrompt whether to override start checks
  case class ChecksOverride(
    obsId:  Observation.Id,
    stepId: Step.Id,
    checks: NonEmptyList[SeqCheck]
  ) extends UserPrompt
      derives Eq

}
