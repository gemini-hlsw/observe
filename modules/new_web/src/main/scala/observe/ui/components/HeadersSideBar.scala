// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import observe.model.*
import observe.ui.ObserveStyles
import react.common.*
import reactST.primereact.components.*

case class HeadersSideBar(
  status:     ClientStatus,
  operator:   Option[Operator],
  conditions: View[Conditions]
) extends ReactFnProps(HeadersSideBar.component):
  val canOperate: Boolean = status.canOperate

private object HeadersSideBar:
  private type Props = HeadersSideBar

  private val component = ScalaFnComponent[Props](props =>
    Card(ObserveStyles.HeaderSideBarCard)(
      <.div(ObserveStyles.HeaderSideBar)(
        <.div(ObserveStyles.ObserverArea)(
          <.label(^.htmlFor := "observer")("Observer Name"),
          InputText.id("observer")
        ),
        <.div(ObserveStyles.OperatorArea)(
          <.label(^.htmlFor := "operator")("Operator"),
          InputText.id("operator")
        ),
        <.div(ObserveStyles.ImageQualityArea)(
          <.label(^.htmlFor := "imageQuality")("Image Quality"),
          Dropdown.id("operator")
        ),
        <.div(ObserveStyles.CloudCoverArea)(
          <.label(^.htmlFor := "cloudCover")("Cloud Cover"),
          Dropdown.id("cloudCover")
        ),
        <.div(ObserveStyles.WaterVaporArea)(
          <.label(^.htmlFor := "waterVapor")("Water Vapor"),
          Dropdown.id("waterVapor")
        ),
        <.div(ObserveStyles.SkyBackgroundArea)(
          <.label(^.htmlFor := "skyBackground")("Sky Background"),
          Dropdown.id("skyBackground")
        )
      )
    )
  )
