// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.syntax.all.*
import crystal.Pot
import lucuma.core.model.Observation
import lucuma.core.model.sequence.InstrumentExecutionConfig
import monocle.Focus
import monocle.Lens

case class LoadedObservation private (
  obsId:  Observation.Id,
  config: Pot[InstrumentExecutionConfig] = Pot.pending
):
  def withConfig(config: Either[Throwable, Option[InstrumentExecutionConfig]]): LoadedObservation =
    copy(config = Pot.fromTry(config.map(Pot.fromOption).toTry).flatten)

object LoadedObservation:
  def apply(obsId: Observation.Id): LoadedObservation = new LoadedObservation(obsId)

  val obsId: Lens[LoadedObservation, Observation.Id]                  = Focus[LoadedObservation](_.obsId)
  val config: Lens[LoadedObservation, Pot[InstrumentExecutionConfig]] =
    Focus[LoadedObservation](_.config)
