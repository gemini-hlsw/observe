// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

sealed abstract class ApplyCommandResult(val tag: String) extends Product with Serializable

object ApplyCommandResult {
  case object Paused    extends ApplyCommandResult("Paused")
  case object Completed extends ApplyCommandResult("Completed")

  /** @group Typeclass Instances */
  given Enumerated[ApplyCommandResult] =
    Enumerated.from(Paused, Completed).withTag(_.tag)
}
