// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.all.*
import lucuma.core.util.Enumerated

sealed abstract class SystemName(val tag: String, val system: String)
    extends Product
    with Serializable {

  def withParam(p: String): String =
    s"$system:$p"

}

object SystemName {

  case object Ocs            extends SystemName("Ocs", "ocs")
  case object Observe        extends SystemName("Observe", "observe")
  case object Instrument     extends SystemName("Instrument", "instrument")
  case object Telescope      extends SystemName("Telescope", "telescope")
  case object Gcal           extends SystemName("Gcal", "gcal")
  case object Calibration    extends SystemName("Calibration", "calibration")
  case object Meta           extends SystemName("Meta", "meta")
  case object AdaptiveOptics extends SystemName("AdaptiveOptics", "adaptive optics")

  def fromString(system: String): Option[SystemName] =
    Enumerated[SystemName].all.find(_.system === system)

  def unsafeFromString(system: String): SystemName =
    fromString(system).getOrElse(sys.error(s"Unknown system name $system"))

  /** @group Typeclass Instances */
  given Enumerated[SystemName] =
    Enumerated
      .from(Ocs, Observe, Instrument, Telescope, Gcal, Calibration, Meta, AdaptiveOptics)
      .withTag(_.tag)
}
