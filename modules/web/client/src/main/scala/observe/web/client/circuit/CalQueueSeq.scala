// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import cats._
import cats.syntax.all._
import monocle.Getter
import monocle.macros.Lenses
import monocle.std
import observe.model.Observation
import observe.model.SequenceMetadata
import observe.model.SequenceState
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.model.enum.Instrument

@Lenses
final case class CalQueueSeq(idName: Observation.IdName, i: Instrument, status: SequenceState)

object CalQueueSeq {
  implicit val eq: Eq[CalQueueSeq] =
    Eq.by(x => (x.idName, x.i, x.status))

  def calQueueSeqG(
    id: Observation.Id
  ): Getter[SequencesQueue[SequenceView], Option[CalQueueSeq]] = {
    val seqO =
      SequencesQueue.queueItemG[SequenceView](_.idName.id === id) ^<-?
        std.option.some

    val sidO = seqO ^|-> SequenceView.idName
    val siO  = seqO ^|-> SequenceView.metadata ^|-> SequenceMetadata.instrument
    val siS  = seqO ^|-> SequenceView.status

    (Getter(sidO.headOption)
      .zip(Getter(siO.headOption).zip(Getter(siS.headOption)))) >>> {
      case (Some(idName), (Some(i), Some(s))) => CalQueueSeq(idName, i, s).some
      case _                                  => none
    }
  }
}
