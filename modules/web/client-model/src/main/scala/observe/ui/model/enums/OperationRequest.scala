// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.enums

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import monocle.Iso

enum OperationRequest derives Eq:
  case Idle, InFlight

  def isInFlight: Boolean = this == InFlight

object OperationRequest:
  val IsInFlight: Iso[OperationRequest, Boolean] =
    Iso[OperationRequest, Boolean](_ === InFlight)(if (_) InFlight else Idle)
