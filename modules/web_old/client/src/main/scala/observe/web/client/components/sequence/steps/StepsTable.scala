// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.steps

import scala.collection.immutable.SortedMap
import scala.math.*
import scala.scalajs.js
import cats.Eq
import cats.data.NonEmptyList
import cats.implicits.*
import japgolly.scalajs.react.ReactMonocle.*
import japgolly.scalajs.react.{CtorType, Reusability, _}
import japgolly.scalajs.react.component.Scala.Component
import japgolly.scalajs.react.component.builder.Lifecycle.*
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.facade.JsNumber
import japgolly.scalajs.react.vdom.html_<^._
import monocle.Lens
import mouse.boolean.*
import org.scalajs.dom.HTMLElement
import react.common.*
import react.common.implicits.*
import react.semanticui.sizes.*
import react.semanticui.{SemanticSize => SSize}
import react.virtualized.*
import observe.model.Observation
import observe.model.RunningStep
import observe.model.SequenceState
import observe.model.Step
import observe.model.StepId
import observe.model.StepState
import observe.model.enums.Instrument
import observe.model.enums.Resource
import observe.model.enums.StepType
import observe.web.client.actions.ClearAllResourceOperations
import observe.web.client.actions.FlipBreakpointStep
import observe.web.client.actions.UpdateSelectedStep
import observe.web.client.actions.UpdateStepTableState
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.circuit.StepsTableAndStatusFocus
import observe.web.client.circuit.StepsTableFocus
import observe.web.client.components.ObserveStyles
import observe.web.client.components.TableContainer
import observe.web.client.icons.*
import observe.web.client.model.ClientStatus
import observe.web.client.model.Formatting.*
import observe.web.client.model.ModelOps.*
import observe.web.client.model.Pages.ObservePages
import observe.web.client.model.ResourceRunOperation
import observe.web.client.model.RunOperation
import observe.web.client.model.StepItems.*
import observe.web.client.model.TabOperations
import observe.web.client.model.lenses.*
import observe.web.client.reusability.*
import web.client.table.*
import web.client.JsNumberOps.*

trait Columns {
  val ControlWidth: Double          = 40
  val StepWidth: Double             = 60
  val ExecutionWidth: Double        = 350
  val ExecutionMinWidth: Double     = 350
  val OffsetWidthBase: Double       = 75
  val OffsetIconWidth: Double       = 23.02
  val OffsetPadding: Double         = 12
  val ExposureWidth: Double         = 75
  val ExposureMinWidth: Double      = 83.667 + ObserveStyles.TableBorderWidth
  val DisperserWidth: Double        = 100
  val DisperserMinWidth: Double     = 100 + ObserveStyles.TableBorderWidth
  val ObservingModeWidth: Double    = 180
  val ObservingModeMinWidth: Double = 130.8 + ObserveStyles.TableBorderWidth
  val FilterWidth: Double           = 180
  val FilterMinWidth: Double        = 100
  val FPUWidth: Double              = 100
  val FPUMinWidth: Double           = 46.667 + ObserveStyles.TableBorderWidth
  val CameraWidth: Double           = 180
  val CameraMinWidth: Double        = 10
  val DeckerWidth: Double           = 110
  val DeckerMinWidth: Double        = 10
  val ImagingMirrorWidth: Double    = 180
  val ImagingMirrorMinWidth: Double = 10
  val ObjectTypeWidth: Double       = 75
  val SettingsWidth: Double         = 34
  val ReadModeMinWidth: Double      = 180
  val ReadModeWidth: Double         = 230

  sealed trait TableColumn        extends Product with Serializable
  case object ControlColumn       extends TableColumn
  case object StepColumn          extends TableColumn
  case object ExecutionColumn     extends TableColumn
  case object OffsetColumn        extends TableColumn
  case object ObservingModeColumn extends TableColumn
  case object ExposureColumn      extends TableColumn
  case object DisperserColumn     extends TableColumn
  case object FilterColumn        extends TableColumn
  case object FPUColumn           extends TableColumn
  case object CameraColumn        extends TableColumn
  case object DeckerColumn        extends TableColumn
  case object ReadModeColumn      extends TableColumn
  case object ImagingMirrorColumn extends TableColumn
  case object ObjectTypeColumn    extends TableColumn
  case object SettingsColumn      extends TableColumn

  val columnsDefaultWidth: Map[TableColumn, Double] = Map(
    ControlColumn       -> ControlWidth,
    StepColumn          -> StepWidth,
    ExecutionColumn     -> ExposureMinWidth,
    OffsetColumn        -> OffsetWidthBase,
    ObservingModeColumn -> ObservingModeWidth,
    ExposureColumn      -> ExposureWidth,
    DisperserColumn     -> DisperserWidth,
    FilterColumn        -> FilterWidth,
    FPUColumn           -> FPUWidth,
    CameraColumn        -> CameraWidth,
    DeckerColumn        -> DeckerWidth,
    ReadModeColumn      -> ReadModeWidth,
    ImagingMirrorColumn -> ImagingMirrorWidth,
    ObjectTypeColumn    -> ObjectTypeWidth,
    SettingsColumn      -> SettingsWidth
  )

  object TableColumn {
    given Eq[TableColumn] = Eq.fromUniversalEquals

    given Reusability[TableColumn] = Reusability.byRef
  }

