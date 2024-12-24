// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.services

import cats.effect.IO
import cats.effect.Resource
import cats.effect.std.Queue
import cats.syntax.all.*
import clue.PersistentClientStatus
import crystal.*
import crystal.react.*
import crystal.react.hooks.*
import crystal.react.syntax.pot.given
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.react.common.ReactFnProps
import lucuma.schemas.odb.input.*
import lucuma.ui.reusability.given
import lucuma.ui.syntax.effect.*
import observe.model.SequenceState
import observe.queries.ObsQueriesGQL
import observe.ui.DefaultErrorPolicy
import observe.ui.model.AppContext
import observe.ui.model.LoadedObservation
import observe.ui.services.ODBQueryApi
import observe.ui.services.SequenceApi

// Renderless component that reloads observation summaries and sequences when observations are selected.
case class ObservationSyncer(
  nighttimeObservation:              View[Option[LoadedObservation]],
  nighttimeObservationSequenceState: SequenceState
) extends ReactFnProps(ObservationSyncer.component)

object ObservationSyncer:
  private type Props = ObservationSyncer

  private val component =
    ScalaFnComponent[Props]: props =>
      for
        ctx             <- useContext(AppContext.ctx)
        sequenceApi     <- useContext(SequenceApi.ctx)
        odbQueryApi     <- useContext(ODBQueryApi.ctx)
        subscribedObsId <- useRef(none[Observation.Id])
        signalQueue     <- useEffectResultOnMount(Queue.unbounded[IO, Boolean])
        _               <- useEffect: // Signal wheter sequence is stopped
                             signalQueue.toOption
                               .map(_.offer(!props.nighttimeObservationSequenceState.isRunning))
                               .orEmpty
        signal          <-            // Signals when obs stopped or ODB reconnected
          useMemo(signalQueue.void): _ =>
            signalQueue.map:
              fs2.Stream
                .fromQueueUnterminated(_, 1)
                .changes
                .filter(_ === true)
                .void
                .merge:
                  ctx.odbClient.statusStream.changes
                    .filter(_ === PersistentClientStatus.Connected)
                    .void
        _               <-
          useEffectStreamResourceWithDeps(
            (props.nighttimeObservation.get.map(_.obsId).toPot, signal.sequencePot).tupled.toOption
          ): deps =>
            import ctx.given

            deps
              .map: (obsId, signal) =>
                Option
                  .unless(subscribedObsId.value.contains(obsId)):
                    Resource.pure(
                      fs2.Stream.eval:
                        subscribedObsId.setAsync(obsId.some)
                    ) >>
                      (odbQueryApi.refreshNighttimeSequence,
                       odbQueryApi.refreshNighttimeVisits
                      ).parTupled.void
                        .reRunOnResourceSignals:
                          // Eventually, there will be another subscription notifying of sequence/visits changes
                          ObsQueriesGQL.SingleObservationEditSubscription
                            .subscribe[IO](obsId.toObservationEditInput)
                            .map(_.merge(signal))
                  .orEmpty
              .getOrElse:
                // If connection broken, or observation unselected, cleanup sequence and visits
                Resource.pure:
                  fs2.Stream.eval:
                    props.nighttimeObservation.async.mod(_.map(_.reset))
      yield EmptyVdom
