package edu.gemini.seqexec.web.client.components

import edu.gemini.seqexec.web.client.model.{SeqexecCircuit, WSConnect}
import edu.gemini.seqexec.web.client.model.InstrumentNames
import edu.gemini.seqexec.web.client.model.Pages._
import edu.gemini.seqexec.web.client.model.NavigateSilentTo
// import edu.gemini.seqexec.web.client.components.sequence.SequenceArea
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.{Callback, ScalaComponent}
import diode.ModelRO

object SeqexecMain {
  private val lbConnect = SeqexecCircuit.connect(_.uiModel.loginBox)
  private val logConnect = SeqexecCircuit.connect(_.uiModel.globalLog)

  private val component = ScalaComponent.builder[Unit]("SeqexecUI")
    .stateless
    .render_P(p =>
      <.div(
        NavBar(),
        QueueArea(),
        /*SequenceArea(SeqexecCircuit.statusAndSequences, SeqexecCircuit.headerSideBarReader),*/
        logConnect(LogArea.apply),
        lbConnect(LoginBox.apply)
      )
    ).build

  def apply() = component()
}

/**
  * Top level UI component
  */
object SeqexecUI {
  case class RouterProps(page: InstrumentPage, router: RouterCtl[InstrumentPage])

  def router: Router[SeqexecPages] = {
    val routerConfig = RouterConfigDsl[SeqexecPages].buildConfig { dsl =>
      import dsl._

      def layout(c: RouterCtl[SeqexecPages], r: Resolution[SeqexecPages]) =
        <.div(r.render()).render

      (emptyRule
      | staticRoute(root, Root) ~> renderR(r => SeqexecMain())
      | dynamicRoute(("/" ~ string("[a-zA-Z0-9-]+") ~ "/" ~ string("[a-zA-Z0-9-]+").option).caseClass[InstrumentPage]) {
          case x @ InstrumentPage(i, _) if InstrumentNames.instruments.list.toList.contains(i) => x
        } ~> dynRenderR((p, r) => SeqexecMain())
      | dynamicRoute(("/" ~ string("[a-zA-Z0-9-]+")).pmap(i => Some(InstrumentPage(i, None)))(i => i.i)) {
          case x @ InstrumentPage(i, _) if InstrumentNames.instruments.list.toList.contains(i) => x
        } ~> dynRenderR((p, r) => SeqexecMain())
      )
        .notFound(redirectToPage(Root)(Redirect.Push))
        // Runtime verification that all pages are routed
        .verify(Root, InstrumentNames.instruments.list.toList.map(i => InstrumentPage(i, None)): _*)
        .onPostRender((_, next) =>
          Callback.when(next != SeqexecCircuit.zoom(_.uiModel.navLocation).value)(Callback.log("silent " + next) >> Callback(SeqexecCircuit.dispatch(NavigateSilentTo(next)))))
        .renderWith(layout)
        .logToConsole
    }

    val (router, routerLogic) = Router.componentAndLogic(BaseUrl.fromWindowOrigin, routerConfig)

    def navigated(page: ModelRO[SeqexecPages]): Unit = {
      scalajs.js.timers.setTimeout(0)(routerLogic.ctl.set(page.value).runNow())
    }

    // subscribe to navigation changes
    SeqexecCircuit.subscribe(SeqexecCircuit.zoom(_.uiModel.navLocation))(navigated _)

    // Initiate the WebSocket connection
    SeqexecCircuit.dispatch(WSConnect(0))

    router
  }

}
