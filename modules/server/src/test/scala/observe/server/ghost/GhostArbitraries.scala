// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.ghost

import lucuma.core.math.Coordinates
import lucuma.core.math.arb.ArbCoordinates
import lucuma.core.arb.ArbTime
import lucuma.core.util.arb.ArbEnumerated.given
import org.scalacheck.Arbitrary.*
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen
import scala.concurrent.duration.Duration

trait GhostArbitraries extends ArbTime {

  import ArbCoordinates._

  val ghostSRSingleTargetConfigGen: Gen[StandardResolutionMode.SingleTarget] =
    for {
      basePos    <- arbitrary[Option[Coordinates]]
      exp        <- arbitrary[Duration]
      fa         <- arbitrary[FiberAgitator]
      srifu1Name <- arbitrary[String]
      srifu1Pos  <- arbitrary[Coordinates]
    } yield StandardResolutionMode.SingleTarget(basePos, exp, fa, srifu1Name, srifu1Pos)

  given Cogen[StandardResolutionMode.SingleTarget] =
    Cogen[(Option[Coordinates], Duration, String, Coordinates)]
      .contramap(x => (x.baseCoords, x.expTime, x.ifu1TargetName, x.ifu1Coordinates))

  val ghostSRDualTargetConfigGen: Gen[StandardResolutionMode.DualTarget] =
    for {
      basePos    <- arbitrary[Option[Coordinates]]
      exp        <- arbitrary[Duration]
      fa         <- arbitrary[FiberAgitator]
      srifu1Name <- arbitrary[String]
      srifu1Pos  <- arbitrary[Coordinates]
      srifu2Name <- arbitrary[String]
      srifu2Pos  <- arbitrary[Coordinates]
    } yield StandardResolutionMode.DualTarget(basePos,
                                              exp,
                                              fa,
                                              srifu1Name,
                                              srifu1Pos,
                                              srifu2Name,
                                              srifu2Pos
    )

  given Cogen[StandardResolutionMode.DualTarget] =
    Cogen[(Option[Coordinates], Duration, String, Coordinates, String, Coordinates)]
      .contramap(x =>
        (x.baseCoords,
         x.expTime,
         x.ifu1TargetName,
         x.ifu1Coordinates,
         x.ifu2TargetName,
         x.ifu2Coordinates
        )
      )

  val ghostSRTargetSkyConfigGen: Gen[StandardResolutionMode.TargetPlusSky] =
    for {
      basePos    <- arbitrary[Option[Coordinates]]
      exp        <- arbitrary[Duration]
      fa         <- arbitrary[FiberAgitator]
      srifu1Name <- arbitrary[String]
      srifu1Pos  <- arbitrary[Coordinates]
      srifu2Pos  <- arbitrary[Coordinates]
    } yield StandardResolutionMode.TargetPlusSky(basePos, exp, fa, srifu1Name, srifu1Pos, srifu2Pos)

  given Cogen[StandardResolutionMode.TargetPlusSky] =
    Cogen[(Option[Coordinates], Duration, String, Coordinates, Coordinates)]
      .contramap(x =>
        (x.baseCoords, x.expTime, x.ifu1TargetName, x.ifu1Coordinates, x.ifu2Coordinates)
      )

  given Gen[StandardResolutionMode.SkyPlusTarget] =
    for {
      basePos    <- arbitrary[Option[Coordinates]]
      exp        <- arbitrary[Duration]
      fa         <- arbitrary[FiberAgitator]
      srifu1Pos  <- arbitrary[Coordinates]
      srifu2Name <- arbitrary[String]
      srifu2Pos  <- arbitrary[Coordinates]
    } yield StandardResolutionMode.SkyPlusTarget(basePos, exp, fa, srifu1Pos, srifu2Name, srifu2Pos)

  given Cogen[StandardResolutionMode.SkyPlusTarget] =
    Cogen[(Option[Coordinates], Duration, Coordinates, String, Coordinates)]
      .contramap(x =>
        (x.baseCoords, x.expTime, x.ifu1Coordinates, x.ifu2TargetName, x.ifu2Coordinates)
      )

  given Gen[HighResolutionMode.SingleTarget] =
    for {
      basePos    <- arbitrary[Option[Coordinates]]
      exp        <- arbitrary[Duration]
      fa         <- arbitrary[FiberAgitator]
      hrifu1Name <- arbitrary[String]
      hrifu1Pos  <- arbitrary[Coordinates]
    } yield HighResolutionMode.SingleTarget(basePos, exp, fa, hrifu1Name, hrifu1Pos)

