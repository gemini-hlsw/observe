// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.Applicative
import cats.Eq
import cats.derived.*
import cats.effect.Concurrent
import cats.syntax.all.*
import coulomb.syntax.*
import coulomb.units.accepted.Millimeter
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.circe.Decoder
import io.circe.DecodingFailure
import monocle.Focus
import monocle.Lens
import monocle.Optional
import monocle.std.either
import monocle.std.option
import mouse.boolean.*
import observe.model.M1GuideConfig
import observe.model.M1GuideConfig.*
import observe.model.M2GuideConfig
import observe.model.M2GuideConfig.*
import observe.model.TelescopeGuideConfig
import observe.model.TelescopeGuideConfig.given
import observe.model.enums.ComaOption
import observe.model.enums.ComaOption.*
import observe.model.enums.M1Source
import observe.model.enums.MountGuideOption
import observe.model.enums.MountGuideOption.*
import observe.model.enums.TipTiltSource
import observe.server.altair.AltairController.{*, given}
import observe.server.gems.GemsController.GemsConfig.given
import observe.server.gems.GemsController.*

case class GuideConfig(
  tcsGuide:      TelescopeGuideConfig,
  gaosGuide:     Option[Either[AltairConfig, GemsConfig]],
  gemsSkyPaused: Boolean
) derives Eq

object GuideConfig {
  val tcsGuide: Lens[GuideConfig, TelescopeGuideConfig]                      = Focus[GuideConfig](_.tcsGuide)
  val gaosGuide: Lens[GuideConfig, Option[Either[AltairConfig, GemsConfig]]] =
    Focus[GuideConfig](_.gaosGuide)
  val gemsSkyPaused: Lens[GuideConfig, Boolean]                              = Focus[GuideConfig](_.gemsSkyPaused)
  val altairGuide: Optional[GuideConfig, AltairConfig]                       =
    gaosGuide.andThen(option.some).andThen(either.stdLeft)
  val gemsGuide: Optional[GuideConfig, GemsConfig]                           =
    gaosGuide.andThen(option.some).andThen(either.stdRight)

}

sealed trait GuideConfigDb[F[_]] {
  def value: F[GuideConfig]

  def set(v: GuideConfig): F[Unit]

  def update(f: GuideConfig => GuideConfig): F[Unit]

  def discrete: Stream[F, GuideConfig]
}

object GuideConfigDb {

  val defaultGuideConfig: GuideConfig =
    GuideConfig(TelescopeGuideConfig(MountGuideOff, M1GuideOff, M2GuideOff), None, false)

  def newDb[F[_]: Concurrent]: F[GuideConfigDb[F]] =
    SignallingRef[F, GuideConfig](defaultGuideConfig).map { ref =>
      new GuideConfigDb[F] {
        override def value: F[GuideConfig] = ref.get

        override def set(v: GuideConfig): F[Unit] = ref.set(v)

        override def update(f: GuideConfig => GuideConfig): F[Unit] = ref.update(f)

        override def discrete: Stream[F, GuideConfig] = ref.discrete
      }
    }

  def constant[F[_]: Applicative]: GuideConfigDb[F] =
    new GuideConfigDb[F] {
      override def value: F[GuideConfig] = GuideConfigDb.defaultGuideConfig.pure[F]

      override def set(v: GuideConfig): F[Unit] = Applicative[F].unit

      override def update(f: GuideConfig => GuideConfig): F[Unit] = Applicative[F].unit

      override def discrete: Stream[F, GuideConfig] = Stream.emit(GuideConfigDb.defaultGuideConfig)
    }

  given Decoder[AltairConfig] = Decoder.instance[AltairConfig] { c =>
    c.downField("aoOn").as[Boolean].flatMap {
      if (_)
        c.downField("mode").as[String].flatMap {
          case "NGS" =>
            for {
              blnd <- c.downField("oiBlend").as[Boolean]
              gsx  <- c.downField("aogsx").as[Double]
              gsy  <- c.downField("aogsy").as[Double]
            } yield Ngs(blnd, (gsx.withUnit[Millimeter], gsy.withUnit[Millimeter]))
          case "LGS" =>
            c.downField("useP1").as[Boolean].flatMap {
              if (_) Right(LgsWithP1)
              else
                c.downField("useOI").as[Boolean].flatMap {
                  if (_) Right(LgsWithOi)
                  else
                    for {
                      strapLoop <- c.downField("strapOn").as[Boolean]
                      sfoLoop   <- c.downField("sfoOn").as[Boolean]
                      gsx       <- c.downField("aogsx").as[Double]
                      gsy       <- c.downField("aogsy").as[Double]
                    } yield Lgs(strapLoop,
                                sfoLoop,
                                (gsx.withUnit[Millimeter], gsy.withUnit[Millimeter])
                    )
                }
            }
          case _     => Left(DecodingFailure("AltairConfig", c.history))
        }
      else Right(AltairOff)
    }
  }

