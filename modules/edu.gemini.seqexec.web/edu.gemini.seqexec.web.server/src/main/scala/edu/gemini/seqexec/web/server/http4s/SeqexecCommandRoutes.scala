package edu.gemini.seqexec.web.server.http4s

import edu.gemini.pot.sp.SPObservationID
import edu.gemini.seqexec.server.Commands
import edu.gemini.seqexec.server.SeqexecEngine
import edu.gemini.seqexec.engine
import edu.gemini.seqexec.web.server.model.CommandsModel._
import edu.gemini.seqexec.web.server.http4s.encoder._
import edu.gemini.seqexec.web.server.security.AuthenticationService
import org.http4s._
import org.http4s.dsl._
import org.http4s.server.middleware.GZip

import scalaz._
import Scalaz._
import scalaz.concurrent.Task

/**
  * Rest Endpoints under the /api route
  */
class SeqexecCommandRoutes(auth: AuthenticationService, inputQueue: engine.EventQueue, se: SeqexecEngine) extends BooEncoders {

  val tokenAuthService = JwtAuthentication(auth)

  val commands = Commands(se.odbProxy)

  val service = tokenAuthService { GZip { HttpService {
    case req @ GET  -> Root  / "host" =>
      Ok(toCommandResult("host", commands.host()))

    case req @ GET  -> Root  / obsId / "count" =>
      Ok(toCommandResult("count", commands.showCount(obsId)))

    /*case req @ GET  -> Root  / obsId / "static" =>
      Ok(toSequenceConfig("show", commands.showStatic(obsId)))

    case req @ GET  -> Root  / obsId / "static" / system =>
      Ok(toSequenceConfig("show", commands.showStatic(obsId, system)))

    case req @ GET  -> Root  / obsId / "dynamic" / step =>
      Ok(toSequenceConfig("show", commands.showDynamic(obsId, step)))

    case req @ GET  -> Root  / obsId / "dynamic" / step / system =>
      Ok(toSequenceConfig("show", commands.showDynamic(obsId, step, system)))*/

    /*case req @ GET  -> Root  / obsId / "state" =>
      Ok(toSequenceConfig("state", commands.state(obsId)))*/

    // New SeqexecEngine

    // TODO: Add obsId parameter
    case POST -> Root / obsId / "start" =>
      // TODO: Get rid of `.toString` How do we want to represent input results
      // now?
      for {
        obsId <-
            \/.fromTryCatchNonFatal(new SPObservationID(obsId))
              .fold(e => Task.fail(e), Task.now)
        _     <- se.start(inputQueue, obsId)
        resp  <- Ok(s"Started sequence $obsId")
      } yield resp

    // TODO: Add obsId parameter
    case POST -> Root / obsId / "pause" =>
      // TODO: Get rid of `.toString` How do we want to represent input results
      // now?
      for {
        obsId <-
            \/.fromTryCatchNonFatal(new SPObservationID(obsId))
              .fold(e => Task.fail(e), Task.now)
        _     <- se.requestPause(inputQueue, obsId)
        resp  <- Ok(s"Pause sequence $obsId")
      } yield resp

    case GET -> Root / "refresh" =>
      se.requestRefresh(inputQueue) *> NoContent()

  }}}
}
