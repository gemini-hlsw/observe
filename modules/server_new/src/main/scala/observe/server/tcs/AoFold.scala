// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

/** AO fold position */
sealed trait AoFold extends Product with Serializable {
  val active: Boolean
}
object AoFold {
  case object In  extends AoFold {
    override val active: Boolean = true
  }
  case object Out extends AoFold {
    override val active: Boolean = false
  }
}
