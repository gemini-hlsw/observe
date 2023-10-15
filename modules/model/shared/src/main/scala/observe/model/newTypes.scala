// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.util.NewType

import java.util.UUID

object Version extends NewType[NonEmptyString]
type Version = Version.Type

object ClientId extends NewType[UUID]
type ClientId = ClientId.Type

object QueueId extends NewType[UUID]
type QueueId = QueueId.Type

object Observer extends NewType[NonEmptyString]
type Observer = Observer.Type

object Operator extends NewType[NonEmptyString]
type Operator = Operator.Type

object ImageFileId extends NewType[String]
type ImageFileId = ImageFileId.Type

object SubsystemEnabled extends NewType[Boolean] {
  val Enabled  = SubsystemEnabled(true)
  val Disabled = SubsystemEnabled(false)
}

type SubsystemEnabled = SubsystemEnabled.Type
