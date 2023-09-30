// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import lucuma.core.util.NewType
import java.util.UUID

object ClientId extends NewType[UUID]
type ClientId = ClientId.Type

object QueueId extends NewType[UUID]
type QueueId = QueueId.Type

object Observer extends NewType[String]
type Observer = Observer.Type

object Operator extends NewType[String]
type Operator = Operator.Type

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
