// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import observe.model.*
import observe.ui.ObserveStyles
import lucuma.react.common.*
import lucuma.react.primereact.Card
import lucuma.react.primereact.Dropdown
import lucuma.react.primereact.InputText

case class HeadersSideBar(
  status:     ClientStatus,
  operator:   Option[Operator],
  conditions: View[Conditions]
) extends ReactFnProps(HeadersSideBar.component):
  val canOperate: Boolean = status.canOperate

private object HeadersSideBar:
  private type Props = HeadersSideBar

  private val component = ScalaFnComponent[Props](props =>
    Card(clazz = ObserveStyles.HeaderSideBarCard)(
      <.div(ObserveStyles.HeaderSideBar)(
        <.div(ObserveStyles.ObserverArea)(
          <.label(^.htmlFor := "observer")("Observer Name"),
          InputText("observer")
        ),
        <.div(ObserveStyles.OperatorArea)(
          <.label(^.htmlFor := "operator")("Operator"),
          InputText("operator")
        ),
        <.div(ObserveStyles.ImageQualityArea)(
          <.label(^.htmlFor := "imageQuality")("Image Quality"),
          Dropdown("", List.empty, id = "imageQuality")
        ),
        <.div(ObserveStyles.CloudCoverArea)(
          <.label(^.htmlFor := "cloudCover")("Cloud Cover"),
          Dropdown("", List.empty, id = "cloudCover")
        ),
        <.div(ObserveStyles.WaterVaporArea)(
          <.label(^.htmlFor := "waterVapor")("Water Vapor"),
          Dropdown("", List.empty, id = "waterVapor")
        ),
        <.div(ObserveStyles.SkyBackgroundArea)(
          <.label(^.htmlFor := "skyBackground")("Sky Background"),
          Dropdown("", List.empty, id = "skyBackground")
        )
      )
    )
  )
