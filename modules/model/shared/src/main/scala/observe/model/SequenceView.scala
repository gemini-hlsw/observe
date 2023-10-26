// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import cats.derived.*
import cats.syntax.all.*
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.{Step => CoreStep}
import monocle.Focus
import monocle.Traversal
import monocle.function.Each.*
import observe.model.enums.ActionStatus
import observe.model.enums.Resource

case class SequenceView(
  obsId:           Observation.Id,
  metadata:        SequenceMetadata,
  status:          SequenceState,
  systemOverrides: SystemOverrides,
  steps:           List[Step],
  willStopIn:      Option[Int],
  stepResources:   List[(CoreStep.Id, List[(Resource | Instrument, ActionStatus)])]
) derives Eq:

  def progress: Option[RunningStep] =
    steps.zipWithIndex
      .find(!_._1.isFinished)
      .flatMap: x =>
        RunningStep.fromInt(x._1.id.some, x._2, steps.length)

  // Returns where on the sequence the execution is at
  def runningStep: Option[RunningStep] = status match
    case SequenceState.Running(_, _) => progress
    case SequenceState.Failed(_)     => progress
    case SequenceState.Aborted       => progress
    case _                           => none

object SequenceView:

  val stepT: Traversal[SequenceView, Step] =
    Focus[SequenceView](_.steps).andThen(each[List[Step], Step])
