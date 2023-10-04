// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.react.common.*
import lucuma.react.primereact.InputText
import lucuma.refined.*
import lucuma.ui.primereact.FormEnumDropdownOptionalView
import lucuma.ui.primereact.given
import observe.model.*
import observe.ui.AppContext
import observe.ui.ObserveStyles

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

      val ce: View[Option[CloudExtinction]] =
        props.conditions
          .zoom(Conditions.ce)
          .withOnMod(_.map(ctx.configApi.setCloudExtinction).orEmpty.runAsync)

      val wv: View[Option[WaterVapor]] =
        props.conditions
          .zoom(Conditions.wv)
          .withOnMod(_.map(ctx.configApi.setWaterVapor).orEmpty.runAsync)

      val sb: View[Option[SkyBackground]] =
        props.conditions
          .zoom(Conditions.sb)
          .withOnMod(_.map(ctx.configApi.setSkyBackground).orEmpty.runAsync)

        // Card(clazz = ObserveStyles.HeaderSideBarCard)(
      <.div(ObserveStyles.ConfigSection)(
        <.div(ObserveStyles.ObserverArea)(
          <.label(^.htmlFor := "observer")("Observer Name"),
          InputText("observer")
        ),
        <.div(ObserveStyles.OperatorArea)(
          <.label(^.htmlFor := "operator")("Operator"),
          InputText("operator")
        ),
        <.div(ObserveStyles.ImageQualityArea)(
          FormEnumDropdownOptionalView(
            id = "imageQuality".refined,
            label = "Image Quality",
            value = iq,
            showClear = false
          )
        ),
        <.div(ObserveStyles.CloudExtinctionArea)(
          FormEnumDropdownOptionalView(
            id = "cloudExtinction".refined,
            label = "Cloud Extinction",
            value = ce,
            showClear = false
          )
        ),
        <.div(ObserveStyles.WaterVaporArea)(
          FormEnumDropdownOptionalView(
            id = "waterVapor".refined,
            label = "Water Vapor",
            value = wv,
            showClear = false
          )
        ),
        <.div(ObserveStyles.SkyBackgroundArea)(
          FormEnumDropdownOptionalView(
            id = "skyBackground".refined,
            label = "Sky Background",
            value = sb,
            showClear = false
          )
        )
        // )
      )
