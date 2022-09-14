// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.components

import react.common.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import cats.syntax.all.*
import observe.model.*
import crystal.react.*

case class HeadersSideBar(
  status:     ClientStatus,
  operator:   Option[Operator],
  conditions: View[Conditions]
) extends ReactFnProps(HeadersSideBar.component):
  val canOperate: Boolean = status.canOperate

private object HeadersSideBar:
  private type Props = HeadersSideBar

  private val component = ScalaFnComponent[Props](props => <.div)
