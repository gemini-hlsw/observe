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
    val r = for {
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST, uri = uri"/resetconditions")
                ).value
    } yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))

  test("update water vapor"):
    val r = for {
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"/${clientId.value}/wv"))
                    .withEntity((WaterVapor.Wet: WaterVapor).asJson)
                ).value
      b      <- l.traverse(_.as[String])
    } yield (l.map(_.status), b)
    assertIO(r.map(_._1), Some(Status.NoContent)) *>
      assertIO(r.map(_._2), Some(s""))

  test("update image quality"):
    val r = for {
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"/${clientId.value}/iq"))
                    .withEntity((ImageQuality.PointTwo: ImageQuality).asJson)
                ).value
      b      <- l.traverse(_.as[String])
    } yield (l.map(_.status), b)
    assertIO(r.map(_._1), Some(Status.NoContent)) *>
      assertIO(r.map(_._2), Some(s""))

  test("update sky background"):
    val r = for {
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"/${clientId.value}/sb"))
                    .withEntity((SkyBackground.Darkest: SkyBackground).asJson)
                ).value
      b      <- l.traverse(_.as[String])
    } yield (l.map(_.status), b)
    assertIO(r.map(_._1), Some(Status.NoContent)) *>
      assertIO(r.map(_._2), Some(s""))

  test("update cloud extinction"):
    val r = for {
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"/${clientId.value}/ce"))
                    .withEntity((CloudExtinction.PointFive: CloudExtinction).asJson)
                ).value
      b      <- l.traverse(_.as[String])
    } yield (l.map(_.status), b)
    assertIO(r.map(_._1), Some(Status.NoContent)) *>
      assertIO(r.map(_._2), Some(s""))

  test("load sequence") {
    val r = for {
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST,
                              uri = Uri.unsafeFromString(
                                s"/load/GmosS/${obsId.show}/${clientId.value}/observer"
                              )
                  )
                ).value
    } yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))
  }

  test("execute sequence step/resource") {
    val r = for {
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST,
                              uri = Uri.unsafeFromString(
                                s"/${obsId.show}/execute/${stepId.show}/TCS/observer/${clientId.value}"
                              )
                  )
                ).value
    } yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))
  }

  test("disable tcs") {
    val r = for {
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST,
                              uri = Uri.unsafeFromString(
                                s"/${obsId.show}/${clientId.value}/tcsEnabled/false"
                              )
                  )
                ).value
    } yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))
  }

  test("disable gcal") {
    val r = for {
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST,
                              uri = Uri.unsafeFromString(
                                s"/${obsId.show}/${clientId.value}/gcalEnabled/false"
                              )
                  )
                ).value
    } yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))
  }

  test("disable instrument") {
    val r = for {
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST,
                              uri = Uri.unsafeFromString(
                                s"/${obsId.show}/${clientId.value}/instrumentEnabled/false"
                              )
                  )
                ).value
    } yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))
  }

  test("disable dhs") {
    val r = for {
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      l      <- s(
                  Request[IO](method = Method.POST,
                              uri = Uri.unsafeFromString(
                                s"/${obsId.show}/${clientId.value}/dhsEnabled/false"
                              )
                  )
                ).value
    } yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))
  }

  //
  // test("start sequence") {
  //   forAll { (obsId: Observation.Id, clientId: ClientId) =>
  //     val (s, b) = (for {
  //       engine <- TestObserveEngine.build[IO]
  //       s      <- commandRoutes(engine)
  //       wsb    <- WebSocketBuilder2[IO]
  //       t      <- newLoginToken(wsb)
  //       l      <- s(
  //                   Request[IO](method = Method.POST,
  //                               uri = Uri.unsafeFromString(
  //                                 s"/${obsId.show}/start/observer/${clientId.self}"
  //                               )
  //                   )
  //                     .addCookie("token", t)
  //                 ).value
  //     } yield (l.map(_.status), l.map(_.as[String]).sequence)).unsafeRunSync()
  //     assert(s === Some(Status.Ok))
  //     assert(b.unsafeRunSync() === Some(s"Started sequence ${obsId.show}"))
  //   }
  // }
  //
  // test("start sequence from") {
  //   forAll { (obsId: Observation.Id, startFrom: StepId, clientId: ClientId) =>
  //     val uri    = Uri.unsafeFromString(
  //       s"/${obsId.show}/${startFrom.show}/startFrom/observer/${clientId.self}"
  //     )
  //     val (s, b) = (for {
  //       engine <- TestObserveEngine.build[IO]
  //       s      <- commandRoutes(engine)
  //       wsb    <- WebSocketBuilder2[IO]
  //       t      <- newLoginToken(wsb)
  //       l      <- s(
  //                   Request[IO](method = Method.POST, uri = uri).addCookie("token", t)
  //                 ).value
  //     } yield (l.map(_.status), l.map(_.as[String]).sequence)).unsafeRunSync()
  //     assert(s === Some(Status.Ok))
  //     assert(
  //       b.unsafeRunSync() === Some(
  //         s"Started sequence ${obsId.show} from step $startFrom"
  //       )
  //     )
  //   }
  // }
  //
  // test("pause sequence") {
  //   forAll { (obsId: Observation.Id) =>
  //     val uri    = Uri.unsafeFromString(s"/${obsId.show}/pause/observer")
  //     val (s, b) = (for {
  //       engine <- TestObserveEngine.build[IO]
  //       s      <- commandRoutes(engine)
  //       wsb    <- WebSocketBuilder2[IO]
  //       t      <- newLoginToken(wsb)
  //       l      <- s(
  //                   Request[IO](method = Method.POST, uri = uri).addCookie("token", t)
  //                 ).value
  //     } yield (l.map(_.status), l.map(_.as[String]).sequence)).unsafeRunSync()
  //     assert(s === Some(Status.Ok))
  //     assert(b.unsafeRunSync() === Some(s"Pause sequence ${obsId.show}"))
  //   }
  // }

  /*  test("cancelpause sequence") {
    val engine = mock[ObserveEngine[IO]]
    inAnyOrder {
      (engine.requestCancelPause _)
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(IO.unit)
    }
    forAll { (obsId: Observation.Id) =>
      val uri    = Uri.unsafeFromString(s"/${obsId.show}/cancelpause/observer")
      val (s, b) = (for {
        s   <- commandRoutes(engine)
        wsb <- WebSocketBuilder2[IO]
        t   <- newLoginToken(wsb)
        l   <- s(
                 Request[IO](method = Method.POST, uri = uri).addCookie("token", t)
               ).value
      } yield (l.map(_.status), l.map(_.as[String]).sequence)).unsafeRunSync()
      assert(s === Some(Status.Ok))
      assert(b.unsafeRunSync() === Some(s"Cancel Pause sequence ${obsId.show}"))
    }
  }

  test("set breakpoint") {
    val engine = mock[ObserveEngine[IO]]
    inAnyOrder {
      (engine.setBreakpoint _)
        .expects(*, *, *, *, *, *)
        .anyNumberOfTimes()
        .returning(IO.unit)
    }
    forAll { (obsId: Observation.Id, toSet: StepId, set: Boolean) =>
      val uri    = Uri.unsafeFromString(s"/${obsId.show}/$toSet/breakpoint/observer/$set")
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
          s"Set breakpoint in step $toSet of sequence ${obsId.show}"
        )
      )
    }
  }

  test("set skip") {
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
  }

  test("stop sequence") {
    val engine = mock[ObserveEngine[IO]]
    inAnyOrder {
      (engine.stopObserve _)
        .expects(*, *, *, *, false)
        .anyNumberOfTimes()
        .returning(IO.unit)
    }
    forAll { (obsId: Observation.Id, step: StepId) =>
      val uri    = Uri.unsafeFromString(s"/${obsId.show}/$step/stop/observer")
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
          s"Stop requested for ${obsId.show} on step $step"
        )
      )
    }
  }

  test("stop sequence gracefully") {
    val engine = mock[ObserveEngine[IO]]
    inAnyOrder {
      (engine.stopObserve _)
        .expects(*, *, *, *, true)
        .anyNumberOfTimes()
        .returning(IO.unit)
    }
    forAll { (obsId: Observation.Id, step: StepId) =>
      val uri    = Uri.unsafeFromString(s"/${obsId.show}/$step/stopGracefully/observer")
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
          s"Stop gracefully requested for ${obsId.show} on step $step"
        )
      )
    }
  }

  test("abort sequence") {
    val engine = mock[ObserveEngine[IO]]
    inAnyOrder {
      (engine.abortObserve _)
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(IO.unit)
    }
    forAll { (obsId: Observation.Id, step: StepId) =>
      val uri    = Uri.unsafeFromString(s"/${obsId.show}/$step/abort/observer")
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
          s"Abort requested for ${obsId.show} on step $step"
        )
      )
    }
  }

  test("pause obs sequence") {
    val engine = mock[ObserveEngine[IO]]
    inAnyOrder {
      (engine.pauseObserve _)
        .expects(*, *, *, *, false)
        .anyNumberOfTimes()
        .returning(IO.unit)
    }
    forAll { (obsId: Observation.Id, step: StepId) =>
      val uri    = Uri.unsafeFromString(s"/${obsId.show}/$step/pauseObs/observer")
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
          s"Pause observation requested for ${obsId.show} on step $step"
        )
      )
    }
  }

  test("pause obs gracefully") {
    val engine = mock[ObserveEngine[IO]]
    inAnyOrder {
      (engine.pauseObserve _)
        .expects(*, *, *, *, true)
        .anyNumberOfTimes()
        .returning(IO.unit)
    }
    forAll { (obsId: Observation.Id, step: StepId) =>
      val uri    =
        Uri.unsafeFromString(s"/${obsId.show}/$step/pauseObsGracefully/observer")
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
          s"Pause observation gracefully requested for ${obsId.show} on step $step"
        )
      )
    }
  }

  test("resume obs") {
    val engine = mock[ObserveEngine[IO]]
    inAnyOrder {
      (engine.resumeObserve _)
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(IO.unit)
    }
    forAll { (obsId: Observation.Id, step: StepId) =>
      val uri    = Uri.unsafeFromString(s"/${obsId.show}/$step/resumeObs/observer")
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
          s"Resume observation requested for ${obsId.show} on step $step"
        )
      )
    }
  }

  test("operator") {
    val engine = mock[ObserveEngine[IO]]
    inAnyOrder {
      (engine.setObserver _)
        .expects(*, *, *, *)
        .anyNumberOfTimes()
        .returning(IO.unit)
    }
    forAll { (obsId: Observation.Id, obs: String) =>
      val uri    = Uri.unsafeFromString(
        s"/${obsId.show}/observer/${URLEncoder.encode(obs, "UTF-8")}"
      )
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
          s"Set observer name to '$obs' for sequence ${obsId.show}"
        )
      )
    }
  }*/
