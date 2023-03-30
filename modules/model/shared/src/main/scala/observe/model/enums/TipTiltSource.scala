// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

/** Enumerated type for Tip/Tilt Source. */
sealed abstract class TipTiltSource(val tag: String) extends Product with Serializable

object TipTiltSource {PWFS1
  case object PWFS1 extends TipTiltSource("PWFS1")
  case object PWFS2 extends TipTiltSource("PWFS2")
  case object OIWFS extends TipTiltSource("OIWFS")
  case object GAOS  extends TipTiltSource("GAOS")

  /** @group Typeclass Instances */
  implicit val TipTiltSourceEnumerated: Enumerated[TipTiltSource] =
    Enumerated.from(PWFS1, PWFS2, OIWFS, GAOS).withTag(_.tag)
}
