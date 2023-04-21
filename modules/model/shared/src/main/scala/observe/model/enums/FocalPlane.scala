// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

/* TODO: Remove this file after updating dependency on lucuma-core > 0.9.0 */

import lucuma.core.util.Enumerated

sealed abstract class FocalPlane(val tag: String) extends Product with Serializable {
  def label: String
}

object FocalPlane {
  case object SingleSlit   extends FocalPlane("SingleSlit")   {
    val label = "Single Slit"
  }
  case object MultipleSlit extends FocalPlane("MultipleSlit") {
    val label = "Multiple Slits"
  }
  case object IFU          extends FocalPlane("IFU")          {
    val label = "IFU"
  }

  /** @group Typeclass Instances */
  given Enumerated[FocalPlane] =
    Enumerated.from(SingleSlit, MultipleSlit, IFU).withTag(_.tag)
}
