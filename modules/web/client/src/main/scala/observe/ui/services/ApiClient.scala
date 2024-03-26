// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.*
import io.circe.Encoder
import io.circe.syntax.*
import observe.model.ClientId
import org.http4s.*
import org.http4s.Method
import org.http4s.Uri
import org.http4s.circe.*
import org.http4s.client.*
import org.http4s.client.Client
import org.http4s.headers.Authorization

case class ApiClient(
  httpClient: Client[IO],
  basePath:   Uri.Path,
  clientId:   ClientId,
  token:      NonEmptyString,
  onError:    Throwable => IO[Unit]
) extends BaseApi[IO]:
  def get(path: Uri.Path): IO[Unit] =
    httpClient
      .expect[Unit](
        Request(Method.GET, Uri(path = basePath.merge(path)))
          .withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token.value)))
      )
      .onError(onError)
      .onCancel(onError(new Exception("There was an error invoking the server.")))

  def post[T: Encoder](path: Uri.Path, data: T, query: Query = Query.empty): IO[Unit] =
    httpClient
      .expect[Unit](
        Request(Method.POST, Uri(path = basePath.merge(path), query = query))
          .withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token.value)))
          .withEntity(data.asJson)
      )
      .onError(onError)
      .onCancel(onError(new Exception("There was an error invoking the server.")))

  def postNoData(path: Uri.Path, query: Query = Query.empty): IO[Unit] =
    post(path, (), query)

  override def refresh: IO[Unit] =
    get(Uri.Path.empty / clientId.value / "refresh")
