// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.effect.IO
import cats.syntax.all.*
import observe.model.{ClientId, Observation, StepId}
import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*

import java.net.URLEncoder
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.server.websocket.WebSocketBuilder2

import observe.server.*
// import observe.web.server.http4s.encoder.*
import observe.model.enums.*
import observe.model.arb.ArbClientId.given

class ObserveCommandRoutesSuite extends munit.CatsEffectSuite with TestRoutes {

  test("reset conditions") {
    val r = for {
      engine <- TestObserveEngine.build[IO]
      s      <- commandRoutes(engine)
      wsb    <- WebSocketBuilder2[IO]
      // t      <- newLoginToken(wsb)
      l      <- s(
                  Request[IO](method = Method.POST, uri = uri"/resetconditions")
                  // .addCookie("token", t)
                  // .withEntity(wv)
                ).value
    } yield l.map(_.status)
    assertIO(r, Some(Status.NoContent))
  }

  // test("update water vapor") {
  //   forAll { (wv: WaterVapor) =>
  //     val (s, b) = (for {
  //       engine <- TestObserveEngine.build[IO]
  //       s      <- commandRoutes(engine)
  //       wsb    <- WebSocketBuilder2[IO]
  //       t      <- newLoginToken(wsb)
  //       l      <- s(
  //                   Request[IO](method = Method.POST, uri = uri"/wv")
  //                     .addCookie("token", t)
  //                     .withEntity(wv)
  //                 ).value
  //     } yield (l.map(_.status), l.map(_.as[String]).sequence)).unsafeRunSync()
  //     assert(s === Some(Status.Ok))
  //     assert(b.unsafeRunSync() === Some(s"Set water vapor to $wv"))
  //   }
  // }
  //
  // test("update image quality") {
  //   forAll { (iq: ImageQuality) =>
  //     val (s, b) = (for {
  //       engine <- TestObserveEngine.build[IO]
  //       s      <- commandRoutes(engine)
  //       wsb    <- WebSocketBuilder2[IO]
  //       t      <- newLoginToken(wsb)
  //       l      <- s(
  //                   Request[IO](method = Method.POST, uri = uri"/iq")
  //                     .addCookie("token", t)
  //                     .withEntity(iq)
  //                 ).value
  //     } yield (l.map(_.status), l.map(_.as[String]).sequence)).unsafeRunSync()
  //     assert(s === Some(Status.Ok))
  //     assert(b.unsafeRunSync() === Some(s"Set image quality to $iq"))
  //   }
  // }
  //
  // test("update sky background") {
  //   forAll { (sb: SkyBackground) =>
  //     val (s, b) = (for {
  //       engine <- TestObserveEngine.build[IO]
  //       s      <- commandRoutes(engine)
  //       wsb    <- WebSocketBuilder2[IO]
  //       t      <- newLoginToken(wsb)
  //       l      <- s(
  //                   Request[IO](method = Method.POST, uri = uri"/sb")
  //                     .addCookie("token", t)
  //                     .withEntity(sb)
  //                 ).value
  //     } yield (l.map(_.status), l.map(_.as[String]).sequence)).unsafeRunSync()
  //     assert(s === Some(Status.Ok))
  //     assert(b.unsafeRunSync() === Some(s"Set sky background to $sb"))
  //   }
  // }
  //
  // test("update cloud cover") {
  //   forAll { (cc: CloudCover) =>
  //     val (s, b) = (for {
  //       engine <- TestObserveEngine.build[IO]
  //       s      <- commandRoutes(engine)
  //       wsb    <- WebSocketBuilder2[IO]
  //       t      <- newLoginToken(wsb)
  //       l      <- s(
  //                   Request[IO](method = Method.POST, uri = uri"/cc")
  //                     .addCookie("token", t)
  //                     .withEntity(cc)
  //                 ).value
  //     } yield (l.map(_.status), l.map(_.as[String]).sequence)).unsafeRunSync()
  //     assert(s === Some(Status.Ok))
  //     assert(b.unsafeRunSync() === Some(s"Set cloud cover to $cc"))
  //   }
  // }
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
}
