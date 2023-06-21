// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.queries

import clue.GraphQLOperation
import clue.annotation.GraphQL
import lucuma.schemas.ObservationDB
// import lucuma.core.enums.Site
// import io.circe.{Decoder, Encoder}
// import io.circe.generic.auto.*
// import lucuma.core.math
// import lucuma.core.enums
// import lucuma.core.model
// import cats.syntax.functor.*
// import lucuma.core.model.sequence.{Atom, ExecutionSequence, Step}
// import lucuma.core.model.sequence.gmos.{DynamicConfig, GmosGratingConfig, StaticConfig}

import java.time
import lucuma.core.model.{ExecutionEvent, Observation, Target}

// gql: import lucuma.schemas.decoders.given
// gql: import io.circe.refined.*
// gql: import lucuma.odb.json.sequence.given
// gql: import lucuma.odb.json.gmos.given

object ObsQueriesGQL {

  @GraphQL
  trait ActiveObservationIdsQuery extends GraphQLOperation[ObservationDB] {
    val document = """
      query {
        observations(WHERE: { status: { EQ: READY } }) {
          matches {
            id
            title
            subtitle
            status
            activeStatus
            instrument
          }
        }
      }
    """
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
}
