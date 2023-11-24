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
import observe.model.Environment
import observe.model.ExecutionState
import observe.model.enums.ActionStatus
import observe.model.events.client.ClientEvent
import observe.model.events.client.ClientEvent.SingleActionState
import observe.ui.model.LoadedObservation
import observe.ui.model.RootModelData
import observe.ui.model.enums.ApiStatus
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
    environment:     View[Pot[Environment]],
    rootModelData:   View[RootModelData],
    syncStatus:      View[Option[SyncStatus]],
    configApiStatus: View[ApiStatus]
  )(
    event:           ClientEvent
  )(using Logger[IO]): IO[Unit] =
    event match
      case ClientEvent.InitialEvent(env)                                         =>
        environment.async.set(env.ready)
      case ClientEvent.SingleActionEvent(obsId, stepId, subsystem, event, error) =>
        (rootModelData.async
          .zoom(RootModelData.sequenceExecution.at(obsId).some)
          .zoom(ExecutionState.stepResources.at(stepId).some.at(subsystem))
          .set:
            event match
              case SingleActionState.Started   => ActionStatus.Running.some
              case SingleActionState.Completed => ActionStatus.Completed.some
              case SingleActionState.Failed    => ActionStatus.Failed.some
        ) >>
          error.map(logMessage(rootModelData, _)).orEmpty
      case ClientEvent.ChecksOverrideEvent(_)                                    =>
        // TODO Update the UI
        IO.unit
      case ClientEvent.ObserveState(sequenceExecution, conditions, operator)     =>
        val asyncRootModel       = rootModelData.async
        val nighttimeLoadedObsId = sequenceExecution.headOption.map(_._1)
        asyncRootModel.zoom(RootModelData.operator).set(operator) >>
          asyncRootModel.zoom(RootModelData.conditions).set(conditions) >>
          asyncRootModel
            .zoom(RootModelData.sequenceExecution)
            .set(sequenceExecution) >>
          asyncRootModel
            .zoom(RootModelData.nighttimeObservation)
            .mod(obs => // Only set if loaded obsId changed, otherwise config and summary are lost.
              if (obs.map(_.obsId) =!= nighttimeLoadedObsId)
                nighttimeLoadedObsId.map(LoadedObservation(_))
              else obs
            ) >>
          syncStatus.async.set(SyncStatus.Synced.some) >>
          configApiStatus.async.set(ApiStatus.Idle)

  protected def processStreamError(
    rootModelData: View[RootModelData]
  )(error: Throwable)(using Logger[IO]): IO[Unit] =
    logMessage(rootModelData, s"ERROR Receiving Client Event: ${error.getMessage}")
