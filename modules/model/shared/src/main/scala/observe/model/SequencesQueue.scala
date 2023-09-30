// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import monocle.Focus
import monocle.Getter
import monocle.Traversal
import monocle.function.Each.*

import scala.collection.immutable.SortedMap
import lucuma.core.enums.Instrument

/**
 * Represents a queue with different levels of details. E.g. it could be a list of Ids Or a list of
 * fully hydrated SequenceViews
 */
final case class SequencesQueue[T](
  loaded:       Map[Instrument, Observation.Id],
  conditions:   Conditions,
  operator:     Option[Operator],
  queues:       SortedMap[QueueId, ExecutionQueueView],
  sessionQueue: List[T]
)

object SequencesQueue {
  given [T: Eq]: Eq[SequencesQueue[T]] =
    Eq.by(x => (x.loaded, x.conditions, x.operator, x.queues, x.sessionQueue))

  def sessionQueueT[T]: Traversal[SequencesQueue[T], T] =
    Focus[SequencesQueue[T]](_.sessionQueue).andThen(each[List[T], T])

  def queueItemG[T](pred: T => Boolean): Getter[SequencesQueue[T], Option[T]] =
    Focus[SequencesQueue[T]](_.sessionQueue).andThen(Getter[List[T], Option[T]](_.find(pred)))
}
