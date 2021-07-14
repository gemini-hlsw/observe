// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.common

import eu.timepit.refined.types.numeric.PosLong
import observe.model.{ Observation, StepId }

package object test {
  def observationId(i: Int): Observation.Id =
    lucuma.core.model.Observation.Id(PosLong.unsafeFrom(i.toLong))

  def stepId(i: Int): StepId = lucuma.core.model.Step.Id(PosLong.unsafeFrom(i.toLong))
}
