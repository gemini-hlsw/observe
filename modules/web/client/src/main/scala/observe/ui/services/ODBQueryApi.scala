// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import cats.effect.Sync
import japgolly.scalajs.react.React
import japgolly.scalajs.react.feature.Context

trait ODBQueryApi[F[_]: Sync]:
  def refreshNighttimeVisits: F[Unit] =
    Sync[F].delay(println("refreshNighttimeVisits invoked with uninitialized ODBQueryApi"))

  def refreshNighttimeSequence: F[Unit] =
    Sync[F].delay(println("refreshNighttimeSequence invoked with uninitialized ODBQueryApi"))

object ODBQueryApi:
  // Default value noop implementations with warning
  val ctx: Context[ODBQueryApi[IO]] = React.createContext(new ODBQueryApi[IO] {})
