// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.enums

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
enum AppTab(val title: String) derives Enumerated:
  case Schedule  extends AppTab("Schedule")
  case Nighttime extends AppTab("Nighttime")
  case Daytime   extends AppTab("Daytime")
  case Excluded  extends AppTab("Excluded")

  lazy val tag: String = title

  lazy val getPage: Page = this match
    case AppTab.Schedule  => Page.Schedule
    case AppTab.Nighttime => Page.Nighttime
    case AppTab.Daytime   => Page.Daytime
    case AppTab.Excluded  => Page.Excluded

object AppTab:
  def from(page: Page): AppTab = page match
    case Page.Schedule  => AppTab.Schedule
    case Page.Nighttime => AppTab.Nighttime
    case Page.Daytime   => AppTab.Daytime
    case Page.Excluded  => AppTab.Excluded

  given Display[AppTab] = Display.byShortName(_.title)
