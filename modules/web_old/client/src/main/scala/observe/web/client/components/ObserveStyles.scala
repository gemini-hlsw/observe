// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import lucuma.react.common.style.*

/**
 * Custom CSS for the Observe UI
 */
object ObserveStyles {

  val headerHeight: Int             = 33
  val rowHeight: Int                = 30
  val overscanRowCount: Int         = 10
  val runningRowHeight: Int         = 60
  val runningBottomRowHeight: Int   = 60
  val TableBorderWidth: Double      = 1.0
  val TableRightPadding: Int        = 13
  val DefaultScrollBarWidth: Double = 15.0

  val DisabledSubsystem: Css =
    Css("ObserveStyles-disabledSubsystems")

  val SubsystemsForm: Css =
    Css("ObserveStyles-subsystemsForm")

  val TabControls: Css =
    Css("ObserveStyles-tabControls")

  val TabTable: Css =
    Css("ObserveStyles-tabTable")

  val ConfigTableControls: Css =
    Css("ObserveStyles-configTableControls")

  val SequencesControl: Css =
    Css("ObserveStyles-sequencesControl")

  val MainUI: Css =
    Css("ObserveStyles-mainUI")

  val Footer: Css =
    Css("ObserveStyles-footer")

  val LoginError: Css =
    Css("ObserveStyles-loginError")

  val ConfirmLine: Css =
    Css("ObserveStyles-confirmLine")

  val Toast: Css =
    Css("ObserveStyles-toast")

  val tableDetailRow: Css =
    Css("ObserveStyles-tableDetailRow")

  val tableDetailRowWithGutter: Css =
    Css("ObserveStyles-tableDetailRowWithGutter")

  val expandedRunningRow: Css =
    Css("ObserveStyles-expandedRunningRow")

  val expandedTopRow: Css =
    Css("ObserveStyles-expandedTopRow")

  val expandedBottomRow: Css =
    Css("ObserveStyles-expandedBottomRow")

  val activeGuide: Css =
    Css("ObserveStyles-activeGuide")

  val LoadButton: Css =
    Css("ObserveStyles-loadButton")

  val activeInstrumentLabel: Css =
    Css("ObserveStyles-activeInstrumentLabel")

  val activeResourceLabel: Css =
    Css("ObserveStyles-activeResourceLabel")

  val resourceLabels: Css =
    Css("ObserveStyles-resourceLabels")

  val tab: Css = Css("ObserveStyles-tab")

  val TabLabel: Css = Css("ObserveStyles-tabLabel")

  val PreviewTab: Css =
    Css("ObserveStyles-previewTabLabel")

  val LoadedTab: Css =
    Css("ObserveStyles-loadedTab")

  val TabTitleRow: Css =
    Css("ObserveStyles-tabTitleRow")

  val ResourceLabels: Css =
    Css("ObserveStyles-resourceLabels")

  val previewTabLoadButton: Css =
    Css("ObserveStyles-previewTabLoadButton")

  val resourcesTabLabels: Css =
    Css("ObserveStyles-resourcesTabLabels")

  val activeTabContent: Css =
    Css("ObserveStyles-activeTabContent")

  val inactiveTabContent: Css =
    Css("ObserveStyles-inactiveTabContent")

  val errorTab: Css = Css("ObserveStyles-errorTab")

  val fieldsNoBottom: Css = Css("ObserveStyles-fieldsNoBottom")

  val SequenceInfo: Css = Css("ObserveStyles-sequenceInfo")

  val headerSideBarStyle: Css =
    Css("ObserveStyles-headerSidebarStyle")

  val emptyInstrumentTab: Css =
    Css("ObserveStyles-emptyInstrumentTab")

  val emptyInstrumentTabLogShown: Css =
    Css("ObserveStyles-emptyInstrumentTabLogShown")

  val emptyInstrumentTabLogHidden: Css =
    Css("ObserveStyles-emptyInstrumentTabLogHidden")

  val tabSegment: Css = Css("ObserveStyles-tabSegment")

  val SequenceControlForm: Css = Css("ObserveStyles-sequenceControlForm")

  val SequenceControlButtons: Css = Css("ObserveStyles-sequenceControlButtons")

  // Sometimes we need to manually add css
  val item: Css = Css("item")

  val ui: Css = Css("ui")

  val header: Css = Css("header")

  // Media queries to hide/display items for mobile
  val notInMobile: Css = Css("ObserveStyles-notInMobile")

  val onlyMobile: Css = Css("ObserveStyles-onlyMobile")

