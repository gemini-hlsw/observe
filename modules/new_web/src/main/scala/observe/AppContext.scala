// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe

import cats.effect.IO
import japgolly.scalajs.react.React
import japgolly.scalajs.react.feature.Context
import org.typelevel.log4cats.Logger

final case class AppContext[F[_]]()(implicit val logger: Logger[F])

object AppContext {
  val ctx: Context[AppContext[IO]] = React.createContext(null) // No default value
}
