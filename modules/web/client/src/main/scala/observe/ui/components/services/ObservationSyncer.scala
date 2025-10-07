// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.services

import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all.*
import clue.PersistentClientStatus
import clue.StreamingClient
import crystal.*
import crystal.react.*
import crystal.react.hooks.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.react.common.ReactFnComponent
import lucuma.react.common.ReactFnProps
import lucuma.schemas.ObservationDB
import lucuma.schemas.odb.input.*
import lucuma.ui.syntax.effect.*
import monocle.Iso
import observe.queries.ObsQueriesGQL
import observe.ui.model.AppContext
import observe.ui.model.LoadedObservation
import observe.ui.model.reusability.given
import observe.ui.services.ODBQueryApi
import observe.ui.services.SequenceApi
import org.typelevel.log4cats.Logger

// Renderless component that reloads observation summaries and sequences when observations are selected.
case class ObservationSyncer(
  loadedObservations: View[Map[Observation.Id, LoadedObservation]]
) extends ReactFnProps(ObservationSyncer)

object ObservationSyncer
    extends ReactFnComponent[ObservationSyncer](props =>
      def updateSequence(
        obsId:       Observation.Id,
        obs:         View[LoadedObservation],
        odbQueryApi: ODBQueryApi[IO]
      )(using Logger[IO]): IO[Unit] = {
        val refreshSequence: IO[Unit] =
          odbQueryApi
            .querySequence(obsId)
            .attempt
            .flatMap: seqData =>
              obs.async.mod(_.withSequenceData(seqData))

        val refreshVisits: IO[Unit] =
          odbQueryApi
            .queryVisits(obsId, obs.get.lastVisitId)
            .attempt
            .flatMap: visits =>
              obs.async.mod(_.addVisits(visits))

        (refreshSequence, refreshVisits).parTupled.void
      }

      // Resource providing a stream of updates for the given observation.
      def observationUpdater(
        obsId:       Observation.Id,
        obs:         View[LoadedObservation],
        odbQueryApi: ODBQueryApi[IO]
      )(using
        StreamingClient[IO, ObservationDB],
        Logger[IO]
      ): Resource[IO, fs2.Stream[IO, Unit]] = {
        val obsChangedSubscription: Resource[IO, fs2.Stream[IO, Unit]] =
          ObsQueriesGQL.SingleObservationEditSubscription
            .subscribe[IO](obsId.toObservationEditInput)
            .logGraphQLErrors: _ =>
              "Error received in ObsQueriesGQL.SingleObservationEditSubscription"
            .map(_.void)

        val datasetChangedSubscription: Resource[IO, fs2.Stream[IO, Unit]] =
          ObsQueriesGQL.DatasetEditSubscription
            .subscribe[IO](obsId)
            .logGraphQLErrors: _ =>
              "Error received in ObsQueriesGQL.DatasetEditSubscription"
            .map(_.void)

        val requerySignal: Resource[IO, fs2.Stream[IO, Unit]] =
          (obsChangedSubscription, datasetChangedSubscription).mapN(_.merge(_))

        updateSequence(obsId, obs, odbQueryApi)
          .reRunOnResourceSignals:
            requerySignal
      }

      def loadedObsView(obsId: Observation.Id): Option[View[LoadedObservation]] =
        props.loadedObservations
          .zoom(Iso.id[Map[Observation.Id, LoadedObservation]].index(obsId))
          .toOptionView

      for
        ctx                    <- useContext(AppContext.ctx)
        sequenceApi            <- useContext(SequenceApi.ctx)
        odbQueryApi            <- useContext(ODBQueryApi.ctx)
        subscribedObservations <- useRef(Map.empty[Observation.Id, IO[Unit]]) // Cancel effects
        _                      <-
          useEffectWithDeps(props.loadedObservations.get.keySet): loadedObsIds =>
            import ctx.given

            val toSubscribe: Set[Observation.Id] =
              loadedObsIds -- subscribedObservations.value.keySet

            val toUnsubscribe: Set[Observation.Id] =
              subscribedObservations.value.keySet -- loadedObsIds

            val subEffects: Set[IO[Unit]] =
              toSubscribe.map: obsId =>
                loadedObsView(obsId)
                  .fold(IO.unit): obsView =>
                    for
                      (_, canceller) <- observationUpdater(obsId, obsView, odbQueryApi)
                                          .map(_.compile.drain)
                                          .flatMap(_.background)
                                          .allocated
                      _              <- subscribedObservations.modAsync(_ + (obsId -> canceller))
                    yield ()

            val unsubscribeEffects: Set[IO[Unit]] =
              toUnsubscribe.map: obsId =>
                for
                  cancel <- subscribedObservations.getAsync.map(_.get(obsId))
                  _      <- subscribedObservations.modAsync(_ - obsId)
                  _      <- cancel.foldMap(identity)
                yield ()

            (subEffects ++ unsubscribeEffects).toList.parSequence_
        odbConnectionStatus    <- useStreamOnMount(ctx.odbClient.statusStream.changes)
        _                      <-
          useEffectWhenDepsReadyOrChange(odbConnectionStatus.toPot):
            // On reconnect, reset and refresh all sequences and visits.
            case PersistentClientStatus.Connected =>
              import ctx.given

              props.loadedObservations.get.keySet.toList.parTraverse_ : obsId =>
                loadedObsView(obsId)
                  .fold(IO.unit): obsView =>
                    obsView.async.mod(_.reset) >>
                      updateSequence(obsId, obsView, odbQueryApi)
            case _                                => // On disconnect, do nothing.
              IO.unit
      yield EmptyVdom
    )