  given Cogen[HighResolutionMode.SingleTarget] =
    Cogen[(Option[Coordinates], Duration, String, Coordinates)]
      .contramap(x => (x.baseCoords, x.expTime, x.ifu1TargetName, x.ifu1Coordinates))

  given Gen[HighResolutionMode.TargetPlusSky] =
    for {
      basePos    <- arbitrary[Option[Coordinates]]
      exp        <- arbitrary[Duration]
      hrifu1Name <- arbitrary[String]
      fa         <- arbitrary[FiberAgitator]
      hrifu1Pos  <- arbitrary[Coordinates]
      hrifu2Pos  <- arbitrary[Coordinates]
    } yield HighResolutionMode.TargetPlusSky(basePos, exp, fa, hrifu1Name, hrifu1Pos, hrifu2Pos)

  given Cogen[HighResolutionMode.TargetPlusSky] =
    Cogen[(Option[Coordinates], Duration, String, Coordinates, Coordinates)]
      .contramap(x =>
        (x.baseCoords, x.expTime, x.ifu1TargetName, x.ifu1Coordinates, x.ifu2Coordinates)
      )

  given Arbitrary[GhostConfig] = Arbitrary {
    Gen.oneOf(
      ghostSRSingleTargetConfigGen,
      ghostSRDualTargetConfigGen,
      ghostSRTargetSkyConfigGen,
      ghostSRSkyTargetConfigGen,
      ghostHRSingleTargetConfigGen,
      ghostHRTargetPlusSkyConfigGen
    )
  }

  object GhostHelpers {
    def extractSRIFU1Name(x: GhostConfig): Option[String] = x match {
      case StandardResolutionMode.SingleTarget(_, _, _, name, _)     => Some(name)
      case StandardResolutionMode.DualTarget(_, _, _, name, _, _, _) => Some(name)
      case StandardResolutionMode.TargetPlusSky(_, _, _, name, _, _) => Some(name)
      case _: StandardResolutionMode.SkyPlusTarget                   => Some("Sky")
      case _                                                         => None
    }

    def extractSRIFU1Coordinates(x: GhostConfig): Option[Coordinates] = x match {
      case c: StandardResolutionMode => Some(c.ifu1Coordinates)
      case _                         => None
    }

    def extractSRIFU2Name(x: GhostConfig): Option[String] = x match {
      case StandardResolutionMode.DualTarget(_, _, _, _, _, name, _) => Some(name)
      case _: StandardResolutionMode.TargetPlusSky                   => Some("Sky")
      case StandardResolutionMode.SkyPlusTarget(_, _, _, _, name, _) => Some(name)
      case _                                                         => None
    }

    def extractSRIFU2Coordinates(x: GhostConfig): Option[Coordinates] = x match {
      case StandardResolutionMode.DualTarget(_, _, _, _, _, _, coords) => Some(coords)
      case StandardResolutionMode.TargetPlusSky(_, _, _, _, _, coords) => Some(coords)
      case StandardResolutionMode.SkyPlusTarget(_, _, _, _, _, coords) => Some(coords)
      case _                                                           => None
    }

    def extractHRIFU1Name(x: GhostConfig): Option[String] = x match {
      case c: HighResolutionMode => Some(c.ifu1TargetName)
      case _                     => None
    }

    def extractHRIFU1Coordinates(x: GhostConfig): Option[Coordinates] = x match {
      case c: HighResolutionMode => Some(c.ifu1Coordinates)
      case _                     => None
    }

    def extractHRIFU2Name(x: GhostConfig): Option[String] = x match {
      case _: HighResolutionMode.TargetPlusSky => Some("Sky")
      case _                                   => None
    }

    def extractHRIFU2Coordinates(x: GhostConfig): Option[Coordinates] = x match {
      case c: HighResolutionMode.TargetPlusSky => Some(c.ifu2Coordinates)
      case _                                   => None
    }
  }

  given Cogen[GhostConfig] = {
    import GhostHelpers._
    Cogen[
      (
        Option[Coordinates],
        Duration,
        Option[String],
        Option[Coordinates],
        Option[String],
        Option[Coordinates],
        Option[String],
        Option[Coordinates],
        Option[String],
        Option[Coordinates]
      )
    ]
      .contramap(x =>
        (x.baseCoords,
         x.expTime,
         extractSRIFU1Name(x),
         extractSRIFU1Coordinates(x),
         extractSRIFU2Name(x),
         extractSRIFU2Coordinates(x),
         extractHRIFU1Name(x),
         extractHRIFU1Coordinates(x),
         extractHRIFU2Name(x),
         extractHRIFU2Coordinates(x)
        )
      )
  }

}
