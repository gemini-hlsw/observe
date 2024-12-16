// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import cats.FlatMap
import cats.data.EitherT
import cats.effect.Clock
import cats.effect.Ref
import cats.effect.Sync
import cats.effect.Temporal
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*
import lucuma.core.enums.Site
import observe.model.dhs.*
import observe.model.enums.DhsKeywordName
import observe.model.enums.KeywordName
import observe.server.*
import observe.server.ObserveFailure
import observe.server.ObserveFailure.ObserveExceptionWhile
import observe.server.keywords.DhsClient.ImageParameters
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.http4s.dsl.io.*
import org.typelevel.log4cats.Logger

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.*

/**
 * Implementation of DhsClient that interfaces with the real DHS over the http interface
 */
class DhsClientHttp[F[_]](base: Client[F], baseURI: Uri, maxKeywords: Int, instrumentName: String)(
  using timer: Temporal[F]
) extends DhsClient[F]
    with Http4sClientDsl[F] {
  import DhsClientHttp.given

  private val clientWithRetry = {
    val max             = 4
    var attemptsCounter = 1
    val policy          = RetryPolicy[F] { (attempts: Int) =>
      if (attempts >= max) None
      else {
        attemptsCounter = attemptsCounter + 1
        10.milliseconds.some
      }
    }
    Retry[F](policy)(base)
  }

  override def createImage(p: ImageParameters): F[ImageFileId] = {
    val req = POST(
      Json.obj("createImage" := p.asJson),
      baseURI
    )
    clientWithRetry
      .expect[Either[ObserveFailure, ImageFileId]](req)(
        jsonOf[F, Either[ObserveFailure, ImageFileId]]
      )
      .attemptT
      .leftMap(ObserveExceptionWhile("creating image in DHS", _): ObserveFailure)
      .flatMap(EitherT.fromEither(_))
      .liftF
  }

  private def keywordsPutReq(id: ImageFileId, keywords: List[InternalKeyword], finalFlag: Boolean) =
    PUT(
      Json.obj(
        "setKeywords" :=
          Json.obj(
            "final"    := finalFlag,
            "keywords" := keywords
          )
      ),
      baseURI / id.value / "keywords"
    )

  override def setKeywords(
    id:        ImageFileId,
    keywords:  KeywordBag,
    finalFlag: Boolean
  ): F[Unit] = {

    val minKeywords: Int = 10

    val limit = minKeywords.max(maxKeywords)

    val pkgs = keywords.keywords.grouped(limit).toList

    if (pkgs.isEmpty) {
      clientWithRetry
        .expect[Either[ObserveFailure, Unit]](
          keywordsPutReq(
            id,
            List(internalKeywordConvert(StringKeyword(KeywordName.INSTRUMENT, instrumentName))),
            finalFlag
          )
        )(jsonOf[F, Either[ObserveFailure, Unit]])
        .attemptT
        .leftMap(ObserveExceptionWhile("sending keywords to DHS", _))
        .flatMap(EitherT.fromEither(_))
        .liftF
        .whenA(finalFlag)
    } else
      pkgs.zipWithIndex
        .map { case (l, i) =>
          clientWithRetry
            .expect[Either[ObserveFailure, Unit]](
              keywordsPutReq(
                id,
                l.prepended(
                  internalKeywordConvert(StringKeyword(KeywordName.INSTRUMENT, instrumentName))
                ),
                (i === pkgs.length - 1) && finalFlag
              )
            )(jsonOf[F, Either[ObserveFailure, Unit]])
            .attemptT
            .leftMap(ObserveExceptionWhile("sending keywords to DHS", _))
            .flatMap(EitherT.fromEither(_))
            .liftF
        }
        .sequence
        .void
  }

}

object DhsClientHttp {

  sealed case class ErrorType(str: String)
  object BadRequest          extends ErrorType("BAD_REQUEST")
  object DhsError            extends ErrorType("DHS_ERROR")
  object InternalServerError extends ErrorType("INTERNAL_SERVER_ERROR")

  given Decoder[ErrorType] = Decoder.instance[ErrorType](
    _.as[String]
      .map {
        case BadRequest.str          => BadRequest
        case DhsError.str            => DhsError
        case InternalServerError.str => InternalServerError
        case _                       => InternalServerError
      }
  )

