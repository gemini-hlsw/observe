// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.Show
import monocle.Iso
import monocle.macros.GenIso

final case class Operator(value: String)

object Operator {

  val Zero: Operator =
    Operator("")

  given Eq[Operator] =
    Eq.fromUniversalEquals

  given Show[Operator] =
    Show.show(_.value)

  val valueI: Iso[Operator, String] = GenIso[Operator, String]

}
