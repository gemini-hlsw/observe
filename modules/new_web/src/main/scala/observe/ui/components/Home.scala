// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import observe.ui.AppContext
import react.common.ReactFnProps
import reactST.primereact.components.*
import observe.ui.ObserveStyles
import react.common.given
import cats.effect.IO
import org.typelevel.log4cats.Logger
import reactST.primereact.dividerMod.DividerAlignType
import reactST.primereact.splitterMod.SplitterLayoutType
import reactST.primereact.splitterMod.SplitterStateStorageType
import reactST.primereact.tagMod.TagSeverityType
import observe.ui.Icons
import observe.ui.model.RootModel
import crystal.react.View
import lucuma.core.model.Observation
import lucuma.core.enums.Instrument
import observe.model.enums.SequenceState
import cats.syntax.all.*
import observe.ui.model.TabOperations
import observe.ui.components.sequence.StepsTable
import observe.model.ClientStatus
import observe.model.UserDetails

case class Home(rootModel: View[RootModel]) extends ReactFnProps(Home.component)

object Home {
  private type Props = Home

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .render { (props, _) =>
        <.div(ObserveStyles.MainUI)(
          Divider(ObserveStyles.Divider, "Observe GS")
            .align(DividerAlignType.center),
          Splitter(^.height := "100%")
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
                        clientStatus =
                          ClientStatus.Default.copy(user = UserDetails("telops", "Telops").some),
                        obsId = Observation.Id.fromLong(1).get,
                        obsName = "Test Observation",
                        instrument = Instrument.GmosSouth,
                        sequenceState = SequenceState.Running(false, false),
                        steps = observe.demo.DemoExecutionSteps,
                        stepConfigDisplayed = none,
                        nextStepToRun = none,
                        runningStep = none,
                        isPreview = false,
                        tabOperations = TabOperations.Default
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
