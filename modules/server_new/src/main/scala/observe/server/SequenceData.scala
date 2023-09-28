// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import lucuma.schemas.ObservationDB.Scalars.VisitId
import observe.engine.Sequence
import observe.model.NodAndShuffleStep.PendingObserveCmd
import observe.model.{Observer, SystemOverrides}
import monocle.{Focus, Lens}

final case class SequenceData[F[_]](
  observer:      Option[Observer],
  visitId:       Option[VisitId],
  overrides:     SystemOverrides,
  seqGen:        SequenceGen[F],
  seq:           Sequence.State[F],
  pendingObsCmd: Option[PendingObserveCmd]
)

object SequenceData {

  def pendingObsCmd[F[_]]: Lens[SequenceData[F], Option[PendingObserveCmd]] =
    Focus[SequenceData[F]](_.pendingObsCmd)

  def observer[F[_]]: Lens[SequenceData[F], Option[Observer]] = Focus[SequenceData[F]](_.observer)

  def seq[F[_]]: Lens[SequenceData[F], Sequence.State[F]] = Focus[SequenceData[F]](_.seq)

  def visitId[F[_]]: Lens[SequenceData[F], Option[VisitId]] = Focus[SequenceData[F]](_.visitId)

}
