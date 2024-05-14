// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.common.test

import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.model.sequence.Step
import observe.model.Observation

import java.util.UUID

def observationId(i: Int): Observation.Id =
  lucuma.core.model.Observation.Id(PosLong.unsafeFrom(i.toLong))

def stepId(i: Int): Step.Id = i match {
  case 1 => Step.Id(UUID.fromString("7b9a3b4a-19a2-11ed-861d-0242ac120002"))
  case 2 => Step.Id(UUID.fromString("b62dd18a-19a3-11ed-861d-0242ac120002"))
  case 3 => Step.Id(UUID.fromString("bbdd559c-19a3-11ed-861d-0242ac120002"))
  case 4 => Step.Id(UUID.fromString("2e986c9c-e393-4c89-b683-80184642fdf8"))
  case 5 => Step.Id(UUID.fromString("a1bd2538-19a9-11ed-861d-0242ac120002"))
  case 6 => Step.Id(UUID.fromString("27b0b124-2a43-4baf-b587-5e5a2b90f5cc"))
  case _ => Step.Id(UUID.fromString("c65addaa-19a3-11ed-861d-0242ac120002"))
}
