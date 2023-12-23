// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import clue.PersistentClientStatus
import japgolly.scalajs.react.ReactCats.*
import japgolly.scalajs.react.Reusability
import lucuma.core.model.Observation
import lucuma.core.model.sequence.ExecutionConfig
import lucuma.core.model.sequence.gmos.GmosNodAndShuffle
import observe.model.Environment
import observe.model.ExecutionState
import observe.model.NodAndShuffleStatus
import observe.model.RunningStep
import observe.model.SequenceState
import observe.model.StepProgress
import observe.ui.model.enums.ClientMode
import observe.ui.model.enums.ObsClass
import observe.ui.model.enums.OffsetsDisplay

object reusability:
  given Reusability[ClientMode]                            = Reusability.byEq
  given Reusability[SequenceState]                         = Reusability.byEq
  given Reusability[ObservationRequests]                   = Reusability.byEq
  given Reusability[ObsClass]                              = Reusability.byEq
  given Reusability[OffsetsDisplay]                        = Reusability.byEq
  given [S: Eq, D: Eq]: Reusability[ExecutionConfig[S, D]] = Reusability.byEq
  given Reusability[GmosNodAndShuffle]                     = Reusability.byEq
  given Reusability[RunningStep]                           = Reusability.byEq
  given Reusability[NodAndShuffleStatus]                   = Reusability.byEq
  given Reusability[ExecutionState]                        = Reusability.byEq
  given Reusability[Environment]                           = Reusability.byEq
  given Reusability[PersistentClientStatus]                = Reusability.byEq
  given Reusability[StepProgress]                          = Reusability.byEq
  given Reusability[Map[Observation.Id, SequenceState]]    = Reusability.map
  given Reusability[RootModelData]                         = Reusability.byEq
