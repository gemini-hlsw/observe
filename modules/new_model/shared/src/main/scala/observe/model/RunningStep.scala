// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.option.*
import lucuma.core.model.sequence.Step
import lucuma.core.util.Display

sealed trait RunningStep derives Eq:
  val id: Option[Step.Id]
  val last: Int
  val total: Int

object RunningStep:
  val Zero: RunningStep = RunningStepImpl(none, 0, 0)

  private case class RunningStepImpl(id: Option[Step.Id], last: Int, total: Int) extends RunningStep

  def fromStepId(id: Option[Step.Id], last: Int, total: Int): Option[RunningStep] =
    if (total >= last) RunningStepImpl(id, last, total).some else none

  def unapply(r: RunningStep): Option[(Option[Step.Id], Int, Int)] =
    Some((r.id, r.last, r.total))

  given Display[RunningStep] = Display.byShortName(u => s"${u.last + 1}/${u.total}")
