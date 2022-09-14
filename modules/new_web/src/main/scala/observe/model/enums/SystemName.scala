// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.eq.*
import cats.Eq
import cats.derived.*

// sealed abstract class SystemName(val system: String) extends Product with Serializable {

//   def withParam(p: String): String =
//     s"$system:$p"

// }

enum SystemName(val system: String) derives Eq:
  case Ocs            extends SystemName("ocs")
  case Observe        extends SystemName("observe")
  case Instrument     extends SystemName("instrument")
  case Telescope      extends SystemName("telescope")
  case Gcal           extends SystemName("gcal")
  case Calibration    extends SystemName("calibration")
  case Meta           extends SystemName("meta")
  case AdaptiveOptics extends SystemName("adaptive optics")

  def withParam(p: String): String =
    s"$system:$p"

object SystemName:
  def fromString(system: String): Option[SystemName] =
    values.find(_.system === system)

  def unsafeFromString(system: String): SystemName =
    fromString(system).getOrElse(sys.error(s"Unknown system name $system"))
