// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.derived.*
import cats.syntax.option.*
import crystal.Pot
import crystal.Pot.Pending
import crystal.Pot.Ready
import crystal.syntax.*
import lucuma.core.model.Visit
import lucuma.schemas.model.ExecutionVisits
import lucuma.ui.sequence.SequenceData
import monocle.Focus
import monocle.Lens

case class LoadedObservation private (
  // obsId:        Observation.Id,
  refreshing:   Boolean = false,
  errorMsg:     Option[String] = none,
  sequenceData: Pot[SequenceData] = Pot.pending,
  visits:       Pot[Option[ExecutionVisits]] = Pot.pending
  // cancelUpdates: F[Unit]
) derives Eq:
  def toPot: Pot[LoadedObservation] =
    errorMsg match
      case Some(msg) => Pot.error(new RuntimeException(msg))
      case None      => this.ready

  private def potFromEither[A](e: Either[Throwable, A]): Pot[A] =
    e.toTry.toPot

  private def potFromEitherOption[A](e: Either[Throwable, Option[A]]): Pot[A] =
    e.toTry.toPot.flatMap(_.toPot)

  def withSequenceData(config: Either[Throwable, SequenceData]): LoadedObservation =
    copy(sequenceData = potFromEither(config))

  def withVisits(visits: Either[Throwable, Option[ExecutionVisits]]): LoadedObservation =
    copy(visits = potFromEitherOption(visits.map(_.some)))

  def addVisits(addedVisits: Either[Throwable, Option[ExecutionVisits]]): LoadedObservation =
    copy(visits = visits match
      case Ready(existing) =>
        potFromEitherOption(addedVisits.map(_.some)) match
          case Ready(Some(added)) => Ready(existing.fold(added)(_.extendWith(added)).some)
          case Ready(None)        => Ready(existing)
          case Pending            => visits
          case error              => error
      case _               => potFromEitherOption(addedVisits.map(_.some))
    )

  def reset: LoadedObservation =
    copy(errorMsg = none, sequenceData = Pot.pending, visits = Pot.pending)

  lazy val lastVisitId: Option[Visit.Id] =
    visits.toOption.flatten.map:
      case ExecutionVisits.GmosNorth(visits)  => visits.last.id
      case ExecutionVisits.GmosSouth(visits)  => visits.last.id
      case ExecutionVisits.Flamingos2(visits) => visits.last.id

object LoadedObservation:
  // def apply(obsId: Observation.Id): LoadedObservation = new LoadedObservation(obsId)
  def apply(): LoadedObservation = new LoadedObservation()

  // val obsId: Lens[LoadedObservation, Observation.Id]                = Focus[LoadedObservation](_.obsId)
  val refreshing: Lens[LoadedObservation, Boolean]                  = Focus[LoadedObservation](_.refreshing)
  val errorMsg: Lens[LoadedObservation, Option[String]]             = Focus[LoadedObservation](_.errorMsg)
  val sequenceData: Lens[LoadedObservation, Pot[SequenceData]]      =
    Focus[LoadedObservation](_.sequenceData)
  val visits: Lens[LoadedObservation, Pot[Option[ExecutionVisits]]] =
    Focus[LoadedObservation](_.visits)
