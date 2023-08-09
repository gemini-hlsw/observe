// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import japgolly.scalajs.react.ReactCats.*
import japgolly.scalajs.react.Reusability
import lucuma.core.model.sequence.ExecutionConfig
import lucuma.core.model.sequence.gmos.GmosNodAndShuffle
import lucuma.core.util.NewType
import observe.model.ClientStatus
import observe.model.NodAndShuffleStatus
import observe.model.RunningStep
import observe.model.SequenceStep
import observe.model.enums.SequenceState
import observe.ui.model.enums.ObsClass
import observe.ui.model.enums.OffsetsDisplay

object reusability:
  given Reusability[ClientStatus]                          = Reusability.byEq
  given Reusability[SequenceState]                         = Reusability.byEq
  given Reusability[TabOperations]                         = Reusability.byEq
  given Reusability[ObsClass]                              = Reusability.byEq
  given Reusability[OffsetsDisplay]                        = Reusability.byEq
  // given Reusability[Execution]      = Reusability.byEq
  given [D: Eq]: Reusability[SequenceStep[D]]              = Reusability.byEq
  // given [D: Eq]: Reusability[StepTableRow[D]]              = Reusability.byEq
  given [S: Eq, D: Eq]: Reusability[ExecutionConfig[S, D]] = Reusability.byEq
  given Reusability[GmosNodAndShuffle]                     = Reusability.byEq
  given Reusability[RunningStep]                           = Reusability.byEq
  given Reusability[NodAndShuffleStatus]                   = Reusability.byEq

  // TODO Move to lucuma-ui (and unify with explore)
  given reusabilityNewType[W, T <: NewType[W]#Type](using
    reusability: Reusability[W]
  ): Reusability[T] =
    reusability.asInstanceOf[Reusability[T]]
