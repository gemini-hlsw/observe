// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import cats.syntax.eq.*
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.Breakpoint
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import observe.model.ClientId
import observe.model.Observer
import org.http4s.Uri
import org.http4s.client.Client
import org.typelevel.log4cats.Logger

case class SequenceApiImpl(
  client:   Client[IO],
  baseUri:  Uri,
  token:    NonEmptyString,
  observer: Observer,
  clientId: ClientId,
  onError:  Throwable => IO[Unit]
)(using Logger[IO])
    extends SequenceApi[IO]
    with ApiImpl:
  override def setBreakpoint(obsId: Observation.Id, stepId: Step.Id, value: Breakpoint): IO[Unit] =
    request(
      s"$obsId/$stepId/${clientId.value}/breakpoint/${observer.value}/${value === Breakpoint.Enabled}",
      ()
    )
