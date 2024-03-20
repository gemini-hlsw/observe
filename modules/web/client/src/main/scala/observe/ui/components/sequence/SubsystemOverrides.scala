// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import crystal.*
import crystal.react.*
import eu.timepit.refined.types.string.NonEmptyString
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.react.common.*
import lucuma.refined.*
import lucuma.ui.primereact.{*, given}
import observe.model.SubsystemEnabled
import observe.model.SystemOverrides
import observe.ui.ObserveStyles
import observe.ui.model.AppContext
import observe.ui.services.ConfigApi

case class SubsystemOverrides(
  obsId:      Observation.Id,
  instrument: Instrument,
  overrides:  View[SystemOverrides]
) extends ReactFnProps(SubsystemOverrides.component)

object SubsystemOverrides:
  private type Props = SubsystemOverrides

  private val component = ScalaFnComponent
    .withHooks[Props]
    .useContext(AppContext.ctx)
    .useContext(ConfigApi.ctx)
    .render: (props, ctx, configApi) =>
      import ctx.given

      def renderOverrideControl(
        label:   NonEmptyString,
        enabled: View[SubsystemEnabled]
      ): VdomNode =
        CheckboxView(
          id = label,
          value = enabled.as(SubsystemEnabled.value),
          label = label.value,
          disabled = configApi.isBlocked
        )

      <.span(ObserveStyles.ObsSummarySubsystems)(
        renderOverrideControl(
          "TCS".refined,
          props.overrides
            .zoom(SystemOverrides.isTcsEnabled)
            .withOnMod(configApi.setTcsEnabled(props.obsId, _).runAsync)
        ),
        renderOverrideControl(
          "GCAL".refined,
          props.overrides
            .zoom(SystemOverrides.isGcalEnabled)
            .withOnMod(configApi.setGcalEnabled(props.obsId, _).runAsync)
        ),
        renderOverrideControl(
          "DHS".refined,
          props.overrides
            .zoom(SystemOverrides.isDhsEnabled)
            .withOnMod(configApi.setDhsEnabled(props.obsId, _).runAsync)
        ),
        renderOverrideControl(
          NonEmptyString.unsafeFrom(props.instrument.longName),
          props.overrides
            .zoom(SystemOverrides.isInstrumentEnabled)
            .withOnMod(configApi.setInstrumentEnabled(props.obsId, _).runAsync)
        )
      )
