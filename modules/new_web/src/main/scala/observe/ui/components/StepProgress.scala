// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import react.common.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*

case class StepProgress() extends ReactFnProps(StepProgress.component)

object StepProgress:
  private type Props = StepProgress

  private val component = ScalaFnComponent[Props](props => <.div)
