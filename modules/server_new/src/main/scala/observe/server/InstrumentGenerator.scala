// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import fs2.Stream
import lucuma.core.model.sequence.gmos.DynamicConfig
import observe.engine.Result

trait InstrumentGenerator[F[_], D <: DynamicConfig] {
  def configure(d: D): F[Result]
  def observe(d:   D): Stream[F, Result]
}
