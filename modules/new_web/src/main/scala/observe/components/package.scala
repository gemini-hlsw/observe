// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.components

import org.typelevel.log4cats.Logger
import cats.effect.IO
import observe.AppContext

val UnknownTargetName: String = "None"

// TODO See if this can be generalized to any number of hooks
def usingContext[F[_], P, T](fn: Logger[F] ?=> P => T): (P, AppContext[F]) => T =
  (props, ctx) =>
    import ctx.given
    fn(props)

// def usingContext[F[_],P, H1, T](fn: Logger[F] ?=> (P, H1) => T): (P, AppContext[F], H1) => T =
//   (props, ctx, h1) =>
//     import ctx.given
//     fn(props, h1)
