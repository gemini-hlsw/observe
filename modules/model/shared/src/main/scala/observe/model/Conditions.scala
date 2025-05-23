// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import coulomb.ops.algebra.cats.all.given
import eu.timepit.refined.cats.given
import io.circe.Decoder
import io.circe.Encoder
import io.circe.refined.*
import lucuma.core.circe.coulomb.given
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.CloudExtinction
import lucuma.core.model.ImageQuality
import lucuma.core.refined.given
import monocle.Focus
import monocle.Lens

case class Conditions(
  ce: Option[CloudExtinction],
  iq: Option[ImageQuality],
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
      CloudExtinction.Preset.ThreePointZero.toCloudExtinction.some,
      ImageQuality.Preset.TwoPointZero.toImageQuality.some,
      SkyBackground.Bright.some,
      WaterVapor.Wet.some
    )

  val Nominal: Conditions =
    Conditions(
      CloudExtinction.Preset.OnePointFive.toCloudExtinction.some,
      ImageQuality.Preset.OnePointZero.toImageQuality.some,
      SkyBackground.Gray.some,
      WaterVapor.Wet.some
    )

  val Best: Conditions =
    Conditions(
      // In the ODB model it's 20% but that value it's marked as obsolete
      // so I took the non-obsolete lowest value.
      CloudExtinction.Preset.PointOne.toCloudExtinction.some,
      ImageQuality.Preset.PointOne.toImageQuality.some,
      SkyBackground.Darkest.some,
      WaterVapor.VeryDry.some
    )

  val Default: Conditions =
    Unknown // Taken from ODB

  val ce: Lens[Conditions, Option[CloudExtinction]] = Focus[Conditions](_.ce)
  val iq: Lens[Conditions, Option[ImageQuality]]    = Focus[Conditions](_.iq)
  val sb: Lens[Conditions, Option[SkyBackground]]   = Focus[Conditions](_.sb)
  val wv: Lens[Conditions, Option[WaterVapor]]      = Focus[Conditions](_.wv)
