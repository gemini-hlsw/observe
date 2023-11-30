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
  obsId:   Observation.Id,
  summary: Pot[ObsSummary] = Pot.pending,
  config:  Pot[InstrumentExecutionConfig] = Pot.pending
):
  def withSummary(summary: Either[Throwable, ObsSummary]): LoadedObservation =
    copy(summary = Pot.fromTry(summary.toTry))

  def withConfig(config: Either[Throwable, Option[InstrumentExecutionConfig]]): LoadedObservation =
    copy(config = Pot.fromTry(config.map(Pot.fromOption).toTry).flatten)

  def unPot: Pot[(Observation.Id, ObsSummary, InstrumentExecutionConfig)] =
    (summary, config).mapN((s, c) => (obsId, s, c))

object LoadedObservation:
  def apply(obsId: Observation.Id): LoadedObservation = new LoadedObservation(obsId)

  val id: Lens[LoadedObservation, Observation.Id] = Focus[LoadedObservation](_.obsId)
