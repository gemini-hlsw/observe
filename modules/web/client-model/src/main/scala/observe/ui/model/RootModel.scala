// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.derived.*
import cats.syntax.option.*
import crystal.*
import crystal.react.View
import eu.timepit.refined.cats.given
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.ui.sso.UserVault
import monocle.Focus
import monocle.Lens
import observe.common.FixedLengthBuffer
import observe.model.ClientConfig
import observe.model.Conditions
import observe.model.ExecutionState
import observe.model.LogMessage
import observe.model.Observer
import observe.model.Operator
import observe.model.StepProgress
import observe.model.odb.ObsRecordedIds
import observe.ui.model.enums.ClientMode

case class RootModelData(
  userVault:            Pot[Option[UserVault]],
  readyObservations:    Pot[List[ObsSummary]],
  loadedObservations:   Map[Observation.Id, LoadedObservation],
  executionState:       Map[Observation.Id, ExecutionState], // Execution state on the server
  recordedIds:          ObsRecordedIds,                      // Map[Observation.Id, RecordedVisit]
  obsProgress:          Map[Observation.Id, StepProgress],
  userSelectedStep:     Map[Observation.Id, Step.Id],
  obsRequests:          Map[Observation.Id, ObservationRequests],
  conditions:           Conditions,
  observer:             Option[Observer],
  operator:             Option[Operator],
  userSelectionMessage: Option[NonEmptyString],
  globalLog:            FixedLengthBuffer[LogMessage],
  isAudioActivated:     IsAudioActivated
) derives Eq:
  // TODO Readonly mode won't depend on user logged or not, but on their permissions.
  // For the moment we are requiring the STAFF role, so all logged users can operate.
  val clientMode: ClientMode =
    userVault.toOption.flatten.fold(ClientMode.ReadOnly)(_ => ClientMode.CanOperate)

  val isUserLogged: Boolean = userVault.toOption.flatten.isDefined

  lazy val readyObservationsMap: Map[Observation.Id, ObsSummary] =
    readyObservations.toOption.orEmpty.map(o => o.obsId -> o).toMap

  def obsInstrument(obsId: Observation.Id): Option[Instrument] =
    readyObservationsMap.get(obsId).map(_.instrument)

  lazy val loadedObsByInstrument: Map[Instrument, Observation.Id] =
    loadedObservations.keySet.map(obsId => obsInstrument(obsId).map(_ -> obsId)).flatten.toMap

  // Adds a LoadedObservation for an instrument, removing the previous one for the same instrument, if any.
  def withLoadedObservation(obsId: Observation.Id, instrument: Instrument): RootModelData =
    copy(
      loadedObservations = loadedObsByInstrument
        .get(instrument)
        .fold(loadedObservations)(oldObsId =>
          loadedObservations - oldObsId
        ) + (obsId -> LoadedObservation())
    )

  // Adjusts the loaded observations to the given set, removing any not in the set, adding new ones, and keeping the rest.
  def withAdjustedLoadedObservations(obsIds: Set[Observation.Id]): RootModelData =
    val newObsIds: Set[Observation.Id] = obsIds -- loadedObservations.keySet
    copy(
      loadedObservations = loadedObservations.view.filterKeys(obsIds.contains).toMap ++
        newObsIds.map(newObsId => newObsId -> LoadedObservation()).toMap
    )

  def isObsLocked(obsId: Observation.Id): Boolean =
    executionState.get(obsId).exists(_.isLocked)

  def obsSelectedStep(obsId: Observation.Id): Option[Step.Id] =
    executionState.get(obsId).flatMap(_.runningStepId).orElse(userSelectedStep.get(obsId))

  def withLoginResult(result: Either[Throwable, Option[UserVault]]): RootModelData =
    val vault: Option[UserVault] = result.toOption.flatten
    copy(
      userVault = vault.ready,
      observer =
        vault.flatMap(v => NonEmptyString.from(v.user.displayName).toOption.map(Observer(_))),
      userSelectionMessage = result.left.toOption.map(t => NonEmptyString.unsafeFrom(t.getMessage))
    )

object RootModelData:
  val MaxGlobalLogEntries: Int = 5000

  val Initial: RootModelData =
    RootModelData(
      userVault = Pot.pending,
      readyObservations = Pot.pending,
      loadedObservations = Map.empty,
      executionState = Map.empty,
      recordedIds = ObsRecordedIds.Empty,
      obsProgress = Map.empty,
      obsRequests = Map.empty,
      userSelectedStep = Map.empty,
      conditions = Conditions.Default,
      observer = none,
      operator = none,
      userSelectionMessage = none,
      globalLog = FixedLengthBuffer.unsafe(MaxGlobalLogEntries),
      isAudioActivated = IsAudioActivated.True
    )

  val userVault: Lens[RootModelData, Pot[Option[UserVault]]]                          = Focus[RootModelData](_.userVault)
  val readyObservations: Lens[RootModelData, Pot[List[ObsSummary]]]                   =
    Focus[RootModelData](_.readyObservations)
  val loadedObservations: Lens[RootModelData, Map[Observation.Id, LoadedObservation]] =
    Focus[RootModelData](_.loadedObservations)
  val executionState: Lens[RootModelData, Map[Observation.Id, ExecutionState]]        =
    Focus[RootModelData](_.executionState)
  val recordedIds: Lens[RootModelData, ObsRecordedIds]                                = Focus[RootModelData](_.recordedIds)
  val obsProgress: Lens[RootModelData, Map[Observation.Id, StepProgress]]             =
    Focus[RootModelData](_.obsProgress)
  val userSelectedStep: Lens[RootModelData, Map[Observation.Id, Step.Id]]             =
    Focus[RootModelData](_.userSelectedStep)
  val obsRequests: Lens[RootModelData, Map[Observation.Id, ObservationRequests]]      =
    Focus[RootModelData](_.obsRequests)
  val conditions: Lens[RootModelData, Conditions]                                     = Focus[RootModelData](_.conditions)
  val observer: Lens[RootModelData, Option[Observer]]                                 = Focus[RootModelData](_.observer)
  val operator: Lens[RootModelData, Option[Operator]]                                 = Focus[RootModelData](_.operator)
  val userSelectionMessage: Lens[RootModelData, Option[NonEmptyString]]               =
    Focus[RootModelData](_.userSelectionMessage)
  val globalLog: Lens[RootModelData, FixedLengthBuffer[LogMessage]]                   =
    Focus[RootModelData](_.globalLog)
  val isAudioActivated: Lens[RootModelData, IsAudioActivated]                         =
    Focus[RootModelData](_.isAudioActivated)

case class RootModel(clientConfig: Pot[ClientConfig], data: View[RootModelData])