  val ControlColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ControlColumn,
    name = "control",
    label = "",
    visible = true,
    width = FixedColumnWidth.unsafeFromDouble(ControlWidth)
  )

  val StepMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    StepColumn,
    name = "idx",
    label = "Step",
    visible = true,
    width = FixedColumnWidth.unsafeFromDouble(StepWidth)
  )

  val ExecutionMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ExecutionColumn,
    name = "state",
    label = "Execution Progress",
    visible = true,
    width = VariableColumnWidth.unsafeFromDouble(0.1, ExecutionMinWidth),
    grow = 20
  )

  val OffsetMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    OffsetColumn,
    name = "offsets",
    label = "Offsets",
    visible = true,
    width = FixedColumnWidth.unsafeFromDouble(OffsetWidthBase)
  )

  val ObservingModeMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ObservingModeColumn,
    name = "obsMode",
    label = "Observing Mode",
    visible = true,
    width = VariableColumnWidth.unsafeFromDouble(0.1, ObservingModeMinWidth)
  )

  val ExposureMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ExposureColumn,
    name = "exposure",
    label = "Exposure",
    visible = true,
    width = VariableColumnWidth.unsafeFromDouble(0.1, ExposureMinWidth)
  )

  val DisperserMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    DisperserColumn,
    name = "disperser",
    label = "Disperser",
    visible = true,
    width = VariableColumnWidth.unsafeFromDouble(0.1, DisperserMinWidth)
  )

  val FilterMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    FilterColumn,
    name = "filter",
    label = "Filter",
    visible = true,
    removeable = 2,
    width = VariableColumnWidth.unsafeFromDouble(0.1, FilterMinWidth)
  )

  val FPUMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    FPUColumn,
    name = "fpu",
    label = "FPU",
    removeable = 3,
    visible = true,
    width = VariableColumnWidth.unsafeFromDouble(0.1, FPUMinWidth)
  )

  val CameraMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    CameraColumn,
    name = "camera",
    label = "Camera",
    visible = true,
    removeable = 4,
    width = VariableColumnWidth.unsafeFromDouble(0.1, CameraMinWidth)
  )

  val DeckerMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    DeckerColumn,
    name = "camera",
    label = "Decker",
    removeable = 5,
    visible = true,
    width = VariableColumnWidth.unsafeFromDouble(0.1, DeckerMinWidth)
  )

  val ReadModeMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ReadModeColumn,
    name = "camera",
    label = "ReadMode",
    visible = true,
    removeable = 6,
    width = VariableColumnWidth.unsafeFromDouble(0.1, ReadModeMinWidth)
  )

  val ImagingMirrorMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ImagingMirrorColumn,
    name = "camera",
    label = "ImagingMirror",
    visible = true,
    removeable = 7,
    width = VariableColumnWidth.unsafeFromDouble(0.1, ImagingMirrorMinWidth)
  )

  val ObjectTypeMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ObjectTypeColumn,
    name = "type",
    label = "Type",
    visible = true,
    removeable = 1,
    width = FixedColumnWidth.unsafeFromDouble(ObjectTypeWidth)
  )

  val SettingsMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    SettingsColumn,
    name = "set",
    label = "",
    visible = true,
    width = FixedColumnWidth.unsafeFromDouble(SettingsWidth)
  )

  val all: NonEmptyList[ColumnMeta[TableColumn]] =
    NonEmptyList.of(
      ControlColumnMeta,
      StepMeta,
      ExecutionMeta,
      OffsetMeta,
      ObservingModeMeta,
      ExposureMeta,
      DisperserMeta,
      FilterMeta,
      FPUMeta,
      CameraMeta,
      DeckerMeta,
      ReadModeMeta,
      ImagingMirrorMeta,
      ObjectTypeMeta,
      SettingsMeta
    )

  val allTC: NonEmptyList[TableColumn] = all.map(_.column)

  val columnsMinWidth: Map[TableColumn, Double] = Map(
    ExposureColumn      -> ExposureMinWidth,
    DisperserColumn     -> DisperserMinWidth,
    ObservingModeColumn -> ObservingModeMinWidth,
    FilterColumn        -> FilterMinWidth,
    FPUColumn           -> FPUMinWidth,
    CameraColumn        -> CameraMinWidth,
    DeckerColumn        -> DeckerMinWidth,
    ImagingMirrorColumn -> ImagingMirrorMinWidth,
    ReadModeColumn      -> ReadModeMinWidth
  )
}

/**
 * Container for a table with the steps
 */
