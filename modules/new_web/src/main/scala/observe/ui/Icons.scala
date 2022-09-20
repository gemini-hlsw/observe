// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import react.fa.FAIcon
import react.fa.FontAwesomeIcon
import react.fa.IconLibrary

import scala.annotation.nowarn
import scala.scalajs.js
import scala.scalajs.js.annotation._

@nowarn
object Icons {
  @js.native
  @JSImport("@fortawesome/pro-regular-svg-icons", "faCalendarDays")
  private val faCalendarDays: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faCheck")
  private val faCheck: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-regular-svg-icons", "faCircle")
  private val faCircle: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-regular-svg-icons", "faCircleCheck")
  private val faCircleCheck: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-regular-svg-icons", "faCircleDot")
  private val faCircleDot: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faCircleNotch")
  private val faCircleNotch: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-regular-svg-icons", "faClock")
  private val faClock: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faMoon")
  private val faMoon: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faPause")
  private val faPause: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faPlay")
  private val faPlay: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faStop")
  private val faStop: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faSun")
  private val faSun: FAIcon = js.native

  // This is tedious but lets us do proper tree-shaking
  IconLibrary.add(
    faCalendarDays,
    faCheck,
    faCircle,
    faCircleCheck,
    faCircleDot,
    faCircleNotch,
    faClock,
    faMoon,
    faPause,
    faPlay,
    faStop,
    faSun
  )

  inline def CalendarDays = FontAwesomeIcon(faCalendarDays)
  inline def Checkmark    = FontAwesomeIcon(faCheck)
  inline def Circle       = FontAwesomeIcon(faCircle)
  inline def CircleCheck  = FontAwesomeIcon(faCircleCheck)
  inline def CircleDot    = FontAwesomeIcon(faCircleDot)
  inline def CircleNotch  = FontAwesomeIcon(faCircleNotch)
  inline def Clock        = FontAwesomeIcon(faClock)
  inline def Moon         = FontAwesomeIcon(faMoon)
  inline def Pause        = FontAwesomeIcon(faPause)
  inline def Play         = FontAwesomeIcon(faPlay)
  inline def Stop         = FontAwesomeIcon(faStop)
  inline def Sun          = FontAwesomeIcon(faSun)
}
