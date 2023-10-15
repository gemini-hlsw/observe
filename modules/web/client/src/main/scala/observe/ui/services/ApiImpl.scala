// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Encoder
import io.circe.*
import io.circe.syntax.*
import org.http4s.Method
import org.http4s.Uri
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.client.*
import org.http4s.headers.Authorization

trait ApiImpl:
  def client: Client[IO]
  def baseUri: Uri
  def token: NonEmptyString
  def onError: Throwable => IO[Unit]

  protected def request[T: Encoder](path: String, data: T): IO[Unit] =
    client
      .expect[Unit](
        Request(Method.POST, baseUri.addPath(path))
          .withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token.value)))
          .withEntity(data.asJson)
      )
      .onError(onError)
      .onCancel(onError(new Exception("There was an error modifying configuration.")))
