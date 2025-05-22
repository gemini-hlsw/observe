// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import monocle.Focus
import monocle.Lens
import observe.engine.Sequence
import observe.model.Observer
import observe.model.SystemOverrides
import observe.model.enums.PendingObserveCmd

case class SequenceData[F[_]](
  observer:       Option[Observer],
  overrides:      SystemOverrides,
  seqGen:         InstrumentSequenceGen[F],
  seq:            Sequence.State[F],
  pendingObsCmd:  Option[PendingObserveCmd],
  visitStartDone: Boolean,
  atomStartDone:  Boolean,
  cleanup:        F[Unit]
) {
  def withCompleteVisitStart: SequenceData[F] = this.copy(visitStartDone = true)
  def withCompleteAtomStart: SequenceData[F]  = this.copy(atomStartDone = true)
}

object SequenceData {

  def apply[F[_]](
    observer:      Option[Observer],
    overrides:     SystemOverrides,
    seqGen:        InstrumentSequenceGen[F],
    seq:           Sequence.State[F],
    pendingObsCmd: Option[PendingObserveCmd],
    cleanup:       F[Unit]
  ): SequenceData[F[_]] = SequenceData(
    observer,
    overrides,
    seqGen,
    seq,
    pendingObsCmd,
    false,
    false,
    cleanup
  )

  def pendingObsCmd[F[_]]: Lens[SequenceData[F], Option[PendingObserveCmd]] =
    Focus[SequenceData[F]](_.pendingObsCmd)

  def observer[F[_]]: Lens[SequenceData[F], Option[Observer]] = Focus[SequenceData[F]](_.observer)

  def seq[F[_]]: Lens[SequenceData[F], Sequence.State[F]] = Focus[SequenceData[F]](_.seq)

  def overrides[F[_]]: Lens[SequenceData[F], SystemOverrides] = Focus[SequenceData[F]](_.overrides)
}
