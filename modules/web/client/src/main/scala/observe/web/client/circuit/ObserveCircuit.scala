// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import scala.scalajs.LinkingInfo

import cats._
import cats.data.NonEmptyList
import cats.syntax.all._
import diode._
import diode.react.ReactConnector
import japgolly.scalajs.react.Callback
import monocle.Prism
import observe.model.Observation
import observe.model._
import observe.model.events._
import observe.web.client.actions.AppendToLog
import observe.web.client.actions.CloseLoginBox
import observe.web.client.actions.CloseUserNotificationBox
import observe.web.client.actions.OpenLoginBox
import observe.web.client.actions.OpenUserNotificationBox
import observe.web.client.actions.ServerMessage
import observe.web.client.actions._
import observe.web.client.actions.show
import observe.web.client.handlers._
import observe.web.client.model._
import typings.loglevel.mod.{ ^ => logger }

/**
 * Diode processor to log some of the action to aid in debugging
 */
final class LoggingProcessor[M <: AnyRef] extends ActionProcessor[M] {
  override def process(
    dispatch:     Dispatcher,
    action:       Any,
    next:         Any => ActionResult[M],
    currentModel: M
  ): ActionResult[M] = {
    // log some of the actions
    action match {
      case AppendToLog(_)                             =>
      case ServerMessage(_: ServerLogMessage)         =>
      case ServerMessage(_: ObservationProgressEvent) =>
      case UpdateStepsConfigTableState(_)             =>
      case UpdateSessionQueueTableState(_)            =>
      case UpdateStepTableState(_, _)                 =>
      case UpdateCalTableState(_, _)                  =>
      case UpdateSelectedStep(_, _)                   =>
      case VerifyLoggedStatus                         =>
      case a: Action                                  =>
        if (LinkingInfo.developmentMode) logger.info(s"Action: ${a.show}")
      case _                                          =>
    }
    // call the next processor
    next(action)
  }
}

/**
 * Contains the Diode circuit to manipulate the page
 */
