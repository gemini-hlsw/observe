// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import cats.derived.*
import cats.syntax.all.*
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.model.sequence.Step
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
  sequenceType:    SequenceType,
  steps:           List[ObserveStep],
  willStopIn:      Option[Int],
  stepResources:   Map[Step.Id, Map[Resource | Instrument, ActionStatus]]
) derives Eq:

  def progress: Option[RunningStep] =
    steps.zipWithIndex
      .find(!_._1.isFinished)
      .flatMap: x =>
        RunningStep.fromInt(x._1.id.some, x._2, steps.length)

  // Returns where on the sequence the execution is at
  def runningStep: Option[RunningStep] = status match
    case SequenceState.Running(_, _, _) => progress
    case SequenceState.Failed(_)        => progress
    case SequenceState.Aborted          => progress
    case _                              => none

  def pausedStep = steps.find(_.isObservePaused).map(_.id).map(PausedStep(_))

object SequenceView:

  val stepT: Traversal[SequenceView, ObserveStep] =
    Focus[SequenceView](_.steps).andThen(each[List[ObserveStep], ObserveStep])
