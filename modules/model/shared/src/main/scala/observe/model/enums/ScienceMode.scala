// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

/* TODO: Remove this file after updating dependency on lucuma-core > 0.9.0 */

import lucuma.core.util.Enumerated

// TODO Move to lucuma core
sealed abstract class ScienceMode(val tag: String) extends Product with Serializable

object ScienceMode {
  case object Imaging      extends ScienceMode("Imaging")
  case object Spectroscopy extends ScienceMode("Spectroscopy")

  given Enumerated[ScienceMode] =
    Enumerated.from(Imaging, Spectroscopy).withTag(_.tag)
}
