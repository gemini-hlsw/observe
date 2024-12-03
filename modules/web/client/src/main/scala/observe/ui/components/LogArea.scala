// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.ReactFnProps
import observe.common.FixedLengthBuffer
import observe.model.events.LogMessage

case class LogArea(globalLog: FixedLengthBuffer[LogMessage]) extends ReactFnProps(LogArea.component)

object LogArea:
  private type Props = LogArea

  private val component =
    ScalaFnComponent[Props]: props =>
      <.div(props.toString)
