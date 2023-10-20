// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.MonadThrow
import cats.effect.IO
import japgolly.scalajs.react.React
import japgolly.scalajs.react.feature.Context
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import observe.model.enums.Resource
import observe.model.enums.RunOverride

trait SequenceApi[F[_]: MonadThrow]:
  def loadObservation(obsId: Observation.Id, instrument: Instrument): F[Unit] = NotAuthorized

  def setBreakpoint(obsId: Observation.Id, stepId: Step.Id, value: Breakpoint): F[Unit] =
    NotAuthorized

  def start(obsId: Observation.Id, runOverride: RunOverride = RunOverride.Default): F[Unit] =
    NotAuthorized

  def execute(obsId: Observation.Id, stepId: Step.Id, resource: Resource): F[Unit] =
    NotAuthorized

object SequenceApi:
  // Default value is NotAuthorized implementations
  val ctx: Context[SequenceApi[IO]] = React.createContext(new SequenceApi[IO] {})
