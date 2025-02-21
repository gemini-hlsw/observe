// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import lucuma.react.common.style.Css

object ObserveStyles:
  val OnlySmallScreens = Css("OnlySmallScreens")
  val OnlyLargeScreens = Css("OnlyLargeScreens")

  val LoginTitle: Css = Css("ObserveStyles-login-title")

  val Centered: Css       = Css("ObserveStyles-centered")
  val ComponentLabel: Css = Css("ObserveStyles-componentLabel")
  val Shrinkable: Css     = Css("ObserveStyles-shrinkable")

  // Prime components restyling
  val SequenceTabPanel: Css = Css("ObserveStyles-sequenceTabPanel")

  val TopPanel: Css              = Css("ObserveStyles-topPanel")
  val MainPanel: Css             = Css("ObserveStyles-mainPanel")
  val ActiveInstrumentLabel: Css = Css("ObserveStyles-activeInstrumentLabel")
  val LabelPointer: Css          = Css("ObserveStyles-labelPointer")
  val IdleTag: Css               = Css("ObserveStyles-idleTag")
  val RunningTag: Css            = Css("ObserveStyles-runningTag")

  val LogArea: Css = Css("ObserveStyles-logArea")
  val Footer: Css  = Css("ObserveStyles-footer")

  val ObservationArea: Css           = Css("ObserveStyles-observationArea")
  val ObservationAreaError: Css      = Css("ObserveStyles-observationAreaError")
  val SequenceTableExpandButton: Css = Css("ObservationArea-sequenceTableExpandButton")

  val ObserveTable: Css = Css("ObserveStyles-observeTable")

  val RowIdle: Css     = Css("ObserveStyles-rowIdle")
  val RowPositive: Css = Css("ObserveStyles-rowPositive")
  val RowWarning: Css  = Css("ObserveStyles-rowWarning")
  val RowActive: Css   = Css("ObserveStyles-rowActive")
  val RowNegative: Css = Css("ObserveStyles-rowNegative")
  val RowError: Css    = Css("ObserveStyles-rowError")
  val RowDisabled: Css = Css("ObserveStyles-rowDisabled")
  val RowDone: Css     = Css("ObserveStyles-rowDone")

  val AcquisitionPrompt: Css     = Css("ObserveStyles-acquisitionPrompt")
  val AcquisitionPromptMain: Css = Css("ObserveStyles-acquisitionPrompt-main")
  val AcquisitionPromptBusy: Css = Css("ObserveStyles-acquisitionPrompt-busy")

  val SessionQueue: Css   = Css("ObserveStyles-sessionQueue")
  val SessionTable: Css   = Css("ObserveStyles-sessionTable")
  val ObsClassSelect: Css = Css("ObserveStyles-obsClassSelect")
  val LoadButtonCell: Css = Css("ObserveStyles-loadButton-cell")
  val LoadButton: Css     = Css("ObserveStyles-loadButton")

  val ConfigSection: Css       = Css("ObserveStyles-ConfigSection")
  val ConditionsSection: Css   = Css("ObserveStyles-ConditionsSection")
  val ConditionsLabel: Css     = Css("ObserveStyles-ConditionsLabel")
  val NamesSection: Css        = Css("ObserveStyles-NamesSection")
  val ObserverArea: Css        = Css("ObserveStyles-ObserverArea")
  val OperatorArea: Css        = Css("ObserveStyles-OperatorArea")
  val ImageQualityArea: Css    = Css("ObserveStyles-ImageQualityArea")
  val CloudExtinctionArea: Css = Css("ObserveStyles-CloudExtinctionArea")
  val WaterVaporArea: Css      = Css("ObserveStyles-WaterVaporArea")
  val SkyBackgroundArea: Css   = Css("ObserveStyles-SkyBackgroundArea")

  val ConfiguringRow: Css     = Css("ObserveStyles-configuringRow")
  val StepTable: Css          = Css("ObserveStyles-stepTable")
  val StepSettingsHeader: Css = Css("ObserveStyles-stepSettingsHeader")

  val StepRowWarning: Css        = Css("ObserveStyles-stepRowWarning")
  val StepRowError: Css          = Css("ObserveStyles-stepRowError")
  val StepRowDone: Css           = Css("ObserveStyles-stepRowDone")
  val StepRowWithBreakpoint: Css = Css("ObserveStyles-stepRowWithBreakpoint")
  val StepRowFirstInAtom: Css    = Css("ObserveStyles-stepRowFirstInAtom")
  val StepRowPossibleFuture: Css = Css("ObserveStyles-stepRowPossibleFuture")

  val ObservationStepProgressBar: Css = Css("ObserveStyles-observationProgressBar")
  val ControlButtonStrip: Css         = Css("ObserveStyles-controlButtonStrip")
  val PauseButton: Css                = Css("ObserveStyles-pauseButton")
  val CancelPauseButton: Css          = Css("ObserveStyles-cancelPauseButton")
  val StopButton: Css                 = Css("ObserveStyles-stopButton")
  val PlayButton: Css                 = Css("ObserveStyles-playButton")
  val AbortButton: Css                = Css("ObserveStyles-abortButton")
  val ReloadButton: Css               = Css("ObserveStyles-reloadButton")
  val SingleButton: Css               = Css("ObserveStyles-singleButton")
  val IconSoft: Css                   = Css("ObserveStyles-iconSoft")
  val QaStatusEditable: Css           = Css("ObserveStyles-qaStatusEditable")
  val QaStatusSelect: Css             = Css("ObserveStyles-qaStatusSelect")
  val QaEditorOverlay: Css            = Css("ObserveStyles-qaEditorOverlay")
  val QaEditorPanel: Css              = Css("ObserveStyles-qaEditorPanel")
  val QaStatusButtonStrip: Css        = Css("ObserveStyles-qaStatusButtonStrip")
  val QaEditorPanelButtons: Css       = Css("ObserveStyles-qaEditorPanelButtons")

  val GuidingCell: Css     = Css("ObserveStyles-guidingCell")
  val OffsetsBlock: Css    = Css("ObserveStyles-offsetsBlock")
  val OffsetsNodLabel: Css = Css("ObserveStyles-offsetsNodLabel")
  val OffsetComponent: Css = Css("ObserveStyles-offsetComponent")

  val StepTypeCell: Css              = Css("ObserveStyles-stepTypeCell")
  val StepTypeTag: Css               = Css("ObserveStyles-stepTypeTag")
  val StepTypeCompleted: Css         = Css("ObserveStyles-stepTypeCompleted")
  val StepTypeObject: Css            = Css("ObserveStyles-stepTypeObject")
  val StepTypeArc: Css               = Css("ObserveStyles-stepTypeArc")
  val StepTypeFlat: Css              = Css("ObserveStyles-stepTypeFlat")
  val StepTypeBias: Css              = Css("ObserveStyles-stepTypeBias")
  val StepTypeDark: Css              = Css("ObserveStyles-stepTypeDark")
  val StepTypeCalibration: Css       = Css("ObserveStyles-stepTypeCalibration")
  val StepTypeAlignAndCalib: Css     = Css("ObserveStyles-stepTypeAlignAndCalib")
  val StepTypeNodAndShuffle: Css     = Css("ObserveStyles-stepTypeNodAndShuffle")
  val StepTypeNodAndShuffleDark: Css = Css("ObserveStyles-stepTypeNodAndShuffleDark")

  val BreakpointTableCell: Css = Css("ObserveStyles-breakpointTableCell")

  val BreakpointHandle: Css = Css("ObserveStyles-breakpointHandle")
  val BreakpointIcon: Css   = Css("ObserveStyles-breakpointIcon")
  val ActiveBreakpoint: Css = Css("ObserveStyles-activeBreakpoint")
  val SkipHandle: Css       = Css("ObserveStyles-skipHandle")
  val SkipIconSet: Css      = Css("ObserveStyles-skipIconSet")

  val DefaultCursor: Css     = Css("ObserveStyles-defaultCursor")
  val ConfigButtonStrip: Css = Css("ObserveStyles-configButtonStrip")
  val ConfigButton: Css      = Css("ObserveStyles-configButton")

  val SyncingPanel: Css = Css("ObserveStyles-syncingPanel")

  val ObsSummary: Css           = Css("ObserveStyles-obsSummary")
  val ObsSummaryTitle: Css      = Css("ObserveStyles-obsSummary-title")
  val ObsSummaryDetails: Css    = Css("ObserveStyles-obsSummary-details")
  val ObsSummarySubsystems: Css = Css("ObserveStyles-obsSummary-subsystems")
  val ObsSummaryButton: Css     = Css("ObserveStyles-obsSummary-button")

  val LogTable: Css      = Css("ObserveStyles-logTable")
  val LogWarningRow: Css = Css("ObserveStyles-logWarningRow")
  val LogErrorRow: Css   = Css("ObserveStyles-logErrorRow")

  val ExternalLink: Css = Css("ObserveStyles-externalLink")

  object Prime:
    val EmptyProgressBar: Css      = Css("p-progressbar p-component")
    val EmptyProgressBarLabel: Css = Css("p-progressbar-label")
