// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import scala.collection.immutable.SortedMap

import cats.Eq
import cats.syntax.all.*
import cats.Order.*
import lucuma.core.util.Enumerated
import monocle.Lens
import monocle.function.At.at
import monocle.function.At.atSortedMap
import observe.model.StepId
import observe.model.enums.ActionStatus
import observe.model.enums.Resource

sealed abstract class RunOperation(val tag: String) extends Product with Serializable
object RunOperation {
  case object RunIdle     extends RunOperation("RunIdle")
  case object RunInFlight extends RunOperation("RunInFlight")

  /** @group Typeclass Instances */
  given Enumerated[RunOperation] =
    Enumerated.from(RunIdle, RunInFlight).withTag(_.tag)

}

sealed abstract class StopOperation(val tag: String) extends Product with Serializable
object StopOperation {
  case object StopIdle     extends StopOperation("StopIdle")
  case object StopInFlight extends StopOperation("StopInFlight")

  /** @group Typeclass Instances */
  given Enumerated[StopOperation] =
    Enumerated.from(StopIdle, StopInFlight).withTag(_.tag)

}

sealed abstract class AbortOperation(val tag: String) extends Product with Serializable
object AbortOperation {
  case object AbortIdle     extends AbortOperation("AbortIdle")
  case object AbortInFlight extends AbortOperation("AbortInFlight")

  /** @group Typeclass Instances */
  given Enumerated[AbortOperation] =
    Enumerated.from(AbortIdle, AbortInFlight).withTag(_.tag)

}

sealed abstract class PauseOperation(val tag: String) extends Product with Serializable
object PauseOperation {
  case object PauseIdle     extends PauseOperation("PauseIdle")
  case object PauseInFlight extends PauseOperation("PauseInFlight")

  /** @group Typeclass Instances */
  given Enumerated[PauseOperation] =
    Enumerated.from(PauseIdle, PauseInFlight).withTag(_.tag)

}

sealed abstract class CancelPauseOperation(val tag: String) extends Product with Serializable
object CancelPauseOperation {
  case object CancelPauseIdle     extends CancelPauseOperation("CancelPauseIdle")
  case object CancelPauseInFlight extends CancelPauseOperation("CancelPauseInFlight")

  /** @group Typeclass Instances */
  given Enumerated[CancelPauseOperation] =
    Enumerated.from(CancelPauseIdle, CancelPauseInFlight).withTag(_.tag)

}

sealed abstract class ResumeOperation(val tag: String) extends Product with Serializable
object ResumeOperation {
  case object ResumeIdle     extends ResumeOperation("ResumeIdle")
  case object ResumeInFlight extends ResumeOperation("ResumeInFlight")

  /** @group Typeclass Instances */
  given Enumerated[ResumeOperation] =
    Enumerated.from(ResumeIdle, ResumeInFlight).withTag(_.tag)

}

sealed abstract class SyncOperation(val tag: String) extends Product with Serializable
object SyncOperation {
  case object SyncIdle     extends SyncOperation("SyncIdle")
  case object SyncInFlight extends SyncOperation("SyncInFlight")

  /** @group Typeclass Instances */
  given Enumerated[SyncOperation] =
    Enumerated.from(SyncIdle, SyncInFlight).withTag(_.tag)

}

sealed trait ResourceRunOperation extends Product with Serializable
sealed trait ResourceRunRequested extends ResourceRunOperation {
  val stepId: StepId
}

object ResourceRunOperation {
  case object ResourceRunIdle                           extends ResourceRunOperation
  final case class ResourceRunInFlight(stepId: StepId)  extends ResourceRunRequested
  final case class ResourceRunCompleted(stepId: StepId) extends ResourceRunRequested
  final case class ResourceRunFailed(stepId: StepId)    extends ResourceRunRequested

  def fromActionStatus(stepId: StepId): ActionStatus => Option[ResourceRunOperation] = {
    case ActionStatus.Running   => ResourceRunOperation.ResourceRunInFlight(stepId).some
    case ActionStatus.Paused    => ResourceRunOperation.ResourceRunInFlight(stepId).some
    case ActionStatus.Completed => ResourceRunOperation.ResourceRunCompleted(stepId).some
    case ActionStatus.Failed    => ResourceRunOperation.ResourceRunFailed(stepId).some
    case _                      => none
  }

