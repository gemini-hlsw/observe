// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

sealed abstract class NSAction(val tag: String) extends Product with Serializable

object NSAction {
  case object Start                extends NSAction("Start")
  case object NodStart             extends NSAction("NodStart")
  case object NodComplete          extends NSAction("NodComplete")
  case object StageObserveStart    extends NSAction("StageObserveStart")
  case object StageObserveComplete extends NSAction("StageObserveComplete")
  case object Done                 extends NSAction("Done")

  /** @group Typeclass Instances */
  implicit val NSActionEnumerated: Enumerated[NSAction] =
    Enumerated.from(Start, NodStart, NodComplete, StageObserveStart, StageObserveComplete, Done).withTag(_.tag)
}
