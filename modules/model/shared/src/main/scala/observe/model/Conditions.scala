// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.syntax.all.*
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import monocle.Focus
import monocle.Lens

final case class Conditions(
  cc: Option[CloudExtinction],
  iq: Option[ImageQuality],
  sb: Option[SkyBackground],
  wv: Option[WaterVapor]
)

object Conditions {

  val Unknown: Conditions =
    Conditions(
      none,
      none,
      none,
      none
    )

  val Worst: Conditions =
    Conditions(
      CloudExtinction.ThreePointZero.some,
      ImageQuality.TwoPointZero.some,
      SkyBackground.Bright.some,
      WaterVapor.Wet.some
    )

  val Nominal: Conditions =
    Conditions(
      CloudExtinction.OnePointFive.some,
      ImageQuality.OnePointZero.some,
      SkyBackground.Gray.some,
      WaterVapor.Wet.some
    )

  val Best: Conditions =
    Conditions(
      // In the ODB model it's 20% but that value it's marked as obsolete
      // so I took the non-obsolete lowest value.
      CloudExtinction.PointOne.some,
      ImageQuality.PointOne.some,
      SkyBackground.Darkest.some,
      WaterVapor.VeryDry.some
    )

  val Default: Conditions =
    Unknown // Taken from ODB

  given Eq[Conditions] =
    Eq.by(x => (x.cc, x.iq, x.sb, x.wv))

  val cc: Lens[Conditions, Option[CloudExtinction]] = Focus[Conditions](_.cc)
  val iq: Lens[Conditions, Option[ImageQuality]]    = Focus[Conditions](_.iq)
  val sb: Lens[Conditions, Option[SkyBackground]]   = Focus[Conditions](_.sb)
  val wv: Lens[Conditions, Option[WaterVapor]]      = Focus[Conditions](_.wv)

}
