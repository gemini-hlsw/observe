// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.data.NonEmptyList
import cats.syntax.all.*

sealed trait UserPrompt extends Product with Serializable

object UserPrompt {
  sealed trait PromptButtonColor

  object PromptButtonColor {
    case object DefaultOk     extends PromptButtonColor
    case object DefaultCancel extends PromptButtonColor
    case object WarningOk     extends PromptButtonColor
    case object WarningCancel extends PromptButtonColor
  }

  sealed trait SeqCheck extends Product with Serializable;

  object SeqCheck {
    given Eq[SeqCheck] = Eq.instance {
      case (a: TargetCheckOverride, b: TargetCheckOverride)               => a === b
      case (a: ObsConditionsCheckOverride, b: ObsConditionsCheckOverride) => a === b
      case _                                                              => false
    }
  }

  final case class Discrepancy[A](actual: A, required: A)

  object Discrepancy {
    given [A: Eq]: Eq[Discrepancy[A]] = Eq.by(x => (x.actual, x.required))
  }

  final case class TargetCheckOverride(self: Discrepancy[String]) extends SeqCheck

  object TargetCheckOverride {
    given Eq[TargetCheckOverride] =
      Eq.by(_.self)
  }

  // UserPrompt whether to override the observing conditions
  final case class ObsConditionsCheckOverride(
    cc: Option[Discrepancy[String]],
    iq: Option[Discrepancy[String]],
    sb: Option[Discrepancy[String]],
    wv: Option[Discrepancy[String]]
  ) extends SeqCheck

  object ObsConditionsCheckOverride {
    given Eq[ObsConditionsCheckOverride] = Eq.by(x => (x.cc, x.iq, x.sb, x.wv))
  }

  given Eq[UserPrompt] =
    Eq.instance { case (a: ChecksOverride, b: ChecksOverride) =>
      a === b
    }

  // UserPrompt whether to override start checks
  final case class ChecksOverride(
    obsId:   Observation.Id,
    stepId:  StepId,
    stepIdx: Int,
    checks:  NonEmptyList[SeqCheck]
  ) extends UserPrompt

  object ChecksOverride {
    given Eq[ChecksOverride] =
      Eq.by(x => (x.obsId, x.stepId, x.stepIdx, x.checks))
  }

}
