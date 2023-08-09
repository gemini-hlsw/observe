// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import lucuma.core.refined.numeric.NonZeroInt
import lucuma.core.util.NewType

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS

trait GmosParameters {

  object NsPairs extends NewType[Int]
  type NsPairs = NsPairs.Type

  object NsRows extends NewType[Int]
  type NsRows = NsRows.Type

  object NsCycles extends NewType[Int]
  type NsCycles = NsCycles.Type

  object NsExposureDivider extends NewType[NonZeroInt]
  type NsExposureDivider = NsExposureDivider.Type

  // Remaining time when it is not safe to stop, pause or abort
  val SafetyCutoff: FiniteDuration = new FiniteDuration(3, SECONDS)

}

object GmosParameters extends GmosParameters
