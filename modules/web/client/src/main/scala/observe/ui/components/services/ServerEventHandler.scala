// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.services

import cats.Eq
import cats.effect.IO
import cats.syntax.all.*
import crystal.*
import crystal.react.*
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import observe.model.ClientConfig
import observe.model.ExecutionState
import observe.model.ObservationProgress
import observe.model.enums.ActionStatus
import observe.model.events.client.ClientEvent
import observe.model.events.client.ClientEvent.SingleActionState
import observe.ui.model.LoadedObservation
import observe.ui.model.ObservationRequests
import observe.ui.model.RootModelData
import observe.ui.model.enums.ApiStatus
import observe.ui.model.enums.OperationRequest
import observe.ui.model.enums.SyncStatus
import org.typelevel.log4cats.Logger

trait ServerEventHandler:
  private def logMessage(
    rootModelData: View[RootModelData],
    msg:           String
  )(using Logger[IO]): IO[Unit] =
    msg match
      case NonEmptyString(nes) => rootModelData.async.zoom(RootModelData.log).mod(_ :+ nes)

  protected def processStreamEvent(
    clientConfig:    View[Pot[ClientConfig]],
    rootModelData:   View[RootModelData],
    syncStatus:      View[Option[SyncStatus]],
    configApiStatus: View[ApiStatus]
  )(
    event:           ClientEvent
  )(using Logger[IO]): IO[Unit] =
    val asyncRootModel = rootModelData.async
    event match
      case ClientEvent.InitialEvent(cc)                                          =>
        clientConfig.async.set(cc.ready)
      case ClientEvent.SingleActionEvent(obsId, stepId, subsystem, event, error) =>
        (asyncRootModel
          .zoom(RootModelData.executionState.at(obsId).some)
          .zoom(ExecutionState.stepResources.at(stepId).some.at(subsystem))
          .set:
            event match
              case SingleActionState.Started   => ActionStatus.Running.some
              case SingleActionState.Completed => ActionStatus.Completed.some
              case SingleActionState.Failed    => ActionStatus.Failed.some
        )
        >> // Reset Request
          asyncRootModel
            .zoom(RootModelData.obsRequests.index(obsId))
            .zoom(ObservationRequests.subsystemRun.index(stepId).index(subsystem))
            .set(OperationRequest.Idle)
          >>
          error.map(logMessage(rootModelData, _)).orEmpty
      case ClientEvent.ChecksOverrideEvent(_)                                    =>
        // TODO Update the UI
        IO.unit
      case ClientEvent.ObserveState(sequenceExecution, conditions, operator)     =>
        val nighttimeLoadedObsId = sequenceExecution.headOption.map(_._1)
        asyncRootModel.zoom(RootModelData.operator).set(operator) >>
          asyncRootModel.zoom(RootModelData.conditions).set(conditions) >>
          asyncRootModel
            .zoom(RootModelData.executionState)
            .set(sequenceExecution) >>
          // All requests are reset on every state update from the server.
          // Or should we only reset the observations that change? In that case, we need to do a thorough comparison.
          asyncRootModel
            .zoom(RootModelData.obsRequests)
            .set(Map.empty) >>
          asyncRootModel
            .zoom(RootModelData.nighttimeObservation)
            .mod(obs => // Only set if loaded obsId changed, otherwise config is lost.
              if (obs.map(_.obsId) =!= nighttimeLoadedObsId)
                nighttimeLoadedObsId.map(LoadedObservation(_))
              else
                obs
            ) >>
          syncStatus.async.set(SyncStatus.Synced.some) >>
          configApiStatus.async.set(ApiStatus.Idle)
      case ClientEvent.ProgressEvent(ObservationProgress(obsId, stepProgress))   =>
        asyncRootModel.zoom(RootModelData.obsProgress.at(obsId)).set(stepProgress.some)

  protected def processStreamError(
    rootModelData: View[RootModelData]
  )(error: Throwable)(using Logger[IO]): IO[Unit] =
    logMessage(rootModelData, s"ERROR Receiving Client Event: ${error.getMessage}")
