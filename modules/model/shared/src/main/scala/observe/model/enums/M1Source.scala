// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

/** Enumerated type for M1 Source. */
sealed abstract class M1Source(val tag: String) extends Product with Serializable

object M1Source {
  case object PWFS1 extends M1Source("PWFS1")
  case object PWFS2 extends M1Source("PWFS2")
  case object OIWFS extends M1Source("OIWFS")
  case object GAOS  extends M1Source("GAOS")
  case object HRWFS extends M1Source("HRWFS")

  /** @group Typeclass Instances */
  given Enumerated[M1Source] =
    Enumerated.from(PWFS1, PWFS2, OIWFS, GAOS, HRWFS).withTag(_.tag)

}