final case class StepsTable(
  router:     RouterCtl[ObservePages],
  canOperate: Boolean,
  stepsTable: StepsTableAndStatusFocus
) extends ReactProps[StepsTable](StepsTable.component) {

  import StepsTable._ // Import static members from Columns

  val status: ClientStatus                  = stepsTable.status
  val steps: Option[StepsTableFocus]        = stepsTable.stepsTable
  val instrument: Option[Instrument]        = steps.map(_.instrument)
  val runningStep: Option[RunningStep]      = steps.flatMap(_.runningStep)
  val obsIdName: Option[Observation.IdName] = steps.map(_.idName)
  val tableState: TableState[TableColumn]   =
    steps.map(_.tableState).getOrElse(State.InitialTableState)
  val stepsList: List[Step]                 = steps.foldMap(_.steps)
  val selectedStep: Option[StepId]          = steps.flatMap(_.selectedStep)
  val rowCount: Int                         = stepsList.length
  val nextStepToRun: Option[StepId]         = steps.flatMap(_.nextStepToRun)
  def tabOperations: TabOperations          =
    steps.map(_.tabOperations).getOrElse(TabOperations.Default)
  val showDisperser: Boolean                = showProp(InstrumentProperties.Disperser)
  val showExposure: Boolean                 = showProp(InstrumentProperties.Exposure)
  val showFilter: Boolean                   = showProp(InstrumentProperties.Filter)
  val showFPU: Boolean                      = showProp(InstrumentProperties.FPU)
  val showCamera: Boolean                   = showProp(InstrumentProperties.Camera)
  val showDecker: Boolean                   = showProp(InstrumentProperties.Decker)
  val showImagingMirror: Boolean            = showProp(
    InstrumentProperties.ImagingMirror
  )
  val isPreview: Boolean                    = steps.exists(_.isPreview)
  val hasControls: Boolean                  = canOperate && !isPreview
  val canSetBreakpoint: Boolean             = canOperate && !isPreview
  val showObservingMode: Boolean            = showProp(
    InstrumentProperties.ObservingMode
  )
  val showReadMode: Boolean                 = showProp(InstrumentProperties.ReadMode)

  val sequenceState: Option[SequenceState] = steps.map(_.state)

  def stepSummary(step: Step): Option[StepStateSummary] =
    (steps.flatMap(_.steps.indexOf(step).some.flatMap(x => (x >= 0).option(x))),
     obsIdName.map(_.id),
     instrument,
     sequenceState
    )
      .mapN(StepStateSummary(step, _, _, _, tabOperations, _))

  def detailRowCount(step: Step, selected: Option[StepId]): Option[Int] =
    stepSummary(step).map(_.detailRows(selected, hasControls).rows)

  def showRowDetails(step: Step, selected: Option[StepId]): Boolean =
    detailRowCount(step, selected).forall(_ > 0)

  def rowDetailsHeight(step: Step, selected: Option[StepId]): Int =
    detailRowCount(step, selected)
      .map(_ * ObserveStyles.runningBottomRowHeight)
      .orEmpty

  def stepSelectionAllowed(stepId: StepId): Boolean =
    canControlSubsystems(stepId) && !tabOperations.resourceInFlight(stepId) &&
      !sequenceState.exists(_.isRunning)

  def rowGetter(stepId: StepId): StepRow =
    steps.flatMap(_.steps.find(_.id === stepId)).fold(StepRow.Zero)(StepRow.apply)

  def rowGetterByIndex(idx: Int): StepRow =
    steps.flatMap(_.steps.lift(idx)).fold(StepRow.Zero)(StepRow.apply)

  def subsystemsNotIdle(stepId: StepId): Boolean =
    tabOperations.resourceRunNotIdle(stepId) && !isPreview

  def canControlSubsystems(stepId: StepId): Boolean =
    !rowGetter(stepId).step.isFinished && canOperate && !isPreview

  val configTableState: TableState[StepConfigTable.TableColumn] =
    stepsTable.configTableState

  // Find out if offsets should be displayed
  val offsetsDisplay: OffsetsDisplay = stepsList.offsetsDisplay

  private def showProp(p: InstrumentProperties): Boolean =
    steps.exists(s => s.instrument.displayItems.contains(p))

  val showOffsets: Boolean =
    stepsList.headOption.flatMap(stepTypeO.getOption) match {
      case Some(StepType.Object) => showProp(InstrumentProperties.Offsets)
      case _                     => false
    }

  val offsetWidth: Option[Double] = {
    val (maxWidth, maxAxisLabelWidth, maxNodLabelWidth) = stepsList.sequenceOffsetMaxWidth
    if (maxNodLabelWidth === 0.0)
      (maxWidth + maxAxisLabelWidth + OffsetIconWidth + OffsetPadding * 4).some
    else
      ((maxWidth + maxAxisLabelWidth + maxNodLabelWidth) * 2 + OffsetIconWidth + OffsetPadding * 5).some
  }

  val exposure: Step => Option[String] = s => instrument.flatMap(s.exposureAndCoaddsS)

  val disperser: Step => Option[String] = s => instrument.flatMap(s.disperser)

  val filter: Step => Option[String] = s => instrument.flatMap(s.filter)

  val fpuOrMask: Step => Option[String] = s => instrument.flatMap(s.fpuOrMask)

  val camera: Step => Option[String] = s => instrument.flatMap(s.cameraName)

  val shownForInstrument: List[ColumnMeta[TableColumn]] =
    all.filter {
      case DisperserMeta     => showDisperser
      case OffsetMeta        => showOffsets
      case ObservingModeMeta => showObservingMode
      case ExposureMeta      => showExposure
      case FilterMeta        => showFilter
      case FPUMeta           => showFPU
      case CameraMeta        => showCamera
      case DeckerMeta        => showDecker
      case ImagingMirrorMeta => showImagingMirror
      case ReadModeMeta      => showReadMode
      case _                 => true
    }

  private val visibleColumns: TableColumn => Boolean =
    shownForInstrument.map(_.column).contains _

  def visibleColumnValues(s: Step) = (exposure(s),
                                      disperser(s),
                                      filter(s),
                                      fpuOrMask(s),
                                      camera(s),
                                      s.deckerName,
                                      s.imagingMirrorName,
                                      s.observingMode,
                                      s.readMode
  )

  val extractors = List[(TableColumn, Step => Option[String])](
    (ExposureColumn, exposure),
    (FPUColumn, fpuOrMask),
    (FilterColumn, filter),
    (DisperserColumn, disperser),
    (CameraColumn, camera),
    (DeckerColumn, _.deckerName),
    (ImagingMirrorColumn, _.imagingMirrorName),
    (ObservingModeColumn, _.observingMode),
    (ReadModeColumn, _.readMode)
  ).toMap

  private val valueCalculatedCols: TableColumn => Option[Double] = {
    case ExecutionColumn => 200.0.some
    case OffsetColumn    => offsetWidth
    case _               => none
  }

  private val measuredColumnWidths: TableColumn => Option[Double] =
    colWidthsO(stepsList, allTC, extractors, columnsMinWidth, Map.empty[TableColumn, Double])

  private val columnWidths: TableColumn => Option[Double] =
    c => measuredColumnWidths(c).orElse(valueCalculatedCols(c))

}

object StepsTable extends Columns {
  type Scope        = RenderScope[Props, State, Unit]
  type DidUpdate    = ComponentDidUpdate[Props, State, Unit, Unit]
  type ReceiveProps = ComponentWillReceiveProps[Props, State, Unit]

  val MiddleButton      = 1 // As defined by React.js
  private val HeaderRow = -1

  val HeightWithOffsets: Int    = 40
  val BreakpointLineHeight: Int = 5

  // ScalaJS defined trait
  trait StepRow extends js.Object {
    var step: Step
  }

  object StepRow {

    def apply(step: Step): StepRow = {
      val p = (new js.Object).asInstanceOf[StepRow]
      p.step = step
      p
    }

    def unapply(l: StepRow): Option[Step] = Some(l.step)

    val Zero: StepRow = (new js.Object).asInstanceOf[StepRow]
  }

  type Props = StepsTable

  final case class StepSummary(id: StepId, status: StepState, breakpoint: Boolean)
  object StepSummary {
    def fromStep(step: Step): StepSummary = StepSummary(step.id, step.status, step.breakpoint)

