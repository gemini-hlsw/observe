// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Show
import cats.Eq
import cats.syntax.option._

sealed trait RunningStep {
  val id: Option[StepId]
  val last: Int
  val total: Int
}

object RunningStep {
  val Zero: RunningStep = RunningStepImpl(none, 0, 0)

  private final case class RunningStepImpl(id: Option[StepId], last: Int, total: Int)
      extends RunningStep

  def fromInt(id: Option[StepId], last: Int, total: Int): Option[RunningStep] =
    if (total >= last) RunningStepImpl(id, last, total).some else none

  def unapply(r: RunningStep): Option[(Option[StepId], Int, Int)] =
    Some((r.id, r.last, r.total))

  implicit val show: Show[RunningStep] =
    Show.show(u => s"${u.last + 1}/${u.total}")

  implicit val eq: Eq[RunningStep] =
    Eq.by(x => (x.id, x.last, x.total))
}
