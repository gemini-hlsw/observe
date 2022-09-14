// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe

import react.common.style.Css

object ObserveStyles {
  val MainUI: Css = Css("ObserveStyles-mainUI")

  val Centered: Css = Css("ObserveStyles-centered")

  // Prime components restyling
  val Divider: Css  = Css("ObserveStyles-divider")
  val TabPanel: Css = Css("ObserveStyles-tab-panel")

  val ActiveInstrumentLabel: Css = Css("ObserveStyles-activeInstrumentLabel")
  val LabelPointer: Css          = Css("ObserveStyles-labelPointer")

  val LogArea: Css = Css("ObserveStyles-logArea")
  val Footer: Css  = Css("ObserveStyles-footer")

  val SessionQueueTable: Css = Css("ObserveStyles-sessionQueueTable")
  val QueueText: Css         = Css("ObserveStyles-queueText")

  val rowPositive: Css = Css("ObserveStyles-rowPositive")
  val rowWarning: Css  = Css("ObserveStyles-rowWarning")
  val rowActive: Css   = Css("ObserveStyles-rowActive")
  val rowNegative: Css = Css("ObserveStyles-rowNegative")
  val rowError: Css    = Css("ObserveStyles-rowError")
  val rowDisabled: Css = Css("ObserveStyles-rowDisabled")
  val rowDone: Css     = Css("ObserveStyles-rowDone")
  val rowNone: Css     = Css.Empty
}
