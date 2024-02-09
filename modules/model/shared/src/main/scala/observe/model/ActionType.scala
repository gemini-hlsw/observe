// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import lucuma.core.enums.Instrument
import observe.model.enums.Resource

sealed trait ActionType extends Product with Serializable derives Eq

object ActionType:
  case object Observe                              extends ActionType
  case object Undefined                            extends ActionType // Used in tests
  case class Configure(sys: Resource | Instrument) extends ActionType
  case object OdbEvent                             extends ActionType
