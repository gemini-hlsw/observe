// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all.*
import clue.FetchClient
import clue.syntax.*
import lucuma.core.enums.Instrument
import lucuma.core.enums.ObserveClass
import lucuma.core.enums.SequenceType
import lucuma.core.model.Observation
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.TelescopeConfig
import lucuma.schemas.ObservationDB
import observe.common.ObsQueriesGQL.*
import observe.model.dhs.*
import observe.server.ObserveFailure

trait OdbProxy[F[_]] private[odb] () extends OdbEventCommands[F] {
  def read(oid:               Observation.Id): F[ObsQuery.Data.Observation]
  def resetAcquisition(obsId: Observation.Id): F[Unit]

  def obsEditSubscription(obsId: Observation.Id): Resource[F, fs2.Stream[F, Unit]]
}

object OdbProxy {
  def apply[F[_]](
    evCmds:     OdbEventCommands[F],
    subscriber: OdbSubscriber[F]
  )(using Sync[F], FetchClient[F, ObservationDB]): OdbProxy[F] =
    new OdbProxy[F] {
      def read(oid: Observation.Id): F[ObsQuery.Data.Observation] =
        ObsQuery[F]
          .query(oid)
          .raiseGraphQLErrors
          .flatMap:
            _.observation.fold(
              Sync[F].raiseError[ObsQuery.Data.Observation]:
                ObserveFailure.Unexpected(s"OdbProxy: Unable to read observation $oid")
            )(_.pure[F])

      def resetAcquisition(obsId: Observation.Id): F[Unit] =
        ResetAcquisitionMutation[F].execute(obsId = obsId).void

      export evCmds.*
      export subscriber.obsEditSubscription
    }

}
