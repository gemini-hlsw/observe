// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import cats.effect.Async
import cats.syntax.all.*
import observe.model.Observation
import observe.model.dhs.ImageFileId
import observe.server.ObserveFailure
import org.http4s.*
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.scalaxml.*

import scala.concurrent.duration.*
import scala.xml.Elem

/**
 * Gemini Data service client
 */
trait GdsClient[F[_]] extends Http4sClientDsl[F] {

  /**
   * Set the keywords for an image
   */
  def setKeywords(id: ImageFileId, ks: KeywordBag): F[Unit]

  def openObservation(obsId: Observation.Id, id: ImageFileId, ks: KeywordBag): F[Unit]

  def closeObservation(id: ImageFileId): F[Unit]
}

object GdsClient {

  def apply[F[_]](base: Client[F], gdsUri: Uri)(using
    timer: Async[F]
  ): GdsClient[F] = new GdsClient[F] {

    private val client = {
      val max             = 2
      var attemptsCounter = 1
      val policy          = RetryPolicy[F] { (attempts: Int) =>
        if (attempts >= max) None
        else {
          attemptsCounter = attemptsCounter + 1
          Some(10.milliseconds)
        }
      }
      Retry(policy)(base)
    }

    // Build an xml rpc request to store keywords
    private def storeKeywords(id: ImageFileId, ks: KeywordBag): Elem =
      <methodCall>
        <methodName>HeaderReceiver.storeKeywords</methodName>
        <params>
          <param>
            <value>
              <string>{id}</string>
            </value>
          </param>
          {keywordsParam(ks)}
        </params>
      </methodCall>

    /**
     * Set the keywords for an image
     */
    override def setKeywords(id: ImageFileId, ks: KeywordBag): F[Unit] = {
      // Build the request
      val xmlRpc      = storeKeywords(id, ks)
      val postRequest = POST(xmlRpc, gdsUri)

      // Do the request
      client
        .expect[Elem](postRequest)(using scalaxml.xmlDecoder)
        .map(GdsClient.parseError)
        .ensureOr(toObserveFailure)(_.isRight)
        .void
    }

    // Build an xml rpc request to open an observation
    private def openObservationRPC(obsId: Observation.Id, id: ImageFileId, ks: KeywordBag): Elem =
      <methodCall>
        <methodName>HeaderReceiver.openObservation</methodName>
        <params>
          <param>
            <value>
              <string>{obsId.toString}</string>
            </value>
          </param>
          <param>
            <value>
              <string>{id}</string>
            </value>
          </param>
          {keywordsParam(ks)}
        </params>
      </methodCall>

    override def openObservation(
      obsId: Observation.Id,
      id:    ImageFileId,
      ks:    KeywordBag
    ): F[Unit] = {
      // Build the request
      val xmlRpc      = openObservationRPC(obsId, id, ks)
      val postRequest = POST(xmlRpc, gdsUri)

      // Do the request
      client
        .expect[Elem](postRequest)(using scalaxml.xmlDecoder)
        .map(GdsClient.parseError)
        .ensureOr(toObserveFailure)(_.isRight)
        .void
    }

    // Build an xml rpc request to close an observation
    private def closeObservationRPC(id: ImageFileId): Elem =
      <methodCall>
        <methodName>HeaderReceiver.closeObservation</methodName>
        <params>
          <param>
            <value>
              <string>{id}</string>
            </value>
          </param>
        </params>
      </methodCall>

    override def closeObservation(id: ImageFileId): F[Unit] = {
      // Build the request
      val xmlRpc      = closeObservationRPC(id)
      val postRequest = POST(xmlRpc, gdsUri)

      // Do the request
      client
        .expect[Elem](postRequest)(using scalaxml.xmlDecoder)
        .map(GdsClient.parseError)
        .ensureOr(toObserveFailure)(_.isRight)
        .void
    }

    private def keywordsParam(ks: KeywordBag): Elem =
      <param>
        <value>
          <array>
            <data>
              {
        ks.keywords.map { k =>
          <value><string>{
            s"${k.name},${KeywordType.gdsKeywordType(k.keywordType)},${k.value}"
          }</string></value>
        }
      }
            </data>
          </array>
        </value>
      </param>

    def toObserveFailure(v: Either[String, Elem]): ObserveFailure =
      ObserveFailure.GdsXmlError(v.left.getOrElse(""), gdsUri)

  }

  def parseError(e: Elem): Either[String, Elem] = {
    val v = for {
      m <- e \\ "methodResponse" \ "fault" \ "value" \ "struct" \\ "member"
      if (m \ "name").text === "faultString"
    } yield (m \ "value").text.trim
    v.headOption.toLeft(e)
  }

  /**
   * Client for testing always returns ok
   */
  def alwaysOkClient[F[_]: Async]: Client[F] = {
    val service = HttpRoutes.of[F] { case _ =>
      val response =
        <methodResponse>
            <params>
              <param>
                  <value><string>Ok</string></value>
              </param>
            </params>
          </methodResponse>
      Response[F](Status.Ok).withEntity(response).pure[F]
    }
    Client.fromHttpApp(service.orNotFound)
  }
}
