// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import lucuma.core.util.NewType
import observe.model.enums.SystemName

import java.util.UUID

object ClientId extends NewType[UUID]
type ClientId = ClientId.Type

object Observer extends NewType[String]
type Observer = Observer.Type

object Operator extends NewType[String]
type Operator = Operator.Type

object ParamName extends NewType[String]
type ParamName = ParamName.Type

object ParamValue extends NewType[String]
type ParamValue = ParamValue.Type

object Parameters extends NewType[Map[ParamName, ParamValue]]
type Parameters = Parameters.Type

// TODO Can we unify with lucuma.core.model.sequence.StepConfig?
object ExecutionStepConfig extends NewType[Map[SystemName, Parameters]]
type ExecutionStepConfig = ExecutionStepConfig.Type

object ImageFileId extends NewType[String]
type ImageFileId = ImageFileId.Type

object NsPairs extends NewType[Int]
type NsPairs = NsPairs.Type

object NsStageIndex extends NewType[Int]
type NsStageIndex = NsStageIndex.Type

object NsRows extends NewType[Int]
type NsRows = NsRows.Type

object NsCycles extends NewType[Int]
type NsCycles = NsCycles.Type

object NsExposureDivider extends NewType[Int]
type NsExposureDivider = NsExposureDivider.Type
