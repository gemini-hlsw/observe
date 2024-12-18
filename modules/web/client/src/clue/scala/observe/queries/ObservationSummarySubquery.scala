// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.queries

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
          program { id }
          title
          subtitle
          instrument
          observationTime
          posAngleConstraint $PosAngleConstraintSubquery
          constraintSet $ConstraintSetSubquery
          timingWindows $TimingWindowSubquery
          attachments { id }
          observingMode $ObservingModeSubquery
          reference {
            label
          }
        }
      """
