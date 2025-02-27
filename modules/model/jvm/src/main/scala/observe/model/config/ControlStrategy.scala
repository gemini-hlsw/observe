// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config

import cats.syntax.all.*
import lucuma.core.util.Enumerated

enum ControlStrategy(val tag: String) derives Enumerated {

  // System will be fully controlled by Observe
  case FullControl extends ControlStrategy("FullControl")
  // Observe connects to system, but only to read values
  case ReadOnly    extends ControlStrategy("ReadOnly")
  // All system interactions are internally simulated
  case Simulated   extends ControlStrategy("Simulated")
}

object ControlStrategy {
  def fromString(v: String): Option[ControlStrategy] = v match {
    case "full"      => Some(FullControl)
    case "readOnly"  => Some(ReadOnly)
    case "simulated" => Some(Simulated)
    case _           => None
  }

  extension (v: ControlStrategy)
    inline def connect: Boolean      = v =!= ControlStrategy.Simulated
    // If connected, then use real values for keywords
    inline def realKeywords: Boolean = connect
    inline def command: Boolean      = v === ControlStrategy.FullControl

}
