// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.syntax.option.*
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.ui.sso.UserVault
import monocle.Focus
import monocle.Lens
import observe.model.*

case class RootModel(
  userVault:            Option[UserVault],
  status:               ClientStatus,
  conditions:           Conditions,
  operator:             Option[Operator],
  userSelectionMessage: Option[NonEmptyString]
)

object RootModel:
  def initial(userVault: Either[Throwable, Option[UserVault]]): RootModel =
    RootModel(
      userVault.toOption.flatten,
      ClientStatus.Default,
      Conditions.Default,
      none,
      userVault.left.toOption.map(t => NonEmptyString.unsafeFrom(t.getMessage))
    )

  val userVault: Lens[RootModel, Option[UserVault]]                 = Focus[RootModel](_.userVault)
  val status: Lens[RootModel, ClientStatus]                         = Focus[RootModel](_.status)
  val conditions: Lens[RootModel, Conditions]                       = Focus[RootModel](_.conditions)
  val operator: Lens[RootModel, Option[Operator]]                   = Focus[RootModel](_.operator)
  val userSelectionMessage: Lens[RootModel, Option[NonEmptyString]] =
    Focus[RootModel](_.userSelectionMessage)
