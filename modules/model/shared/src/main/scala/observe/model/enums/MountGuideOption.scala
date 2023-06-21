// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

/** Enumerated type for offloading of tip/tilt corrections from M2 to mount. */
sealed abstract class MountGuideOption(val tag: String) extends Product with Serializable

object MountGuideOption {
  case object MountGuideOff extends MountGuideOption("MountGuideOff")
  case object MountGuideOn  extends MountGuideOption("MountGuideOn")

  /** @group Typeclass Instances */
  given Enumerated[MountGuideOption] =
    Enumerated.from(MountGuideOff, MountGuideOn).withTag(_.tag)
}
