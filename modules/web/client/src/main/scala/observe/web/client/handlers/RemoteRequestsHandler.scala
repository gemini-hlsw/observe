// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import cats.syntax.all._
import diode.ActionHandler
import diode.ActionResult
import diode.Effect
import diode.ModelRW
import observe.model.ClientId
import observe.web.client.actions._
import observe.web.client.services.ObserveWebClient

/**
 * Handles actions sending requests to the backend
 */
class RemoteRequestsHandler[M](modelRW: ModelRW[M, Option[ClientId]])
    extends ActionHandler(modelRW)
    with Handlers[M, Option[ClientId]] {

  def handleRun: PartialFunction[Any, ActionResult[M]] = { case RequestRun(s, options) =>
    val effect = value
      .map(clientId =>
        Effect(
          ObserveWebClient
            .run(s, clientId, options)
            .as(RunStarted(s))
            .recover { case _ =>
              RunStartFailed(s)
            }
        )
      )
      .getOrElse(VoidEffect)
    effectOnly(effect)
  }

  def handlePause: PartialFunction[Any, ActionResult[M]] = { case RequestPause(id) =>
    effectOnly(requestEffect(id, ObserveWebClient.pause, RunPaused.apply, RunPauseFailed.apply))
  }

  def handleRunFrom: PartialFunction[Any, ActionResult[M]] = {
    case RequestRunFrom(id, stepId, options) =>
      val effect = value
        .map(clientId =>
          requestEffect(id,
                        ObserveWebClient.runFrom(_, stepId, clientId, options),
                        RunFromComplete(_, stepId),
                        RunFromFailed(_, stepId)
          )
        )
        .getOrElse(VoidEffect)
      effectOnly(effect)
  }

  def handleCancelPause: PartialFunction[Any, ActionResult[M]] = { case RequestCancelPause(id) =>
    effectOnly(
      requestEffect(id,
                    ObserveWebClient.cancelPause,
                    RunCancelPaused.apply,
                    RunCancelPauseFailed.apply
      )
    )
  }

  def handleStop: PartialFunction[Any, ActionResult[M]] = { case RequestStop(id, step) =>
    effectOnly(
      Effect(
        ObserveWebClient
          .stop(id, step)
          .as(RunStop(id))
          .recover { case _ =>
            RunStopFailed(id)
          }
      )
    )
  }

  def handleGracefulStop: PartialFunction[Any, ActionResult[M]] = {
    case RequestGracefulStop(id, step) =>
      effectOnly(
        Effect(
          ObserveWebClient
            .stopGracefully(id, step)
            .as(RunGracefulStop(id))
            .recover { case _ =>
              RunGracefulStopFailed(id)
            }
        )
      )
  }

  def handleAbort: PartialFunction[Any, ActionResult[M]] = { case RequestAbort(id, step) =>
    effectOnly(
      Effect(
        ObserveWebClient
          .abort(id, step)
          .as(RunAbort(id))
          .recover { case _ =>
            RunAbortFailed(id)
          }
      )
    )
  }

  def handleObsPause: PartialFunction[Any, ActionResult[M]] = { case RequestObsPause(id, step) =>
    effectOnly(
      Effect(
        ObserveWebClient
          .pauseObs(id, step)
          .as(RunObsPause(id))
          .recover { case _ =>
            RunObsPauseFailed(id)
          }
      )
    )
  }

  def handleGracefulObsPause: PartialFunction[Any, ActionResult[M]] = {
    case RequestGracefulObsPause(id, step) =>
      effectOnly(
        Effect(
          ObserveWebClient
            .pauseObsGracefully(id, step)
            .as(RunGracefulObsPause(id))
            .recover { case _ =>
              RunGracefulObsPauseFailed(id)
            }
        )
      )
  }

  def handleObsResume: PartialFunction[Any, ActionResult[M]] = { case RequestObsResume(id, step) =>
    effectOnly(
      Effect(
        ObserveWebClient
          .resumeObs(id, step)
          .as(RunObsResume(id))
          .recover { case _ =>
            RunObsResumeFailed(id)
          }
      )
    )
  }

  def handleSync: PartialFunction[Any, ActionResult[M]] = { case RequestSync(id) =>
    effectOnly(requestEffect(id, ObserveWebClient.sync, RunSync.apply, RunSyncFailed.apply))
  }

  def handleResourceRun: PartialFunction[Any, ActionResult[M]] = {
    case RequestResourceRun(id, step, resource) =>
      val effect = value
        .map(clientId =>
          requestEffect(
            id,
            ObserveWebClient.runResource(step, resource, _, clientId),
            RunResource(_, step, resource),
            RunResourceFailed(_, step, resource, s"Http call to configure ${resource.show} failed")
          )
        )
        .getOrElse(VoidEffect)
      effectOnly(effect)
  }

  override def handle: PartialFunction[Any, ActionResult[M]] =
    List(
      handleRun,
      handlePause,
      handleCancelPause,
      handleStop,
      handleGracefulStop,
      handleAbort,
      handleObsPause,
      handleGracefulObsPause,
      handleObsResume,
      handleSync,
      handleRunFrom,
      handleResourceRun
    ).combineAll

}
