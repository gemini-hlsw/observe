// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import lucuma.core.enums.Instrument
import monocle.Focus
import monocle.Getter
import monocle.Traversal
import monocle.function.Each.*
import eu.timepit.refined.cats.given

import scala.collection.immutable.SortedMap

/**
 * Represents a queue with different levels of details. E.g. it could be a list of Ids Or a list of
 * fully hydrated SequenceViews
 */
case class SequencesQueue[T](
  loaded:       Map[Instrument, Observation.Id],
  conditions:   Conditions,
  operator:     Option[Operator],
  queues:       SortedMap[QueueId, ExecutionQueueView],
  sessionQueue: List[T]
) derives Eq

object SequencesQueue:
  def sessionQueueT[T]: Traversal[SequencesQueue[T], T] =
    Focus[SequencesQueue[T]](_.sessionQueue).andThen(each[List[T], T])

  def queueItemG[T](pred: T => Boolean): Getter[SequencesQueue[T], Option[T]] =
    Focus[SequencesQueue[T]](_.sessionQueue).andThen(Getter[List[T], Option[T]](_.find(pred)))
