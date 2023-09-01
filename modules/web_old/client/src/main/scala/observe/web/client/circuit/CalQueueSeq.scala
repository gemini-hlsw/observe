// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import cats.*
import cats.syntax.all.*
import monocle.Getter
import monocle.std
import observe.model.Observation
import observe.model.SequenceMetadata
import observe.model.SequenceState
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.model.enums.Instrument

final case class CalQueueSeq(obsId: Observation.Id, i: Instrument, status: SequenceState)

object CalQueueSeq {
  given Eq[CalQueueSeq] =
    Eq.by(x => (x.obsId, x.i, x.status))

  def calQueueSeqG(
    id: Observation.Id
  ): Getter[SequencesQueue[SequenceView], Option[CalQueueSeq]] = {
    val seqO =
      SequencesQueue
        .queueItemG[SequenceView](_.obsId === id)
        .andThen(std.option.some[SequenceView])

    val sidO = seqO.andThen(SequenceView.obsId)
    val siO  = seqO.andThen(SequenceView.metadata).andThen(SequenceMetadata.instrument)
    val siS  = seqO.andThen(SequenceView.status)

    (Getter(sidO.headOption)
      .zip(Getter(siO.headOption).zip(Getter(siS.headOption)))) >>> {
      case (Some(obsId), (Some(i), Some(s))) => CalQueueSeq(obsId, i, s).some
      case _                                 => none
    }
  }
}
