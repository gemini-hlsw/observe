// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import cats.Eq

sealed trait AOCRFollow extends Product with Serializable

object AOCRFollow {
  case object Following extends AOCRFollow
  case object Fixed     extends AOCRFollow

  given Eq[AOCRFollow] = Eq.fromUniversalEquals
}
