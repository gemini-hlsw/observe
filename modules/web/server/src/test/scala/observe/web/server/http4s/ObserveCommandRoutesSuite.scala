// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.effect.IO
import cats.syntax.all.*
import io.circe.syntax.*
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import observe.model.ClientId
import org.http4s.*
import org.http4s.circe.*
import org.http4s.implicits.*
import org.http4s.server.websocket.WebSocketBuilder2

import java.util.UUID

class ObserveCommandRoutesSuite extends munit.CatsEffectSuite with TestRoutes:
  val clientId = ClientId(UUID.randomUUID())
  val obsId    = Observation.Id.fromLong(1000).get
  val stepId   = Step.Id.fromUuid(UUID.randomUUID())

  test("reset conditions"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(Request[IO](method = Method.POST, uri = uri"/resetconditions")).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("update water vapor"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"/${clientId.value}/wv"))
                    .withEntity((WaterVapor.Wet: WaterVapor).asJson)
                ).value
      b      <- l.traverse(_.as[String])
    yield (l.map(_.status), b)
    assertIO(r.map(_._1), Some(Status.NoContent)) *>
      assertIO(r.map(_._2), Some(s""))

  test("update image quality"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"/${clientId.value}/iq"))
                    .withEntity((ImageQuality.PointTwo: ImageQuality).asJson)
                ).value
      b      <- l.traverse(_.as[String])
    yield (l.map(_.status), b)
    assertIO(r.map(_._1), Some(Status.NoContent)) *>
      assertIO(r.map(_._2), Some(s""))

  test("update sky background"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"/${clientId.value}/sb"))
                    .withEntity((SkyBackground.Darkest: SkyBackground).asJson)
                ).value
      b      <- l.traverse(_.as[String])
    yield (l.map(_.status), b)
    assertIO(r.map(_._1), Some(Status.NoContent)) *>
      assertIO(r.map(_._2), Some(s""))

  test("update cloud extinction"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"/${clientId.value}/ce"))
                    .withEntity((CloudExtinction.PointFive: CloudExtinction).asJson)
                ).value
      b      <- l.traverse(_.as[String])
    yield (l.map(_.status), b)
    assertIO(r.map(_._1), Some(Status.NoContent)) *>
      assertIO(r.map(_._2), Some(s""))

  test("load sequence"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](
                    method = Method.POST,
                    uri = Uri.unsafeFromString(
                      s"/load/GmosSouth/${obsId.show}/${clientId.value}/observer"
                    )
                  )
                ).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("execute sequence step/resource"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](
                    method = Method.POST,
                    uri = Uri.unsafeFromString(
                      s"/${obsId.show}/${stepId.show}/${clientId.value}/execute/TCS/observer"
                    )
                  )
                ).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("disable tcs"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](
                    method = Method.POST,
                    uri = Uri.unsafeFromString(
                      s"/${obsId.show}/${clientId.value}/tcsEnabled/false"
                    )
                  )
                ).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("disable gcal"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](
                    method = Method.POST,
                    uri = Uri.unsafeFromString(
                      s"/${obsId.show}/${clientId.value}/gcalEnabled/false"
                    )
                  )
                ).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("disable instrument"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](
                    method = Method.POST,
                    uri = Uri.unsafeFromString(
                      s"/${obsId.show}/${clientId.value}/instrumentEnabled/false"
                    )
                  )
                ).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("disable dhs"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](
                    method = Method.POST,
                    uri = Uri.unsafeFromString(
                      s"/${obsId.show}/${clientId.value}/dhsEnabled/false"
                    )
                  )
                ).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("set breakpoint"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <-
        s(
          Request[IO](
            method = Method.POST,
            uri = Uri.unsafeFromString(
              s"/${obsId.show}/${stepId.show}/${clientId.value}/breakpoint/observer/true"
            )
          )
        ).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("set breakpoints"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <-
        s(
          Request[IO](
            method = Method.POST,
            uri = Uri
              .unsafeFromString(
                s"/${obsId.show}/${clientId.value}/breakpoints/observer/true"
              )
          )
            .withEntity(List(stepId).asJson)
        ).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("start"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <-
        s(
          Request[IO](
            method = Method.POST,
            uri = Uri.unsafeFromString(
              s"/${obsId.show}/${clientId.value}/start/observer?overrideTargetCheck=true"
            )
          )
        ).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("set operator"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <-
        s(
          Request[IO](
            method = Method.POST,
            uri = Uri.unsafeFromString(
              s"/${clientId.value}/operator/Anybody"
            )
          )
        ).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("set observer"):
    val r = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <-
        s(
          Request[IO](
            method = Method.POST,
            uri = Uri.unsafeFromString(
              s"/${obsId.show}/${clientId.value}/observer/Anybody"
            )
          )
        ).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("start sequence from"):
    val uri = Uri.unsafeFromString(
      s"/${obsId.show}/${stepId.show}/${clientId.value}/startFrom/observer"
    )
    val r   = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(Request[IO](method = Method.POST, uri = uri)).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("pause sequence"):
    val uri = Uri.unsafeFromString(s"/${obsId.show}/${clientId.value}/pause/observer")
    val r   = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(Request[IO](method = Method.POST, uri = uri)).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("cancelpause sequence"):
    val uri = Uri.unsafeFromString(s"/${obsId.show}/${clientId.value}/cancelPause/observer")
    val r   = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(Request[IO](method = Method.POST, uri = uri)).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  /* test("set skip") {
    val engine = mock[ObserveEngine[IO]]
    inAnyOrder {
      (engine.setSkipMark _)
        .expects(*, *, *, *, *, *)
        .anyNumberOfTimes()
        .returning(IO.unit)
    }
    forAll { (obsId: Observation.Id, toSet: StepId, set: Boolean) =>
      val uri    = Uri.unsafeFromString(s"/${obsId.show}/$toSet/skip/observer/$set")
      val (s, b) = (for {
        s   <- commandRoutes(engine)
        wsb <- WebSocketBuilder2[IO]
        t   <- newLoginToken(wsb)
        l   <- s(
                 Request[IO](method = Method.POST, uri = uri).addCookie("token", t)
               ).value
      } yield (l.map(_.status), l.map(_.as[String]).sequence)).unsafeRunSync()
      assert(s === Some(Status.Ok))
      assert(
        b.unsafeRunSync() === Some(
          s"Set skip mark in step $toSet of sequence ${obsId.show}"
        )
      )
    }
  } */

  test("stop sequence"):
    val uri = Uri.unsafeFromString(s"/${obsId.show}/${stepId.show}/${clientId.value}/stop/observer")
    val r   = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(Request[IO](method = Method.POST, uri = uri)).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("stop sequence gracefully"):
    val uri = Uri.unsafeFromString(
      s"/${obsId.show}/${stepId.show}/${clientId.value}/stopGracefully/observer"
    )
    val r   = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(Request[IO](method = Method.POST, uri = uri)).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("abort sequence"):
    val uri =
      Uri.unsafeFromString(s"/${obsId.show}/${stepId.show}/${clientId.value}/abort/observer")
    val r   = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(Request[IO](method = Method.POST, uri = uri)).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("pause obs sequence"):
    val uri =
      Uri.unsafeFromString(s"/${obsId.show}/${stepId.show}/${clientId.value}/pauseObs/observer")
    val r   = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(Request[IO](method = Method.POST, uri = uri)).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("pause obs gracefully"):
    val uri = Uri.unsafeFromString(
      s"/${obsId.show}/${stepId.show}/${clientId.value}/pauseObsGracefully/observer"
    )
    val r   = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(Request[IO](method = Method.POST, uri = uri)).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("resume obs"):
    val uri =
      Uri.unsafeFromString(s"/${obsId.show}/${stepId.show}/${clientId.value}/resumeObs/observer")
    val r   = for
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(Request[IO](method = Method.POST, uri = uri)).value
    yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))
