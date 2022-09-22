// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.ui.table

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.table.*
import react.common.*
import react.common.style.Css
import reactST.{ tanstackTableCore => raw }

import javax.swing.text.html.HTML

import scalajs.js

final case class PrimeTable[T](
  table:      raw.mod.Table[T],
  tableClass: Css = Css.Empty,
  cellClass:  Css = Css.Empty,
  rowClassFn: (Int, T) => Css = (_, _: T) => Css.Empty
) extends ReactFnProps(PrimeTable.component)
    with HTMLTableProps[T]

final case class PrimeVirtualizedTable[T](
  table:          raw.mod.Table[T],
  containerClass: Css = Css.Empty,
  tableClass:     Css = Css.Empty,
  cellClass:      Css = Css.Empty,
  rowClassFn:     (Int, T) => Css = (_, _: T) => Css.Empty
) extends ReactFnProps(PrimeVirtualizedTable.component)
    with HTMLVirtualizedTableProps[T]

private val baseHTMLRenderer: HTMLTableRenderer[Any] =
  new HTMLTableRenderer[Any]:
    override protected val TableClass: Css = Css(
      "react-table p-datatable p-component p-datatable-hoverable-rows"
    ) // TODO Hoverable as prop?
    override protected val TheadClass: Css   = Css("p-datatable-thead")
    override protected val TheadTrClass: Css = Css.Empty
    override protected val TheadThClass: Css = Css.Empty
    override protected val TbodyClass: Css   = Css("p-datatable-table")
    override protected val TbodyTrClass: Css = Css.Empty
    override protected val TbodyTdClass: Css = Css.Empty
    override protected val TfootClass: Css   = Css("p-datatable-tfoot")
    override protected val TfootTrClass: Css = Css.Empty
    override protected val TfootThClass: Css = Css.Empty

    override protected val ResizerContent: VdomNode = "⋮"

object PrimeTable:
  private val component = HTMLTableRenderer.componentBuilder[Any, HTMLTable](baseHTMLRenderer)

object PrimeVirtualizedTable:
  private val component =
    HTMLTableRenderer.componentBuilderVirtualized[Any, HTMLVirtualizedTable](baseHTMLRenderer)
