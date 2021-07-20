// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.URIUtils._
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBuffer

import boopickle.Default.Pickle
import boopickle.Default.Pickler
import boopickle.Default.Unpickle
import cats.syntax.all._
import org.scalajs.dom.XMLHttpRequest
import org.scalajs.dom.ext.Ajax
import observe.common.HttpStatusCodes
import observe.model.ClientId
import observe.model.Observation
import observe.model.Observer
import observe.model.Operator
import observe.model.QueueId
import observe.model.Step
import observe.model.StepId
import observe.model.UserDetails
import observe.model.UserLoginRequest
import observe.model.boopickle._
import observe.model.enum.CloudCover
import observe.model.enum.ImageQuality
import observe.model.enum.Instrument
import observe.model.enum.Resource
import observe.model.enum.SkyBackground
import observe.model.enum.WaterVapor
import observe.web.client.actions.RunOptions

/**
 * Encapsulates remote calls to the Observe Web API
 */
object ObserveWebClient extends ModelBooPicklers {
  private val baseUrl = "/api/observe"

  // Decodes the binary response with BooPickle, errors are not handled
  def unpickle[A](r: XMLHttpRequest)(implicit u: Pickler[A]): A = {
    val ab = TypedArrayBuffer.wrap(r.response.asInstanceOf[ArrayBuffer])
    Unpickle[A].fromBytes(ab)
  }

  def toggleTCS(id: Observation.Id, enabled: Boolean): Future[Unit] =
    toggle(id, enabled, "tcsEnabled")

  def toggleGCAL(id: Observation.Id, enabled: Boolean): Future[Unit] =
    toggle(id, enabled, "gcalEnabled")

  def toggleDHS(id: Observation.Id, enabled: Boolean): Future[Unit] =
    toggle(id, enabled, "dhsEnabled")

  def toggleInstrument(id: Observation.Id, enabled: Boolean): Future[Unit] =
    toggle(id, enabled, "instEnabled")

