// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import crystal.Pot
import crystal.Pot.Pending
import crystal.Pot.Ready
import crystal.syntax.*
import lucuma.core.model.Observation
import lucuma.core.model.Visit
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.schemas.model.ExecutionVisits
import monocle.Focus
import monocle.Lens

case class LoadedObservation private (
  obsId:  Observation.Id,
  config: Pot[InstrumentExecutionConfig] = Pot.pending,
  visits: Pot[ExecutionVisits] = Pot.pending
):
  private def potFromEitherOption[A](e: Either[Throwable, Option[A]]): Pot[A] =
    e.toTry.toPot.flatMap(_.toPot)

  def withConfig(config: Either[Throwable, Option[InstrumentExecutionConfig]]): LoadedObservation =
    copy(config = potFromEitherOption(config))

  def withVisits(visits: Either[Throwable, Option[ExecutionVisits]]): LoadedObservation =
    copy(visits = potFromEitherOption(visits))

  def addVisits(addedVisits: Either[Throwable, Option[ExecutionVisits]]): LoadedObservation =
    copy(visits = visits match
      case Ready(existing) =>
        potFromEitherOption(addedVisits) match
          case Ready(added) => Ready(existing.extendWith(added))
          case Pending      => visits
          case error        => error
      case _               => potFromEitherOption(addedVisits)
    )

  def reset: LoadedObservation =
    copy(config = Pot.pending, visits = Pot.pending)

  lazy val lastVisitId: Option[Visit.Id] =
    visits.toOption.flatMap:
      case ExecutionVisits.GmosNorth(_, visits) => visits.lastOption.map(_.id)
      case ExecutionVisits.GmosSouth(_, visits) => visits.lastOption.map(_.id)

object LoadedObservation:
  def apply(obsId: Observation.Id): LoadedObservation = new LoadedObservation(obsId)

  val obsId: Lens[LoadedObservation, Observation.Id]                  = Focus[LoadedObservation](_.obsId)
  val config: Lens[LoadedObservation, Pot[InstrumentExecutionConfig]] =
    Focus[LoadedObservation](_.config)
  val visits: Lens[LoadedObservation, Pot[ExecutionVisits]]           = Focus[LoadedObservation](_.visits)
