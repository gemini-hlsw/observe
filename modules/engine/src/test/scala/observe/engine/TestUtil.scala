// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.effect.IO
import monocle.Focus
import monocle.Optional
import monocle.function.Index.*
import observe.model.Observation

object TestUtil {
  final case class TestState(sequences: Map[Observation.Id, Sequence.State[IO]])

  object TestState extends Engine.State[IO, TestState] {
    override def sequenceStateIndex(sid: Observation.Id): Optional[TestState, Sequence.State[IO]] =
      Focus[TestState](_.sequences).andThen(mapIndex[Observation.Id, Sequence.State[IO]].index(sid))

  }
}
