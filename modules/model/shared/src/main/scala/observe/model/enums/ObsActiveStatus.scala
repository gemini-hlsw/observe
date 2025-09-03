// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

/* TODO: Remove this file after updating dependency on lucuma-core > 0.28.1 */

import lucuma.core.util.Display
import lucuma.core.util.Enumerated
import monocle.Iso

/**
 * Determines whether an observation should be considered active. It, for example, "allows PIs to
 * prevent or halt execution of "Ready'' or "Ongoing'' observations while retaining their status
 * information".
 */
enum ObsActiveStatus(val tag: String, val label: String, val toBoolean: Boolean)
    derives Enumerated {
  case Active extends ObsActiveStatus("Active", "Active", true)

  case Inactive extends ObsActiveStatus("Inactive", "Inactive", false)

  def fold[A](active: => A, inactive: => A): A =
    this match {
      case ObsActiveStatus.Active   => active
      case ObsActiveStatus.Inactive => inactive
    }

}

object ObsActiveStatus {

  given Display[ObsActiveStatus] =
    Display.byShortName(_.label)

  val FromBoolean: Iso[Boolean, ObsActiveStatus] =
    Iso[Boolean, ObsActiveStatus](b => if (b) Active else Inactive)(_.toBoolean)

}
