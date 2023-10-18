// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.syntax.all.*
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.Observation
import crystal.Pot
import monocle.Lens
import monocle.Focus

case class LoadedObservation private (
  obsId:   Observation.Id,
  summary: Pot[ObsSummary] = Pot.pending,
  config:  Pot[InstrumentExecutionConfig] = Pot.pending
):
  def withSummary(summary: Either[Throwable, ObsSummary]): LoadedObservation =
    copy(summary = Pot.fromTry(summary.toTry))

  def withConfig(config: Either[Throwable, InstrumentExecutionConfig]): LoadedObservation =
    copy(config = Pot.fromTry(config.toTry))

  def unPot: Pot[(Observation.Id, ObsSummary, InstrumentExecutionConfig)] =
    (summary, config).mapN((s, c) => (obsId, s, c))

  // def whenReady[A](f: (Observation.Id, ObsSummary, InstrumentExecutionConfig) => A): Pot[A] =
  //   (summary, config).mapN((s, c) => f(obsId, s, c))

object LoadedObservation:
  def apply(obsId: Observation.Id): LoadedObservation = new LoadedObservation(obsId)

  val id: Lens[LoadedObservation, Observation.Id] = Focus[LoadedObservation](_.obsId)
