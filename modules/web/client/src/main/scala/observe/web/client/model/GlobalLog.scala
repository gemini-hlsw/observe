// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats._
import observe.common.FixedLengthBuffer
import observe.model.events.*

/**
 * Keeps a list of log entries for display
 */
final case class GlobalLog(
  log:     FixedLengthBuffer[ServerLogMessage],
  display: SectionVisibilityState
)

object GlobalLog {
  given Eq[GlobalLog] = Eq.by(x => (x.log, x.display))
}
