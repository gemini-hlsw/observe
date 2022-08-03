// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import lucuma.schemas.ObservationDB.Enums.SequenceType
import observe.model.{Observation, StepId}
import observe.server.keywords._
import observe.server.tcs.Tcs

/**
 * Describes the parameters for an observation
 */
final case class ObserveEnvironment[F[_]](
  odb:          OdbProxy[F],
  dhs:          DhsClient[F],
  config:       CleanConfig,
  stepType:     StepType,
  obsIdName:    Observation.IdName,
  stepId:       StepId,
  datasetIndex: Int,
  sequenceType: SequenceType,
  inst:         InstrumentSystem[F],
  insSpecs:     InstrumentSpecifics,
  otherSys:     List[System[F]],
  headers:      HeaderExtraData => List[Header[F]],
  ctx:          HeaderExtraData
) {
  def getTcs: Option[Tcs[F]] = otherSys.collectFirst { case x: Tcs[F] => x }
}
