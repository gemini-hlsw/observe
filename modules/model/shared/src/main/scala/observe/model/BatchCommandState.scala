// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.syntax.all.*
import eu.timepit.refined.cats.given
import lucuma.core.model.User

sealed trait BatchCommandState extends Product with Serializable

object BatchCommandState {
  case object Idle                                                         extends BatchCommandState
  final case class Run(observer: Observer, user: User, clientId: ClientId) extends BatchCommandState
  case object Stop                                                         extends BatchCommandState

  given Eq[BatchCommandState] = Eq.instance {
    case (Idle, Idle)                       => true
    case (Run(o1, u1, c1), Run(o2, u2, c2)) => o1 === o2 && u1 === u2 && c1 === c2
    case (Stop, Stop)                       => true
    case _                                  => false
  }
}
