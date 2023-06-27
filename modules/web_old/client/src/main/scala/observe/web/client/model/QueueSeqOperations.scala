// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats.Eq
import lucuma.core.util.Enumerated

sealed abstract class RemoveSeqQueue(val tag: String) extends Product with Serializable
object RemoveSeqQueue {
  case object RemoveSeqQueueIdle     extends RemoveSeqQueue("RemoveSeqQueueIdle")
  case object RemoveSeqQueueInFlight extends RemoveSeqQueue("RemoveSeqQueueInFlight")

  given Enumerated[RemoveSeqQueue] =
    Enumerated.from(RemoveSeqQueueIdle, RemoveSeqQueueInFlight).withTag(_.tag)
}

sealed abstract class MoveSeqQueue(val tag: String) extends Product with Serializable
object MoveSeqQueue {
  case object MoveSeqQueueInFlight extends MoveSeqQueue("MoveSeqQueueInFlight")
  case object MoveSeqQueueIdle     extends MoveSeqQueue("MoveSeqQueueIdle")

  given Enumerated[MoveSeqQueue] =
    Enumerated.from(MoveSeqQueueIdle, MoveSeqQueueInFlight).withTag(_.tag)
}

/**
 * Hold transient states while excuting an operation on a queue element
 */
final case class QueueSeqOperations(removeSeqQueue: RemoveSeqQueue, moveSeqQueue: MoveSeqQueue)

object QueueSeqOperations {
  given Eq[QueueSeqOperations] =
    Eq.by(x => (x.removeSeqQueue, x.moveSeqQueue))

  val Default: QueueSeqOperations =
    QueueSeqOperations(RemoveSeqQueue.RemoveSeqQueueIdle, MoveSeqQueue.MoveSeqQueueInFlight)
}
