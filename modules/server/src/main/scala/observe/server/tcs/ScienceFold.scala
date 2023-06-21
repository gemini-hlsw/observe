// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats._
import cats.syntax.all.*
import lucuma.core.enums.LightSinkName
import observe.server.tcs.TcsController.*

sealed trait ScienceFold extends Product with Serializable

object ScienceFold {
  case object Parked                                                             extends ScienceFold
  final case class Position(source: LightSource, sink: LightSinkName, port: Int) extends ScienceFold

  given Eq[Position] = Eq.by(x => (x.source, x.sink, x.port))

  given Eq[ScienceFold] = Eq.instance {
    case (Parked, Parked)           => true
    case (a: Position, b: Position) => a === b
    case _                          => false
  }
}
