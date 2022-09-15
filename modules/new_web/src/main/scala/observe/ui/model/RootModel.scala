// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.syntax.option.*
import monocle.Lens
import monocle.Focus
import observe.model.*

case class RootModel(
  status:     ClientStatus,
  conditions: Conditions,
  operator:   Option[Operator]
)

object RootModel:
  val Initial = RootModel(
    ClientStatus.Default,
    Conditions.Default,
    none
  )

  val status: Lens[RootModel, ClientStatus]       = Focus[RootModel](_.status)
  val conditions: Lens[RootModel, Conditions]     = Focus[RootModel](_.conditions)
  val operator: Lens[RootModel, Option[Operator]] = Focus[RootModel](_.operator)
