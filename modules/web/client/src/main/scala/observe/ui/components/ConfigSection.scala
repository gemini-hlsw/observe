// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.*
import lucuma.react.primereact.Card
import lucuma.react.primereact.Dropdown
import lucuma.react.primereact.InputText
import observe.model.*
import observe.ui.AppContext
import observe.ui.ObserveStyles
import lucuma.core.enums.ImageQuality
import lucuma.refined.*
import lucuma.ui.primereact.given
import lucuma.ui.primereact.EnumDropdownOptionalView

case class ConfigSection(
  // status:     ClientStatus,
  operator:   Option[Operator],
  conditions: View[Conditions]
) extends ReactFnProps(ConfigSection.component)
// val canOperate: Boolean = status.canOperate

object ConfigSection:
  private type Props = ConfigSection

  private val component = ScalaFnComponent
    .withHooks[Props]
    .useContext(AppContext.ctx)
    .render: (props, ctx) =>
      import ctx.given

      val iq: View[Option[ImageQuality]] =
        props.conditions
          .zoom(Conditions.iq)
          .withOnMod(_.map(ctx.configApi.setImageQuality).orEmpty.runAsync)

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
            EnumDropdownOptionalView(id = "imageQuality".refined, value = iq, showClear = false)
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
