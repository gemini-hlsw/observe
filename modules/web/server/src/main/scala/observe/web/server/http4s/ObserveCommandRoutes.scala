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
import lucuma.sso.client.SsoClient
import observe.server
import observe.server.ObserveEngine
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.*
import org.http4s.server.middleware.GZip
// import observe.model.enums.RunOverride

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

  private val commandServices: HttpRoutes[F] = HttpRoutes.of[F] {
    // case req @ POST -> Root / ObsIdVar(obsId) / "start" / ObserverVar(obs) / ClientIDVar(
    //       clientId
    //   ) :? OptionalRunOverride(runOverride) =>
    // ssoClient.require(req) { user =>
    //   oe.start(obsId, user, obs, clientId, runOverride.getOrElse(RunOverride.Default)) *>
    //     NoContent()
    // }

    case req @ POST -> Root / ObsIdVar(oid) / StepIdVar(step) / ClientIDVar(clientId) / "execute" /
        ResourceVar(resource) / ObserverVar(obs) =>
      ssoClient.require(req) { user =>
        oe.configSystem(oid, obs, user, step, resource, clientId) *>
          NoContent()
      }

    // case POST -> Root / ObsId(obsId) / StepId(stepId) / "startFrom" / ObserverVar(
    //       obs
    //     ) / ClientIDVar(
    //       clientId
    //     ) :? OptionalRunOverride(runOverride) as _ =>
    //   se.startFrom(inputQueue,
    //                obsId,
    //                obs,
    //                stepId,
    //                clientId,
    //                runOverride.getOrElse(RunOverride.Default)
    //   ) *>
    //     Ok(s"Started sequence $obsId from step $stepId")
    //
    // case POST -> Root / ObsIdVar(obsId) / "pause" / ObserverVar(obs) as user =>
    //   se.requestPause(inputQueue, obsId, obs, user) *>
    //     Ok(s"Pause sequence $obsId")
    //
    // case POST -> Root / ObsId(obsId) / "cancelpause" / ObserverVar(obs) as user =>
    //   se.requestCancelPause(inputQueue, obsId, obs, user) *>
    //     Ok(s"Cancel Pause sequence $obsId")
    //
    case req @ POST -> Root / ObsIdVar(obsId) / StepIdVar(stepId) / ClientIDVar(clientId) /
        "breakpoint" / ObserverVar(obs) / BooleanVar(bp) =>
      ssoClient.require(req) { user =>
        oe.setBreakpoint(obsId, user, obs, stepId, bp) *>
          NoContent()
      }
    //
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
    //
    // case POST -> Root / ObsIdVar(obsId) / StepId(stepId) / "stop" / ObserverVar(
    //       obs
    //     ) as user =>
    //   se.stopObserve(inputQueue, obsId, obs, user, graceful = false) *>
    //     Ok(s"Stop requested for $obsId on step $stepId")
    //
    // case POST -> Root / ObsId(obsId) / StepId(stepId) / "stopGracefully" / ObserverVar(
    //       obs
    //     ) as user =>
    //   se.stopObserve(inputQueue, obsId, obs, user, graceful = true) *>
    //     Ok(s"Stop gracefully requested for $obsId on step $stepId")
    //
    // case POST -> Root / ObsId(obsId) / StepId(stepId) / "abort" / ObserverVar(obs) as user =>
    //   se.abortObserve(inputQueue, obsId, obs, user) *>
    //     Ok(s"Abort requested for $obsId on step $stepId")
    //
    // case POST -> Root / ObsId(obsId) / StepId(stepId) / "pauseObs" / ObserverVar(obs) as user =>
    //   se.pauseObserve(inputQueue, obsId, obs, user, graceful = false) *>
    //     Ok(s"Pause observation requested for $obsId on step $stepId")
    //
    // case POST -> Root / ObsId(obsId) / StepId(stepId) / "pauseObsGracefully" / ObserverVar(
    //       obs
    //     ) as user =>
    //   se.pauseObserve(inputQueue, obsId, obs, user, graceful = true) *>
    //     Ok(s"Pause observation gracefully requested for $obsId on step $stepId")
    //
    // case POST -> Root / ObsIdVar(obsId) / StepId(stepId) / "resumeObs" / ObserverVar(obs) as user =>
    //   se.resumeObserve(inputQueue, obsId, obs, user) *>
    //     Ok(s"Resume observation requested for $obsId on step $stepId")
    //
    // case POST -> Root / "operator" / OperatorVar(op) as user =>
    //   se.setOperator(inputQueue, user, op) *> Ok(s"Set operator name to '${op.value}'")
    //
    // case POST -> Root / ObsId(obsId) / "observer" / ObserverVar(obs) as user =>
    //   se.setObserver(inputQueue, obsId, user, obs) *>
    //     Ok(s"Set observer name to '${obs.value}' for sequence $obsId")
    //
    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "tcsEnabled" /
        SubsystemEnabledVar(tcsEnabled) =>
      ssoClient.require(req) { user =>
        oe.setTcsEnabled(obsId, user, tcsEnabled, clientId) *>
          NoContent()
      }

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "gcalEnabled" /
        SubsystemEnabledVar(gcalEnabled) =>
      ssoClient.require(req) { user =>
        oe.setGcalEnabled(obsId, user, gcalEnabled, clientId) *>
          NoContent()
      }

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "instrumentEnabled" /
        SubsystemEnabledVar(instEnabled) =>
      ssoClient.require(req) { user =>
        oe.setInstrumentEnabled(obsId, user, instEnabled, clientId) *>
          NoContent()
      }

    case req @ POST -> Root / ObsIdVar(obsId) / ClientIDVar(clientId) / "dhsEnabled" /
        SubsystemEnabledVar(dhsEnabled) =>
      ssoClient.require(req) { user =>
        oe.setDhsEnabled(obsId, user, dhsEnabled, clientId) *>
          NoContent()
      }

    case req @ POST -> Root / ClientIDVar(clientId) / "iq" =>
      ssoClient.require(req) { u =>
        req.decode[ImageQuality](iq => oe.setImageQuality(iq, u, clientId) *> NoContent())
      }

    case req @ POST -> Root / ClientIDVar(clientId) / "wv" =>
      ssoClient.require(req) { u =>
        req.decode[WaterVapor](wv => oe.setWaterVapor(wv, u, clientId) *> NoContent())
      }

    case req @ POST -> Root / ClientIDVar(clientId) / "sb" =>
      ssoClient.require(req) { u =>
        req.decode[SkyBackground](sb => oe.setSkyBackground(sb, u, clientId) *> NoContent())
      }

    case req @ POST -> Root / ClientIDVar(clientId) / "ce" =>
      ssoClient.require(req) { u =>
        req.decode[CloudExtinction](ce => oe.setCloudExtinction(ce, u, clientId) *> NoContent())
      }

    case req @ POST -> Root / "load" / InstrumentVar(i) / ObsIdVar(obsId) /
        ClientIDVar(clientId) / ObserverVar(observer) =>
      ssoClient.require(req) { user =>
        oe.selectSequence(i, obsId, observer, user, clientId) *> NoContent()
      }

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
      ssoClient.require(req) { u =>
        oe.resetConditions *> NoContent()
      }
  }

  val service: HttpRoutes[F] =
    GZip(commandServices)
//   val service: HttpRoutes[F] =
//     refreshCommand <+> TokenRefresher(GZip(httpAuthentication.reqAuth(commandServices)),
//                                       httpAuthentication
//   )
}