    given Eq[StepSummary] = Eq.by(x => (x.id, x.status, x.breakpoint))
  }

  final case class State(
    tableState:               TableState[TableColumn],
    breakpointHover:          Option[StepId],
    selected:                 Option[StepId],
    scrollCount:              Int,
    scrollBarWidth:           Double,
    prevStepSummaries:        List[StepSummary],
    prevSelectedStep:         Option[StepId],
    prevSequenceState:        Option[SequenceState],
    prevRunning:              Option[RunningStep],
    prevResourceRunRequested: SortedMap[Resource, ResourceRunOperation],
    recomputeFrom:            Option[StepId],           // Min row to recompute heights from
    runNewStep:               Option[(StepId, Boolean)] // (New running step, scroll to it?)
  ) {

    def visibleCols(p: Props): State =
      Focus[State](_.columns).replace(NonEmptyList.fromListUnsafe(p.shownForInstrument))(this)

    def recalculateWidths(p: Props, size: Size): TableState[TableColumn] =
      tableState.recalculateWidths(size, p.visibleColumns, p.columnWidths)

  }

  object State {
    // Lenses
    val columns: Lens[State, NonEmptyList[ColumnMeta[TableColumn]]] =
      tableState.andThen(TableState.columns[TableColumn])

    val scrollPosition: Lens[State, JsNumber] =
      tableState.andThen(TableState.scrollPosition[TableColumn])

    val userModified: Lens[State, UserModified] =
      tableState.andThen(TableState.userModified[TableColumn])

    val InitialTableState: TableState[TableColumn] =
      TableState(NotModified, 0, all)

    val InitialState: State = State(InitialTableState,
                                    None,
                                    None,
                                    0,
                                    0.0,
                                    List.empty,
                                    None,
                                    None,
                                    None,
                                    SortedMap.empty,
                                    None,
                                    None
    )
  }

  given Reusability[Props] =
    Reusability.by(x =>
      (x.canOperate,
       x.selectedStep,
       x.stepsList,
       x.stepsList.map(x.visibleColumnValues),
       x.tabOperations.resourceRunRequested
      )
    )

  given Reusability[TableColumn] = Reusability.byRef
  given Reusability[Double]      = Reusability.double(1.0)
  given Reusability[State]       =
    Reusability.by(x => (x.tableState, x.breakpointHover, x.selected, x.scrollBarWidth))

  private def canSetBreakpoint(step: Step, l: List[Step]): Boolean = step.canSetBreakpoint(l)

  def stepControlRenderer(
    f:                       StepsTableFocus,
    $                      : Scope,
    rowBreakpointHoverOnCB:  StepId => Callback,
    rowBreakpointHoverOffCB: StepId => Callback,
    recomputeHeightsCB:      StepId => Callback
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) =>
      StepToolsCell(
        $.props.status,
        row.step,
        rowHeight($)(row.step.id),
        $.props.rowDetailsHeight(row.step, $.state.selected),
        f.isPreview,
        f.nextStepToRun,
        f.idName,
        canSetBreakpoint(row.step, f.steps),
        rowBreakpointHoverOnCB,
        rowBreakpointHoverOffCB,
        recomputeHeightsCB
      )

