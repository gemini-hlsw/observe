// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import react.common.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import observe.model.*
import lucuma.react.table.*

case class StepsTable(steps: List[ExecutionStep]) extends ReactFnProps(StepsTable.component)

object StepsTable:
  private type Props = StepsTable

  private val ColDef = ColumnDef[ExecutionStep]

  // all: NonEmptyList[ColumnMeta[TableColumn]] =
  //    NonEmptyList.of(
  //      ControlColumnMeta,
  //      StepMeta,
  //      ExecutionMeta,
  //      OffsetMeta,
  //      ObservingModeMeta,
  //      ExposureMeta,
  //      DisperserMeta,
  //      FilterMeta,
  //      FPUMeta,
  //      CameraMeta,
  //      DeckerMeta,
  //      ReadModeMeta,
  //      ImagingMirrorMeta,
  //      ObjectTypeMeta,
  //      SettingsMeta
  //    )

  private val component =
    ScalaFnComponent[Props](_ => <.div)
