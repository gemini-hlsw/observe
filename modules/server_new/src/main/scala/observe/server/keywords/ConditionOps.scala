// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import cats.data.NonEmptyList
import cats.syntax.all.*
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.Site
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.math.Angle
import lucuma.core.math.Wavelength
import lucuma.core.model.ConstraintSet
import observe.model.Conditions

import scala.annotation.tailrec
import scala.math.Ordering.Implicits.infixOrderingOps
import scala.math.Pi
import scala.math.abs
import scala.math.pow
import scala.math.sin

object ConditionOps {

  extension (wv: WaterVapor) {
    def toMillimeters(site: Site): Double = site match
      case Site.GN =>
        wv match
          case WaterVapor.VeryDry => 1.0
          case WaterVapor.Dry     => 1.6
          case WaterVapor.Median  => 3.0
          case WaterVapor.Wet     => 5.0
      case Site.GS =>
        wv match
          case WaterVapor.VeryDry => 2.3
          case WaterVapor.Dry     => 4.3
          case WaterVapor.Median  => 7.6
          case WaterVapor.Wet     => 10.0
  }

  extension (sb: SkyBackground) {
    def toMicroVolts: Double = sb match
      case SkyBackground.Darkest => 21.3
      case SkyBackground.Dark    => 20.7
      case SkyBackground.Gray    => 19.5
      case SkyBackground.Bright  => 18.0
  }

  private def nearestLookup[V](map: NonEmptyList[(Double, V)])(key: Double): V = {
    @tailrec
    def findAcc(acc: (Double, V), rem: List[(Double, V)]): V = rem match {
      case Nil            => acc._2
      case ::(head, next) =>
        if (abs(acc._1 - key) <= abs(head._1 - key))
          acc._2
        else
          findAcc(head, next)
    }

    findAcc(map.head, map.tail)
  }

  private def binnedLookup[A: Ordering, V](map: NonEmptyList[(A, V)])(key: A): V = {
    @tailrec
    def findAcc(acc: (A, V), rem: List[(A, V)]): V = rem match {
      case Nil            => acc._2
      case ::(head, next) =>
        if (key <= head._1)
          acc._2
        else
          findAcc(head, next)
    }

    findAcc(map.head, map.tail)
  }

  private val iqBins = NonEmptyList
    .of(0.350, 0.475, 0.630, 0.780, 0.900, 1.02, 1.20, 1.65, 2.2, 3.4, 4.8, 11.7)
    .zip(
      NonEmptyList
        .of(
          NonEmptyList.of(0.60, 0.90, 1.20, 2.00),
          NonEmptyList.of(0.60, 0.85, 1.10, 1.90),
          NonEmptyList.of(0.50, 0.75, 1.05, 1.80),
          NonEmptyList.of(0.50, 0.75, 1.05, 1.70),
          NonEmptyList.of(0.50, 0.70, 0.95, 1.70),
          NonEmptyList.of(0.40, 0.70, 0.95, 1.65),
          NonEmptyList.of(0.40, 0.60, 0.85, 1.55),
          NonEmptyList.of(0.40, 0.60, 0.85, 1.50),
          NonEmptyList.of(0.35, 0.55, 0.80, 1.40),
          NonEmptyList.of(0.35, 0.50, 0.75, 1.25),
          NonEmptyList.of(0.35, 0.50, 0.70, 1.15),
          NonEmptyList.of(0.31, 0.37, 0.45, 0.75)
        )
        .map(_.zip(NonEmptyList.of(20, 70, 85, 100)))
    )

  extension (iq: ImageQuality) {
    def toPercentile(wv: Wavelength, elevation: Angle): Int = {
      // Pickering approximation (thanks Andy Stephens)
      val airmass         = 1.0 / sin(
        (90.0 - elevation.toDoubleDegrees + 244.0 / (165.0 + 47.0 * pow(
          90.0 - elevation.toDoubleDegrees,
          1.1
        ))) * Pi / 180
      )
      val atZenith: Angle = iq.toAngle * (1.0 / pow(airmass, 0.6))
      binnedLookup(nearestLookup(iqBins)(wv.toMicrometers.value.value.toDouble))(
        atZenith.toDoubleDegrees * 3600.0
      )
    }
  }

  extension (ce: CloudExtinction) {
    def toPercentile: Int = ce match {
      case CloudExtinction.PointOne       => 50
      case CloudExtinction.PointThree     => 70
      case CloudExtinction.PointFive      => 80
      case CloudExtinction.OnePointZero   => 80
      case CloudExtinction.OnePointFive   => 100
      case CloudExtinction.TwoPointZero   => 100
      case CloudExtinction.ThreePointZero => 100
    }
  }

  extension (wv: WaterVapor) {
    def toPercentile: Int = wv match {
      case WaterVapor.VeryDry => 20
      case WaterVapor.Dry     => 50
      case WaterVapor.Median  => 80
      case WaterVapor.Wet     => 100
    }
  }

  extension (bg: SkyBackground) {
    def toPercentile: Int = bg match {
      case SkyBackground.Darkest => 20
      case SkyBackground.Dark    => 50
      case SkyBackground.Gray    => 80
      case SkyBackground.Bright  => 100
    }
  }

  extension (constrainSet: ConstraintSet) {
    def asConditions: Conditions = Conditions(
      constrainSet.cloudExtinction.some,
      constrainSet.imageQuality.some,
      constrainSet.skyBackground.some,
      constrainSet.waterVapor.some
    )
  }

}
