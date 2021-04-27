// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import scala.scalajs.js.timers.SetTimeoutHandle

import cats.effect.Sync
import cats.syntax.all._
import diode.ModelRO
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.MonocleReact._
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.enum.Site
import monocle.Prism
import observe.model.Observation
import observe.model.enum.Instrument
import observe.web.client.actions.NavigateSilentTo
import observe.web.client.actions.RequestSoundEcho
import observe.web.client.actions.WSConnect
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.model.ModelOps._
import observe.web.client.model.Pages._

/**
 * UI Router
 */
object ObserveUI {
  private def pageTitle(site: Site)(p: ObservePages): String =
    p match {
      case SequenceConfigPage(_, id, _) => s"Observe - ${id.format}"
      case SequencePage(_, id, _)       => s"Observe - ${id.format}"
      case PreviewPage(_, id, _)        => s"Observe - ${id.format}"
      case PreviewConfigPage(_, id, _)  => s"Observe - ${id.format}"
      case CalibrationQueuePage         => s"Observe - Daycal queue"
      case _                            => s"Observe - ${site.shortName}"
    }

  // Prism from url params to config page
  private def configPageP(
    instrumentNames: Map[String, Instrument]
  ): Prism[(String, String, Int), SequenceConfigPage] =
    Prism[(String, String, Int), SequenceConfigPage] { case (i, s, step) =>
      (instrumentNames.get(i), Observation.Id.fromString(s)).mapN(SequenceConfigPage(_, _, step))
    } { p =>
      (p.instrument.show, p.obsId.format, p.step)
    }

  // Prism from url params to sequence page
  private def sequencePageSP(
    instrumentNames: Map[String, Instrument]
  ): Prism[(String, String, Option[Int]), SequencePage] =
    Prism[(String, String, Option[Int]), SequencePage] { case (i, s, st) =>
      (instrumentNames.get(i), Observation.Id.fromString(s))
        .mapN(SequencePage(_, _, StepIdDisplayed(st.foldMap(_ - 1))))
    } { p =>
      (p.instrument.show, p.obsId.format, (p.step.step + 1).some)
    }

  // Prism from url params to the preview page to a given step
  private def previewPageSP(
    instrumentNames: Map[String, Instrument]
  ): Prism[(String, String, Option[Int]), PreviewPage] =
    Prism[(String, String, Option[Int]), PreviewPage] { case (i, s, st) =>
      (instrumentNames.get(i), Observation.Id.fromString(s))
        .mapN(PreviewPage(_, _, StepIdDisplayed(st.foldMap(_ - 1))))
    } { p =>
      (p.instrument.show, p.obsId.format, (p.step.step + 1).some)
    }

  // Prism from url params to the preview page with config
  private def previewConfigPageP(
    instrumentNames: Map[String, Instrument]
  ): Prism[(String, String, Int), PreviewConfigPage] =
    Prism[(String, String, Int), PreviewConfigPage] { case (i, s, step) =>
      (instrumentNames.get(i), Observation.Id.fromString(s)).mapN(PreviewConfigPage(_, _, step))
    } { p =>
      (p.instrument.show, p.obsId.format, p.step)
    }

  def router[F[_]](site: Site)(implicit F: Sync[F]): F[Router[ObservePages]] = {
    val instrumentNames = site.instruments.map(i => (i.show, i)).toList.toMap

    val routerConfig = RouterConfigDsl[ObservePages].buildConfig { dsl =>
      import dsl._

      (emptyRule
        | staticRoute(root, Root) ~> renderR(r => ObserveMain(site, r))
        | staticRoute("/soundtest", SoundTest) ~> renderR(r => ObserveMain(site, r))
        | staticRoute("/daycal", CalibrationQueuePage) ~> renderR(r => ObserveMain(site, r))
        | dynamicRouteCT(
          ("/" ~ string("[a-zA-Z0-9-]+") ~ "/" ~ string("[a-zA-Z0-9-]+") ~ "/configuration/" ~ int)
            .pmapL(configPageP(instrumentNames))
        ) ~> dynRenderR((_: SequenceConfigPage, r) => ObserveMain(site, r))
        | dynamicRouteCT(
          ("/" ~ string("[a-zA-Z0-9-]+") ~ "/" ~ string("[a-zA-Z0-9-]+") ~ ("/step/" ~ int).option)
            .pmapL(sequencePageSP(instrumentNames))
        ) ~> dynRenderR((_: SequencePage, r) => ObserveMain(site, r))
        | dynamicRouteCT(
          ("/preview/" ~ string("[a-zA-Z0-9-]+") ~ "/" ~ string(
            "[a-zA-Z0-9-]+"
          ) ~ ("/step/" ~ int).option)
            .pmapL(previewPageSP(instrumentNames))
        ) ~> dynRenderR((_: PreviewPage, r) => ObserveMain(site, r))
        | dynamicRouteCT(
          ("/preview/" ~ string("[a-zA-Z0-9-]+") ~ "/" ~ string(
            "[a-zA-Z0-9-]+"
          ) ~ "/configuration/" ~ int)
            .pmapL(previewConfigPageP(instrumentNames))
        ) ~> dynRenderR((_: PreviewConfigPage, r) => ObserveMain(site, r)))
        .notFound(redirectToPage(Root)(SetRouteVia.HistoryPush))
        // Runtime verification that all pages are routed
        .verify(Root, List(SoundTest, CalibrationQueuePage): _*)
        .onPostRender((_, next) =>
          Callback.when(next === SoundTest)(ObserveCircuit.dispatchCB(RequestSoundEcho)) *>
            Callback.when(next =!= ObserveCircuit.zoom(_.uiModel.navLocation).value)(
              ObserveCircuit.dispatchCB(NavigateSilentTo(next))
            )
        )
        .renderWith { case (_, r) => <.div(r.render()) }
        .setTitle(pageTitle(site))
        .logToConsole
    }

    def navigated(
      routerLogic: RouterLogic[ObservePages, Unit],
      page:        ModelRO[ObservePages]
    ): SetTimeoutHandle =
      scalajs.js.timers.setTimeout(0)(routerLogic.ctl.set(page.value).runNow())

    for {
      r                    <- F.delay(Router.componentAndLogic(BaseUrl.fromWindowOrigin, routerConfig))
      (router, routerLogic) = r
      // subscribe to navigation changes
      _                    <- F.delay(ObserveCircuit.subscribe(ObserveCircuit.zoom(_.uiModel.navLocation)) { x =>
                                navigated(routerLogic, x); ()
                              })
      // Initiate the WebSocket connection
      _                    <- F.delay(ObserveCircuit.dispatch(WSConnect(0)))
    } yield router
  }

}
