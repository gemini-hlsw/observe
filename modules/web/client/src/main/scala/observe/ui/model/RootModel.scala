// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.derived.*
import cats.syntax.option.*
import crystal.Pot
import crystal.react.View
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
  nighttimeObservation: Option[LoadedObservation],
  daytimeObservations:  List[LoadedObservation],
  sequenceExecution:    Map[Observation.Id, ExecutionState],
  conditions:           Conditions,
  observer:             Option[Observer],
  operator:             Option[Operator],
  userSelectionMessage: Option[NonEmptyString],
  log:                  List[NonEmptyString]
) derives Eq:
  // TODO Readonly mode won't depend on user logged or not, but on their permissions.
  // For the moment we are requiring the STAFF role, so all logged users can operate.
  val clientMode: ClientMode = userVault.fold(ClientMode.ReadOnly)(_ => ClientMode.CanOperate)

  val isUserLogged: Boolean = userVault.isDefined

object RootModelData:
  def initial(userVault: Either[Throwable, Option[UserVault]]): RootModelData =
    val vault: Option[UserVault] = userVault.toOption.flatten
    RootModelData(
      userVault = vault,
      nighttimeObservation = none,
      daytimeObservations = List.empty,
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
  val nighttimeObservation: Lens[RootModelData, Option[LoadedObservation]]        =
    Focus[RootModelData](_.nighttimeObservation)
  val daytimeObservations: Lens[RootModelData, List[LoadedObservation]]           =
    Focus[RootModelData](_.daytimeObservations)
  val sequenceExecution: Lens[RootModelData, Map[Observation.Id, ExecutionState]] =
    Focus[RootModelData](_.sequenceExecution)
  val conditions: Lens[RootModelData, Conditions]                                 = Focus[RootModelData](_.conditions)
  val observer: Lens[RootModelData, Option[Observer]]                             = Focus[RootModelData](_.observer)
  val operator: Lens[RootModelData, Option[Operator]]                             = Focus[RootModelData](_.operator)
  val userSelectionMessage: Lens[RootModelData, Option[NonEmptyString]]           =
    Focus[RootModelData](_.userSelectionMessage)
  val log: Lens[RootModelData, List[NonEmptyString]]                              = Focus[RootModelData](_.log)

  given Reusability[RootModelData] = Reusability.byEq

case class RootModel(environment: Pot[Environment], data: View[RootModelData])
