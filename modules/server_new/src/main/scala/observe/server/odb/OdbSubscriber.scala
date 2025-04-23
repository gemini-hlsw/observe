// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.effect.Resource
import cats.syntax.all.*
import clue.StreamingClient
import lucuma.core.model.Observation
import lucuma.schemas.ObservationDB
import lucuma.schemas.odb.input.*
import observe.common.ObsQueriesGQL

case class OdbSubscriber[F[_]]()(using client: StreamingClient[F, ObservationDB]):
  def obsEditSubscription(obsId: Observation.Id): Resource[F, fs2.Stream[F, Unit]] =
    ObsQueriesGQL.ObsEditSubscription
      .subscribe(obsId.toObservationEditInput)
      .map(_.filter(_.data.exists(_.observationEdit.observationId == obsId)).void)
