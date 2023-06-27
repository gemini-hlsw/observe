// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats.Eq
import observe.model.Notification
import observe.web.client.model.SectionVisibilityState.SectionClosed

/**
 * Utility class to display a generic notification sent by the server
 */
final case class UserNotificationState(
  visibility:   SectionVisibilityState,
  notification: Option[Notification]
)

object UserNotificationState {
  val Empty: UserNotificationState = UserNotificationState(SectionClosed, None)

  given Eq[UserNotificationState] =
    Eq.by(x => (x.visibility, x.notification))
}
