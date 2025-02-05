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
    // TODO The ODB API doesn't provide a way to filter ready observations,
    // so we filter by accepted proposals for now.
    // Revise this when the API supports it OR we start getting obersvations from the scheduler.
    val document = s"""
      query {
        observations(
          WHERE: {
            program: {
              OR: [
                { proposalStatus: { EQ: ACCEPTED } }
                { type: { IN: [ENGINEERING, CALIBRATION] } }
              ] 
            }
          }
        ) {
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
          value {
            id
          }
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