  val stepIdRenderer: CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, _, index) => StepIdCell(index + 1)

  def settingsControlRenderer(
    p: Props,
    f: StepsTableFocus
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, stepRow, _) =>
      SettingsCell(p.router, f.instrument, f.idName.id, stepRow.step.id, p.isPreview)

  def stepProgressRenderer(
    f:  StepsTableFocus,
    $ : Scope
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, index) =>
      StepProgressCell(
        $.props.status,
        StepStateSummary(row.step,
                         index,
                         f.idName.id,
                         f.instrument,
                         $.props.tabOperations,
                         f.state
        ),
        $.state.selected,
        $.props.isPreview
      )

  def stepStatusRenderer(
    offsetsDisplay: OffsetsDisplay
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) => OffsetsDisplayCell(offsetsDisplay, row.step)

  def stepItemRenderer(
    f: Step => Option[String]
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) => StepItemCell(f(row.step))

  private def stepItemRendererS(f: Step => Option[String]) =
    stepItemRenderer(f(_).map(_.sentenceCase))

  def stepExposureRenderer(
    i: Instrument
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) => ExposureTimeCell(row.step, i)

  def stepFPURenderer(
    i: Instrument
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) => {
      val fpu = row.step
        .fpu(i)
        .orElse(row.step.fpuOrMask(i).map(_.sentenceCase))
      StepItemCell(fpu)
    }

  def stepObjectTypeRenderer(
    i:    Instrument,
    size: SSize
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) => ObjectTypeCell(i, row.step, size)

  private val stepRowStyle: Step => Css = {
    case s if s.hasError                       => ObserveStyles.rowError
    case s if s.status === StepState.Running   => ObserveStyles.rowWarning
    case s if s.status === StepState.Paused    => ObserveStyles.rowWarning
    case s if s.status === StepState.Completed => ObserveStyles.rowDone
    case s if s.status === StepState.Skipped   => ObserveStyles.rowActive
    case s if s.status === StepState.Aborted   => ObserveStyles.rowError
    case s if s.isFinished                     => ObserveStyles.rowDone
    case _                                     => ObserveStyles.stepRow
  }

  private val breakpointRowStyle: Step => Css = {
    case s if s.isFinished => ObserveStyles.stepDoneWithBreakpoint
    case _                 => ObserveStyles.stepRowWithBreakpoint
  }

  private val breakpointAndControlRowStyle: Step => Css = {
    case s if s.isFinished => ObserveStyles.stepDoneWithBreakpointAndControl
    case _                 => ObserveStyles.stepRowWithBreakpointAndControl
  }

  /**
   * Class for the row depends on properties
   */
  def rowClassName($ : Scope)(i: Int): String = (
    if (i === HeaderRow)
      ObserveStyles.headerRowStyle
    else
      ($.props.rowGetterByIndex(i), $.props.canSetBreakpoint, $.state.breakpointHover) match {
        case (StepRow(s), true, _) if s.breakpoint                   =>
          // row with control elements and breakpoint
          breakpointAndControlRowStyle($.props.rowGetterByIndex(i - 1).step) |+| stepRowStyle(s)
        case (StepRow(s), false, _) if s.breakpoint                  =>
          // row with breakpoint
          breakpointRowStyle($.props.rowGetterByIndex(i - 1).step) |+| stepRowStyle(s)
        case (StepRow(s), _, Some(k)) if !s.breakpoint && s.id === k =>
          // row with breakpoint and hover
          ObserveStyles.stepRowWithBreakpointHover |+| stepRowStyle(s)
        case (StepRow(s), _, _)                                      =>
          // Regular row
          ObserveStyles.stepRow |+| stepRowStyle(s)
        case _                                                       =>
          // Regular row
          ObserveStyles.stepRow
      }
  ).htmlClass

  /**
   * Height depending if we use offsets
   */
  def baseHeight(p: Props): Int =
    if (p.showOffsets)
      HeightWithOffsets
    else
      ObserveStyles.rowHeight

  /**
   * Calculates the row height depending on conditions
   */

  private def _rowHeight($ : Scope)(row: StepRow): Int =
    row match {
      case StepRow(s) if $.props.showRowDetails(s, $.state.selected)    =>
        // Selected
        ObserveStyles.runningRowHeight + $.props.rowDetailsHeight(s, $.state.selected)
      case StepRow(s) if s.status === StepState.Running && s.breakpoint =>
        // Row running with a breakpoint set
        ObserveStyles.runningRowHeight + BreakpointLineHeight
      case StepRow(s) if s.status === StepState.Running                 =>
        // Row running
        ObserveStyles.runningRowHeight
      case StepRow(s)
          if $.state.selected.exists(_ === s.id) && !s.skip &&
            ($.props.canControlSubsystems(s.id) || $.props.subsystemsNotIdle(s.id)) =>
        // Selected
        ObserveStyles.runningRowHeight
      case StepRow(s) if s.breakpoint                                   =>
        // Row with a breakpoint set
        baseHeight($.props) + BreakpointLineHeight
      case _                                                            =>
        // default row
        baseHeight($.props)
    }

  def rowHeight($ : Scope)(stepId: StepId): Int = _rowHeight($)($.props.rowGetter(stepId))

  def rowHeightByIndex($ : Scope)(i: Int): Int = _rowHeight($)($.props.rowGetterByIndex(i))

  val columnClassName: TableColumn => Option[Css] = {
    case ControlColumn                => ObserveStyles.controlCellRow.some
    case StepColumn | ExecutionColumn => ObserveStyles.paddedStepRow.some
    case ObservingModeColumn | ExposureColumn | DisperserColumn | FilterColumn | FPUColumn |
        CameraColumn | ObjectTypeColumn | DeckerColumn | ReadModeColumn | ImagingMirrorColumn =>
      ObserveStyles.centeredCell.some
    case SettingsColumn               => ObserveStyles.settingsCellRow.some
    case _                            => none
  }

  val headerClassName: TableColumn => Option[Css] = {
    case ControlColumn  =>
      (ObserveStyles.centeredCell |+| ObserveStyles.tableHeaderIcons).some
    case SettingsColumn =>
      (ObserveStyles.centeredCell |+| ObserveStyles.tableHeaderIcons).some
    case _              => none
  }

  val controlHeaderRenderer: HeaderRenderer[js.Object] = (_, _, _, _, _, _) =>
    <.span(
      ^.title := "Control",
      IconSettings
    )

  val settingsHeaderRenderer: HeaderRenderer[js.Object] = (_, _, _, _, _, _) =>
    <.span(
      ^.title := "Settings",
      IconBrowser
    )

  private val fixedHeaderRenderer: TableColumn => HeaderRenderer[js.Object] = {
    case ControlColumn  => controlHeaderRenderer
    case SettingsColumn => settingsHeaderRenderer
    case _              => defaultHeaderRendererS
  }

  private def columnCellRenderer(
    $ : Scope,
    c:  TableColumn
  ): CellRenderer[js.Object, js.Object, StepRow] = {
    val optR = c match {
      case ControlColumn       =>
        $.props.steps.map(
          stepControlRenderer(_,
                              $,
                              rowBreakpointHoverOnCB($),
                              rowBreakpointHoverOffCB($),
                              recomputeRowHeightsCB($.props)
          )
        )
      case StepColumn          => stepIdRenderer.some
      case ExecutionColumn     => $.props.steps.map(stepProgressRenderer(_, $))
      case OffsetColumn        => stepStatusRenderer($.props.offsetsDisplay).some
      case ObservingModeColumn => stepItemRenderer(_.observingMode).some
      case ExposureColumn      => $.props.instrument.map(stepExposureRenderer)
      case DisperserColumn     => $.props.instrument.map(i => stepItemRenderer(_.disperser(i)))
      case FilterColumn        => $.props.instrument.map(i => stepItemRenderer(_.filter(i)))
      case FPUColumn           => $.props.instrument.map(i => stepFPURenderer(i))
      case CameraColumn        => $.props.instrument.map(i => stepItemRenderer(_.cameraName(i)))
      case ObjectTypeColumn    => $.props.instrument.map(stepObjectTypeRenderer(_, Small))
      case SettingsColumn      => $.props.steps.map(p => settingsControlRenderer($.props, p))
      case ReadModeColumn      => stepItemRendererS(_.readMode).some
      case DeckerColumn        => stepItemRendererS(_.deckerName).some
      case ImagingMirrorColumn => stepItemRendererS(_.imagingMirrorName).some
      case _                   => none
    }
    optR.getOrElse(defaultCellRendererS)
  }

  // Columns for the table
  private def colBuilder(
    $   : Scope,
    size: Size
  ): ColumnRenderArgs[TableColumn] => Table.ColumnArg =
    tb => {
      def updateState(s: TableState[TableColumn]): Callback =
        ($.modState(Focus[State](_.tableState).replace(s)) *> $.props.obsIdName
          .map(i => ObserveCircuit.dispatchCB(UpdateStepTableState(i.id, s)))
          .getOrEmpty).when_(size.width.toInt > 0)

      tb match {
        case ColumnRenderArgs(meta, _, width, true)  =>
          Column(
            Column.propsNoFlex(
              width = width,
              dataKey = meta.name,
              label = meta.label,
              headerRenderer = resizableHeaderRenderer(
                $.state.tableState
                  .resizeColumn(meta.column,
                                size,
                                updateState,
                                $.props.visibleColumns,
                                $.props.columnWidths
                  )
              ),
              headerClassName = headerClassName(meta.column).foldMap(_.htmlClass),
              cellRenderer = columnCellRenderer($, meta.column),
              className = columnClassName(meta.column).foldMap(_.htmlClass)
            )
          )
        case ColumnRenderArgs(meta, _, width, false) =>
          Column(
            Column.propsNoFlex(
              width = width,
              dataKey = meta.name,
              label = meta.label,
              headerRenderer = fixedHeaderRenderer(meta.column),
              headerClassName = headerClassName(meta.column).foldMap(_.htmlClass),
              cellRenderer = columnCellRenderer($, meta.column),
              className = columnClassName(meta.column).foldMap(_.htmlClass)
            )
          )
      }
    }

  // Called when the scroll position changes
  // This is fairly convoluted as the table always calls this at start when
  // the table is rendered with position 0
  // Aditionally if we programatically scroll to a position we get another call
  // Only after that we assume scroll is user initatied
  def updateScrollPosition($ : Scope, pos: JsNumber): Callback = {
    val posMod            = $.setStateL(State.scrollPosition)(pos)
    // This is done to ignore the scrolls made automatically upon startup
    val hasScrolledBefore = $.state.scrollCount > 2
    val modMod            = $.setStateL(State.userModified)(IsModified).when_(hasScrolledBefore)
    val scrollCountMods   = $.modStateL(State.scrollCount)(_ + 1)
    // Separately calculate the state to send upstream
    val newTs             =
      if (hasScrolledBefore)
        (Focus[State](_.userModified).replace(IsModified) >>> Focus[State](_.scrollPosition)
          .replace(pos))($.state)
      else
        Focus[State](_.scrollPosition).replace(pos)($.state)
    val posDiff           = abs(pos.toDouble - $.state.tableState.scrollPosition.toDouble)
    // Always update the position and counts, but the modification flag only if
    // we are sure the user did scroll manually
    (posMod *>
      scrollCountMods *>
      modMod *>
      // And silently update the model
      $.props.obsIdName
        .map(idName => ObserveCircuit.dispatchCB(UpdateStepTableState(idName.id, newTs.tableState)))
        .getOrEmpty)
      .when_(posDiff > 1) // Only update the state if the change is significant
  }

  def startScrollTop(state: State): js.UndefOr[JsNumber] =
    if (state.tableState.isModified)
      state.tableState.scrollPosition
    else
      js.undefined

  def startScrollToIndex($ : Scope): Int =
    if ($.state.tableState.isModified)
      -1
    else
      (
        for {
          next <- $.props.nextStepToRun
          idx  <- index($.props.stepsList, next)
        } yield max(0, idx - 1)
      ).getOrElse(0)

  // Single click puts the row as selected
  def singleClick($ : Scope)(i: Int): Callback =
    ($.props.obsIdName, $.props.steps.flatMap(_.steps.lift(i)).map(_.id)).mapN {
      case (idName, stepId) =>
        (ObserveCircuit
          .dispatchCB(UpdateSelectedStep(idName.id, stepId)) *>
          ObserveCircuit
            .dispatchCB(ClearAllResourceOperations(idName.id)) *>
          $.modState(Focus[State](_.selected).replace(Some(stepId))) *>
          recomputeRowHeightsCB($.props)(stepId))
          .when_(
            $.props.stepSelectionAllowed(stepId) && State.selected.get($.state).forall(_ =!= stepId)
          )
    }.getOrEmpty

  def stepsTableProps($ : Scope)(size: Size): Table.Props =
    Table.props(
      disableHeader = false,
      noRowsRenderer = () =>
        <.div(
          ^.cls    := "ui center aligned segment noRows",
          ^.height := size.height.toInt.px,
          "No Steps"
        ),
      overscanRowCount = ObserveStyles.overscanRowCount,
      height = max(1, size.height.toInt),
      rowCount = $.props.rowCount,
      rowHeight = rowHeightByIndex($) _,
      rowClassName = rowClassName($) _,
      width = max(1, size.width.toInt),
      rowGetter = $.props.rowGetterByIndex _,
      scrollToIndex = startScrollToIndex($),
      scrollTop = startScrollTop($.state),
      onRowClick = singleClick($),
      onScroll = (a, _, pos) => updateScrollPosition($, pos).when_(a.toDouble > 0),
      scrollToAlignment = ScrollToAlignment.Center,
      headerClassName = ObserveStyles.tableHeader.htmlClass,
      headerHeight = ObserveStyles.headerHeight,
      rowRenderer = stepsRowRenderer($.props, $.state.selected)
    )

  private def index(l: List[Step], stepId: StepId): Option[Int] =
    l.zipWithIndex.find(_._1.id === stepId).map(_._2)

  // We want clicks to be processed only if the click is not on the first row with the breakpoint/skip controls
  private def allowedClick(
    p:          Props,
    stepId:     StepId,
    onRowClick: Option[OnRowClick]
  )(e: ReactMouseEvent): Callback =
    // If alt is pressed or middle button flip the breakpoint
    if (e.altKey || e.button === MiddleButton)
      e.stopPropagationCB *> e.preventDefaultCB >>
        (p.obsIdName, p.stepsList.dropWhile(_.id =!= stepId).drop(1).headOption)
          .mapN((oid, step) =>
            ObserveCircuit
              .dispatchCB(FlipBreakpointStep(oid, step))
              .when_(canSetBreakpoint(step, p.stepsList))
          )
          .getOrEmpty
          .when_(p.canSetBreakpoint)
    else
      onRowClick
        .filter(_ => e.clientX > ControlWidth)
        .flatMap(index(p.stepsList, stepId).map)
        .getOrEmpty

  private def stepsRowRenderer(p: Props, selected: Option[StepId]) =
    (
      className:        String,
      columns:          Array[VdomNode],
      index:            Int,
      _:                Boolean,
      key:              String,
      _:                StepRow,
      onRowClick:       Option[OnRowClick],
      onRowDoubleClick: Option[OnRowClick],
      _:                Option[OnRowClick],
      _:                Option[OnRowClick],
      _:                Option[OnRowClick],
      style:            Style
    ) => {
      val stepId = p.stepsList.lift(index).map(_.id)
      (p.rowGetterByIndex(index), stepId) match {
        case (StepRow(s), Some(sid)) if p.showRowDetails(s, selected) && sid === s.id =>
          <.div(
            ^.key   := key,
            ^.style := Style.toJsObject(style),
            ObserveStyles.expandedRunningRow,
            ObserveStyles.stepRow,
            <.div(
              ^.cls    := className,
              ^.key    := s"$key-top",
              ObserveStyles.expandedTopRow,
              ^.height := ObserveStyles.runningRowHeight.px,
              ^.onMouseDown ==> allowedClick(p, sid, onRowClick),
              ^.onDoubleClick -->? onRowDoubleClick.map(h => h(index)),
              columns.toTagMod
            ),
            p.stepSummary(s).whenDefined { s =>
              val rowComponents: List[StepStateSummary => ReactProps[_]] =
                if (s.isAC)
                  List(AlignAndCalibProgress.apply)
                else if (s.isNS)
                  List(NodAndShuffleCycleRow(p.status), NodAndShuffleNodRow(p.status))
                else
                  List.empty

              rowComponents.zipWithIndex.toTagMod { case (rowComponent, rowIdx) =>
                <.div(
                  ^.key    := s"$key-subRow-$rowIdx",
                  ObserveStyles.expandedBottomRow,
                  ObserveStyles.tableDetailRow,
                  ObserveStyles.tableDetailRowWithGutter
                    .when(p.status.isLogged)
                    .unless(p.isPreview),
                  ^.height := ObserveStyles.runningRowHeight.px,
                  ^.onMouseDown ==> allowedClick(p, sid, onRowClick),
                  ^.onDoubleClick -->? onRowDoubleClick.map(h => h(index)),
                  rowComponent(s)
                )
              }
            }
          )
        case (_, Some(sid))                                                           =>
          <.div(
            ^.cls   := className,
            ^.key   := key,
            ^.role  := "row",
            ^.style := Style.toJsObject(style),
            ^.onMouseDown ==> allowedClick(p, sid, onRowClick),
            ^.onDoubleClick -->? onRowDoubleClick.map(h => h(index)),
            columns.toTagMod
          )
        case _                                                                        =>
          <.div(
            ^.cls   := className,
            ^.key   := key,
            ^.role  := "row",
            ^.style := Style.toJsObject(style),
            ^.onDoubleClick -->? onRowDoubleClick.map(h => h(index)),
            columns.toTagMod
          )
      }
    }

  // Create a ref
  private val ref = Ref.toJsComponent(Table.component)

  private def recomputeRowHeightsCB(props: Props)(stepId: StepId): Callback = index(props.stepsList,
                                                                                    stepId
  ).map(i => ref.get.asCBO.flatMapCB(_.raw.recomputeRowsHeightsCB(i)).toCallback).getOrEmpty

  def rowBreakpointHoverOnCB($ : Scope)(stepId: StepId): Callback =
    (if ($.props.rowGetter(stepId).step.breakpoint)
       $.modState(Focus[State](_.breakpointHover).replace(None))
     else $.modState(Focus[State](_.breakpointHover).replace(stepId.some))) *>
      recomputeRowHeightsCB($.props)(stepId)

  def rowBreakpointHoverOffCB($ : Scope)(stepId: StepId): Callback =
    $.modState(Focus[State](_.breakpointHover).replace(None)) *> recomputeRowHeightsCB($.props)(
      stepId
    )

  private def scrollTo(props: Props)(stepId: StepId): Callback = index(props.stepsList, stepId)
    .map(i => ref.get.asCBO.flatMapCB(_.raw.scrollToRowCB(i)).toCallback)
    .getOrEmpty

  // Scroll to pos on run requested
  private def scrollToCB(cur: Props, next: Props): Callback =
    next.nextStepToRun
      .map(scrollTo(next))
      .getOrEmpty
      .when_(
        cur.tabOperations.runRequested =!= next.tabOperations.runRequested
          && next.tabOperations.runRequested === RunOperation.RunInFlight
      )

  private val computeScrollBarWidth: CallbackTo[Double] =
    ref.get.asCBO.map(_.getDOMNode.toHtml).asCallback.map {
      _.flatten
        .flatMap { tableNode =>
          // Table has a Grid inside, which is the one actually showing the scroll bar.
          Option(tableNode.querySelector(".ReactVirtualized__Table__Grid")).map {
            case gridNode: HTMLElement => gridNode.offsetWidth - gridNode.clientWidth
            case _                     => ObserveStyles.DefaultScrollBarWidth
          }
        }
        .getOrElse(ObserveStyles.DefaultScrollBarWidth)
    }

  // Wire it up from VDOM
  def render($ : Scope): VdomElement =
    TableContainer(
      $.props.hasControls,
      size => {
        val areaSize = Size(size.height, size.width.toInt - $.state.scrollBarWidth)
        val ts       =
          $.state.tableState
            .columnBuilder(areaSize, colBuilder($, areaSize), $.props.columnWidths)
            .map(_.vdomElement)

        if (size.width.toInt > 0)
          ref
            .component(stepsTableProps($)(size))(ts: _*)
            .vdomElement
        else
          <.div()
      },
      onResize = s =>
        $.modStateL(State.tableState)(
          _.recalculateWidths(s, $.props.visibleColumns, $.props.columnWidths)
        )
    )

  def initialState(p: Props): State =
    (
      Focus[State](_.tableState).replace(p.tableState) >>>
        Focus[State](_.selected).replace(p.selectedStep) >>>
        Focus[State](_.prevStepSummaries).replace(p.stepsList.map(StepSummary.fromStep)) >>>
        Focus[State](_.prevSelectedStep).replace(p.selectedStep) >>>
        Focus[State](_.prevSequenceState).replace(p.sequenceState) >>>
        Focus[State](_.prevRunning).replace(p.runningStep) >>>
        Focus[State](_.prevResourceRunRequested).replace(p.tabOperations.resourceRunRequested)
    )(State.InitialState)

  private def updateStep(obsId: Observation.Id, i: StepId): Callback =
    ObserveCircuit.dispatchCB(UpdateSelectedStep(obsId, i))

  private def selectStep[A](
    curStep:      Option[RunningStep],
    nextStep:     Option[RunningStep],
    curSeqState:  Option[SequenceState],
    nextSeqState: Option[SequenceState]
  )(f: Option[(StepId, Boolean)] => A): A =
    (curStep, nextStep) match {
      case (Some(RunningStep(Some(i), _, _)), None)                                        =>
        // This happens when a sequence stops, e.g. with a pause
        f((i, false).some)
      case (Some(RunningStep(Some(i), _, _)), Some(RunningStep(Some(j), _, _))) if i =!= j =>
        // This happens when we keep running and move to the next step
        // If the user hasn't scrolled we'll focus on the next step
        f((j, true).some)
      case (_, Some(RunningStep(Some(j), _, _)))
          if curSeqState =!= nextSeqState && nextSeqState.exists(_.isRunning) =>
        // When we start running
        f((j, false).some)
      case _                                                                               =>
        f(none)
    }

  def deriveNewState(p: Props, s: State): State = {
    // Collect StepIds to recompute heights starting from the minimum needed
    // Collect State mods to store previous properties

    // If status or breakpoint changes, recompute height
    val steps: Option[(StepId, State => State)] = {
      val propsStepSummaries = p.stepsList.map(StepSummary.fromStep)
      propsStepSummaries
        .zip(s.prevStepSummaries)
        .collectFirst {
          case (cur, prev) if cur.status =!= prev.status         => cur.id
          case (cur, prev) if cur.breakpoint =!= prev.breakpoint => cur.id
        }
        .map(stepId => (stepId, Focus[State](_.prevStepSummaries).replace(propsStepSummaries)))
    }

    // If the selected step changes recompute height
    val selected: Option[(StepId, State => State)] = for {
      sel   <- p.selectedStep
      prev  <- s.prevSelectedStep
      first <- p.stepsList.find(x => x.id === sel || x.id === prev)
      if p.selectedStep =!= s.prevSelectedStep
    } yield (first.id,
             State.prevSelectedStep
               .replace(p.selectedStep) >>> Focus[State](_.selected).replace(p.selectedStep)
    )

    // If the step is running recompute height
    val runRequested: Option[(StepId, State => State)] =
      p.selectedStep
        .filter(_ => s.prevResourceRunRequested =!= p.tabOperations.resourceRunRequested)
        .map(stepId =>
          (stepId,
           Focus[State](_.prevResourceRunRequested).replace(p.tabOperations.resourceRunRequested)
          )
        )

    // Recompute selected step
    val runningSelectedStepUpdate: Option[State => State] =
      selectStep[Option[State => State]](s.prevRunning,
                                         p.runningStep,
                                         s.prevSequenceState,
                                         p.sequenceState
      )(
        _.map { case (stepId, scroll) =>
          Function.chain(
            State.selected
              .replace(stepId.some)
              .some
              .filter(_ => p.canControlSubsystems(stepId))
              .toList :+
              Focus[State](_.runNewStep).replace((stepId, scroll).some)
          )
        }
      )

    // Update previous properties
    val runningStepUpdate: Option[State => State] =
      p.runningStep.some
        .filter(_ =!= s.prevRunning)
        .map(Focus[State](_.prevRunning).replace)

    val sequenceStateUpdate: Option[State => State] =
      p.sequenceState.some
        .filter(_ =!= s.prevSequenceState)
        .map(Focus[State](_.prevSequenceState).replace)

    val (minSteps, stateMods) = List(steps, selected, runRequested).flatten.unzip

    Function.chain(
      (stateMods :+ State.recomputeFrom
        .replace(p.stepsList.find(minSteps.contains).map(_.id))) ::: List(
        runningSelectedStepUpdate,
        runningStepUpdate,
        sequenceStateUpdate
      ).flatten
    )(s)
  }

  def didUpdate($ : DidUpdate): Callback =
    if ($.prevState.scrollBarWidth =!= $.currentState.scrollBarWidth)
      Callback.empty // We were just updating the srollbar width, don't recompute everything again.
    else {
      val prevP = $.prevProps
      val currP = $.currentProps

      scrollToCB(prevP, currP) *>
        currP.obsIdName
          .zip($.currentState.runNewStep)
          .map { case (obsIdName, (stepId, scroll)) =>
            updateStep(obsIdName.id, stepId) *> scrollTo(currP)(stepId).when(scroll).void *>
              $.setStateL(State.runNewStep)(
                none
              ) // This does't cause a loop because runNewStep is not in State reusability.
          }
          .getOrEmpty *>
        $.currentState.recomputeFrom.map(recomputeRowHeightsCB(currP)).getOrEmpty *>
        $.setStateL(State.recomputeFrom)(
          none
        ) *> // This does't cause a loop because recomputeFrom is not in State reusability.
        computeScrollBarWidth >>= { sw =>
        $.setStateL(State.scrollBarWidth)(sw).when(sw =!= $.currentState.scrollBarWidth).void
      }
    }

  protected val component: Component[Props, State, NoSnapshot, CtorType.Props] = ScalaComponent
    .builder[Props]
    .initialStateFromProps(initialState)
    .render(render)
    .getDerivedStateFromProps(deriveNewState _)
    .componentDidUpdate(didUpdate)
    .configure(Reusability.shouldComponentUpdate)
    .build
}
