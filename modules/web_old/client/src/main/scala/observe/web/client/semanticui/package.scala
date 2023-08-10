// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.react.semanticui.elements.button.*
import lucuma.react.semanticui.elements.icon.Icon

package object semanticui {

  import lucuma.react.semanticui.SemanticColor

  import lucuma.react.semanticui.modules.popup.Popup

  // Custom attributes used by SemanticUI
  val dataTab: VdomAttr[String]      = VdomAttr("data-tab")
  val dataTooltip: VdomAttr[String]  = VdomAttr("data-tooltip")
  val dataContent: VdomAttr[String]  = VdomAttr("data-content")
  val dataPosition: VdomAttr[String] = VdomAttr("data-position")
  val dataInverted: VdomAttr[String] = VdomAttr("data-inverted")
  val formId: VdomAttr[String]       = VdomAttr("form")

  def controlButton(
    icon:     Icon,
    color:    SemanticColor,
    onClick:  Callback,
    disabled: Boolean,
    tooltip:  String,
    text:     String
  ): VdomNode =
    Popup(content = tooltip,
          trigger = Button(
            icon = true,
            labelPosition = LabelPosition.Left,
            onClick = onClick,
            color = color,
            disabled = disabled
          )(icon, text)
    )
}
