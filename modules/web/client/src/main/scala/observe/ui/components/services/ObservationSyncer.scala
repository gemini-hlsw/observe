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
import lucuma.ui.reusability.given
import lucuma.ui.syntax.effect.*
import monocle.Iso
import observe.model.SequenceState
import observe.queries.ObsQueriesGQL
import observe.ui.model.AppContext
import observe.ui.model.LoadedObservation
import observe.ui.services.ODBQueryApi
import observe.ui.services.SequenceApi
import org.typelevel.log4cats.Logger

// Renderless component that reloads observation summaries and sequences when observations are selected.
case class ObservationSyncer(
  loadedObservations: View[Map[Observation.Id, LoadedObservation]],
  sequenceStates:     Map[Observation.Id, SequenceState] // TODO Unused for the moment
) extends ReactFnProps(ObservationSyncer)

object ObservationSyncer
    extends ReactFnComponent[ObservationSyncer](props =>
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

          // Option
          //   .unless(subscribedObsId.value.contains(obsId)):
          //     Resource.pure(
          //       fs2.Stream.eval:
          //         subscribedObsId.setAsync(obsId.some)
          //     ) >>
        (refreshSequence, refreshVisits).parTupled.void
          .reRunOnResourceSignals:
            requerySignal
        // .map(_.merge(odbConnectedSignal))
        // .orEmpty
      }

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
                props.loadedObservations
                  .zoom(Iso.id[Map[Observation.Id, LoadedObservation]].index(obsId))
                  .toOptionView
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
      // stoppedSignal      <- useSignalStream(!props.nighttimeObservationSequenceState.isRunning)
      // odbConnectedSignal <-
      //   useMemo(stoppedSignal):
      //     _.map: // Reusable
      //       _.map: // Pot
      //         _.filter(_ === true) // Only signal when sequence is stopped
      //           .void
      //           .merge: // Signal when ODB reconnects
      //             ctx.odbClient.statusStream.changes
      //               .filter(_ === PersistentClientStatus.Connected)
      //               .void
      // _                  <-
      //   useEffectStreamResourceWithDeps(
      //     (props.nighttimeObservation.get.map(_.obsId).toPot,
      //      odbConnectedSignal.sequencePot
      //     ).tupled.toOption
      //   ): deps =>
      //     import ctx.given

      //     deps
      //       .map: (obsId, odbConnectedSignal) =>
      //         val obsChangedSubscription: Resource[IO, fs2.Stream[IO, Unit]] =
      //           ObsQueriesGQL.SingleObservationEditSubscription
      //             .subscribe[IO](obsId.toObservationEditInput)
      //             .logGraphQLErrors: _ =>
      //               "Error received in ObsQueriesGQL.SingleObservationEditSubscription"
      //             .map(_.void)

      //         val datasetChangedSubscription: Resource[IO, fs2.Stream[IO, Unit]] =
      //           ObsQueriesGQL.DatasetEditSubscription
      //             .subscribe[IO](obsId)
      //             .logGraphQLErrors: _ =>
      //               "Error received in ObsQueriesGQL.DatasetEditSubscription"
      //             .map(_.void)

      //         val requerySignal: Resource[IO, fs2.Stream[IO, Unit]] =
      //           (obsChangedSubscription, datasetChangedSubscription).mapN(_.merge(_))

      //         Option
      //           .unless(subscribedObsId.value.contains(obsId)):
      //             Resource.pure(
      //               fs2.Stream.eval:
      //                 subscribedObsId.setAsync(obsId.some)
      //             ) >>
      //               (odbQueryApi.refreshNighttimeSequence,
      //                odbQueryApi.refreshNighttimeVisits
      //               ).parTupled.void
      //                 .reRunOnResourceSignals:
      //                   requerySignal
      //                     .map(_.merge(odbConnectedSignal))
      //           .orEmpty
      //       .getOrElse:
      //         // If connection broken, or observation unselected, cleanup sequence and visits
      //         Resource.pure:
      //           fs2.Stream.eval:
      //             props.nighttimeObservation.async.mod(_.map(_.reset))
      yield EmptyVdom
    )
