// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.events.client

import cats.*
import cats.data.NonEmptyList
import cats.derived.*
import cats.syntax.all.*
import eu.timepit.refined.cats.*
import io.circe.Decoder
import io.circe.Encoder
import io.circe.KeyDecoder
import io.circe.KeyEncoder
import io.circe.refined.*
import io.circe.syntax.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.util.Enumerated
import observe.model.Conditions
import observe.model.Environment
import observe.model.ExecutionState
import observe.model.ObservationProgress
import observe.model.Operator
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.model.UserPrompt.ChecksOverride
import observe.model.UserPrompt.SeqCheck
import observe.model.enums.Resource
import observe.model.given

sealed trait ClientEvent derives Eq

private given KeyEncoder[Observation.Id] = _.toString
private given KeyDecoder[Observation.Id] = Observation.Id.parse(_)

extension (v: SequencesQueue[SequenceView])
  def sequencesState: Map[Observation.Id, ExecutionState] =
    v.sessionQueue.map(o => (o.obsId, o.executionState)).toMap

extension (q: SequenceView)
  def executionState: ExecutionState =
    ExecutionState(
      q.status,
      q.metadata.observer,
      q.runningStep.flatMap(_.id),
      None,
      q.stepResources,
      q.systemOverrides,
      q.steps.mapFilter(s => if (s.breakpoint === Breakpoint.Enabled) s.id.some else none).toSet,
      q.pausedStep
    )

object ClientEvent:
  enum SingleActionState(val tag: String) derives Enumerated:
    case Started   extends SingleActionState("started")
    case Completed extends SingleActionState("completed")
    case Failed    extends SingleActionState("failed")

  case class InitialEvent(environment: Environment) extends ClientEvent
      derives Eq,
        Encoder.AsObject,
        Decoder

  case class ObserveState(
    sequenceExecution: Map[Observation.Id, ExecutionState],
    conditions:        Conditions,
    operator:          Option[Operator]
  ) extends ClientEvent
      derives Eq,
        Encoder.AsObject,
        Decoder

  case class SingleActionEvent(
    obsId:     Observation.Id,
    stepId:    Step.Id,
    subsystem: Resource | Instrument,
    event:     SingleActionState,
    error:     Option[String]
  ) extends ClientEvent
      derives Eq,
        Encoder.AsObject,
        Decoder

  case class ChecksOverrideEvent(prompt: ChecksOverride) extends ClientEvent
      derives Eq,
        Encoder.AsObject,
        Decoder

  case class ProgressEvent(progress: ObservationProgress) extends ClientEvent
      derives Eq,
        Encoder.AsObject,
        Decoder

  given Encoder[ClientEvent] = Encoder.instance:
    case e @ InitialEvent(_)                  => e.asJson
    case e @ ObserveState(_, _, _)            => e.asJson
    case e @ SingleActionEvent(_, _, _, _, _) => e.asJson
    case e @ ChecksOverrideEvent(_)           => e.asJson
    case e @ ProgressEvent(_)                 => e.asJson

  given Decoder[ClientEvent] =
    List[Decoder[ClientEvent]](
      Decoder[InitialEvent].widen,
      Decoder[ObserveState].widen,
      Decoder[SingleActionEvent].widen,
      Decoder[ChecksOverrideEvent].widen,
      Decoder[ProgressEvent].widen
    ).reduceLeft(_ or _)
