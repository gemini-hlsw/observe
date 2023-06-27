// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.toolbars

import cats.syntax.all.*
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.vdom.html_<^._
import react.common.*
import react.semanticui.colors.*
import react.semanticui.elements.label.Label
import react.semanticui.sizes.*
import observe.model.SequenceState
import observe.model.UnknownTargetName
import observe.web.client.circuit.SequenceInfoFocus
import observe.web.client.components.ObserveStyles
import observe.web.client.icons.*

final case class SequenceInfo(p: SequenceInfoFocus)
    extends ReactProps[SequenceInfo](SequenceInfo.component)

/**
 * Display the name of the sequence and the observer
 */
object SequenceInfo {
  type Props = SequenceInfo

  val component =
    ScalaComponent
      .builder[Props]("SequenceInfo")
      .stateless
      .render_P { p =>
        val SequenceInfoFocus(isLogged, obsName, status, tName) = p.p
        val targetName: String                                  = tName
          .filter(_.nonEmpty)
          .getOrElse(UnknownTargetName)
        <.div(
          ObserveStyles.SequenceInfo,
          Label(color = Green, size = Medium)(IconCheckmark, "Sequence Complete")
            .when(status === SequenceState.Completed),
          Label(obsName).when(isLogged),
          Label(targetName).when(isLogged)
        )
      }
      .build

}
