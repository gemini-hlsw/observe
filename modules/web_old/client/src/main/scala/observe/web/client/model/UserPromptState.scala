// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats.Eq
import observe.model.UserPrompt

/**
 * Utility class to display a generic notification sent by the server
 */
final case class UserPromptState(notification: Option[UserPrompt])

object UserPromptState {
  val Empty: UserPromptState = UserPromptState(None)

  given Eq[UserPromptState] =
    Eq.by(_.notification)
}
