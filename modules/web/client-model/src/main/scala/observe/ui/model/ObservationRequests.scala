// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
// import cats.Order.*
import cats.derived.*
import cats.syntax.all.*
import lucuma.core.enums.Instrument
// import lucuma.core.model.sequence.Step
// import monocle.Focus
// import monocle.Lens
// import monocle.function.At.at
// import monocle.function.At.atSortedMap
// import observe.model.enums.ActionStatus
import observe.model.enums.Resource
// import observe.model.given

// import scala.collection.immutable.SortedMap
import observe.ui.model.enums.OperationRequest
// import lucuma.core.util.Enumerated
import lucuma.core.model.sequence.Step
import monocle.Lens
import monocle.Focus

case class ObservationRequests(
  run:          OperationRequest,
  stop:         OperationRequest,
  abort:        OperationRequest,
  pause:        OperationRequest,
  cancelPause:  OperationRequest,
  resume:       OperationRequest,
  // sync: OperationRequest,
  startFrom:    OperationRequest,
  subsystemRun: Map[Step.Id, Map[Resource | Instrument, OperationRequest]]
) derives Eq:
  val stepRequestInFlight: Boolean =
    pause === OperationRequest.InFlight ||
      cancelPause === OperationRequest.InFlight ||
      resume === OperationRequest.InFlight ||
      stop === OperationRequest.InFlight ||
      abort === OperationRequest.InFlight ||
      startFrom === OperationRequest.InFlight

    // Indicate if any resource is being executed
  def subsystemInFlight(stepId: Step.Id): Boolean =
    subsystemRun.get(stepId).exists(_.exists(_._2 === OperationRequest.InFlight))

  // Indicate if any resource is in error
  // def resourceInError(stepId: Step.Id): Boolean =
  //   resourceRunRequested.exists(_._2 match
  //     case SubsystemRunOperation.SubsystemRunFailed(sid) if sid === id => true
  //     case _                                                           => false
  //   )

object ObservationRequests:
  val Idle: ObservationRequests = ObservationRequests(
    run = OperationRequest.Idle,
    stop = OperationRequest.Idle,
    abort = OperationRequest.Idle,
    pause = OperationRequest.Idle,
    cancelPause = OperationRequest.Idle,
    resume = OperationRequest.Idle,
    startFrom = OperationRequest.Idle,
    subsystemRun = Map.empty
  )

  val run: Lens[ObservationRequests, OperationRequest]         =
    Focus[ObservationRequests](_.run)
  val stop: Lens[ObservationRequests, OperationRequest]        =
    Focus[ObservationRequests](_.stop)
  val abort: Lens[ObservationRequests, OperationRequest]       =
    Focus[ObservationRequests](_.abort)
  val pause: Lens[ObservationRequests, OperationRequest]       =
    Focus[ObservationRequests](_.pause)
  val cancelPause: Lens[ObservationRequests, OperationRequest] =
    Focus[ObservationRequests](_.cancelPause)
  val resume: Lens[ObservationRequests, OperationRequest]      =
    Focus[ObservationRequests](_.resume)
  val startFrom: Lens[ObservationRequests, OperationRequest]   =
    Focus[ObservationRequests](_.startFrom)
  val subsystemRun
    : Lens[ObservationRequests, Map[Step.Id, Map[Resource | Instrument, OperationRequest]]] =
    Focus[ObservationRequests](_.subsystemRun)

// enum RunOperation derives Eq:
//   case RunIdle, RunInFlight

// enum StopOperation derives Eq:
//   case StopIdle, StopInFlight

// enum AbortOperation derives Eq:
//   case AbortIdle, AbortInFlight

// enum PauseOperation derives Eq:
//   case PauseIdle, PauseInFlight

// enum CancelPauseOperation derives Eq:
//   case CancelPauseIdle, CancelPauseInFlight

// enum ResumeOperation derives Eq:
//   case ResumeIdle, ResumeInFlight

// enum SyncOperation derives Eq:
//   case SyncIdle, SyncInFlight

// /**
//  * Hold transient states while excuting an operation
//  */
// case class SequenceOperations(
//   runRequested:         RunOperation,
//   syncRequested:        SyncOperation,
//   pauseRequested:       PauseOperation,
//   cancelPauseRequested: CancelPauseOperation,
//   resumeRequested:      ResumeOperation,
//   stopRequested:        StopOperation,
//   abortRequested:       AbortOperation,
//   startFromRequested:   StartFromOperation,
//   resourceRunRequested: SortedMap[Resource | Instrument, SubsystemRunOperation]
// ) derives Eq:
//   // Indicate if any resource is being executed
//   def resourceInFlight(id: Step.Id): Boolean =
//     resourceRunRequested.exists(_._2 match
//       case SubsystemRunOperation.SubsystemRunInFlight(sid) if sid === id => true
//       case _                                                             => false
//     )

