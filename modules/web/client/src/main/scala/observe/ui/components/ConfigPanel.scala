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
import observe.ui.ObserveStyles
import observe.ui.model.AppContext
import observe.ui.services.ConfigApi
import lucuma.ui.primereact.FormInputTextView
import crystal.react.View
// import lucuma.core.optics.ValidSplitEpi
import eu.timepit.refined.cats.*
// import eu.timepit.refined.collection.NonEmpty
import lucuma.core.optics.*
import lucuma.core.validation.InputValidSplitEpi

case class ConfigPanel(
  clientId:   ClientId,
  operator:   Option[View[Operator]],
  conditions: View[Conditions]
) extends ReactFnProps(ConfigPanel.component)

object ConfigPanel:
  private type Props = ConfigPanel

  private val component = ScalaFnComponent
    .withHooks[Props]
    .useContext(AppContext.ctx)
    .useContext(ConfigApi.ctx)
    .render: (props, ctx, configApi) =>
      import ctx.given

      val iq: View[Option[ImageQuality]] =
        props.conditions
          .zoom(Conditions.iq)
          .withOnMod(_.map(configApi.setImageQuality(props.clientId, _)).orEmpty.runAsync)

      val ce: View[Option[CloudExtinction]] =
        props.conditions
          .zoom(Conditions.ce)
          .withOnMod(_.map(configApi.setCloudExtinction(props.clientId, _)).orEmpty.runAsync)

      val wv: View[Option[WaterVapor]] =
        props.conditions
          .zoom(Conditions.wv)
          .withOnMod(_.map(configApi.setWaterVapor(props.clientId, _)).orEmpty.runAsync)

      val sb: View[Option[SkyBackground]] =
        props.conditions
          .zoom(Conditions.sb)
          .withOnMod(_.map(configApi.setSkyBackground(props.clientId, _)).orEmpty.runAsync)

      <.div(ObserveStyles.ConfigSection)(
        <.div(ObserveStyles.ObserverArea)(
          <.label(^.htmlFor := "observer")("Observer Name"),
          InputText("observer")
        ),
        props.operator.map: operator =>
          <.div(ObserveStyles.OperatorArea)(
            FormInputTextView(
              id = "operator".refined,
              label = "Operator",
              value = operator,
              validFormat = InputValidSplitEpi.nonEmptyString
                .andThen(ValidSplitEpi.fromIso(Operator.value.reverse))
            )
          ),
        <.div(ObserveStyles.ImageQualityArea)(
          FormEnumDropdownOptionalView(
            id = "imageQuality".refined,
            label = "Image Quality",
            value = iq,
            showClear = false,
            disabled = configApi.isBlocked
          )
        ),
        <.div(ObserveStyles.CloudExtinctionArea)(
          FormEnumDropdownOptionalView(
            id = "cloudExtinction".refined,
            label = "Cloud Extinction",
            value = ce,
            showClear = false,
            disabled = configApi.isBlocked
          )
        ),
        <.div(ObserveStyles.WaterVaporArea)(
          FormEnumDropdownOptionalView(
            id = "waterVapor".refined,
            label = "Water Vapor",
            value = wv,
            showClear = false,
            disabled = configApi.isBlocked
          )
        ),
        <.div(ObserveStyles.SkyBackgroundArea)(
          FormEnumDropdownOptionalView(
            id = "skyBackground".refined,
            label = "Sky Background",
            value = sb,
            showClear = false,
            disabled = configApi.isBlocked
          )
        )
      )
