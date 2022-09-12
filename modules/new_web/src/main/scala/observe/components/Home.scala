// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.components

import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import observe.AppContext
import react.common.ReactFnProps
import reactST.primereact.components.*
import observe.ObserveStyles
import react.common.given
import cats.effect.IO
import org.typelevel.log4cats.Logger
import reactST.primereact.dividerMod.DividerAlignType
import reactST.primereact.splitterMod.SplitterLayoutType
import reactST.primereact.splitterMod.SplitterStateStorageType
import reactST.primereact.tagMod.TagSeverityType
import observe.Icons

case class Home() extends ReactFnProps(Home.component)

object Home {
  private type Props = Home

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useEffectBy(usingContext(_ => Logger[IO].debug("Rendering Home component")))
      .useState(0)
      .render { (_, _, clicks) =>
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
                      SessionQueue()
                    ),
                    SplitterPanel(
                      "CONDITIONS"
                    )
                  )
              ),
              SplitterPanel(
                TabView(
                  TabPanel(ObserveStyles.TabPanel)
                    .header(
                      React.Fragment(
                        <.span(ObserveStyles.ActiveInstrumentLabel, "Daytime Queue"),
                        Tag(ObserveStyles.LabelPointer)
                          .iconFA(Icons.CircleDot)
                          .value("Idle")
                          .severity(TagSeverityType.warning)
                      )
                    )(
                      s"You clicked ${clicks.value} time(s).",
                      Button("Click me!")
                        .onClick(_ => clicks.modState(_ + 1))
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
