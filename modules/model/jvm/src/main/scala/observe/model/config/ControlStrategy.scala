// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config

import cats.syntax.all.*
import lucuma.core.util.Enumerated

sealed abstract class ControlStrategy(val tag: String) extends Product with Serializable

object ControlStrategy {
  // System will be fully controlled by Observe
  case object FullControl extends ControlStrategy("FullControl")
  // Observe connects to system, but only to read values
  case object ReadOnly    extends ControlStrategy("ReadOnly")
  // All system interactions are internally simulated
  case object Simulated   extends ControlStrategy("Simulated")

  def fromString(v: String): Option[ControlStrategy] = v match {
    case "full"      => Some(FullControl)
    case "readOnly"  => Some(ReadOnly)
    case "simulated" => Some(Simulated)
    case _           => None
  }

  given Enumerated[ControlStrategy] =
    Enumerated.from(FullControl, ReadOnly, Simulated).withTag(_.tag)

  extension (v: ControlStrategy)
    inline def connect: Boolean      = v =!= ControlStrategy.Simulated
    // If connected, then use real values for keywords
    inline def realKeywords: Boolean = connect
    inline def command: Boolean      = v === ControlStrategy.FullControl

}
