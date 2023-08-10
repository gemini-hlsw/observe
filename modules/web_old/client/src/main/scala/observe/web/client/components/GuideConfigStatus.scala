// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import cats.*
import cats.syntax.all.*
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.react.common.*
import lucuma.react.common.implicits.*
import lucuma.react.semanticui.elements.header.Header
import lucuma.react.semanticui.sizes.*
import observe.model.M1GuideConfig
import observe.model.M2GuideConfig
import observe.model.TelescopeGuideConfig
import observe.model.enums.ComaOption
import observe.model.enums.MountGuideOption
import observe.web.client.reusability.*

final case class GuideConfigStatus(config: TelescopeGuideConfig)
    extends ReactProps[GuideConfigStatus](GuideConfigStatus.component)

/**
 * Alert message when the connection disappears
 */
object GuideConfigStatus {
  type Props = GuideConfigStatus

  implicit val mountGuideShow = Show.show[MountGuideOption] {
    case MountGuideOption.MountGuideOn  => "On"
    case MountGuideOption.MountGuideOff => "Off"
  }

  implicit val comaOptionShow = Show.show[ComaOption] {
    case ComaOption.ComaOn  => "On"
    case ComaOption.ComaOff => "Off"
  }

  implicit val m1GuideShow = Show.show[M1GuideConfig] {
    case s: M1GuideConfig.M1GuideOn => s.show
    case M1GuideConfig.M1GuideOff   => "Off"
  }

  given Reusability[Props] = Reusability.derive[Props]

  private val component = ScalaComponent
    .builder[Props]("GuideConfigStatus")
    .stateless
    .render_P { p =>
      React.Fragment(
        Header(as = "span",
               size = Small,
               clazz = ObserveStyles.item |+| ObserveStyles.activeGuide
                 .when_(p.config.mountGuide === MountGuideOption.MountGuideOn)
        )(
          s"Mount: ${p.config.mountGuide.show}"
        ),
        Header(as = "span",
               size = Small,
               clazz = ObserveStyles.item |+| ObserveStyles.activeGuide
                 .when_(p.config.m1Guide =!= M1GuideConfig.M1GuideOff)
        )(
          s"M1: ${p.config.m1Guide.show}"
        ),
        p.config.m2Guide match {
          case M2GuideConfig.M2GuideOn(c, s) =>
            React.Fragment(
              Header(as = "span",
                     size = Small,
                     clazz = ObserveStyles.item |+| ObserveStyles.activeGuide.when_(s.nonEmpty)
              )(
                s"Tip/Tilt: ${s.map(_.toString).mkString("+")}".when(s.nonEmpty),
                s"Tip/Tilt: Off".when(s.isEmpty)
              ),
              Header(
                as = "span",
                size = Small,
                clazz =
                  ObserveStyles.item |+| ObserveStyles.activeGuide.when_(c === ComaOption.ComaOn)
              )(
                s"Coma: ${c.show}"
              )
            )
          case M2GuideConfig.M2GuideOff      =>
            React.Fragment(
              Header(as = "span", size = Small, clazz = ObserveStyles.item)(
                "Tip/Tilt: Off"
              ),
              Header(as = "span", size = Small, clazz = ObserveStyles.item)(
                "Coma: Off"
              )
            )
        }
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build

}
