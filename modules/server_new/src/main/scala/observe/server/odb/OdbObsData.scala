// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import lucuma.core.model.sequence.InstrumentExecutionConfig
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation as OdbObservation

final case class OdbObservationData(
  observation:     OdbObservation,
  executionConfig: InstrumentExecutionConfig
)
