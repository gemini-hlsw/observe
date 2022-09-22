// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.syntax.all.*
import lucuma.core.util.NewType
import observe.ui.model.enums.ObsClass

// opaque type SessionQueueFilter = Option[ObsClass]

object SessionQueueFilter extends NewType[Option[ObsClass]]:
  inline def All: SessionQueueFilter       = SessionQueueFilter(none)
  inline def Daytime: SessionQueueFilter   = SessionQueueFilter(ObsClass.Daytime.some)
  inline def Nighttime: SessionQueueFilter = SessionQueueFilter(ObsClass.Nighttime.some)

  extension (filter: SessionQueueFilter)
    def isSelected(obsClass: ObsClass): Boolean =
      filter.value.contains(obsClass)

    def toggle(newObsClass: ObsClass): SessionQueueFilter =
      filter.value match
        case Some(obsClass) if obsClass === newObsClass => SessionQueueFilter(none)
        case _                                          => SessionQueueFilter(newObsClass.some)

    // def dayTimeSelected: Boolean = filter.value match
    //   case Some(ObsClass.Daytime) => true
    //   case _                      => false

    // def nightTimeSelected: Boolean = filter.value match
    //   case Some(ObsClass.Nighttime) => true
    //   case _                        => false

    def filter(seq: List[SessionQueueRow]): List[SessionQueueRow] =
      filter.value match
        case None           => seq
        case Some(obsClass) => seq.filter(_.obsClass === obsClass)

    // def filterS(seq: List[SequenceView]): List[SequenceView] =
    //   obsClass match {
    //     case ObsClass.All       => seq
    //     case ObsClass.Daytime   =>
    //       seq.filter(
    //         obsClassT
    //           .headOption(_)
    //           .map(ObsClass.fromString) === ObsClass.Daytime.some
    //       )
    //     case ObsClass.Nighttime =>
    //       seq.filter(
    //         obsClassT
    //           .headOption(_)
    //           .map(ObsClass.fromString) === ObsClass.Nighttime.some
    //       )
    //   }

    def isFilterApplied: Boolean = filter.value.isDefined

type SessionQueueFilter = SessionQueueFilter.Type
