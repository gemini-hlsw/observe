// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.MonadThrow
import cats.effect.IO
import japgolly.scalajs.react.React
import japgolly.scalajs.react.feature.Context
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import observe.model.enums.Resource
import observe.model.enums.RunOverride

trait SequenceApi[F[_]: MonadThrow]:
  /** Load a sequence in the server */
  def loadObservation(obsId: Observation.Id, instrument: Instrument): F[Unit] = NotAuthorized

  /** Set or unset a single breakpoint */
  def setBreakpoint(obsId: Observation.Id, stepId: Step.Id, value: Breakpoint): F[Unit] =
    NotAuthorized

  /** Set or unset multiple breakpoints */
  def setBreakpoints(obsId: Observation.Id, stepIds: List[Step.Id], value: Breakpoint): F[Unit] =
    NotAuthorized

  /** Start the sequence from the next pending step */
  def start(obsId: Observation.Id, runOverride: RunOverride = RunOverride.Default): F[Unit] =
    NotAuthorized

  /** Start the sequence from the specified step */
  def startFrom(
    obsId:       Observation.Id,
    stepId:      Step.Id,
    runOverride: RunOverride = RunOverride.Default
  ): F[Unit] = NotAuthorized

  /** Pause the sequence after current exposure */
  def pause(obsId: Observation.Id): F[Unit] = NotAuthorized

  /** Cancel requested pause */
  def cancelPause(obsId: Observation.Id): F[Unit] = NotAuthorized

  /** Stop the current exposure */
  def stop(obsId: Observation.Id): F[Unit] = NotAuthorized

  /** N&S: Stop the sequence after the current nod(?) */
  def stopGracefully(obsId: Observation.Id): F[Unit] = NotAuthorized

  /** Stop the sequence immediately */
  def abort(obsId: Observation.Id): F[Unit] = NotAuthorized

  /** Pause the sequence immediately, even mid-exposure */
  def pauseObs(obsId: Observation.Id): F[Unit] = NotAuthorized

  /** N&S: Pause the sequence after the current nod(?) */
  def pauseObsGracefully(obsId: Observation.Id): F[Unit] = NotAuthorized

  /** Resume the current exposure if it was paused mid-exposure */
  def resumeObs(obsId: Observation.Id): F[Unit] = NotAuthorized

  /** Runs a resource or instrument */
  def execute(obsId: Observation.Id, stepId: Step.Id, subsystem: Resource | Instrument): F[Unit] =
    NotAuthorized

  /** Loads next atom of specified sequence type and resumes execution */
  def loadNextAtom(obsId: Observation.Id, sequenceType: SequenceType): F[Unit] = NotAuthorized

object SequenceApi:
  // Default value is NotAuthorized implementations
  val ctx: Context[SequenceApi[IO]] = React.createContext(new SequenceApi[IO] {})
