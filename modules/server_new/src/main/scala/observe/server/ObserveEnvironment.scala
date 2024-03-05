// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import observe.model.Observation
import observe.server.keywords.*
import observe.server.tcs.Tcs

import odb.OdbProxy

/**
 * Describes the parameters for an observation
 */
final case class ObserveEnvironment[F[_]](
  odb:      OdbProxy[F],
  dhs:      DhsClientProvider[F],
  stepType: StepType,
  obsId:    Observation.Id,
  inst:     InstrumentSystem[F],
  otherSys: List[System[F]],
  headers:  HeaderExtraData => List[Header[F]],
  ctx:      HeaderExtraData
) {
  def getTcs: Option[Tcs[F]] = otherSys.collectFirst {
    case x if x.isInstanceOf[Tcs[F]] => x.asInstanceOf[Tcs[F]]
  }
}
