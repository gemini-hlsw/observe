// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.syntax.all.*
import crystal.Pot
import lucuma.core.model.Observation
import lucuma.core.model.sequence.InstrumentExecutionConfig

case class LoadedObservation private (
  // summary: ObsSummary,
  obsId:  Observation.Id,
  config: Pot[InstrumentExecutionConfig] = Pot.pending
):
  // export summary.obsId

  def withConfig(config: Either[Throwable, Option[InstrumentExecutionConfig]]): LoadedObservation =
    copy(config = Pot.fromTry(config.map(Pot.fromOption).toTry).flatten)

  // def unPot: Pot[(Observation.Id, InstrumentExecutionConfig)] =
  //   config.map(c => (obsId, c))

object LoadedObservation:
  def apply(obsId: Observation.Id): LoadedObservation = new LoadedObservation(obsId)