  val errorText: Css = Css("ObserveStyles-errorText")

  val noRowsSegment: Css = Css("ObserveStyles-noRowsSegment")

  val logSegment: Css = Css("ObserveStyles-logSegment")

  val logSecondarySegment: Css =
    Css("ObserveStyles-logSecondarySegment")

  val logControlRow: Css = Css("ObserveStyles-logControlRow")

  val logTableRow: Css = Css("ObserveStyles-logTableRow")

  val logTable: Css = Css("ObserveStyles-logTable")

  val selectorFields: Css = Css("ObserveStyles-selectorFields")

  val logLevelBox: Css = Css("ObserveStyles-logLevelBox")

  val queueTextColumn: Css =
    Css("ObserveStyles-queueTextColumn")

  val queueText: Css = Css("ObserveStyles-queueText")

  val queueIconColumn: Css =
    Css("ObserveStyles-queueIconColumn")

  val queueListPane: Css = Css("ObserveStyles-queueListPane")

  val labelPointer: Css = Css("ObserveStyles-labelPointer")

  val shorterRow: Css = Css("ObserveStyles-shorterRow")

  val titleRow: Css = Css("ObserveStyles-titleRow")

  val blinking: Css = Css("ObserveStyles-blinking")

  val queueAreaRow: Css = Css("ObserveStyles-queueAreaRow")

  val queueArea: Css = Css("ObserveStyles-queueArea")

  val headerSideBarArea: Css =
    Css("ObserveStyles-headerSidebarArea")

  val logArea: Css = Css("ObserveStyles-logArea")

  val lowerRow: Css = Css("ObserveStyles-lowerRow")

  val observerField: Css = Css("ObserveStyles-observerField")

  val shorterFields: Css = Css("ObserveStyles-shorterFields")

  val configLabel: Css = Css("ObserveStyles-configLabel")

  val observationProgressRow: Css =
    Css("ObserveStyles-observationProgressRow")

  val observationProgressBar: Css =
    Css("ObserveStyles-observationProgressBar")

  val observationBar: Css = Css("ObserveStyles-observationBar")

  val observationLabel: Css =
    Css("ObserveStyles-observationLabel")

  val guidingCell: Css = Css("ObserveStyles-guidingCell")

  val offsetsBlock: Css = Css("ObserveStyles-offsetsBlock")

  val offsetsNodLabel: Css = Css("ObserveStyles-offsetsNodLabel")

  val offsetComponent: Css = Css("ObserveStyles-offsetComponent")

  val configuringRow: Css = Css("ObserveStyles-configuringRow")

  val specialStateLabel: Css = Css("ObserveStyles-specialStateLabel")

  val progressMessage: Css = Css("ObserveStyles-progressMessage")

  val subsystems: Css = Css("ObserveStyles-subsystems")

  val componentLabel: Css = Css("ObserveStyles-componentLabel")

  val paddedStepRow: Css = Css("ObserveStyles-paddedStepRow")

  val stepRow: Css = Css("ObserveStyles-stepRow")

  val observeConfig: Css = Css("ObserveStyles-observeConfig")

  val headerRowStyle: Css = Css("ObserveStyles-headerRowStyle")

  val infoLog: Css = Css("ObserveStyles-infoLog")

  val errorLog: Css = Css("ObserveStyles-errorLog")

  val warningLog: Css = Css("ObserveStyles-warningLog")

  // Row styles taken from sematic ui tables
  val rowPositive: Css = Css("ObserveStyles-rowPositive")

  val rowWarning: Css = Css("ObserveStyles-rowWarning")

  val rowActive: Css = Css("ObserveStyles-rowActive")

  val rowNegative: Css = Css("ObserveStyles-rowNegative")

  val rowError: Css = Css("ObserveStyles-rowError")

  val rowDisabled: Css = Css("ObserveStyles-rowDisabled")

  val rowDone: Css = Css("ObserveStyles-rowDone")

  val rowNone: Css = Css.Empty

  val stepRowWithBreakpoint: Css =
    Css("ObserveStyles-stepRowWithBreakpoint")

  val stepDoneWithBreakpoint: Css =
    Css("ObserveStyles-stepDoneWithBreakpoint")

  val stepRowWithBreakpointHover: Css =
    Css("ObserveStyles-stepRowWithBreakpointHover")

  val stepRowWithBreakpointAndControl: Css =
    Css("ObserveStyles-stepRowWithBreakpointAndControl")