  given Decoder[Error] = Decoder.instance[Error](c =>
    for {
      t   <- c.downField("type").as[ErrorType]
      msg <- c.downField("message").as[String]
    } yield Error(t, msg)
  )

  given obsIdDecode: Decoder[Either[ObserveFailure, ImageFileId]] =
    Decoder.instance[Either[ObserveFailure, ImageFileId]] { c =>
      val r = c.downField("response")
      val s = r.downField("status").as[String]
      s.flatMap {
        case "success" =>
          r.downField("result").as[String].map(i => ImageFileId(i).asRight)
        case "error"   =>
          r.downField("errors")
            .as[List[Error]]
            .map(l => ObserveFailure.Unexpected(l.mkString(", ")).asLeft)
        case r         =>
          Left(DecodingFailure(s"Unknown response: $r", c.history))
      }
    }

  given unitDecode: Decoder[Either[ObserveFailure, Unit]] =
    Decoder.instance[Either[ObserveFailure, Unit]] { c =>
      val r = c.downField("response")
      val s = r.downField("status").as[String]
      s.flatMap {
        case "success" =>
          ().asRight.asRight
        case "error"   =>
          r.downField("errors")
            .as[List[Error]]
            .map(l => ObserveFailure.Unexpected(l.mkString(", ")).asLeft[Unit])
        case r         =>
          Left(DecodingFailure(s"Unknown response: $r", c.history))
      }
    }

  given imageParametersEncode: Encoder[DhsClient.ImageParameters] =
    Encoder.instance[DhsClient.ImageParameters](p =>
      Json.obj(
        "lifetime"     := p.lifetime.str,
        "contributors" := p.contributors
      )
    )

  given keywordEncode: Encoder[InternalKeyword] =
    Encoder.instance[InternalKeyword](k =>
      Json.obj(
        "name"  := DhsKeywordName.all.find(_.keyword === k.name).map(_.name).getOrElse(k.name.name),
        "type"  := KeywordType.dhsKeywordType(k.keywordType),
        "value" := k.value
      )
    )

  final case class Error(t: ErrorType, msg: String) {
    override def toString = s"(${t.str}) $msg"
  }

  def apply[F[_]](client: Client[F], uri: Uri, maxKeywords: Int, instrumentName: String)(using
    timer: Temporal[F]
  ): DhsClient[F] =
    new DhsClientHttp[F](client, uri, maxKeywords, instrumentName)
}

/**
 * Implementation of the Dhs client that simulates a dhs without external dependencies
 */
private class DhsClientSim[F[_]: FlatMap: Logger](site: Site, date: LocalDate, counter: Ref[F, Int])
    extends DhsClient[F] {

  val format: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

  private val sitePrefix = site match {
    case Site.GS => "S"
    case Site.GN => "N"
  }

  override def createImage(p: ImageParameters): F[ImageFileId] =
    counter.modify(x => (x + 1, x + 1)).map { c =>
      ImageFileId(f"$sitePrefix${date.format(format)}S$c%04d")

    }

  override def setKeywords(id: ImageFileId, keywords: KeywordBag, finalFlag: Boolean): F[Unit] = {
    val keyStr = keywords.keywords.map(k => s"${k.name} = ${k.value}").mkString(", ")
    Logger[F].info(s"file: $id, final: $finalFlag, keywords: $keyStr")
  }

}

object DhsClientSim {
  def apply[F[_]: Sync: Logger](site: Site): F[DhsClient[F]] =
    Clock[F].monotonic
      .map(d => Instant.EPOCH.plusNanos(d.toNanos))
      .map(LocalDateTime.ofInstant(_, ZoneId.systemDefault))
      .flatMap(apply(site, _))

  def apply[F[_]: Sync: Logger](site: Site, dateTime: LocalDateTime): F[DhsClient[F]] =
    Ref // Initialize with ordinal of 10-second lapse in the day, between 0 and 8640
      .of[F, Int](dateTime.getHour() * 360 + dateTime.getMinute() * 6 + dateTime.getSecond() / 10)
      .map: counter =>
        new DhsClientSim[F](site, dateTime.toLocalDate, counter)

}
