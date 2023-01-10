// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.tabs

import cats.syntax.all._
import diode.react.ReactConnectProxy
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import react.semanticui.As
import react.semanticui.elements.segment.Segment
import react.semanticui.elements.segment.SegmentAttached
import react.semanticui.modules.tab.TabPane
import observe.web.client.circuit._
import observe.web.client.components.ObserveStyles
import observe.web.client.components.sequence.steps.StepConfigTable
import observe.web.client.components.sequence.steps.StepsTable
import observe.web.client.components.sequence.toolbars.SequenceDefaultToolbar
import observe.web.client.components.sequence.toolbars.StepConfigToolbar
import observe.web.client.model.Pages.ObservePages
import observe.web.client.reusability._
import observe.web.client.semanticui._

/**
 * Content of a single tab with a sequence
 */
final case class SequenceTabContent(
  router:  RouterCtl[ObservePages],
  content: SequenceTabContentFocus
) extends ReactProps[SequenceTabContent](SequenceTabContent.component) {

  val stepsConnect: ReactConnectProxy[StepsTableAndStatusFocus] =
    ObserveCircuit.connect(ObserveCircuit.stepsTableReader(content.id))
}

object SequenceTabContent {
  type Props = SequenceTabContent

  implicit val stcfReuse: Reusability[SequenceTabContentFocus] =
    Reusability.derive[SequenceTabContentFocus]
  implicit val propsReuse: Reusability[Props]                  = Reusability.by(_.content)

  private def toolbar(p: Props) =
    p.content.tableType match {
      case StepsTableTypeSelection.StepsTableSelected         =>
        SequenceDefaultToolbar(p.content.id)
          .when(p.content.canOperate && !p.content.isPreview)
      case StepsTableTypeSelection.StepConfigTableSelected(s) =>
        StepConfigToolbar(p.router,
                          p.content.instrument,
                          p.content.id,
                          s,
                          p.content.steps,
                          p.content.isPreview
        ): TagMod
    }

  def stepsTable(p: Props): VdomElement =
    p.content.tableType match {
      case StepsTableTypeSelection.StepsTableSelected =>
        p.stepsConnect { x =>
          StepsTable(p.router, p.content.canOperate, x())
        }

      case StepsTableTypeSelection.StepConfigTableSelected(i) =>
        p.stepsConnect { x =>
          val focus = x()

          focus.stepsTable
            .foldMap(_.steps)
            .find(_.id === i)
            .map { steps =>
              val hs = focus.configTableState
              <.div(
                ^.height := "100%",
                StepConfigTable(steps, hs)
              )
            }
            .getOrElse(<.div())
        }
    }

  protected val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P { p =>
      val instrument = p.content.instrument
      TabPane(
        active = p.content.isActive,
        as = As.Segment(
          Segment(compact = true, attached = SegmentAttached.Attached, secondary = true)
        ),
        clazz = ObserveStyles.tabSegment
      )(
        dataTab := instrument.show,
        <.div(ObserveStyles.TabControls, toolbar(p).when(p.content.canOperate)),
        <.div(ObserveStyles.TabTable, stepsTable(p).when(p.content.isActive))
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build
}
