// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.effect.IO
import cats.data.Nested
import cats.tests.CatsSuite
import org.http4s._
import org.http4s.Uri.uri
import observe.model.UserLoginRequest
import observe.web.server.http4s.encoder._

class ObserveUIApiRoutesSpec extends CatsSuite with ClientBooEncoders with TestRoutes {
  test("ObserveUIApiRoutes login: reject requests without body") {
    (for {
      s <- uiRoutes
      r <- Nested(
             s.apply(Request(method = Method.POST, uri = uri("/observe/login"))).value
           ).map(_.status).value
    } yield assert(r === Some(Status.BadRequest))).unsafeRunSync()
  }

  test("ObserveUIApiRoutes login: reject GET requests") {
    // This should in principle return a 405
    // see https://github.com/http4s/http4s/issues/234
    val r = (for {
      s <- uiRoutes
      r <- Nested(
             s.apply(Request(method = Method.GET, uri = uri("/observe/login"))).value
           ).map(_.status).value
    } yield r).unsafeRunSync()
    assert(r === Some(Status.NotFound))
  }

  test("ObserveUIApiRoutes login: successful login gives a cookie") {
    (for {
      s <- uiRoutes
      r <- s
             .apply(
               Request(method = Method.POST, uri = uri("/observe/login"))
                 .withEntity(UserLoginRequest("telops", "pwd"))
             )
             .value
      s <- r.map(_.status).pure[IO]
      k <- r.map(_.cookies).orEmpty.pure[IO]
      t  = k.find(_.name === "token")
    } yield assert(t.isDefined && s === Some(Status.Ok))).unsafeRunSync()
  }

  test("ObserveUIApiRoutes logout: successful logout clears the cookie") {
    (for {
      s <- uiRoutes
      t <- newLoginToken
      r <- s(
             Request[IO](method = Method.POST, uri = uri("/observe/logout"))
               .addCookie("token", t)
           ).value
      s <- r.map(_.status).pure[IO]
      k <- r.map(_.cookies).orEmpty.pure[IO]
      t  = k.find(_.name === "token")
      c  = t.map(_.content).exists(_ === "") // Cleared cookie
    } yield assert(c && s === Some(Status.Ok))).unsafeRunSync()
  }

  test("ObserveUIApiRoutes site") {
    (for {
      s <- uiRoutes
      t <- newLoginToken
      r <- s(
             Request[IO](method = Method.POST, uri = uri("/observe/site"))
               .addCookie("token", t)
           ).value
      s <- r.map(_.as[String]).sequence
    } yield assert(s === Some("GS"))).unsafeRunSync()
  }

}
