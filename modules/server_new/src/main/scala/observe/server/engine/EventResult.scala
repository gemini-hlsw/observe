// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.engine

import observe.server.SeqEvent

sealed trait EventResult extends Product with Serializable

object EventResult:
  enum Outcome:
    case Ok, Failure

  case class UserCommandResponse[F[_]](
    userEvent: UserEvent[F],
    outcome:   Outcome,
    ud:        Option[SeqEvent]
  ) extends EventResult

  case class SystemUpdate[F[_]](se: SystemEvent, outcome: Outcome) extends EventResult
