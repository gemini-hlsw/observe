// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import react.common.style.Css

object ObserveStyles {
  val MainUI: Css = Css("ObserveStyles-mainUI")

  val Centered: Css       = Css("ObserveStyles-centered")
  val ComponentLabel: Css = Css("ObserveStyles-componentLabel")
  val Shrinkable: Css     = Css("ObserveStyles-shrinkable")

  // Prime components restyling
  val Divider: Css          = Css("ObserveStyles-divider")
  val SequenceTabPanel: Css = Css("ObserveStyles-sequenceTabPanel")

  val TopPanel: Css              = Css("ObserveStyles-topPanel")
  val ActiveInstrumentLabel: Css = Css("ObserveStyles-activeInstrumentLabel")
  val LabelPointer: Css          = Css("ObserveStyles-labelPointer")
  val IdleTag: Css               = Css("ObserveStyles-idleTag")
  val RunningTag: Css            = Css("ObserveStyles-runningTag")

  val LogArea: Css = Css("ObserveStyles-logArea")
  val Footer: Css  = Css("ObserveStyles-footer")

  val ObserveTable: Css = Css("ObserveStyles-observeTable")

  val RowPositive: Css = Css("ObserveStyles-rowPositive")
  val RowWarning: Css  = Css("ObserveStyles-rowWarning")
  val RowActive: Css   = Css("ObserveStyles-rowActive")
  val RowNegative: Css = Css("ObserveStyles-rowNegative")
  val RowError: Css    = Css("ObserveStyles-rowError")
  val RowDisabled: Css = Css("ObserveStyles-rowDisabled")
  val RowDone: Css     = Css("ObserveStyles-rowDone")
  val RowNone: Css     = Css.Empty

  val SessionQueue: Css   = Css("ObserveStyles-sessionQueue")
  val ObsClassSelect: Css = Css("ObserveStyles-obsClassSelect")

  val HeaderSideBarCard: Css = Css("ObserveStyles-HeaderSideBarCard")
  val HeaderSideBar: Css     = Css("ObserveStyles-HeaderSideBar")
  val ObserverArea: Css      = Css("ObserveStyles-ObserverArea")
  val OperatorArea: Css      = Css("ObserveStyles-OperatorArea")
  val ImageQualityArea: Css  = Css("ObserveStyles-ImageQualityArea")
  val CloudCoverArea: Css    = Css("ObserveStyles-CloudCoverArea")
  val WaterVaporArea: Css    = Css("ObserveStyles-WaterVaporArea")
  val SkyBackgroundArea: Css = Css("ObserveStyles-SkyBackgroundArea")

  val SequenceTabView: Css    = Css("ObserveStyles-sequenceTabView")
  val ConfiguringRow: Css     = Css("ObserveStyles-configuringRow")
  val StepTable: Css          = Css("ObserveStyles-stepTable")
  val StepSettingsHeader: Css = Css("ObserveStyles-stepSettingsHeader")

  val StepRowRunning: Css = Css("ObserveStyles-stepRowRunning")
  val StepRowWarning: Css = Css("ObserveStyles-stepRowWarning")
  val StepRowError: Css   = Css("ObserveStyles-stepRowError")
  val StepRowDone: Css    = Css("ObserveStyles-stepRowDone")

  val ObservationProgressBarAndLabel: Css = Css("ObserveStyles-observationProgressBarAndLabel")
  val ObservationProgressBar: Css         = Css("ObserveStyles-observationProgressBar")
  val ObservationProgressLabel: Css       = Css("ObserveStyles-observationProgressLabel")
  val ControlButtonStrip: Css             = Css("ObserveStyles-controlButtonStrip")
  val PauseButton: Css                    = Css("ObserveStyles-pauseButton")
  val StopButton: Css                     = Css("ObserveStyles-stopButton")
  val PlayButton: Css                     = Css("ObserveStyles-playButton")
  val AbortButton: Css                    = Css("ObserveStyles-abortButton")

  val GuidingCell: Css     = Css("ObserveStyles-guidingCell")
  val OffsetsBlock: Css    = Css("ObserveStyles-offsetsBlock")
  val OffsetsNodLabel: Css = Css("ObserveStyles-offsetsNodLabel")
  val OffsetComponent: Css = Css("ObserveStyles-offsetComponent")

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

  val ControlTableCell: Css = Css("ObserveStyles-controlTableCell")
  val ControlCell: Css      = Css("ObserveStyles-controlCell")
  val GutterCell: Css       = Css("ObserveStyles-gutterCell")
  val SkippedIconCell: Css  = Css("ObserveStyles-skippedIconCell")
  val IconCell: Css         = Css("ObserveStyles-iconCell")
}
