// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.queries

import clue.GraphQLOperation
import clue.annotation.GraphQL
import lucuma.schemas.ObservationDB
import observe.ui.model.ObsSummary

object ObsQueriesGQL {

  @GraphQL
  trait ActiveObservationIdsQuery extends GraphQLOperation[ObservationDB] {
    val document = s"""
      query {
        observations(WHERE: { status: { EQ: READY } }) {
          matches $ObservationSummarySubquery
        }
      }
    """

    object Observations:
      type Matches = ObsSummary
  }

  @GraphQL
  trait ObservationEditSubscription extends GraphQLOperation[ObservationDB] {
    val document = """
      subscription {
        observationEdit {
          id
        }
      }
    """
  }

  @GraphQL
  trait SingleObservationEditSubscription extends GraphQLOperation[ObservationDB] {
    val document = """
      subscription($input: ObservationEditInput!) {
        observationEdit(input: $input) {
          value {
            id
          }
        }
      }
    """
  }
}
