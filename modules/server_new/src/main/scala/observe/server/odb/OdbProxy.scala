// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all.*
import clue.FetchClient
import clue.syntax.*
import lucuma.core.enums.ObserveClass
import lucuma.core.model.Observation
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.TelescopeConfig
import lucuma.schemas.ObservationDB
import observe.common.ObsQueriesGQL.*
import observe.model.dhs.*
import observe.server.ObserveFailure

trait OdbProxy[F[_]] private[odb] () extends OdbCommands[F] {
  def read(oid:               Observation.Id): F[OdbObservationData]
  def resetAcquisition(obsId: Observation.Id): F[Unit]

  def obsEditSubscription(obsId: Observation.Id): Resource[F, fs2.Stream[F, Unit]]
}

object OdbProxy {
  def apply[F[_]](
    evCmds:     OdbCommands[F],
    subscriber: OdbSubscriber[F]
  )(using FetchClient[F, ObservationDB])(using F: Sync[F]): OdbProxy[F] =
    new OdbProxy[F] {
      def read(oid: Observation.Id): F[OdbObservationData] =
        ObsQuery[F]
          .query(oid)
          .raiseGraphQLErrors
          .flatMap: data =>
            (data.observation, data.executionConfig).tupled
              .fold(
                F.raiseError[OdbObservationData]:
                  ObserveFailure.Unexpected(s"OdbProxy: Unable to read observation $oid")
              )((obs, ec) => OdbObservationData(obs, ec).pure[F])

      def resetAcquisition(obsId: Observation.Id): F[Unit] =
        ResetAcquisitionMutation[F].execute(obsId = obsId).void

      export evCmds.*
      export subscriber.obsEditSubscription
    }

}
