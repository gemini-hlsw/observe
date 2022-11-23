// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import react.fa.FAIcon
import react.fa.FontAwesomeIcon
import react.fa.IconLibrary

import scala.scalajs.js
import scala.scalajs.js.annotation.*

object Icons:
  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faBan")
  private val faBan: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-regular-svg-icons", "faCalendarDays")
  private val faCalendarDays: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faCaretRight")
  private val faCaretRight: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faCaretDown")
  private val faCaretDown: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faCheck")
  private val faCheck: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faChevronRight")
  private val faChevronRight: FAIcon = js.native

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
  @JSImport("@fortawesome/pro-solid-svg-icons", "faCircleExclamation")
  private val faCircleExclamation: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faCircleNotch")
  private val faCircleNotch: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-regular-svg-icons", "faClock")
  private val faClock: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-regular-svg-icons", "faCrosshairs")
  private val faCrosshairs: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faGears")
  private val faGears: FAIcon = js.native

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
  @JSImport("@fortawesome/pro-solid-svg-icons", "faRectangleList")
  private val faRectangleList: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faReply")
  private val faReply: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faStop")
  private val faStop: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faSun")
  private val faSun: FAIcon = js.native

  @js.native
  @JSImport("@fortawesome/pro-solid-svg-icons", "faTrash")
  private val faTrash: FAIcon = js.native

  @js.native
  // @JSImport("@fortawesome/sharp-solid-svg-icons", "faXmark") // FIXME Can't get sharp to work
  @JSImport("@fortawesome/pro-solid-svg-icons", "faXmark")
  private val faXMark: FAIcon = js.native

  // This is tedious but lets us do proper tree-shaking
  IconLibrary.add(
    faBan,
    faCalendarDays,
    faCaretRight,
    faCheck,
    faCaretDown,
    faChevronRight,
    faCircle,
    faCircleCheck,
    faCircleDot,
    faCircleExclamation,
    faCircleNotch,
    faClock,
    faCrosshairs,
    faGears,
    faMoon,
    faPause,
    faPlay,
    faRectangleList,
    faReply,
    faStop,
    faSun,
    faTrash,
    faXMark
  )

  inline def Ban               = FontAwesomeIcon(faBan)
  inline def CalendarDays      = FontAwesomeIcon(faCalendarDays)
  inline def CaretDown         = FontAwesomeIcon(faCaretDown)
  inline def CaretRight        = FontAwesomeIcon(faCaretRight)
  inline def Check             = FontAwesomeIcon(faCheck)
  inline def ChevronRight      = FontAwesomeIcon(faChevronRight)
  inline def Circle            = FontAwesomeIcon(faCircle)
  inline def CircleCheck       = FontAwesomeIcon(faCircleCheck)
  inline def CircleDot         = FontAwesomeIcon(faCircleDot)
  inline def CircleExclamation = FontAwesomeIcon(faCircleExclamation)
  inline def CircleNotch       = FontAwesomeIcon(faCircleNotch)
  inline def Clock             = FontAwesomeIcon(faClock)
  inline def Crosshairs        = FontAwesomeIcon(faCrosshairs)
  inline def Gears             = FontAwesomeIcon(faGears)
  inline def Moon              = FontAwesomeIcon(faMoon)
  inline def Pause             = FontAwesomeIcon(faPause)
  inline def Play              = FontAwesomeIcon(faPlay)
  inline def RectangleList     = FontAwesomeIcon(faRectangleList)
  inline def Reply             = FontAwesomeIcon(faReply)
  inline def Stop              = FontAwesomeIcon(faStop)
  inline def Sun               = FontAwesomeIcon(faSun)
  inline def Trash             = FontAwesomeIcon(faTrash)
  inline def XMark             = FontAwesomeIcon(faXMark)
