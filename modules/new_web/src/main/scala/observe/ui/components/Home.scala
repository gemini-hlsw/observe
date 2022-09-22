// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
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
import reactST.primereact.components.*
import reactST.primereact.dividerMod.DividerAlignType
import reactST.primereact.splitterMod.SplitterLayoutType
import reactST.primereact.splitterMod.SplitterStateStorageType
import reactST.primereact.tagMod.TagSeverityType

case class Home(rootModel: View[RootModel]) extends ReactFnProps(Home.component)

object Home {
  private type Props = Home

  private val clientStatus = ClientStatus.Default.copy(user = UserDetails("telops", "Telops").some)

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .render { (props, _) =>
        <.div(ObserveStyles.MainUI)(
          Divider(ObserveStyles.Divider, "Observe GS")
            .align(DividerAlignType.center),
          Splitter(ObserveStyles.Shrinkable)
            .layout(SplitterLayoutType.vertical)
            .stateKey("main-splitter")
            .stateStorage(SplitterStateStorageType.local)(
              SplitterPanel(
                Splitter
                  .stateKey("top-splitter")
                  .stateStorage(SplitterStateStorageType.local)(
                    SplitterPanel(
                      SessionQueue(observe.demo.DemoSessionQueue)
                    ),
                    SplitterPanel(
                      HeadersSideBar(
                        props.rootModel.get.status,
                        props.rootModel.get.operator,
                        props.rootModel.zoom(RootModel.conditions)
                      )
                    )
                  )
              ),
              SplitterPanel(
                TabView(ObserveStyles.SequenceTabView)(
                  TabPanel(ObserveStyles.SequenceTabPanel)
                    .header(
                      React.Fragment(
                        <.span(ObserveStyles.ActiveInstrumentLabel, "Daytime Queue"),
                        Tag(ObserveStyles.LabelPointer |+| ObserveStyles.IdleTag)
                          .iconFA(Icons.CircleDot)
                          .value("Idle")
                      )
                    )(
                      StepsTable(
                        clientStatus = clientStatus,
                        execution = none
                      )
                    ),
                  TabPanel(ObserveStyles.SequenceTabPanel)
                    .header(
                      React.Fragment(
                        <.span(ObserveStyles.ActiveInstrumentLabel, "GMOS-S"),
                        Tag(ObserveStyles.LabelPointer |+| ObserveStyles.RunningTag)
                          .iconFA(Icons.CircleNotch.copy(spin = true))
                          .value(Observation.Id.fromLong(133742).get.shortName)
                      )
                    )(
                      StepsTable(
                        clientStatus = clientStatus,
                        execution = Execution(
                          obsId = Observation.Id.fromLong(133742).get,
                          obsName = "Test Observation",
                          instrument = Instrument.GmosSouth,
                          sequenceState = SequenceState.Running(false, false),
                          steps = observe.demo.DemoExecutionSteps,
                          stepConfigDisplayed = none,
                          nextStepToRun = none,
                          runningStep = none,
                          isPreview = false,
                          tabOperations = TabOperations.Default
                        ).some
                      )
                    )
                )
              )
            ),
          Accordion(
            AccordionTab(ObserveStyles.LogArea)
              .header("Show Log")(
                <.div(^.height := "200px")
              )
          ),
          Toolbar(ObserveStyles.Footer)
            .left("Observe - GS")
            .right(React.Fragment(ThemeSelector()).rawElement)
        )
      }
}
