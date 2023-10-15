// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.derived.*
import cats.syntax.option.*
import eu.timepit.refined.cats.given
import eu.timepit.refined.types.string.NonEmptyString
import japgolly.scalajs.react.ReactCats.*
import japgolly.scalajs.react.Reusability
import lucuma.ui.sso.UserVault
import monocle.Focus
import monocle.Lens
import observe.model.*
import observe.ui.model.enums.ClientMode

case class RootModelData(
  userVault:            Option[UserVault],
  sequenceExecution:    Map[Observation.Id, ExecutionState],
  conditions:           Conditions,
  observer:             Option[Observer],
  operator:             Option[Operator],
  userSelectionMessage: Option[NonEmptyString],
  log:                  List[NonEmptyString]
) derives Eq:
  val clientMode: ClientMode = userVault.fold(ClientMode.ReadOnly)(_ => ClientMode.CanOperate)

object RootModelData:
  def initial(userVault: Either[Throwable, Option[UserVault]]): RootModelData =
    val vault: Option[UserVault] = userVault.toOption.flatten
    RootModelData(
      userVault = vault,
      sequenceExecution = Map.empty,
      conditions = Conditions.Default,
      observer =
        vault.flatMap(v => NonEmptyString.from(v.user.displayName).toOption.map(Observer(_))),
      operator = none,
      userSelectionMessage =
        userVault.left.toOption.map(t => NonEmptyString.unsafeFrom(t.getMessage)),
      log = List.empty
    )

  val userVault: Lens[RootModelData, Option[UserVault]]                           = Focus[RootModelData](_.userVault)
  val sequenceExecution: Lens[RootModelData, Map[Observation.Id, ExecutionState]] =
    Focus[RootModelData](_.sequenceExecution)
  val conditions: Lens[RootModelData, Conditions]                                 = Focus[RootModelData](_.conditions)
  val observer: Lens[RootModelData, Option[Observer]]                             = Focus[RootModelData](_.observer)
  val operator: Lens[RootModelData, Option[Operator]]                             = Focus[RootModelData](_.operator)
  val userSelectionMessage: Lens[RootModelData, Option[NonEmptyString]]           =
    Focus[RootModelData](_.userSelectionMessage)
  val log: Lens[RootModelData, List[NonEmptyString]]                              = Focus[RootModelData](_.log)

  given Reusability[RootModelData] = Reusability.byEq

case class RootModel(environment: Environment, data: RootModelData) derives Eq:
  export data.*

object RootModel:
  private val data: Lens[RootModel, RootModelData]                            = Focus[RootModel](_.data)
  private val environment: Lens[RootModel, Environment]                       = Focus[RootModel](_.environment)
  val userVault: Lens[RootModel, Option[UserVault]]                           = data.andThen(RootModelData.userVault)
  val sequenceExecution: Lens[RootModel, Map[Observation.Id, ExecutionState]] =
    data.andThen(RootModelData.sequenceExecution)
  val conditions: Lens[RootModel, Conditions]                                 = data.andThen(RootModelData.conditions)
  val observer: Lens[RootModel, Option[Observer]]                             = data.andThen(RootModelData.observer)
  val operator: Lens[RootModel, Option[Operator]]                             = data.andThen(RootModelData.operator)
  val clientId: Lens[RootModel, ClientId]                                     = environment.andThen(Environment.clientId)
  val userSelectionMessage: Lens[RootModel, Option[NonEmptyString]]           =
    data.andThen(RootModelData.userSelectionMessage)
  val log: Lens[RootModel, List[NonEmptyString]]                              = data.andThen(RootModelData.log)