  given Decoder[GemsConfig] = Decoder.instance[GemsConfig] { c =>
    c.downField("aoOn").as[Boolean].flatMap { x =>
      if (x)
        for {
          cwfs1 <- c.downField("ttgs1On").as[Boolean]
          cwfs2 <- c.downField("ttgs2On").as[Boolean]
          cwfs3 <- c.downField("ttgs3On").as[Boolean]
          odgw1 <- c.downField("odgw1On").as[Boolean]
          odgw2 <- c.downField("odgw2On").as[Boolean]
          odgw3 <- c.downField("odgw3On").as[Boolean]
          odgw4 <- c.downField("odgw4On").as[Boolean]
          useP1 <- c.downField("useP1").as[Boolean].recover { case _ => false }
          useOI <- c.downField("useOI").as[Boolean].recover { case _ => false }
        } yield GemsOn(
          Cwfs1Usage.fromBoolean(cwfs1),
          Cwfs2Usage.fromBoolean(cwfs2),
          Cwfs3Usage.fromBoolean(cwfs3),
          Odgw1Usage.fromBoolean(odgw1),
          Odgw2Usage.fromBoolean(odgw2),
          Odgw3Usage.fromBoolean(odgw3),
          Odgw4Usage.fromBoolean(odgw4),
          P1Usage.fromBoolean(useP1),
          OIUsage.fromBoolean(useOI)
        )
      else Right(GemsOff)
    }
  }

  given Decoder[Either[AltairConfig, GemsConfig]] =
    Decoder.decodeEither[AltairConfig, GemsConfig]("altair", "gems")

  given Decoder[MountGuideOption] =
    Decoder.decodeBoolean.map(_.fold(MountGuideOn, MountGuideOff))

  given Decoder[M1Source] = Decoder.decodeString.flatMap {
    case "PWFS1" => Decoder.const(M1Source.PWFS1)
    case "PWFS2" => Decoder.const(M1Source.PWFS2)
    case "OIWFS" => Decoder.const(M1Source.OIWFS)
    case "HRWFS" => Decoder.const(M1Source.HRWFS)
    case "GAOS"  => Decoder.const(M1Source.GAOS)
    case _       => Decoder.failedWithMessage("M1Source")
  }

  given Decoder[M1GuideConfig] = Decoder.instance[M1GuideConfig] { c =>
    c.downField("on").as[Boolean].flatMap {
      if (_)
        c.downField("source").as[M1Source].map(M1GuideOn(_))
      else Right(M1GuideOff)
    }
  }

  given Decoder[TipTiltSource] = Decoder.decodeString.flatMap {
    case "PWFS1" => Decoder.const(TipTiltSource.PWFS1)
    case "PWFS2" => Decoder.const(TipTiltSource.PWFS2)
    case "OIWFS" => Decoder.const(TipTiltSource.OIWFS)
    case "GAOS"  => Decoder.const(TipTiltSource.GAOS)
    case _       => Decoder.failedWithMessage("TipTiltSource")
  }

  given Decoder[ComaOption] = Decoder.decodeBoolean.map(_.fold(ComaOn, ComaOff))

  given Decoder[M2GuideConfig] = Decoder.instance[M2GuideConfig] { c =>
    c.downField("on").as[Boolean].flatMap {
      if (_)
        for {
          srcs <- c.downField("sources").as[Set[TipTiltSource]]
          coma <- c.downField("comaOn").as[ComaOption] // (comaDecoder)
        } yield M2GuideOn(coma, srcs)
      else Right(M2GuideOff)
    }
  }

  given Decoder[TelescopeGuideConfig] =
    Decoder.forProduct3[TelescopeGuideConfig, MountGuideOption, M1GuideConfig, M2GuideConfig](
      "mountGuideOn",
      "m1Guide",
      "m2Guide"
    )(TelescopeGuideConfig(_, _, _)) // (mountGuideDecoder, m1GuideDecoder, m2GuideDecoder)

  given Decoder[GuideConfig] =
    Decoder
      .forProduct2[GuideConfig, TelescopeGuideConfig, Option[Either[AltairConfig, GemsConfig]]](
        "tcsGuide",
        "gaosGuide"
      )(GuideConfig(_, _, false))

}