object ObserveCircuit
    extends Circuit[ObserveAppRootModel]
    with ReactConnector[ObserveAppRootModel] {
  addProcessor(new LoggingProcessor[ObserveAppRootModel]())

  // Model read-writers
  val webSocketFocusRW: ModelRW[ObserveAppRootModel, WebSocketsFocus] =
    this.zoomRWL(WebSocketsFocus.webSocketFocusL)

  val initialSyncFocusRW: ModelRW[ObserveAppRootModel, InitialSyncFocus] =
    this.zoomRWL(ObserveAppRootModel.uiModel.andThen(InitialSyncFocus.initialSyncFocusL))

  val tableStateRW: ModelRW[ObserveAppRootModel, AppTableStates] =
    this.zoomRWL(ObserveAppRootModel.uiModel.andThen(ObserveUIModel.appTableStates))

  // Reader to indicate the allowed interactions
  val statusReader: ModelR[ObserveAppRootModel, ClientStatus] =
    this.zoomL(ClientStatus.clientStatusFocusL)

  // Reader to read/write the sound setting
  val soundSettingReader: ModelR[ObserveAppRootModel, SoundSelection] =
    this.zoomL(ObserveAppRootModel.soundSettingL)

  // Reader for the queue operations
  val queueOperationsRW: ModelRW[ObserveAppRootModel, CalibrationQueues] =
    this.zoomRWL(ObserveAppRootModel.uiModel.andThen(ObserveUIModel.queues))

  // Reader to update the sequences in both parts of the model being used
  val sequencesReaderRW: ModelRW[ObserveAppRootModel, SequencesFocus] =
    this.zoomRWL(SequencesFocus.sequencesFocusL)

  // Reader to update the selected sequences and location
  val sodLocationReaderRW: ModelRW[ObserveAppRootModel, SODLocationFocus] =
    this.zoomRWL(SODLocationFocus.sodLocationFocusL)

  val statusAndLoadedSequencesReader: ModelR[ObserveAppRootModel, StatusAndLoadedSequencesFocus] =
    this.zoomG(StatusAndLoadedSequencesFocus.statusAndLoadedSequencesG)

  val sessionQueueFilterReader: ModelR[ObserveAppRootModel, SessionQueueFilter] =
    this.zoomL(ObserveAppRootModel.sessionQueueFilterL)

  // Reader for sequences on display
  val headerSideBarReader: ModelR[ObserveAppRootModel, HeaderSideBarFocus] =
    this.zoomG(HeaderSideBarFocus.headerSideBarG)

  val logDisplayedReader: ModelR[ObserveAppRootModel, SectionVisibilityState] =
    this.zoomL(ObserveAppRootModel.logDisplayL)

  val tabsReader: ModelR[ObserveAppRootModel, TabFocus] =
    this.zoomG(TabFocus.tabFocusG)

  val observeTabs: ModelR[ObserveAppRootModel, NonEmptyList[TabContentFocus]] =
    this.zoomG(TabContentFocus.tabContentFocusG)

  val sequencesOnDisplayRW: ModelRW[ObserveAppRootModel, SequencesOnDisplay] =
    this.zoomRWL(ObserveAppRootModel.sequencesOnDisplayL)

  val queueFocusRW: ModelRW[ObserveAppRootModel, QueueRequestsFocus] =
    this.zoomRWL(QueueRequestsFocus.unsafeQueueRequestsFocusL)

  val acProgressRW: ModelRW[ObserveAppRootModel, AlignAndCalibStep] =
    this.zoomRWL(ObserveAppRootModel.alignAndCalib)

  val userLoginRW: ModelRW[SeqexecAppRootModel, UserLoginFocus] =
    this.zoomRWL(SeqexecAppRootModel.userLoginFocus)

  val userPromptRW: ModelRW[SeqexecAppRootModel, UserPromptFocus] =
    this.zoomRWL(SeqexecAppRootModel.userPromptFocus)

  def sequenceTab(
    id: Observation.Id
  ): ModelR[ObserveAppRootModel, Option[ObserveTabActive]] =
    this.zoomG(
      ObserveAppRootModel.sequencesOnDisplayL
        .andThen(SequencesOnDisplay.tabG(id))
    )

  def sequenceObserverReader(
    id: Observation.Id
  ): ModelR[ObserveAppRootModel, Option[SequenceInfoFocus]] =
    this.zoomG(SequenceInfoFocus.sequenceInfoG(id))

  def obsProgressReader[P <: Progress: Eq](
    id:                     Observation.Id,
    stepId:                 StepId
  )(implicit progressPrism: Prism[Progress, P]): ModelR[ObserveAppRootModel, Option[P]] =
    this.zoomO(AllObservationsProgressState.progressStateO[P](id, stepId))

  def statusAndStepReader(
    id: Observation.Id
  ): ModelR[ObserveAppRootModel, Option[StatusAndStepFocus]] =
    this.zoomG(StatusAndStepFocus.statusAndStepG(id))

  def stepsTableReaderF(
    id: Observation.Id
  ): ModelR[ObserveAppRootModel, Option[StepsTableFocus]] =
    this.zoomG(StepsTableFocus.stepsTableG(id))

  def stepsTableReader(
    id: Observation.Id
  ): ModelR[ObserveAppRootModel, StepsTableAndStatusFocus] =
    this.zoomG(StepsTableAndStatusFocus.stepsTableAndStatusFocusG(id))

  def sequenceControlReader(
    id: Observation.Id
  ): ModelR[ObserveAppRootModel, Option[SequenceControlFocus]] =
    this.zoomG(SequenceControlFocus.seqControlG(id))

  def calQueueControlReader(
    id: QueueId
  ): ModelR[ObserveAppRootModel, Option[CalQueueControlFocus]] =
    this.zoomG(CalQueueControlFocus.queueControlG(id))

  def calQueueReader(
    id: QueueId
  ): ModelR[ObserveAppRootModel, Option[CalQueueFocus]] =
    this.zoomG(CalQueueFocus.calQueueG(id))

  private val wsHandler               = new WebSocketHandler(zoomTo(_.ws))
  private val serverMessagesHandler   = new ServerMessagesHandler(webSocketFocusRW)
  private val initialSyncHandler      = new InitialSyncHandler(initialSyncFocusRW)
  private val navigationHandler       = new NavigationHandler(zoomTo(_.uiModel.navLocation))
  private val loginBoxHandler         =
    new ModalBoxHandler(OpenLoginBox, CloseLoginBox, zoomTo(_.uiModel.loginBox))
  private val notificationBoxHandler  = new ModalBoxHandler(OpenUserNotificationBox,
                                                           CloseUserNotificationBox,
                                                           zoomTo(_.uiModel.notification.visibility)
  )
  private val userLoginHandler        = new UserLoginHandler(userLoginRW)
  private val userNotificationHandler = new NotificationsHandler(zoomTo(_.uiModel.notification))
  private val userPromptHandler       = new UserPromptHandler(userPromptRW)
  private val sequenceDisplayHandler  = new SequenceDisplayHandler(sequencesReaderRW)
  private val sequenceExecHandler     = new SequenceExecutionHandler(zoomTo(_.sequences))
  private val globalLogHandler        = new GlobalLogHandler(zoomTo(_.uiModel.globalLog))
  private val conditionsHandler       = new ConditionsHandler(zoomTo(_.sequences.conditions))
  private val operatorHandler         = new OperatorHandler(zoomTo(_.sequences.operator))
  private val displayNameHandler      = new DisplayNameHandler(zoomTo(_.uiModel.displayNames))
  private val remoteRequestsHandler   = new RemoteRequestsHandler(zoomTo(_.clientId))
  private val queueRequestsHandler    = new QueueRequestsHandler(queueFocusRW)
  private val tableStateHandler       = new TableStateHandler(tableStateRW)
  private val loadSequencesHandler    = new LoadedSequencesHandler(sodLocationReaderRW)
  private val operationsStateHandler  = new OperationsStateHandler(sequencesOnDisplayRW)
  private val siteHandler             = new SiteHandler(zoomTo(_.site))
  private val queueOpsHandler         = new QueueOperationsHandler(queueOperationsRW)
  private val queueStateHandler       = new QueueStateHandler(queueOperationsRW)
  private val openConnectionHandler   = new OpenConnectionHandler(zoomTo(_.uiModel.queues))
  private val observationsProgHandler = new ObservationsProgressStateHandler(
    zoomTo(_.uiModel.obsProgress)
  )
  private val sessionFilterHandler    = new SessionQueueFilterHandler(
    zoomTo(_.uiModel.sessionQueueFilter)
  )
  private val soundHandler            = new SoundOnOffHandler(zoomTo(_.uiModel.sound))

  def dispatchCB[A <: Action](a: A): Callback = Callback(dispatch(a))

  override protected def initialModel = ObserveAppRootModel.Initial

  override protected def actionHandler =
    composeHandlers(
      wsHandler,
      foldHandlers(
        serverMessagesHandler,
        initialSyncHandler,
        loadSequencesHandler,
        userNotificationHandler,
        userPromptHandler,
        openConnectionHandler,
        queueStateHandler,
        observationsProgHandler
      ),
      sequenceExecHandler,
      notificationBoxHandler,
      loginBoxHandler,
      userLoginHandler,
      sequenceDisplayHandler,
      globalLogHandler,
      conditionsHandler,
      operatorHandler,
      displayNameHandler,
      foldHandlers(remoteRequestsHandler, operationsStateHandler),
      foldHandlers(queueOpsHandler, queueRequestsHandler),
      navigationHandler,
      tableStateHandler,
      siteHandler,
      sessionFilterHandler,
      soundHandler
    )

  /**
   * Handles a fatal error most likely during action processing
   */
  override def handleFatal(action: Any, e: Throwable): Unit = {
    logger.error(s"Action not handled $action")
    super.handleFatal(action, e)
  }

  /**
   * Handle a non-fatal error, such as dispatching an action with no action handler.
   */
  override def handleError(msg: String): Unit =
    logger.error(s"Action error $msg")

}
