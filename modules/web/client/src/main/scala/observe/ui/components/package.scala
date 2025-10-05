// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.given
import crystal.Pot
import crystal.syntax.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.Reusable
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.ObservationReference
import lucuma.core.model.Program
import lucuma.react.primereact.Tooltip
import lucuma.react.primereact.TooltipOptions
import lucuma.react.primereact.tooltip.*
import observe.model.Observation
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.AppContext
import observe.ui.model.RootModel
import org.typelevel.log4cats.Logger

// TODO See if this can be generalized to any number of hooks
def usingContext[F[_], P, T](fn: Logger[F] ?=> P => T): (P, AppContext[F]) => T =
  (props, ctx) =>
    import ctx.given
    fn(props)

// def usingContext[F[_],P, H1, T](fn: Logger[F] ?=> (P, H1) => T): (P, AppContext[F], H1) => T =
//   (props, ctx, h1) =>
//     import ctx.given
//     fn(props, h1)

val DefaultTooltipOptions =
  TooltipOptions(position = Tooltip.Position.Top, showDelay = 100, autoHide = false)

extension (rootModel: RootModel)
  def renderExploreLinkToObs
    : Pot[Reusable[Either[(Program.Id, Observation.Id), ObservationReference] => VdomNode]] =
    (rootModel.clientConfig, rootModel.data.get.userVault.map(_.toPot).flatten).tupled
      .map: (clientConfig, _) =>
        Reusable.always: obsIdOrRef =>
          <.a(
            ^.href := clientConfig.linkToExploreObs(obsIdOrRef).toString,
            ^.target.blank,
            ^.onClick ==> (e => e.stopPropagationCB),
            ObserveStyles.ExternalLink
          )(Icons.ExternalLink).withTooltip(
            content = "Open in Explore",
            position = Tooltip.Position.Top,
            showDelay = 200
          )
