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
import scala.annotation.nowarn

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

  @nowarn
  def toggle(id: Observation.Id, enabled: Boolean, section: String): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(id.toString)}/$section/$enabled"
      )
      .void

  @nowarn
  def sync(idName: Observation.IdName): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(idName.id.toString)}/sync"
      )
      .void

  /**
   * Requests the backend to execute a sequence
   */
  @nowarn
  def run(
    id:       Observation.Id,
    name:     Observer,
    clientId: ClientId,
    options:  RunOptions
  ): Future[Unit] = {
    val param = options match {
      case RunOptions.Normal         => ""
      case RunOptions.ChecksOverride => "?overrideTargetCheck=true"
    }
    Ajax
      .post(
        url =
          s"$baseUrl/commands/${encodeURI(id.toString)}/start/${encodeURI(name.value)}/${encodeURI(clientId.self.show)}$param"
      )
      .void
  }

  /**
   * Requests the backend to set a breakpoint
   */
  @nowarn
  def breakpoint(sid: Observation.Id, name: Observer, step: Step): Future[Unit] =
    Ajax
      .post(
        url =
          s"$baseUrl/commands/${encodeURI(sid.toString)}/${step.id}/breakpoint/${encodeURI(name.value)}/${step.breakpoint}"
      )
      .void

  /**
   * Requests the backend to set a breakpoint
   */
  @nowarn
  def skip(sid: Observation.Id, name: Observer, step: Step): Future[Unit] =
    Ajax
      .post(
        url =
          s"$baseUrl/commands/${encodeURI(sid.toString)}/${step.id}/skip/${encodeURI(name.value)}/${step.skip}"
      )
      .void

  /**
   * Requests the backend to stop this sequence immediately
   */
  @nowarn
  def stop(sid: Observation.Id, name: Observer, step: StepId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(sid.toString)}/$step/stop/${encodeURI(name.value)}"
      )
      .void

  /**
   * Requests the backend to stop this sequence gracefully
   */
  @nowarn
  def stopGracefully(sid: Observation.Id, name: Observer, step: StepId): Future[Unit] =
    Ajax
      .post(
        url =
          s"$baseUrl/commands/${encodeURI(sid.toString)}/$step/stopGracefully/${encodeURI(name.value)}"
      )
      .void

  /**
   * Requests the backend to abort this sequenece immediately
   */
  @nowarn
  def abort(sid: Observation.Id, name: Observer, step: StepId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(sid.toString)}/$step/abort/${encodeURI(name.value)}"
      )
      .void

  /**
   * Requests the backend to hold the current exposure immediately
   */
  @nowarn
  def pauseObs(sid: Observation.Id, name: Observer, step: StepId): Future[Unit] =
    Ajax
      .post(
        url =
          s"$baseUrl/commands/${encodeURI(sid.toString)}/$step/pauseObs/${encodeURI(name.value)}"
      )
      .void

  /**
   * Requests the backend to hold the current exposure gracefully
   */
  @nowarn
  def pauseObsGracefully(sid: Observation.Id, name: Observer, step: StepId): Future[Unit] =
    Ajax
      .post(
        url =
          s"$baseUrl/commands/${encodeURI(sid.toString)}/$step/pauseObsGracefully/${encodeURI(name.value)}"
      )
      .void

  /**
   * Requests the backend to resume the current exposure
   */
  @nowarn
  def resumeObs(sid: Observation.Id, name: Observer, step: StepId): Future[Unit] =
    Ajax
      .post(
        url =
          s"$baseUrl/commands/${encodeURI(sid.toString)}/$step/resumeObs/${encodeURI(name.value)}"
      )
      .void

  /**
   * Requests the backend to set the operator name of a sequence
   */
  @nowarn
  def setOperator(name: Operator): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/operator/${encodeURI(name.show)}"
      )
      .void

  /**
   * Requests the backend to set the observer name of a sequence
   */
  @nowarn
  def setObserver(id: Observation.Id, name: String): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(id.toString)}/observer/${encodeURI(name)}"
      )
      .void

  /**
   * Requests the backend to set the ImageQuality
   */
  @nowarn
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
  @nowarn
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
  @nowarn
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
  @nowarn
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
  @nowarn
  def refresh(clientId: ClientId): Future[Unit] =
    Ajax
      .get(
        url = s"$baseUrl/commands/refresh/${encodeURI(clientId.self.show)}"
      )
      .void

  /**
   * Requests the backend to pause a sequence
   */
  @nowarn
  def pause(idName: Observation.IdName, name: Observer): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(idName.id.toString)}/pause"
      )
      .void

  /**
   * Requests the backend to cancel a pausing request in process
   */
  @nowarn
  def cancelPause(id: Observation.Id, name: Observer): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(id.toString)}/cancelpause/${encodeURI(name.value)}"
      )
      .void

  /**
   * Login request
   */
  @nowarn
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
  @nowarn
  def logout(): Future[String] =
    Ajax
      .post(
        url = s"$baseUrl/logout"
      )
      .map(_.responseText)

  /**
   * Ping request
   */
  @nowarn
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
  @nowarn
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
  @nowarn
  def site(): Future[String] =
    Ajax
      .post(
        url = s"$baseUrl/site"
      )
      .map(_.responseText)

  /**
   * Add a sequence from a queue
   */
  @nowarn
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
  @nowarn
  def clearQueue(queueId: QueueId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/queue/${encodeURI(queueId.self.show)}/clear"
      )
      .void

  /**
   * Runs a queue
   */
  @nowarn
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
  @nowarn
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
  @nowarn
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
  @nowarn
  def addSequenceToQueue(id: Observation.Id, qid: QueueId): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/queue/${encodeURI(qid.self.show)}/add/${encodeURI(id.toString)}"
      )
      .void

  /**
   * Stops a queue
   */
  @nowarn
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
  @nowarn
  def runResource(
    pos:       StepId,
    resource:  Resource,
    name:      Observer,
    obsIdName: Observation.Id,
    clientId:  ClientId
  ): Future[Unit] =
    Ajax
      .post(
        url = s"$baseUrl/commands/execute/${encodeURI(obsIdName.toString)}/$pos/${encodeURI(
            resource.show
          )}/${encodeURI(name.value)}/${encodeURI(clientId.self.show)}"
      )
      .void

  /**
   * Runs a step starting at
   */
  @nowarn
  def runFrom(
    obsIdName: Observation.Id,
    stepId:    StepId,
    name:      Observer,
    clientId:  ClientId,
    options:   RunOptions
  ): Future[Unit] = {
    val param = options match {
      case RunOptions.Normal         => ""
      case RunOptions.ChecksOverride => "?overrideTargetCheck=true"
    }
    Ajax
      .post(
        url = s"$baseUrl/commands/${encodeURI(obsIdName.toString)}/$stepId/startFrom/${encodeURI(
            name.value
          )}/${encodeURI(clientId.self.show)}$param"
      )
      .void
  }

}
