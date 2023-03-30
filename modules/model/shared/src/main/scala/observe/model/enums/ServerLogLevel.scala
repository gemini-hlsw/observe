// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

sealed abstract class ServerLogLevel(val tag: String, val label: String) extends Product with Serializable

object ServerLogLevel {

  case object INFO  extends ServerLogLevel("INFO", "INFO")
  case object WARN  extends ServerLogLevel("WARNING", "WARNING")
  case object ERROR extends ServerLogLevel("ERROR", "ERROR")

  /** @group Typeclass Instances */
  implicit val ServerLogLevelEnumerated: Enumerated[ServerLogLevel] =
    Enumerated.from(INFO, WARN, ERROR).withTag(_.tag)
}
