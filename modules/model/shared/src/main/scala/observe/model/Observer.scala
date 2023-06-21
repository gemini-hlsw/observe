// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.Show
import monocle.Iso
import monocle.macros.GenIso

final case class Observer(value: String)

object Observer {

  val Zero: Observer =
    Observer("")

  given Eq[Observer] =
    Eq.fromUniversalEquals

  given Show[Observer] =
    Show.show(_.value)

  val valueI: Iso[Observer, String] = GenIso[Observer, String]
}