  given Eq[ResourceRunOperation] = Eq.instance {
    case (ResourceRunIdle, ResourceRunIdle)                 => true
    case (ResourceRunInFlight(a), ResourceRunInFlight(b))   => a === b
    case (ResourceRunCompleted(a), ResourceRunCompleted(b)) => a === b
    case (ResourceRunFailed(a), ResourceRunFailed(b))       => a === b
    case _                                                  => false
  }
}

sealed abstract class StartFromOperation(val tag: String) extends Product with Serializable
object StartFromOperation {
  case object StartFromInFlight extends StartFromOperation("StartFromInFlight")
  case object StartFromIdle     extends StartFromOperation("StartFromIdle")

  /** @group Typeclass Instances */
  given Enumerated[StartFromOperation] =
    Enumerated.from(StartFromIdle, StartFromInFlight).withTag(_.tag)

}

/**
 * Hold transient states while excuting an operation
 */
final case class TabOperations(
  runRequested:         RunOperation,
  syncRequested:        SyncOperation,
  pauseRequested:       PauseOperation,
  cancelPauseRequested: CancelPauseOperation,
  resumeRequested:      ResumeOperation,
  stopRequested:        StopOperation,
  abortRequested:       AbortOperation,
  startFromRequested:   StartFromOperation,
  resourceRunRequested: SortedMap[Resource, ResourceRunOperation]
) {
  // Indicate if any resource is being executed
  def resourceInFlight(id: StepId): Boolean =
    resourceRunRequested.exists(_._2 match {
      case ResourceRunOperation.ResourceRunInFlight(sid) if sid === id =>
        true
      case _                                                           => false
    })

  // Indicate if any resource is in error
  def resourceInError(id: StepId): Boolean =
    resourceRunRequested.exists(_._2 match {
      case ResourceRunOperation.ResourceRunFailed(sid) if sid === id =>
        true
      case _                                                         => false
    })

  // Indicate if any resource has had a run requested (which may be complete or not)
  def resourceRunNotIdle(id: StepId): Boolean =
    resourceRunRequested.exists(_._2 match {
      case r: ResourceRunRequested if r.stepId === id => true
      case _                                          => false
    })

  def anyResourceInFlight: Boolean =
    resourceRunRequested.exists(_._2 match {
      case ResourceRunOperation.ResourceRunInFlight(_) =>
        true
      case _                                           => false
    })

  val stepRequestInFlight: Boolean =
    pauseRequested === PauseOperation.PauseInFlight ||
      cancelPauseRequested === CancelPauseOperation.CancelPauseInFlight ||
      resumeRequested === ResumeOperation.ResumeInFlight ||
      stopRequested === StopOperation.StopInFlight ||
      abortRequested === AbortOperation.AbortInFlight ||
      startFromRequested === StartFromOperation.StartFromInFlight
}

object TabOperations {
  given Eq[TabOperations] =
    Eq.by(x =>
      (x.runRequested,
       x.syncRequested,
       x.pauseRequested,
       x.cancelPauseRequested,
       x.resumeRequested,
       x.stopRequested,
       x.abortRequested,
       x.startFromRequested,
       x.resourceRunRequested
      )
    )

  def resourceRun(
    r: Resource
  ): Lens[TabOperations, Option[ResourceRunOperation]] =
    Focus[TabOperations](_.resourceRunRequested).andThen(at(r))

  // Set the resource operations in the map to idle.
  def clearAllResourceOperations: TabOperations => TabOperations =
    TabOperations.resourceRunRequested.modify(_.map { case (r, _) =>
      r -> ResourceRunOperation.ResourceRunIdle
    })

  // Set the resource operations in the map to idle.
  def clearResourceOperations(re: Resource): TabOperations => TabOperations =
    TabOperations.resourceRunRequested.modify(_.map {
      case (r, _) if re === r => r -> ResourceRunOperation.ResourceRunIdle
      case r                  => r
    })

  // Set the resource operations in the map to idle.
  def clearCommonResourceCompleted(
    re: Resource
  ): TabOperations => TabOperations =
    TabOperations.resourceRunRequested.modify(_.map {
      case (r, ResourceRunOperation.ResourceRunCompleted(_)) if re === r =>
        r -> ResourceRunOperation.ResourceRunIdle
      case r                                                             => r
    })

  val Default: TabOperations =
    TabOperations(
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
}
