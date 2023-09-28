// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import cats.FlatMap
import cats.effect.kernel.Clock
import org.typelevel.log4cats.Logger

trait DhsClientProvider[F[_]] {
  def dhsClient(instrumentName: String): DhsClient[F]
}

object DhsClientProvider {
  def dummy[F[_]: FlatMap: Clock: Logger]: DhsClientProvider[F] = new DhsClientProvider[F] {
    override def dhsClient(instrumentName: String): DhsClient[F] = new DhsClientDisabled[F]
  }
}
