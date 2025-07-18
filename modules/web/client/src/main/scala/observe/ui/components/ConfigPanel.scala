// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import coulomb.policy.strict.given
import crystal.react.*
import eu.timepit.refined.cats.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.CloudExtinction
import lucuma.core.model.ImageQuality
import lucuma.core.model.validation.ModelValidators
import lucuma.core.optics.*
import lucuma.core.syntax.validation.*
import lucuma.core.validation.*
import lucuma.react.common.*
import lucuma.react.primereact.TooltipOptions
import lucuma.refined.*
import lucuma.ui.input.ChangeAuditor
import lucuma.ui.optics.*
import lucuma.ui.primereact.FormEnumDropdownOptionalView
import lucuma.ui.primereact.FormInputTextView
import lucuma.ui.primereact.given
import observe.model.*
import observe.ui.ObserveStyles
import observe.ui.model.AppContext
import observe.ui.services.ConfigApi

case class ConfigPanel(
  obsId:      Option[Observation.Id],
  observer:   View[Option[Observer]],
  operator:   View[Option[Operator]],
  conditions: View[Conditions]
) extends ReactFnProps(ConfigPanel)

object ConfigPanel
    extends ReactFnComponent[ConfigPanel](props =>
      for
        ctx       <- useContext(AppContext.ctx)
        configApi <- useContext(ConfigApi.ctx)
      yield
        import ctx.given

        val iq: View[Option[ImageQuality]] =
          props.conditions
            .zoom(Conditions.iq)
            .withOnMod(_.map(configApi.setImageQuality).orEmpty.runAsync)

        val ce: View[Option[CloudExtinction]] =
          props.conditions
            .zoom(Conditions.ce)
            .withOnMod(_.map(configApi.setCloudExtinction).orEmpty.runAsync)

        val wv: View[Option[WaterVapor]] =
          props.conditions
            .zoom(Conditions.wv)
            .withOnMod(_.map(configApi.setWaterVapor).orEmpty.runAsync)

        val sb: View[Option[SkyBackground]] =
          props.conditions
            .zoom(Conditions.sb)
            .withOnMod(_.map(configApi.setSkyBackground).orEmpty.runAsync)

        val op: View[Option[Operator]] =
          props.operator.withOnMod(configApi.setOperator(_).runAsync)

        <.div(ObserveStyles.ConfigSection)(
          <.div(ObserveStyles.ConditionsSection)(
            <.span(ObserveStyles.ConditionsLabel)("Current Conditions"),
            <.div(ObserveStyles.ImageQualityArea)(
              FormInputTextView(
                id = "imageQuality".refined,
                label = "IQ",
                value = iq,
                validFormat = ModelValidators.ImageQualityValidWedge.optional
                  .nonEmpty("Must not be empty".toEitherErrorsUnsafe),
                changeAuditor = ChangeAuditor.accept.decimal(2.refined).denyNeg,
                disabled = configApi.isBlocked,
                tooltipOptions = TooltipOptions.Top
              )
            ),
            <.div(ObserveStyles.CloudExtinctionArea)(
              FormInputTextView(
                id = "cloudExtinction".refined,
                label = "CE",
                value = ce,
                validFormat = ModelValidators.CloudExtinctionValidWedge.optional
                  .nonEmpty("Must not be empty".toEitherErrorsUnsafe),
                changeAuditor = ChangeAuditor.accept.decimal(2.refined).denyNeg,
                disabled = configApi.isBlocked,
                tooltipOptions = TooltipOptions.Top
              )
            ),
            <.div(ObserveStyles.WaterVaporArea)(
              FormEnumDropdownOptionalView(
                id = "waterVapor".refined,
                label = "WV",
                value = wv,
                showClear = false,
                disabled = configApi.isBlocked
              )
            ),
            <.div(ObserveStyles.SkyBackgroundArea)(
              FormEnumDropdownOptionalView(
                id = "skyBackground".refined,
                label = "BG",
                value = sb,
                showClear = false,
                disabled = configApi.isBlocked
              )
            )
          ),
          <.div(ObserveStyles.NamesSection)(
            <.div(ObserveStyles.OperatorArea)(
              FormInputTextView(
                id = "operator".refined,
                label = React.Fragment(
                  <.span(ObserveStyles.OnlyLargeScreens)("Operator"),
                  <.span(ObserveStyles.OnlySmallScreens)("Op")
                ),
                value = op,
                validFormat = InputValidSplitEpi
                  .fromIso(OptionNonEmptyStringIso.reverse)
                  .andThen(Operator.Value.reverse.option),
                disabled = configApi.isBlocked
              )
            ),
            <.div(ObserveStyles.ObserverArea)(
              props.obsId.map: obsId =>
                val obs: View[Option[Observer]] =
                  props.observer.withOnMod(configApi.setObserver(obsId, _).runAsync)

                FormInputTextView(
                  id = "observer".refined,
                  label = React.Fragment(
                    <.span(ObserveStyles.OnlyLargeScreens)("Observer"),
                    <.span(ObserveStyles.OnlySmallScreens)("Obs")
                  ),
                  value = obs,
                  validFormat = InputValidSplitEpi
                    .fromIso(OptionNonEmptyStringIso.reverse)
                    .andThen(Observer.Value.reverse.option),
                  disabled = configApi.isBlocked
                )
            )
          )
        )
    )
