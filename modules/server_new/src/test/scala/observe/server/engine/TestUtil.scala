// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.engine

import cats.effect.IO
import cats.syntax.option.*
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.SequenceType
import lucuma.core.model.sequence.Atom
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation as OdbObservation
import observe.model.Conditions
import observe.model.Observation
import observe.model.SystemOverrides
import observe.server.EngineState
import observe.server.Selected
import observe.server.SequenceData
import observe.server.SequenceGen

import java.util.UUID

object TestUtil {
  def initStateWithSequence(obsId: Observation.Id, seq: Sequence.State[IO]): EngineState[IO] =
    EngineState[IO](
      queues = Map.empty,
      selected = Selected(
        gmosNorth = SequenceData(
          observer = none,
          overrides = SystemOverrides.AllEnabled,
          seqGen = SequenceGen.GmosNorth[IO](
            obsData = OdbObservation(
              id = obsId,
              title = NonEmptyString.unsafeFrom("Test Observation"),
              program = null,           // Not used in tests
              targetEnvironment = null, // Not used in tests
              constraintSet = null,     // Not used in tests
              timingWindows = List.empty,
              itc = null                // Not used in tests
            ),
            staticCfg = null, // Not used in tests
            nextAtom = SequenceGen.AtomGen.GmosNorth[IO](
              atomId = Atom.Id.fromUuid(UUID.randomUUID()),
              sequenceType = SequenceType.Science,
              steps = List.empty
            )
          ),
          seq = seq,
          pendingObsCmd = none,
          visitStartDone = false,
          cleanup = IO.unit
        ).some,
        gmosSouth = none,
        flamingos2 = none
      ),
      conditions = Conditions.Default,
      operator = None
    )
}
