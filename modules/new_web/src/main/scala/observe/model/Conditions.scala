// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import observe.model.enums.*
import cats.derived.*

final case class Conditions(
  cc: CloudCover,
  iq: ImageQuality,
  sb: SkyBackground,
  wv: WaterVapor
) derives Eq

object Conditions:
  val Unknown: Conditions =
    Conditions(
      CloudCover.Unknown,
      ImageQuality.Unknown,
      SkyBackground.Unknown,
      WaterVapor.Unknown
    )

  val Worst: Conditions =
    Conditions(
      CloudCover.Any,
      ImageQuality.Any,
      SkyBackground.Any,
      WaterVapor.Any
    )

  val Nominal: Conditions =
    Conditions(
      CloudCover.Percent50,
      ImageQuality.Percent70,
      SkyBackground.Percent50,
      WaterVapor.Any
    )

  val Best: Conditions =
    Conditions(
      // In the ODB model it's 20% but that value it's marked as obsolete
      // so I took the non-obsolete lowest value.
      CloudCover.Percent50,
      ImageQuality.Percent20,
      SkyBackground.Percent20,
      WaterVapor.Percent20
    )

  val Default: Conditions =
    Unknown // Taken from ODB
