// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package queries.common

import clue.GraphQLSubquery
import observe.ui.model.ObsSummary
import lucuma.schemas.ObservationDB
import lucuma.schemas.odb.*
import clue.annotation.GraphQL

@GraphQL
object ObservationSummarySubquery
    extends GraphQLSubquery.Typed[ObservationDB, ObsSummary]("Observation"):

  override val subquery: String = s"""
        {
          id
          title
          visualizationTime
          posAngleConstraint $PosAngleConstraintSubquery
          constraintSet $ConstraintSetSubquery
          timingWindows $TimingWindowSubquery
          obsAttachments {
            id
          }
          observingMode $ObservingModeSubquery
        }
      """
