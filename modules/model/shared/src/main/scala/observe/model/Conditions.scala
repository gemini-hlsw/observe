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
import eu.timepit.refined.numeric.Interval
import coulomb.*
import coulomb.units.accepted.*
import coulomb.units.si.*
import coulomb.units.si.prefixes.*
import lucuma.core.math.units.*

import eu.timepit.refined.api.*
import coulomb.syntax.*

import eu.timepit.refined.internal.WitnessAs
import eu.timepit.refined.cats.given
import coulomb.ops.algebra.cats.quantity.ctx_Quantity_Eq
import io.circe.refined.*
import lucuma.core.circe.coulomb.given

type ImageQualityRefinement = Interval.OpenClosed[0, 500]
type ImageQualityMagnitude  = Int Refined ImageQualityRefinement
val ImageQualityMagnitude = new RefinedTypeOps[ImageQualityMagnitude, Int]
type CentiArcSecond    = Centi * ArcSecond
// opaque type ImageQualityValue = Quantity[ImageQualityMagnitude, CentiArcSecond]
type ImageQualityValue = Quantity[ImageQualityMagnitude, CentiArcSecond]
object ImageQualityValue:
  def fromCentiArcSecond(value: Int): Either[String, ImageQualityValue] =
    ImageQualityMagnitude.from(value).map(_.withUnit[CentiArcSecond])
  def unsafeFromCentiArcSecond(value: Int): ImageQualityValue           =
    ImageQualityMagnitude.unsafeFrom(value).withUnit[CentiArcSecond]
  // def fromImageQuality(value: ImageQuality): ImageQualityValue          =
  //   ImageQualityMagnitude
  //     .from(value.toDeciArcSeconds. .toUnit[CentiArcSecond])
  //     .map(_.withUnit[CentiArcSecond])

type CloudExtinctionRefinement = Interval.Closed[0, 500]
type CloudExtinctionMagnitude  = Int Refined CloudExtinctionRefinement
val CloudExtinctionMagnitude = new RefinedTypeOps[CloudExtinctionMagnitude, Int]
type CentiMagnitude       = Centi * VegaMagnitude
// opaque type CloudExtinctionValue = Quantity[CloudExtinctionMagnitude, CentiMagnitude]
type CloudExtinctionValue = Quantity[CloudExtinctionMagnitude, CentiMagnitude]
object CloudExtinctionValue:
  def fromCentiMagnitude(value: Int): Either[String, CloudExtinctionValue] =
    CloudExtinctionMagnitude.from(value).map(_.withUnit[CentiMagnitude])
  def unsafeFromCentiMagnitude(value: Int): CloudExtinctionValue           =
    CloudExtinctionMagnitude.unsafeFrom(value).withUnit[CentiMagnitude]

// Move to core? (Decoder is already in schemas)
// given quantityDecoder[N: Decoder, U]: Decoder[Quantity[N, U]] =
//   Decoder.instance(_.as[N].map(_.withUnit[U]))

// import io.circe.syntax.*
// given quantityEncoder[N: Encoder, U]: Encoder[Quantity[N, U]] =
//   Encoder.instance(_.value.asJson)

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
