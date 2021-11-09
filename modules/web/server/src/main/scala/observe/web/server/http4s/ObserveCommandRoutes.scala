// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.effect.Async
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl._
import org.http4s.server.middleware.GZip
import observe.model.Observation
import observe.model._
import observe.model.enum._
import observe.server
import observe.server.ObserveEngine
import observe.web.server.http4s.OptionalRunOverride
import observe.web.server.http4s.encoder._
import observe.web.server.security.AuthenticationService
import observe.web.server.security.Http4sAuthentication
import observe.web.server.security.TokenRefresher
import lucuma.core.model.Observation.{ Id => ObsId }
import lucuma.core.model.Step.{ Id => StepId }

/**
 * Rest Endpoints under the /api route
 */
class ObserveCommandRoutes[F[_]: Async](
  auth:       AuthenticationService[F],
  inputQueue: server.EventQueue[F],
  se:         ObserveEngine[F]
) extends BooEncoders
    with Http4sDsl[F] {

  // Handles authentication
  private val httpAuthentication = new Http4sAuthentication(auth)

  private val commandServices: AuthedRoutes[UserDetails, F] = AuthedRoutes.of {
    case POST -> Root / ObsIdVar(obsId) / "start" / ObserverVar(obs) / ClientIDVar(
          clientId
        ) :? OptionalRunOverride(
          runOverride
        ) as user =>
      se.start(inputQueue,
               obsId,
               user,
               obs,
               clientId,
               runOverride.getOrElse(RunOverride.Default)
      ) *>
        Ok(s"Started sequence ${obsId.format}")

    case POST -> Root / ObsId(obsId) / StepId(stepId) / "startFrom" / ClientIDVar(
          clientId
        ) :? OptionalRunOverride(runOverride) as _ =>
      se.startFrom(inputQueue,
                   obsId,
                   stepId,
                   clientId,
                   runOverride.getOrElse(RunOverride.Default)
      ) *>
        Ok(s"Started sequence $obsId from step $stepId")

    case POST -> Root / ObsIdVar(obsId) / "pause" as user =>
      se.requestPause(inputQueue, obsId, user) *>
        Ok(s"Pause sequence $obsId")

    case POST -> Root / ObsId(obsId) / "cancelpause" as user =>
      se.requestCancelPause(inputQueue, obsId, user) *>
        Ok(s"Cancel Pause sequence $obsId")

    case POST -> Root / ObsId(obsId) / StepId(stepId) / "breakpoint" / BooleanVar(
          bp
        ) as user =>
      se.setBreakpoint(inputQueue, obsId, user, stepId, bp) *>
        Ok(s"Set breakpoint in step $stepId of sequence $obsId")

    case POST -> Root / ObsId(obsId) / "sync" as _ =>
      for {
        u    <- se.sync(inputQueue, obsId).attempt
        resp <-
          u.fold(_ => NotFound(s"Not found sequence $obsId"), _ => Ok(s"Sync requested for $obsId"))
      } yield resp

    case POST -> Root / ObsId(obsId) / StepId(stepId) / "skip" / BooleanVar(bp) as user =>
      se.setSkipMark(inputQueue, obsId, user, stepId, bp) *>
        Ok(s"Set skip mark in step $stepId of sequence $obsId")

    case POST -> Root / ObsId(obsId) / StepId(stepId) / "stop" as _ =>
      se.stopObserve(inputQueue, obsId, graceful = false) *>
        Ok(s"Stop requested for $obsId on step $stepId")

    case POST -> Root / ObsId(obsId) / StepId(stepId) / "stopGracefully" as _ =>
      se.stopObserve(inputQueue, obsId, graceful = true) *>
        Ok(s"Stop gracefully requested for $obsId on step $stepId")

    case POST -> Root / ObsId(obsId) / StepId(stepId) / "abort" as _ =>
      se.abortObserve(inputQueue, obsId) *>
        Ok(s"Abort requested for $obsId on step $stepId")

    case POST -> Root / ObsId(obsId) / StepId(stepId) / "pauseObs" as _ =>
      se.pauseObserve(inputQueue, obsId, graceful = false) *>
        Ok(s"Pause observation requested for $obsId on step $stepId")

    case POST -> Root / ObsId(obsId) / StepId(stepId) / "pauseObsGracefully" as _ =>
      se.pauseObserve(inputQueue, obsId, graceful = true) *>
        Ok(s"Pause observation gracefully requested for $obsId on step $stepId")

    case POST -> Root / ObsId(obsId) / StepId(stepId) / "resumeObs" as _ =>
      se.resumeObserve(inputQueue, obsId) *>
        Ok(s"Resume observation requested for $obsId on step $stepId")

    case POST -> Root / "operator" / OperatorVar(op) as user =>
      se.setOperator(inputQueue, user, op) *> Ok(s"Set operator name to '${op.value}'")

    case POST -> Root / ObsId(obsId) / "observer" / ObserverVar(obs) as user =>
      se.setObserver(inputQueue, obsId, user, obs) *>
        Ok(s"Set observer name to '${obs.value}' for sequence $obsId")

    case POST -> Root / ObsId(obsId) / "tcsEnabled" / BooleanVar(tcsEnabled) as user =>
      se.setTcsEnabled(inputQueue, obsId, user, tcsEnabled) *>
        Ok(s"Set TCS enable flag to '$tcsEnabled' for sequence $obsId")

    case POST -> Root / ObsId(obsId) / "gcalEnabled" / BooleanVar(gcalEnabled) as user =>
      se.setGcalEnabled(inputQueue, obsId, user, gcalEnabled) *>
        Ok(s"Set GCAL enable flag to '$gcalEnabled' for sequence $obsId")

    case POST -> Root / ObsId(obsId) / "instEnabled" / BooleanVar(instEnabled) as user =>
      se.setInstrumentEnabled(inputQueue, obsId, user, instEnabled) *>
        Ok(s"Set instrument enable flag to '$instEnabled' for sequence $obsId")

    case POST -> Root / ObsId(obsId) / "dhsEnabled" / BooleanVar(dhsEnabled) as user =>
      se.setDhsEnabled(inputQueue, obsId, user, dhsEnabled) *>
        Ok(s"Set DHS enable flag to '$dhsEnabled' for sequence $obsId")

    case req @ POST -> Root / "iq" as user =>
      req.req.decode[ImageQuality](iq =>
        se.setImageQuality(inputQueue, iq, user) *>
          Ok(s"Set image quality to $iq")
      )

    case req @ POST -> Root / "wv" as user =>
      req.req.decode[WaterVapor](wv =>
        se.setWaterVapor(inputQueue, wv, user) *> Ok(s"Set water vapor to $wv")
      )

    case req @ POST -> Root / "sb" as user =>
      req.req.decode[SkyBackground](sb =>
        se.setSkyBackground(inputQueue, sb, user) *>
          Ok(s"Set sky background to $sb")
      )

    case req @ POST -> Root / "cc" as user =>
      req.req.decode[CloudCover](cc =>
        se.setCloudCover(inputQueue, cc, user) *> Ok(s"Set cloud cover to $cc")
      )

    case POST -> Root / "load" / InstrumentVar(i) / ObsId(obsId) / ObserverVar(
          observer
        ) / ClientIDVar(clientId) as user =>
      se.selectSequence(inputQueue, i, obsId, observer, user, clientId) *>
        Ok(s"Set selected sequence $obsId for $i")

    case POST -> Root / "unload" / "all" as user =>
      se.clearLoadedSequences(inputQueue, user) *> Ok(s"Queue cleared")

    case req @ POST -> Root / "queue" / QueueIdVar(qid) / "add" as _ =>
      req.req.decode[List[Observation.Id]](ids =>
        se.addSequencesToQueue(inputQueue, qid, ids) *>
          Ok(s"${ids.mkString(",")} added to queue $qid")
      )

    case POST -> Root / "queue" / QueueIdVar(qid) / "add" / ObsId(obsId) as _ =>
      se.addSequenceToQueue(inputQueue, qid, obsId) *>
        Ok(s"$obsId added to queue $qid")

    case POST -> Root / "queue" / QueueIdVar(qid) / "remove" / ObsId(obsId) as _ =>
      se.removeSequenceFromQueue(inputQueue, qid, obsId) *>
        Ok(s"$obsId removed from queue $qid")

    case POST -> Root / "queue" / QueueIdVar(qid) / "move" / ObsId(obsId) / IntVar(
          delta
        ) / ClientIDVar(clientId) as _ =>
      se.moveSequenceInQueue(inputQueue, qid, obsId, delta, clientId) *>
        Ok(s"$obsId moved $delta positions in queue $qid")

    case POST -> Root / "queue" / QueueIdVar(qid) / "clear" as _ =>
      se.clearQueue(inputQueue, qid) *>
        Ok(s"All sequences removed from queue $qid")

    case POST -> Root / "queue" / QueueIdVar(qid) / "run" / ObserverVar(observer) / ClientIDVar(
          clientId
        ) as user =>
      se.startQueue(inputQueue, qid, observer, user, clientId) *>
        Ok(s"Started queue $qid")

    case POST -> Root / "queue" / QueueIdVar(qid) / "stop" / ClientIDVar(clientId) as _ =>
      se.stopQueue(inputQueue, qid, clientId) *>
        Ok(s"Stopped from queue $qid")

    case POST -> Root / "execute" / ObsIdVar(oid) / StepId(step) / ResourceVar(
          resource
        ) / ClientIDVar(clientId) as u =>
      se.configSystem(inputQueue, oid, step, resource, clientId) *>
        Ok(s"Run ${resource.show} from config at $oid/$step by ${u.username}")

  }

  val refreshCommand: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "refresh" / ClientIDVar(clientId) =>
      se.requestRefresh(inputQueue, clientId) *> NoContent()

    case POST -> Root / "resetconditions" =>
      se.resetConditions(inputQueue) *> NoContent()
  }

  val service: HttpRoutes[F] =
    refreshCommand <+> TokenRefresher(GZip(httpAuthentication.reqAuth(commandServices)),
                                      httpAuthentication
    )
}
