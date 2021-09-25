// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import observe.web.client.circuit._
import observe.web.client.model.Pages._

/**
 * Container for the queue table
 */
object SessionQueueTableSection {
  private val sequencesConnect =
    ObserveCircuit.connect(ObserveCircuit.statusAndLoadedSequencesReader)

  private val component        = ScalaComponent
    .builder[RouterCtl[ObservePages]]("SessionQueueTableSection")
    .stateless
    .render_P(p =>
      React.Fragment(
        <.div(
          ObserveStyles.queueListPane,
          sequencesConnect(c => SessionQueueTable(p, c()))
        ),
        SessionQueueTableFilter()
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

  def apply(
    ctl: RouterCtl[ObservePages]
  ): Unmounted[RouterCtl[ObservePages], Unit, Unit] =
    component(ctl)

}
