// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import lucuma.schemas.ObservationDB.Scalars.VisitId
import observe.engine.Sequence
import observe.model.NodAndShuffleStep.PendingObserveCmd
import observe.model.Observer
import observe.model.SystemOverrides
import observe.model.Observation

final case class SequenceData[F[_]](
  id:            Observation.Id,
  name:          Observation.Name,
  observer:      Option[Observer],
  visitId:       Option[VisitId],
  overrides:     SystemOverrides,
  seqGen:        SequenceGen[F],
  seq:           Sequence.State[F],
  pendingObsCmd: Option[PendingObserveCmd]
)
