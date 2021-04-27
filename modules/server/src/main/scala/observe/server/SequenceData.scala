// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import monocle.macros.Lenses
import observe.engine.Sequence
import observe.model.NodAndShuffleStep.PendingObserveCmd
import observe.model.Observer
import observe.model.SystemOverrides

@Lenses
final case class SequenceData[F[_]](
  observer:      Option[Observer],
  overrides:     SystemOverrides,
  seqGen:        SequenceGen[F],
  seq:           Sequence.State[F],
  pendingObsCmd: Option[PendingObserveCmd]
)
