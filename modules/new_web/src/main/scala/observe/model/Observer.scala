// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import monocle.Iso
import monocle.macros.GenIso
import cats.derived.*
import lucuma.core.util.Display

case class Observer(value: String) derives Eq

object Observer:
  val Zero: Observer = Observer("")

  given Display[Observer] = Display.byShortName(_.value)

  val value: Iso[Observer, String] = GenIso[Observer, String]
