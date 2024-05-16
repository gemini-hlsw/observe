// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import lucuma.react.primereact.Tooltip
import lucuma.react.primereact.TooltipOptions
import observe.ui.model.AppContext
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
