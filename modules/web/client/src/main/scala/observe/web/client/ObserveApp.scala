// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client

import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel
import cats.effect.Sync
import cats.effect._
import lucuma.core.enums.Site
import org.scalajs.dom.document
import org.scalajs.dom.Element
import observe.web.client.actions.Initialize
import observe.web.client.actions.WSClose
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.components.ObserveUI
import observe.web.client.services.ObserveWebClient
import typings.loglevel.mod.{^ => logger}

/**
 * Observe WebApp entry point
 */
final class ObserveLauncher[F[_]](implicit val F: Sync[F], L: LiftIO[F]) {
  japgolly.scalajs.react.extra.ReusabilityOverlay.overrideGloballyInDev()

  def serverSite: F[Site] =
    L.liftIO(IO.fromFuture {
      IO {
        import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

        // Read the site from the webserver
        ObserveWebClient.site().map(Site.fromTag(_).getOrElse(Site.GS))
      }
    })

  def initializeDataModel(observeSite: Site): F[Unit] =
    F.delay {
      // Set the instruments before adding it to the dom
      ObserveCircuit.dispatch(Initialize(observeSite))
    }

  def renderingNode: F[Element] =
    F.delay {
      // Find or create the node where we render
      Option(document.getElementById("root")).getOrElse {
        val elem = document.createElement("div")
        elem.id = "root"
        document.body.appendChild(elem)
        elem
      }
    }

}

/**
 * Observe WebApp entry point Exposed to the js world
 */
@JSExportTopLevel("ObserveApp")
object ObserveApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val launcher = new ObserveLauncher[IO]
    // Render the UI using React
    for {
      observeSite <- launcher.serverSite
      _           <- launcher.initializeDataModel(observeSite)
      router      <- ObserveUI.router[IO](observeSite)
      node        <- launcher.renderingNode
      _           <- IO(router().renderIntoDOM(node)).handleErrorWith(p => IO(logger.error(p.toString)))
    } yield ExitCode.Success
  }

  @JSExport
  def stop(): Unit =
    // Close the websocket
    ObserveCircuit.dispatch(WSClose)

  @JSExport
  def start(): Unit =
    super.main(Array())

}
