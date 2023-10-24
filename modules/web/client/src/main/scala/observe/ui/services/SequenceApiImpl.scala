// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import cats.syntax.eq.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.util.Enumerated
import observe.model.ClientId
import observe.model.Observer
import observe.model.enums.Resource
import observe.model.enums.RunOverride
import observe.model.given
import org.http4s.Query
import org.http4s.Uri
import org.typelevel.log4cats.Logger

case class SequenceApiImpl(
  client:   ApiClient,
  observer: Observer
)(using Logger[IO])
    extends SequenceApi[IO]:
  override def loadObservation(obsId: Observation.Id, instrument: Instrument): IO[Unit] =
    client.postNoData:
      Uri.Path.empty / "load" / instrument.tag / obsId.toString / client.clientId.value / observer.toString

  override def setBreakpoint(
    obsId:  Observation.Id,
    stepId: Step.Id,
    value:  Breakpoint
  ): IO[Unit] =
    client.postNoData:
      Uri.Path.empty / obsId.toString / stepId.toString / client.clientId.value / "breakpoint" / observer.toString / (value === Breakpoint.Enabled)

  override def setBreakpoints(
    obsId:   Observation.Id,
    stepIds: List[Step.Id],
    value:   Breakpoint
  ): IO[Unit] =
    client.post(
      Uri.Path.empty / obsId.toString / client.clientId.value / "breakpoints" / observer.toString / (value === Breakpoint.Enabled),
      stepIds
    )

  override def start(
    obsId:       Observation.Id,
    runOverride: RunOverride = RunOverride.Default
  ): IO[Unit] =
    client.postNoData(
      Uri.Path.empty / obsId.toString / client.clientId.value / "start" / observer.toString,
      if (runOverride === RunOverride.Override) Query.fromPairs("overrideTargetCheck" -> "true")
      else Query.empty
    )

  override def execute(
    obsId:     Observation.Id,
    stepId:    Step.Id,
    subsystem: Resource | Instrument
  ): IO[Unit] =
    client.postNoData:
      Uri.Path.empty / obsId.toString / stepId.toString / client.clientId.value / "execute" /
        Enumerated[Resource | Instrument].tag(subsystem) / observer.toString
