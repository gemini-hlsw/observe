// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import lucuma.core.util.Enumerated

// UI model
sealed abstract class UserPromptResult(val tag: String) extends Product with Serializable

object UserPromptResult {
  case object Cancel extends UserPromptResult("Cancel")
  case object Ok     extends UserPromptResult("Ok")

  /** @group Typeclass Instances */
  implicit val SectionVisibilityStateEnumerated: Enumerated[UserPromptResult] =
    Enumerated.from(Cancel, Ok).withTag(_.tag)

}