  val stepDoneWithBreakpointAndControl: Css =
    Css("ObserveStyles-stepDoneWithBreakpointAndControl")

  val centeredCell: Css = Css("ObserveStyles-centeredCell")

  val fullCell: Css = Css("ObserveStyles-fullCell")

  val tableHeaderIcons: Css =
    Css("ObserveStyles-tableHeaderIcons")

  val buttonsRow: Css = Css("ObserveStyles-buttonsRow")

  val gutterCell: Css = Css("ObserveStyles-gutterCell")

  val controlCell: Css = Css("ObserveStyles-controlCell")

  val breakPointHandleOn: Css =
    Css("ObserveStyles-breakPointHandleOn")

  val breakPointHandleOff: Css =
    Css("ObserveStyles-breakPointHandleOff")

  val skipHandleHeight: Int = 13

  val skipHandle: Css = Css("ObserveStyles-skipHandle")

  val runningIconCell: Css =
    Css("ObserveStyles-runningIconCell")

  val completedIconCell: Css =
    Css("ObserveStyles-completedIconCell")

  val errorCell: Css = Css("ObserveStyles-errorCell")

  val skippedIconCell: Css =
    Css("ObserveStyles-skippedIconCell")

  val iconCell: Css = Css("ObserveStyles-iconCell")

  val settingsCell: Css = Css("ObserveStyles-settingsCell")

  val logIconRow: Css = Css("ObserveStyles-logIconRow")

  val logIconHeader: Css = Css("ObserveStyles-logIconHeader")

  val selectedIcon: Css = Css("ObserveStyles-selectedIcon")

  val runningIcon: Css = Css("ObserveStyles-runningIcon")

  val errorIcon: Css = Css("ObserveStyles-errorIcon")

  val completedIcon: Css = Css("ObserveStyles-completedIcon")

  val breakPointOnIcon: Css =
    Css("ObserveStyles-breakPointOnIcon")

  val breakPointOffIcon: Css =
    Css("ObserveStyles-breakPointOffIcon")

  val clipboardIconDiv: Css =
    Css("ObserveStyles-clipboardIconDiv")

  val clipboardIconHeader: Css =
    Css("ObserveStyles-clipboardIconHeader")

  val tableHeader: Css = Css("ObserveStyles-tableHeader")

  val controlCellRow: Css = Css("ObserveStyles-controlCellRow")

  val settingsCellRow: Css =
    Css("ObserveStyles-settingsCellRow")

  val labelAsButton: Css = Css("ObserveStyles-labelAsButton")

  val calTableBorder: Css = Css("ObserveStyles-calTableBorder")

  val calRowBackground: Css =
    Css("ObserveStyles-calRowBackground")

  val autoMargin: Css = Css("ObserveStyles-autoMargin")

  val deletedRow: Css = Css("ObserveStyles-deletedRow")

  val noselect: Css = Css("ObserveStyles-noselect")

  val draggedRowHelper: Css =
    Css("ObserveStyles-draggedRowHelper")

  val draggableRow: Css =
    Css("ObserveStyles-draggableRow")

  val filterPane: Css =
    Css("ObserveStyles-filterPane")

  val filterActiveButton: Css =
    Css("ObserveStyles-filterActiveButton")

  val dropOnTab: Css =
    Css("ObserveStyles-dropOnTab")

  val runFrom: Css =
    Css("ObserveStyles-runFrom")

  val defaultCursor: Css =
    Css("ObserveStyles-defaultCursor")

  val dividedProgress: Css =
    Css("ObserveStyles-dividedProgress")

  val dividedProgressSectionLeft: Css =
    Css("ObserveStyles-dividedProgressSectionLeft")

  val dividedProgressSectionMiddle: Css =
    Css("ObserveStyles-dividedProgressSectionMiddle")

  val dividedProgressSectionRight: Css =
    Css("ObserveStyles-dividedProgressSectionRight")

  val dividedProgressBar: Css =
    Css("ObserveStyles-dividedProgressBar")

  val dividedProgressBarLeft: Css =
    Css("ObserveStyles-dividedProgressBarLeft")

  val dividedProgressBarMiddle: Css =
    Css("ObserveStyles-dividedProgressBarMiddle")

  val dividedProgressBarRight: Css =
    Css("ObserveStyles-dividedProgressBarRight")

  val nodAndShuffleDetailRow: Css =
    Css("ObserveStyles-nodAndShuffleDetailRow")

  val nodAndShuffleControls: Css =
    Css("ObserveStyles-nodAndShuffleControls")
}
