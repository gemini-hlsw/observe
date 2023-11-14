// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.effect.Async
import cats.syntax.all.*
import fs2.compression.Compression
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.User
import lucuma.core.model.sequence.Step
import lucuma.sso.client.SsoClient
import observe.model.enums.RunOverride
import observe.server
import observe.server.ObserveEngine
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.*
import org.http4s.server.middleware.GZip

/**
 * Rest Endpoints under the /api route
 */
class ObserveCommandRoutes[F[_]: Async: Compression](
  ssoClient: SsoClient[F, User],
  oe:        ObserveEngine[F]
) extends Http4sDsl[F] {

  given EntityDecoder[F, WaterVapor]      = jsonOf[F, WaterVapor]
  given EntityDecoder[F, ImageQuality]    = jsonOf[F, ImageQuality]
  given EntityDecoder[F, SkyBackground]   = jsonOf[F, SkyBackground]
  given EntityDecoder[F, CloudExtinction] = jsonOf[F, CloudExtinction]
  given EntityDecoder[F, List[Step.Id]]   = jsonOf[F, List[Step.Id]]

  private val commandServices: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "start" /
        ObserverVar(obs) :? OptionalRunOverride(runOverride) =>
      ssoClient.require(req): user =>
        oe.start(obsId, user, obs, clientId, runOverride.getOrElse(RunOverride.Default)) *>
          NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / StepIdVar(stepId) / ClientIDVar(clientId) /
        "execute" / ResourceVar(resource) / ObserverVar(obs) =>
      ssoClient.require(req): user =>
        oe.configSystem(obsId, obs, user, stepId, resource, clientId) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / StepIdVar(stepId) / ClientIDVar(clientId) /
        "startFrom" / ObserverVar(obs) :? OptionalRunOverride(runOverride) =>
      ssoClient.require(req): _ =>
        oe.startFrom(
          obsId,
          obs,
          stepId,
          clientId,
          runOverride.getOrElse(RunOverride.Default)
        ) *> NoContent()

    // In a number of endpoints, clientId is not used but we keep it anyway so that it's logged.
    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "pause" /
        ObserverVar(obs) =>
      ssoClient.require(req): user =>
        oe.requestPause(obsId, obs, user) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "cancelPause" /
        ObserverVar(obs) =>
      ssoClient.require(req): user =>
        oe.requestCancelPause(obsId, obs, user) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / StepIdVar(stepId) / ClientIDVar(clientId) /
        "breakpoint" / ObserverVar(obs) / BreakpointVar(bp) =>
      ssoClient.require(req): user =>
        oe.setBreakpoints(obsId, user, obs, List(stepId), bp) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) /
        "breakpoints" / ObserverVar(obs) / BreakpointVar(bp) =>
      ssoClient.require(req): user =>
        req.decode[List[Step.Id]]: steps =>
          oe.setBreakpoints(obsId, user, obs, steps, bp) *> NoContent()

    // case POST -> Root / ObsId(obsId) / "sync" as _ =>
    //   for {
    //     u    <- se.sync(inputQueue, obsId).attempt
    //     resp <-
    //       u.fold(_ => NotFound(s"Not found sequence $obsId"), _ => Ok(s"Sync requested for $obsId"))
    //   } yield resp
    //
    // case POST -> Root / ObsId(obsId) / StepId(stepId) / "skip" / ObserverVar(
    //       obs
    //     ) / BooleanVar(bp) as user =>
    //   se.setSkipMark(inputQueue, obsId, user, obs, stepId, bp) *>
    //     Ok(s"Set skip mark in step $stepId of sequence $obsId")

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "stop" /
        ObserverVar(obs) =>
      ssoClient.require(req): user =>
        oe.stopObserve(obsId, obs, user, graceful = false) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) /
        "stopGracefully" / ObserverVar(obs) =>
      ssoClient.require(req): user =>
        oe.stopObserve(obsId, obs, user, graceful = true) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) /
        "abort" / ObserverVar(obs) =>
      ssoClient.require(req): user =>
        oe.abortObserve(obsId, obs, user) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) /
        "pauseObs" / ObserverVar(obs) =>
      ssoClient.require(req): user =>
        oe.pauseObserve(obsId, obs, user, graceful = false) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) /
        "pauseObsGracefully" / ObserverVar(obs) =>
      ssoClient.require(req): user =>
        oe.pauseObserve(obsId, obs, user, graceful = true) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) /
        "resumeObs" / ObserverVar(obs) =>
      ssoClient.require(req): user =>
        oe.resumeObserve(obsId, obs, user) *> NoContent()

    case req @ POST -> Root / ClientIDVar(clientId) / "operator" / OperatorVar(op) =>
      ssoClient.require(req): user =>
        oe.setOperator(user, op) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "observer" /
        ObserverVar(obs) =>
      ssoClient.require(req): user =>
        oe.setObserver(obsId, user, obs) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "tcsEnabled" /
        SubsystemEnabledVar(tcsEnabled) =>
      ssoClient.require(req): user =>
        oe.setTcsEnabled(obsId, user, tcsEnabled, clientId) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "gcalEnabled" /
        SubsystemEnabledVar(gcalEnabled) =>
      ssoClient.require(req): user =>
        oe.setGcalEnabled(obsId, user, gcalEnabled, clientId) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "instrumentEnabled" /
        SubsystemEnabledVar(instEnabled) =>
      ssoClient.require(req): user =>
        oe.setInstrumentEnabled(obsId, user, instEnabled, clientId) *> NoContent()

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "dhsEnabled" /
        SubsystemEnabledVar(dhsEnabled) =>
      ssoClient.require(req): user =>
        oe.setDhsEnabled(obsId, user, dhsEnabled, clientId) *> NoContent()

    case req @ POST -> Root / ClientIDVar(clientId) / "iq" =>
      ssoClient.require(req): user =>
        req.decode[ImageQuality](iq => oe.setImageQuality(iq, user, clientId) *> NoContent())

    case req @ POST -> Root / ClientIDVar(clientId) / "wv" =>
      ssoClient.require(req): user =>
        req.decode[WaterVapor](wv => oe.setWaterVapor(wv, user, clientId) *> NoContent())

    case req @ POST -> Root / ClientIDVar(clientId) / "sb" =>
      ssoClient.require(req): user =>
        req.decode[SkyBackground](sb => oe.setSkyBackground(sb, user, clientId) *> NoContent())

    case req @ POST -> Root / ClientIDVar(clientId) / "ce" =>
      ssoClient.require(req): user =>
        req.decode[CloudExtinction](ce => oe.setCloudExtinction(ce, user, clientId) *> NoContent())

    case req @ POST -> Root / "load" / InstrumentVar(i) / ObsIdVar(obsId) /
        ClientIDVar(clientId) / ObserverVar(observer) =>
      ssoClient.require(req): user =>
        oe.selectSequence(i, obsId, observer, user, clientId) *> NoContent()

    // case POST -> Root / "unload" / "all" as user         =>
    //   oe.clearLoadedSequences(inputQueue, user) *> Ok(s"Queue cleared")

    //   case req @ POST -> Root / "queue" / QueueIdVar(qid) / "add" as _ =>
    //     req.req.decode[List[Observation.Id]](ids =>
    //       se.addSequencesToQueue(inputQueue, qid, ids) *>
    //         Ok(s"${ids.mkString(",")} added to queue $qid")
    //     )
    //
    //   case POST -> Root / "queue" / QueueIdVar(qid) / "add" / ObsId(obsId) as _ =>
    //     se.addSequenceToQueue(inputQueue, qid, obsId) *>
    //       Ok(s"$obsId added to queue $qid")
    //
    //   case POST -> Root / "queue" / QueueIdVar(qid) / "remove" / ObsId(obsId) as _ =>
    //     se.removeSequenceFromQueue(inputQueue, qid, obsId) *>
    //       Ok(s"$obsId removed from queue $qid")
    //
    //   case POST -> Root / "queue" / QueueIdVar(qid) / "move" / ObsId(obsId) / IntVar(
    //         delta
    //       ) / ClientIDVar(clientId) as _ =>
    //     se.moveSequenceInQueue(inputQueue, qid, obsId, delta, clientId) *>
    //       Ok(s"$obsId moved $delta positions in queue $qid")
    //
    //   case POST -> Root / "queue" / QueueIdVar(qid) / "clear" as _ =>
    //     se.clearQueue(inputQueue, qid) *>
    //       Ok(s"All sequences removed from queue $qid")
    //
    //   case POST -> Root / "queue" / QueueIdVar(qid) / "run" / ObserverVar(observer) / ClientIDVar(
    //         clientId
    //       ) as user =>
    //     se.startQueue(inputQueue, qid, observer, user, clientId) *>
    //       Ok(s"Started queue $qid")
    //
    //   case POST -> Root / "queue" / QueueIdVar(qid) / "stop" / ClientIDVar(clientId) as _ =>
    //     se.stopQueue(inputQueue, qid, clientId) *>
    //       Ok(s"Stopped from queue $qid")
    //
    //
    // }
    //
    // val refreshCommand: HttpRoutes[F] = HttpRoutes.of[F] {

    case GET -> Root / ClientIDVar(clientId) / "refresh" =>
      oe.requestRefresh(clientId) *> NoContent()

    case req @ POST -> Root / "resetconditions" =>
      ssoClient.require(req): _ =>
        oe.resetConditions *> NoContent()
  }

  val service: HttpRoutes[F] =
    GZip(commandServices)
//   val service: HttpRoutes[F] =
//     refreshCommand <+> TokenRefresher(GZip(httpAuthentication.reqAuth(commandServices)),
//                                       httpAuthentication
//   )
}
