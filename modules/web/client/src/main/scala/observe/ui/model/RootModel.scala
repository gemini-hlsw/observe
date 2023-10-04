// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.derived.*
import cats.syntax.option.*
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.ui.sso.UserVault
import monocle.Focus
import monocle.Lens
import observe.model.*
import observe.ui.model.enums.ClientMode
import eu.timepit.refined.cats.given

case class RootModelData(
  userVault:            Option[UserVault],
  conditions:           Conditions,
  operator:             Option[Operator],
  userSelectionMessage: Option[NonEmptyString],
  log:                  List[NonEmptyString]
) derives Eq:
  val clientMode: ClientMode = userVault.fold(ClientMode.ReadOnly)(_ => ClientMode.CanOperate)

object RootModelData:
  def initial(userVault: Either[Throwable, Option[UserVault]]): RootModelData =
    RootModelData(
      userVault.toOption.flatten,
      conditions = Conditions.Default,
      operator = none,
      userSelectionMessage =
        userVault.left.toOption.map(t => NonEmptyString.unsafeFrom(t.getMessage)),
      log = List.empty
    )

  val userVault: Lens[RootModelData, Option[UserVault]]                 = Focus[RootModelData](_.userVault)
  val conditions: Lens[RootModelData, Conditions]                       = Focus[RootModelData](_.conditions)
  val operator: Lens[RootModelData, Option[Operator]]                   = Focus[RootModelData](_.operator)
  val userSelectionMessage: Lens[RootModelData, Option[NonEmptyString]] =
    Focus[RootModelData](_.userSelectionMessage)
  val log: Lens[RootModelData, List[NonEmptyString]]                    = Focus[RootModelData](_.log)

case class RootModel(environment: Environment, data: RootModelData) derives Eq:
  export data.*

object RootModel:
  private val data: Lens[RootModel, RootModelData]                  = Focus[RootModel](_.data)
  val userVault: Lens[RootModel, Option[UserVault]]                 = data.andThen(RootModelData.userVault)
  val conditions: Lens[RootModel, Conditions]                       = data.andThen(RootModelData.conditions)
  val operator: Lens[RootModel, Option[Operator]]                   = data.andThen(RootModelData.operator)
  val userSelectionMessage: Lens[RootModel, Option[NonEmptyString]] =
    data.andThen(RootModelData.userSelectionMessage)
  val log: Lens[RootModel, List[NonEmptyString]]                    = data.andThen(RootModelData.log)
