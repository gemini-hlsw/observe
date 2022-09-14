// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import lucuma.core.util.NewType
import java.util.UUID

object ClientId extends NewType[UUID]
type ClientId = ClientId.Type

object Observer extends NewType[String]
type Observer = Observer.Type

object Operator extends NewType[String]
type Operator = Operator.Type
