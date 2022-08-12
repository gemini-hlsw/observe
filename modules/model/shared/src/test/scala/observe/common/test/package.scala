// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.common

import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.model.sequence.Step
import observe.model.{ Observation, StepId }

import java.util.UUID

package object test {
  def observationId(i: Int): Observation.Id =
    lucuma.core.model.Observation.Id(PosLong.unsafeFrom(i.toLong))

  def stepId(i: Int): StepId = i match {
    case 1 => Step.Id(UUID.fromString("7b9a3b4a-19a2-11ed-861d-0242ac120002"))
    case 2 => Step.Id(UUID.fromString("b62dd18a-19a3-11ed-861d-0242ac120002"))
    case 3 => Step.Id(UUID.fromString("bbdd559c-19a3-11ed-861d-0242ac120002"))
    case 4 => Step.Id(UUID.fromString("2e986c9c-e393-4c89-b683-80184642fdf8"))
    case 5 => Step.Id(UUID.fromString("a1bd2538-19a9-11ed-861d-0242ac120002"))
    case _ => Step.Id(UUID.fromString("c65addaa-19a3-11ed-861d-0242ac120002"))
  }
}
