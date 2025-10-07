// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.enums

import lucuma.core.enums.Instrument
import lucuma.core.util.Display
import lucuma.core.util.Enumerated
import observe.ui.model.Page

/**
 * Describes the application tab buttons in the sidebar
 *
 * @param title
 *   The text for the button
 * @param buttonGroup
 *   Groups the buttons with the same value together
 *
 * Within a button group, order is determined by the AppTab Order instance, which is determined by
 * the order in AppTab.all.
 */
enum AppTab(val title: String):
  case ObsList                           extends AppTab("Observations")
  case LoadedObs(instrument: Instrument) extends AppTab(instrument.shortName)

  lazy val getPage: Page =
    this match
      case ObsList                      => Page.Observations
      case AppTab.LoadedObs(instrument) => Page.LoadedInstrument(instrument)

object AppTab:
  def from(page: Page): AppTab =
    page match
      case Page.Observations        => AppTab.ObsList
      case Page.LoadedInstrument(i) => AppTab.LoadedObs(i)

  given Enumerated[AppTab] =
    Enumerated
      .from(AppTab.ObsList, Enumerated[Instrument].all.map(AppTab.LoadedObs(_))*)
      .withTag(_.title)

  given Display[AppTab] = Display.byShortName(_.title)
