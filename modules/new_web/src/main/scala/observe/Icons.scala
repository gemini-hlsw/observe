// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe

import react.fa.FAIcon
import react.fa.FontAwesomeIcon
import react.fa.IconLibrary

import scala.annotation.nowarn
import scala.scalajs.js
import scala.scalajs.js.annotation._

@nowarn
object Icons {
  @js.native
  @JSImport("@fortawesome/pro-regular-svg-icons", "faCircleDot")
  val faCircleDot: FAIcon = js.native

  // This is tedious but lets us do proper tree-shaking
  IconLibrary.add(
    faCircleDot
  )

  val CircleDot = FontAwesomeIcon(faCircleDot)
}
