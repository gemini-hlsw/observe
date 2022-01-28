// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import scala.collection.immutable.SortedMap
import scala.scalajs.js.timers._
import cats._
import cats.syntax.all._
import lucuma.core.enum.Site
import monocle.Getter
import monocle.Lens
import monocle.Traversal
import monocle.function.At.atSortedMap
import monocle.function.Each.mapEach
import monocle.function.FilterIndex.sortedMapFilterIndex
import monocle.macros.Lenses
import observe.model.CalibrationQueueId
import observe.model.ClientId
import observe.model.Conditions
import observe.model.ExecutionQueueView
import observe.model.M1GuideConfig._
import observe.model.M2GuideConfig._
import observe.model.Observation
import observe.model.QueueId
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.model.TelescopeGuideConfig
import observe.model.enum.MountGuideOption._
import observe.web.client.components.SessionQueueTable
import observe.web.client.components.sequence.steps.StepConfigTable
import observe.web.client.components.sequence.steps.StepsTable
import observe.web.client.circuit.UserLoginFocus
import observe.web.client.circuit.SequencesQueueFocus
import web.client.table._

/**
 * Root of the UI Model of the application
 */
@Lenses
final case class ObserveAppRootModel(
  sequences:     SequencesQueue[SequenceView],
  ws:            WebSocketConnection,
  site:          Option[Site],
  clientId:      Option[ClientId],
  uiModel:       ObserveUIModel,
  serverVersion: Option[String],
  guideConfig:   TelescopeGuideConfig,
  alignAndCalib: AlignAndCalibStep,
  pingInterval:  Option[SetTimeoutHandle]
)

object ObserveAppRootModel {
  val NoSequencesLoaded: SequencesQueue[SequenceView] =
    SequencesQueue[SequenceView](Map.empty, Conditions.Default, none, SortedMap.empty, Nil)

  val Initial: ObserveAppRootModel = ObserveAppRootModel(
    NoSequencesLoaded,
    WebSocketConnection.Empty,
    none,
    none,
    ObserveUIModel.Initial,
    none,
    TelescopeGuideConfig(MountGuideOff, M1GuideOff, M2GuideOff),
    AlignAndCalibStep.NoAction,
    None
  )

  val logDisplayL: Lens[ObserveAppRootModel, SectionVisibilityState] =
    ObserveAppRootModel.uiModel.andThen(ObserveUIModel.globalLog).andThen(GlobalLog.display)

  val userLoginFocus: Lens[ObserveAppRootModel, UserLoginFocus] =
    ObserveAppRootModel.uiModel.andThen(ObserveUIModel.userLoginFocus)

  val sessionQueueFilterL: Lens[ObserveAppRootModel, SessionQueueFilter] =
    ObserveAppRootModel.uiModel.andThen(ObserveUIModel.sessionQueueFilter)

  val sequencesOnDisplayL: Lens[ObserveAppRootModel, SequencesOnDisplay] =
    ObserveAppRootModel.uiModel.andThen(ObserveUIModel.sequencesOnDisplay)

  val sequenceTabsT: Traversal[ObserveAppRootModel, SequenceTab] =
    ObserveAppRootModel.sequencesOnDisplayL.andThen(SequencesOnDisplay.sequenceTabs)

  val sessionQueueL: Lens[ObserveAppRootModel, List[SequenceView]] =
    ObserveAppRootModel.sequences.andThen(SequencesQueue.sessionQueue)

  val sessionQueueTableStateL
    : Lens[ObserveAppRootModel, TableState[SessionQueueTable.TableColumn]] =
    ObserveAppRootModel.uiModel
      .andThen(ObserveUIModel.appTableStates)
      .andThen(AppTableStates.sessionQueueTable)

  def stepsTableStateL(
    id: Observation.Id
  ): Lens[ObserveAppRootModel, Option[TableState[StepsTable.TableColumn]]] =
    ObserveAppRootModel.uiModel
      .andThen(ObserveUIModel.appTableStates)
      .andThen(
        AppTableStates
          .stepsTableAtL(id)
      )

  val unsafeSequencesQueueFocus: Lens[ObserveAppRootModel, SequencesQueueFocus] =
    Lens[ObserveAppRootModel, SequencesQueueFocus](m =>
      SequencesQueueFocus(m.sequences,
                          m.uiModel.user.flatMap(u => m.uiModel.displayNames.get(u.username))
      )
    )(n => a => a.copy(sequences = n.sequences))

  val soundSettingL: Lens[ObserveAppRootModel, SoundSelection] =
    ObserveAppRootModel.uiModel.andThen(ObserveUIModel.sound)

  val configTableStateL: Lens[ObserveAppRootModel, TableState[StepConfigTable.TableColumn]] =
    ObserveAppRootModel.uiModel
      .andThen(ObserveUIModel.appTableStates)
      .andThen(AppTableStates.stepConfigTable)

  def executionQueuesT(
    id: QueueId
  ): Traversal[ObserveAppRootModel, ExecutionQueueView] =
    ObserveAppRootModel.sequences
      .andThen(SequencesQueue.queues[SequenceView])
      .andThen(
        sortedMapFilterIndex[QueueId, ExecutionQueueView].filterIndex((qid: QueueId) => qid === id)
      )

  val queuesT: Traversal[ObserveAppRootModel, ExecutionQueueView] =
    ObserveAppRootModel.sequences
      .andThen(SequencesQueue.queues[SequenceView])
      .andThen(mapEach[QueueId, ExecutionQueueView].each)

  val dayCalG: Getter[ObserveAppRootModel, Option[ExecutionQueueView]] =
    ObserveAppRootModel.sequences
      .andThen(SequencesQueue.queues[SequenceView])
      .andThen(atSortedMap[QueueId, ExecutionQueueView].at(CalibrationQueueId))
      .asGetter

  implicit val eq: Eq[ObserveAppRootModel] =
    Eq.by(x => (x.sequences, x.ws, x.site, x.clientId, x.uiModel, x.serverVersion))
}