//   // Indicate if any resource is in error
//   def resourceInError(id: Step.Id): Boolean =
//     resourceRunRequested.exists(_._2 match
//       case SubsystemRunOperation.SubsystemRunFailed(sid) if sid === id => true
//       case _                                                           => false
//     )

//   // Indicate if any resource has had a run requested (which may be complete or not)
//   def resourceRunNotIdle(id: Step.Id): Boolean =
//     resourceRunRequested.exists(_._2 match
//       case r: SubsystemRunRequested if r.stepId === id => true
//       case _                                           => false
//     )

//   def anyResourceInFlight: Boolean =
//     resourceRunRequested.exists(_._2 match
//       case SubsystemRunOperation.SubsystemRunInFlight(_) => true
//       case _                                             => false
//     )

//   val stepRequestInFlight: Boolean =
//     pauseRequested === PauseOperation.PauseInFlight ||
//       cancelPauseRequested === CancelPauseOperation.CancelPauseInFlight ||
//       resumeRequested === ResumeOperation.ResumeInFlight ||
//       stopRequested === StopOperation.StopInFlight ||
//       abortRequested === AbortOperation.AbortInFlight ||
//       startFromRequested === StartFromOperation.StartFromInFlight

// object SequenceOperations:
//   val runRequested: Lens[SequenceOperations, RunOperation]                 =
//     Focus[SequenceOperations](_.runRequested)
//   val syncRequested: Lens[SequenceOperations, SyncOperation]               =
//     Focus[SequenceOperations](_.syncRequested)
//   val pauseRequested: Lens[SequenceOperations, PauseOperation]             =
//     Focus[SequenceOperations](_.pauseRequested)
//   val cancelPauseRequested: Lens[SequenceOperations, CancelPauseOperation] =
//     Focus[SequenceOperations](_.cancelPauseRequested)
//   val resumeRequested: Lens[SequenceOperations, ResumeOperation]           =
//     Focus[SequenceOperations](_.resumeRequested)
//   val stopRequested: Lens[SequenceOperations, StopOperation]               =
//     Focus[SequenceOperations](_.stopRequested)
//   val abortRequested: Lens[SequenceOperations, AbortOperation]             =
//     Focus[SequenceOperations](_.abortRequested)
//   val startFromRequested: Lens[SequenceOperations, StartFromOperation]     =
//     Focus[SequenceOperations](_.startFromRequested)
//   val resourceRunRequested
//     : Lens[SequenceOperations, SortedMap[Resource | Instrument, SubsystemRunOperation]] =
//     Focus[SequenceOperations](_.resourceRunRequested)

//   def resourceRun(r: Resource): Lens[SequenceOperations, Option[SubsystemRunOperation]] =
//     SequenceOperations.resourceRunRequested.andThen(at(r))

//   // Set the resource operations in the map to idle.
//   def clearAllResourceOperations: SequenceOperations => SequenceOperations =
//     SequenceOperations.resourceRunRequested.modify(_.map { case (r, _) =>
//       r -> SubsystemRunOperation.SubsystemRunIdle
//     })

//   // Set the resource operations in the map to idle.
//   def clearResourceOperations(re: Resource | Instrument): SequenceOperations => SequenceOperations =
//     SequenceOperations.resourceRunRequested.modify(_.map {
//       case (r, _) if re === r => r -> SubsystemRunOperation.SubsystemRunIdle
//       case r                  => r
//     })

//   // Set the resource operations in the map to idle.
//   def clearCommonResourceCompleted(
//     re: Resource | Instrument
//   ): SequenceOperations => SequenceOperations =
//     SequenceOperations.resourceRunRequested.modify(_.map {
//       case (r, SubsystemRunOperation.SubsystemRunCompleted(_)) if re === r =>
//         r -> SubsystemRunOperation.SubsystemRunIdle
//       case r                                                               => r
//     })

//   val Default: SequenceOperations =
//     SequenceOperations(
//       RunOperation.RunIdle,
//       SyncOperation.SyncIdle,
//       PauseOperation.PauseIdle,
//       CancelPauseOperation.CancelPauseIdle,
//       ResumeOperation.ResumeIdle,
//       StopOperation.StopIdle,
//       AbortOperation.AbortIdle,
//       StartFromOperation.StartFromIdle,
//       SortedMap.empty
//     )
