// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

sealed trait EventResult[U] extends Product with Serializable

object EventResult:
  enum Outcome:
    case Ok, Failure

  case class UserCommandResponse[F[_], U](
    ue:      UserEvent[F, ?, U],
    outcome: Outcome,
    ud:      Option[U]
  ) extends EventResult[U]

  case class SystemUpdate[F[_], U](se: SystemEvent, outcome: Outcome) extends EventResult[U]
