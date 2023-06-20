// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import cats.syntax.all.*
import crystal.react.View
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.syntax.display.*
import lucuma.ui.primereact.LucumaStyles
import observe.model.ClientStatus
import observe.model.UserDetails
import observe.model.enums.SequenceState
import observe.ui.AppContext
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.queue.SessionQueue
import observe.ui.components.sequence.StepsTable
import observe.ui.model.Execution
import observe.ui.model.RootModel
import observe.ui.model.TabOperations
import org.typelevel.log4cats.Logger
import react.common.ReactFnProps
import react.common.given
import react.primereact.*
import crystal.ViewF
import crystal.react.hooks.*

case class Home(rootModel: View[RootModel]) extends ReactFnProps(Home.component)

object Home {
  private type Props = Home

  private val clientStatus = ClientStatus.Default.copy(user = UserDetails("telops", "Telops").some)

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useStateView(
        Execution(
          obsId = Observation.Id.fromLong(133742).get,
          obsName = "Test Observation",
          instrument = Instrument.GmosSouth,
          sequenceState = SequenceState.Running(false, false),
          steps = observe.demo.DemoExecutionSteps,
          stepConfigDisplayed = none,
          nextStepToRun = observe.demo.DemoExecutionSteps.get(2).map(_.id),
          runningStep = none, // observe.demo.DemoExecutionSteps.get(2).map(_.id),
          isPreview = false,
          tabOperations = TabOperations.Default
        ).some
      )
      .render { (props, _, demo) =>
        <.div(ObserveStyles.MainUI)(
          Divider(position = Divider.Position.HorizontalCenter, clazz = ObserveStyles.Divider)(
            "Observe GS"
          ),
          Splitter(
            layout = Layout.Vertical,
            stateKey = "main-splitter",
            stateStorage = StateStorage.Local,
            clazz = ObserveStyles.Shrinkable
          )(
            SplitterPanel()(
              Splitter(stateKey = "top-splitter",
                       stateStorage = StateStorage.Local,
                       clazz = ObserveStyles.TopPanel
              )(
                SplitterPanel(size = 80)(
                  SessionQueue(observe.demo.DemoSessionQueue)
                ),
                SplitterPanel()(
                  HeadersSideBar(
                    props.rootModel.get.status,
                    props.rootModel.get.operator,
                    props.rootModel.zoom(RootModel.conditions)
                  )
                )
              )
            ),
            SplitterPanel()(
              TabView(clazz = ObserveStyles.SequenceTabView, activeIndex = 1)(
                TabPanel(
                  clazz = ObserveStyles.SequenceTabPanel,
                  header = React.Fragment(
                    <.span(ObserveStyles.ActiveInstrumentLabel, "Daytime Queue"),
                    Tag(
                      clazz = ObserveStyles.LabelPointer |+| ObserveStyles.IdleTag,
                      icon = Icons.CircleDot,
                      value = "Idle"
                    )
                  )
                )(
                ),
                TabPanel(
                  clazz = ObserveStyles.SequenceTabPanel,
                  header = React.Fragment(
                    <.span(ObserveStyles.ActiveInstrumentLabel, "GMOS-S"),
                    Tag(
                      clazz = ObserveStyles.LabelPointer |+| ObserveStyles.RunningTag,
                      icon = Icons.CircleNotch.withSpin(true),
                      value =
                        s"${Observation.Id.fromLong(133742).get.shortName} - 3/${observe.demo.DemoExecutionSteps.length}"
                    )
                  )
                )(
                  StepsTable(
                    clientStatus = clientStatus,
                    execution = demo
                  )
                )
              )
            )
          ),
          Accordion(tabs =
            List(
              AccordionTab(clazz = ObserveStyles.LogArea, header = "Show Log")(
                <.div(^.height := "200px")
              )
            )
          ),
          Toolbar(
            clazz = ObserveStyles.Footer,
            left = "Observe - GS",
            right = React.Fragment(ThemeSelector()).rawElement
          )
        )
      }
}
