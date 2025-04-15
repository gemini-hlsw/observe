// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.effect.Resource
import cats.syntax.all.*
import clue.StreamingClient
import lucuma.core.model.Observation
import lucuma.schemas.ObservationDB
import observe.common.ObsQueriesGQL

case class OdbSubscriber[F[_]]()(using client: StreamingClient[F, ObservationDB]):
  def obsEditSubscription(obsId: Observation.Id): Resource[F, fs2.Stream[F, Unit]] =
    ObsQueriesGQL.ObservationEditSubscription.subscribe(obsId).map(_.void)
