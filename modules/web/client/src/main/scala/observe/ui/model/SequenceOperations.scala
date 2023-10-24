// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.Order.*
import cats.derived.*
import cats.syntax.all.*
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.Step
import monocle.Focus
import monocle.Lens
import monocle.function.At.at
import monocle.function.At.atSortedMap
import observe.model.enums.ActionStatus
import observe.model.enums.Resource
import observe.model.given

import scala.collection.immutable.SortedMap

enum RunOperation derives Eq:
  case RunIdle, RunInFlight

enum StopOperation derives Eq:
  case StopIdle, StopInFlight

enum AbortOperation derives Eq:
  case AbortIdle, AbortInFlight

enum PauseOperation derives Eq:
  case PauseIdle, PauseInFlight

enum CancelPauseOperation derives Eq:
  case CancelPauseIdle, CancelPauseInFlight

enum ResumeOperation derives Eq:
  case ResumeIdle, ResumeInFlight

enum SyncOperation derives Eq:
  case SyncIdle, SyncInFlight

enum StartFromOperation derives Eq:
  case StartFromInFlight, StartFromIdle

sealed trait ResourceRunOperation derives Eq
sealed trait ResourceRunRequested extends ResourceRunOperation:
  val stepId: Step.Id

object ResourceRunOperation:
  case object ResourceRunIdle                      extends ResourceRunOperation
  case class ResourceRunInFlight(stepId: Step.Id)  extends ResourceRunRequested
  case class ResourceRunCompleted(stepId: Step.Id) extends ResourceRunRequested
  case class ResourceRunFailed(stepId: Step.Id)    extends ResourceRunRequested

  def fromActionStatus(stepId: Step.Id): ActionStatus => Option[ResourceRunOperation] =
    case ActionStatus.Running   => ResourceRunOperation.ResourceRunInFlight(stepId).some
    case ActionStatus.Paused    => ResourceRunOperation.ResourceRunInFlight(stepId).some
    case ActionStatus.Completed => ResourceRunOperation.ResourceRunCompleted(stepId).some
    case ActionStatus.Failed    => ResourceRunOperation.ResourceRunFailed(stepId).some
    case _                      => none

/**
 * Hold transient states while excuting an operation
 */
case class SequenceOperations(
  runRequested:         RunOperation,
  syncRequested:        SyncOperation,
  pauseRequested:       PauseOperation,
  cancelPauseRequested: CancelPauseOperation,
  resumeRequested:      ResumeOperation,
  stopRequested:        StopOperation,
  abortRequested:       AbortOperation,
  startFromRequested:   StartFromOperation,
  resourceRunRequested: SortedMap[Resource | Instrument, ResourceRunOperation]
) derives Eq:
  // Indicate if any resource is being executed
  def resourceInFlight(id: Step.Id): Boolean =
    resourceRunRequested.exists(_._2 match
      case ResourceRunOperation.ResourceRunInFlight(sid) if sid === id => true
      case _                                                           => false
    )

  // Indicate if any resource is in error
  def resourceInError(id: Step.Id): Boolean =
    resourceRunRequested.exists(_._2 match
      case ResourceRunOperation.ResourceRunFailed(sid) if sid === id => true
      case _                                                         => false
    )

  // Indicate if any resource has had a run requested (which may be complete or not)
  def resourceRunNotIdle(id: Step.Id): Boolean =
    resourceRunRequested.exists(_._2 match
      case r: ResourceRunRequested if r.stepId === id => true
      case _                                          => false
    )

  def anyResourceInFlight: Boolean =
    resourceRunRequested.exists(_._2 match
      case ResourceRunOperation.ResourceRunInFlight(_) => true
      case _                                           => false
    )

  val stepRequestInFlight: Boolean =
    pauseRequested === PauseOperation.PauseInFlight ||
      cancelPauseRequested === CancelPauseOperation.CancelPauseInFlight ||
      resumeRequested === ResumeOperation.ResumeInFlight ||
      stopRequested === StopOperation.StopInFlight ||
      abortRequested === AbortOperation.AbortInFlight ||
      startFromRequested === StartFromOperation.StartFromInFlight

object SequenceOperations:
  val runRequested: Lens[SequenceOperations, RunOperation]                 =
    Focus[SequenceOperations](_.runRequested)
  val syncRequested: Lens[SequenceOperations, SyncOperation]               =
    Focus[SequenceOperations](_.syncRequested)
  val pauseRequested: Lens[SequenceOperations, PauseOperation]             =
    Focus[SequenceOperations](_.pauseRequested)
  val cancelPauseRequested: Lens[SequenceOperations, CancelPauseOperation] =
    Focus[SequenceOperations](_.cancelPauseRequested)
  val resumeRequested: Lens[SequenceOperations, ResumeOperation]           =
    Focus[SequenceOperations](_.resumeRequested)
  val stopRequested: Lens[SequenceOperations, StopOperation]               =
    Focus[SequenceOperations](_.stopRequested)
  val abortRequested: Lens[SequenceOperations, AbortOperation]             =
    Focus[SequenceOperations](_.abortRequested)
  val startFromRequested: Lens[SequenceOperations, StartFromOperation]     =
    Focus[SequenceOperations](_.startFromRequested)
  val resourceRunRequested
    : Lens[SequenceOperations, SortedMap[Resource | Instrument, ResourceRunOperation]] =
    Focus[SequenceOperations](_.resourceRunRequested)

  def resourceRun(r: Resource): Lens[SequenceOperations, Option[ResourceRunOperation]] =
    SequenceOperations.resourceRunRequested.andThen(at(r))

  // Set the resource operations in the map to idle.
  def clearAllResourceOperations: SequenceOperations => SequenceOperations =
    SequenceOperations.resourceRunRequested.modify(_.map { case (r, _) =>
      r -> ResourceRunOperation.ResourceRunIdle
    })

  // Set the resource operations in the map to idle.
  def clearResourceOperations(re: Resource | Instrument): SequenceOperations => SequenceOperations =
    SequenceOperations.resourceRunRequested.modify(_.map {
      case (r, _) if re === r => r -> ResourceRunOperation.ResourceRunIdle
      case r                  => r
    })

  // Set the resource operations in the map to idle.
  def clearCommonResourceCompleted(
    re: Resource | Instrument
  ): SequenceOperations => SequenceOperations =
    SequenceOperations.resourceRunRequested.modify(_.map {
      case (r, ResourceRunOperation.ResourceRunCompleted(_)) if re === r =>
        r -> ResourceRunOperation.ResourceRunIdle
      case r                                                             => r
    })

  val Default: SequenceOperations =
    SequenceOperations(
      RunOperation.RunIdle,
      SyncOperation.SyncIdle,
      PauseOperation.PauseIdle,
      CancelPauseOperation.CancelPauseIdle,
      ResumeOperation.ResumeIdle,
      StopOperation.StopIdle,
      AbortOperation.AbortIdle,
      StartFromOperation.StartFromIdle,
      SortedMap.empty
    )
