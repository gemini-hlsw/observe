// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.queries

import clue.GraphQLOperation
import clue.annotation.GraphQL
import lucuma.schemas.ObservationDB

object ObsQueriesGQL {

  @GraphQL
  trait ActiveObservationIdsQuery extends GraphQLOperation[ObservationDB] {
    val document = s"""
      query($$site: Site!, $$date: Date!) {
        observations(
          WHERE: {
            site: { EQ: $$site },
            program: {
              AND: [
                { activeStart: { LTE: $$date } },
                { activeEnd: { GTE: $$date } }
              ]
            },
            reference: { IS_NULL: false },
            workflow: {
              workflowState: {
                IN: [READY, ONGOING, COMPLETED]
              }
            }
          }
        ) {
          matches $ObservationSummarySubquery
        }
      }
    """
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

  @GraphQL
  trait DatasetEditSubscription extends GraphQLOperation[ObservationDB]:
    val document = s"""
      subscription($$obsId: ObservationId!) {
        datasetEdit(input: { observationId: $$obsId }) {
          value { id }
        }
      }
    """
}
