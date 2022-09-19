// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import react.common.style.Css

object ObserveStyles {
  val MainUI: Css = Css("ObserveStyles-mainUI")

  val Centered: Css = Css("ObserveStyles-centered")

  // Prime components restyling
  val Divider: Css          = Css("ObserveStyles-divider")
  val SequenceTabPanel: Css = Css("ObserveStyles-sequenceTabPanel")

  val ActiveInstrumentLabel: Css = Css("ObserveStyles-activeInstrumentLabel")
  val LabelPointer: Css          = Css("ObserveStyles-labelPointer")

  val LogArea: Css = Css("ObserveStyles-logArea")
  val Footer: Css  = Css("ObserveStyles-footer")

  val QueueText: Css = Css("ObserveStyles-queueText")

  val RowPositive: Css = Css("ObserveStyles-rowPositive")
  val RowWarning: Css  = Css("ObserveStyles-rowWarning")
  val RowActive: Css   = Css("ObserveStyles-rowActive")
  val RowNegative: Css = Css("ObserveStyles-rowNegative")
  val RowError: Css    = Css("ObserveStyles-rowError")
  val RowDisabled: Css = Css("ObserveStyles-rowDisabled")
  val RowDone: Css     = Css("ObserveStyles-rowDone")
  val RowNone: Css     = Css.Empty

  val HeaderSideBarCard: Css = Css("ObserveStyles-HeaderSideBarCard")
  val HeaderSideBar: Css     = Css("ObserveStyles-HeaderSideBar")
  val ObserverArea: Css      = Css("ObserveStyles-ObserverArea")
  val OperatorArea: Css      = Css("ObserveStyles-OperatorArea")
  val ImageQualityArea: Css  = Css("ObserveStyles-ImageQualityArea")
  val CloudCoverArea: Css    = Css("ObserveStyles-CloudCoverArea")
  val WaterVaporArea: Css    = Css("ObserveStyles-WaterVaporArea")
  val SkyBackgroundArea: Css = Css("ObserveStyles-SkyBackgroundArea")

  val SequenceTabView: Css   = Css("ObserveStyles-sequenceTabView")
  val ConfiguringRow: Css    = Css("ObserveStyles-configuringRow")
  val ObservationProgressRow = Css("ObserveStyles-observationProgressRow")
}