  def toggle(id: Observation.Id, enabled: Boolean, section: String): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(id.toString)}/$section/$enabled"
      )
      .void

  def sync(idName: Observation.IdName): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(idName.id.toString)}/sync"
      )
      .void

  /**
   * Requests the backend to execute a sequence
   */
  def run(id: Observation.Id, clientId: ClientId, options: RunOptions): Future[Unit] = {
    val param = options match {
      case RunOptions.Normal         => ""
      case RunOptions.ChecksOverride => "?overrideTargetCheck=true"
    }
    Ajax
      .post(
        url =
          s"$baseUrl/commands/${encodeURI(id.toString)}/start/${encodeURI(clientId.self.show)}$param"
      )
      .void
  }

  /**
   * Requests the backend to set a breakpoint
   */
  def breakpoint(sid: Observation.Id, step: Step): Future[Unit] =
    Ajax
      .post(
        url =
          s"$baseUrl/commands/${encodeURI(sid.toString)}/${step.id}/breakpoint/${step.breakpoint}"
      )
      .void

  /**
   * Requests the backend to set a breakpoint
   */
  def skip(sid: Observation.Id, step: Step): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(sid.toString)}/${step.id}/skip/${step.skip}"
      )
      .void

  /**
   * Requests the backend to stop this sequence immediately
   */
  def stop(sid: Observation.Id, step: StepId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(sid.toString)}/$step/stop"
      )
      .void

  /**
   * Requests the backend to stop this sequence gracefully
   */
  def stopGracefully(sid: Observation.Id, step: StepId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(sid.toString)}/$step/stopGracefully"
      )
      .void

  /**
   * Requests the backend to abort this sequenece immediately
   */
  def abort(sid: Observation.Id, step: StepId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(sid.toString)}/$step/abort"
      )
      .void

  /**
   * Requests the backend to hold the current exposure immediately
   */
  def pauseObs(sid: Observation.Id, step: StepId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(sid.toString)}/$step/pauseObs"
      )
      .void

  /**
   * Requests the backend to hold the current exposure gracefully
   */
  def pauseObsGracefully(sid: Observation.Id, step: StepId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(sid.toString)}/$step/pauseObsGracefully"
      )
      .void

  /**
   * Requests the backend to resume the current exposure
   */
  def resumeObs(sid: Observation.Id, step: StepId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(sid.toString)}/$step/resumeObs"
      )
      .void

  /**
   * Requests the backend to set the operator name of a sequence
   */
  def setOperator(name: Operator): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/operator/${encodeURI(name.show)}"
      )
      .void

  /**
   * Requests the backend to set the observer name of a sequence
   */
  def setObserver(id: Observation.Id, name: String): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(id.toString)}/observer/${encodeURI(name)}"
      )
      .void

  /**
   * Requests the backend to set the ImageQuality
   */
  def setImageQuality(iq: ImageQuality): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/iq",
        data = Pickle.intoBytes[ImageQuality](iq)
      )
      .void

  /**
   * Requests the backend to set the CloudCover
   */
  def setCloudCover(cc: CloudCover): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/cc",
        data = Pickle.intoBytes[CloudCover](cc)
      )
      .void

  /**
   * Requests the backend to set the WaterVapor
   */
  def setWaterVapor(wv: WaterVapor): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/wv",
        data = Pickle.intoBytes[WaterVapor](wv)
      )
      .void

  /**
   * Requests the backend to set the SkyBackground
   */
  def setSkyBackground(sb: SkyBackground): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/sb",
        data = Pickle.intoBytes[SkyBackground](sb)
      )
      .void

  /**
   * Requests the backend to send a copy of the current state
   */
  def refresh(clientId: ClientId): Future[Unit] =
    Ajax
      .get(
        url = s"$baseUrl/commands/refresh/${encodeURI(clientId.self.show)}"
      )
      .void

  /**
   * Requests the backend to pause a sequence
   */
  def pause(idName: Observation.IdName): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(idName.id.toString)}/pause"
      )
      .void

  /**
   * Requests the backend to cancel a pausing request in process
   */
  def cancelPause(id: Observation.Id): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(id.toString)}/cancelpause"
      )
      .void

  /**
   * Login request
   */
  def login(u: String, p: String): Future[UserDetails] =
    Ajax
      .post(
        url = s"$baseUrl/login",
        data = Pickle.intoBytes(UserLoginRequest(u, p)),
        responseType = "arraybuffer"
      )
      .map(unpickle[UserDetails])

  /**
   * Logout request
   */
  def logout(): Future[String] =
    Ajax
      .post(
        url = s"$baseUrl/logout"
      )
      .map(_.responseText)

  /**
   * Ping request
   */
  def ping(): Future[Int] =
    Ajax
      .get(
        url = "/ping"
      )
      .map(_.status)
      .handleError(_ => HttpStatusCodes.Unauthorized)

  /**
   * Load a sequence
   */
  def loadSequence(
    instrument: Instrument,
    id:         Observation.Id,
    name:       Observer,
    clientId:   ClientId
  ): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/load/${encodeURI(instrument.show)}/${encodeURI(
          id.toString
        )}/${encodeURI(name.value)}/${encodeURI(clientId.self.show)}"
      )
      .void

  /**
   * Read the site of the server
   */
  def site(): Future[String] =
    Ajax
      .post(
        url = s"$baseUrl/site"
      )
      .map(_.responseText)

  /**
   * Add a sequence from a queue
   */
  def removeSequenceFromQueue(queueId: QueueId, idName: Observation.IdName): Future[Unit] =
    Ajax
      .post(
        url =
          s"$baseUrl/commands/queue/${encodeURI(queueId.self.show)}/remove/${encodeURI(idName.id.toString)}"
      )
      .void

  /**
   * Clears a queue
   */
  def clearQueue(queueId: QueueId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/queue/${encodeURI(queueId.self.show)}/clear"
      )
      .void

  /**
   * Runs a queue
   */
  def runQueue(queueId: QueueId, clientId: ClientId, observer: Observer): Future[Unit] =
    Ajax
      .post(
        url =
          s"$baseUrl/commands/queue/${encodeURI(queueId.self.show)}/run/${encodeURI(observer.value)}/${encodeURI(clientId.self.show)}"
      )
      .void

  /**
   * Stops a queue
   */
  def stopQueue(queueId: QueueId, clientId: ClientId): Future[Unit] =
    Ajax
      .post(
        url =
          s"$baseUrl/commands/queue/${encodeURI(queueId.self.show)}/stop/${encodeURI(clientId.self.show)}"
      )
      .void

  /**
   * Add a sequence from a queue
   */
  def addSequencesToQueue(ids: List[Observation.Id], qid: QueueId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/queue/${encodeURI(qid.self.show)}/add",
        data = Pickle.intoBytes(ids)
      )
      .void

  /**
   * Add a sequence from a queue
   */
  def addSequenceToQueue(id: Observation.Id, qid: QueueId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/queue/${encodeURI(qid.self.show)}/add/${encodeURI(id.toString)}"
      )
      .void

  /**
   * Stops a queue
   */
  def moveSequenceQueue(
    queueId:  QueueId,
    obsId:    Observation.Id,
    pos:      Int,
    clientId: ClientId
  ): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/queue/${encodeURI(queueId.self.show)}/move/${encodeURI(
          obsId.toString
        )}/$pos/${encodeURI(clientId.self.show)}"
      )
      .void

  /**
   * Runs a reusource
   */
  def runResource(
    pos:       StepId,
    resource:  Resource,
    obsIdName: Observation.IdName,
    clientId:  ClientId
  ): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/execute/${encodeURI(obsIdName.id.toString)}/$pos/${encodeURI(
          resource.show
        )}/${encodeURI(clientId.self.show)}"
      )
      .void

  /**
   * Runs a step starting at
   */
  def runFrom(
    obsIdName: Observation.IdName,
    stepId:    StepId,
    clientId:  ClientId,
    options:   RunOptions
  ): Future[Unit] = {
    val param = options match {
      case RunOptions.Normal         => ""
      case RunOptions.ChecksOverride => "?overrideTargetCheck=true"
    }
    Ajax
      .post(
        url =
          s"$baseUrl/commands/${encodeURI(obsIdName.id.toString)}/$stepId/startFrom/${encodeURI(clientId.self.show)}$param"
      )
      .void
  }

}
