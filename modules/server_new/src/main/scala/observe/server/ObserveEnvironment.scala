// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import eu.timepit.refined.types.numeric.PosInt
import lucuma.core.model.sequence.gmos.{DynamicConfig, StaticConfig}
import lucuma.schemas.ObservationDB.Enums.SequenceType
import lucuma.schemas.ObservationDB.Scalars.StepId
import observe.model.Observation
import observe.server.keywords.*
import observe.server.tcs.Tcs

/**
 * Describes the parameters for an observation
 */
final case class ObserveEnvironment[F[_]](
  odb:          OdbProxy[F],
  dhs:          DhsClientProvider[F],
  stepType:     StepType,
  obsId:        Observation.Id,
  stepId:       StepId,
  datasetIndex: PosInt,
  inst:         InstrumentSystem[F],
  otherSys:     List[System[F]],
  headers:      HeaderExtraData => List[Header[F]],
  ctx:          HeaderExtraData
) {
  def getTcs: Option[Tcs[F]] = otherSys.collectFirst { case x: Tcs[F] => x }
}
