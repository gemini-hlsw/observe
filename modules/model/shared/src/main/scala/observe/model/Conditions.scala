// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.Encoder
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.CloudExtinction
import lucuma.core.model.ImageQuality
import monocle.Focus
import monocle.Lens

case class Conditions(
  ce: Option[CloudExtinction.Preset],
  iq: Option[ImageQuality.Preset],
  sb: Option[SkyBackground],
  wv: Option[WaterVapor]
) derives Eq,
      Encoder.AsObject,
      Decoder

object Conditions:

  val Unknown: Conditions =
    Conditions(
      none,
      none,
      none,
      none
    )

  val Worst: Conditions =
    Conditions(
      CloudExtinction.Preset.ThreePointZero.some,
      ImageQuality.Preset.TwoPointZero.some,
      SkyBackground.Bright.some,
      WaterVapor.Wet.some
    )

  val Nominal: Conditions =
    Conditions(
      CloudExtinction.Preset.OnePointFive.some,
      ImageQuality.Preset.OnePointZero.some,
      SkyBackground.Gray.some,
      WaterVapor.Wet.some
    )

  val Best: Conditions =
    Conditions(
      // In the ODB model it's 20% but that value it's marked as obsolete
      // so I took the non-obsolete lowest value.
      CloudExtinction.Preset.PointOne.some,
      ImageQuality.Preset.PointOne.some,
      SkyBackground.Darkest.some,
      WaterVapor.VeryDry.some
    )

  val Default: Conditions =
    Unknown // Taken from ODB

  val ce: Lens[Conditions, Option[CloudExtinction.Preset]] = Focus[Conditions](_.ce)
  val iq: Lens[Conditions, Option[ImageQuality.Preset]]    = Focus[Conditions](_.iq)
  val sb: Lens[Conditions, Option[SkyBackground]]          = Focus[Conditions](_.sb)
  val wv: Lens[Conditions, Option[WaterVapor]]             = Focus[Conditions](_.wv)
