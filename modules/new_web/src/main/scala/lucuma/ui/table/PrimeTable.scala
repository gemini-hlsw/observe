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
import reactST.{ tanstackVirtualCore => rawVirtual }

import scalajs.js

case class PrimeTable[T](
  table:      raw.mod.Table[T],
  tableClass: Css = Css.Empty,
  rowClassFn: (Int, T) => Css = (_, _: T) => Css.Empty
) extends ReactFnProps(PrimeTable.component)
    with HTMLTableProps[T]

case class PrimeVirtualizedTable[T](
  table:               raw.mod.Table[T],
  estimateRowHeightPx: Int => Int,
  // Table options
  containerClass:      Css = Css.Empty,
  tableClass:          Css = Css.Empty,
  rowClassFn:          (Int, T) => Css = (_, _: T) => Css.Empty,
  // Virtual options
  overscan:            js.UndefOr[Int] = js.undefined,
  getItemKey:          js.UndefOr[Int => rawVirtual.mod.Key] = js.undefined
) extends ReactFnProps(PrimeVirtualizedTable.component)
    with HTMLVirtualizedTableProps[T]

private val baseHTMLRenderer: HTMLTableRenderer[Any] =
  new HTMLTableRenderer[Any]:
    override protected val TableClass: Css = Css(
      "pl-react-table p-component p-datatable p-datatable-table p-datatable-hoverable-rows p-datatable-responsive-scroll"
    ) // TODO Props for hoverable/scroll/etc?
    override protected val TheadClass: Css   = Css("p-datatable-thead")
    override protected val TheadTrClass: Css = Css.Empty
    override protected val TheadThClass: Css = Css("p-column-title")
    override protected val TbodyClass: Css   = Css("p-datatable-tbody")
    override protected val TbodyTrClass: Css = Css.Empty
    override protected val TbodyTdClass: Css = Css.Empty
    override protected val TfootClass: Css   = Css("p-datatable-tfoot")
    override protected val TfootTrClass: Css = Css.Empty
    override protected val TfootThClass: Css = Css.Empty

    override protected val ResizerClass: Css         = Css("pl-resizer")
    override protected val IsResizingTHeadClass: Css = Css("pl-head-resizing")
    override protected val IsResizingColClass: Css   = Css("pl-col-resizing")
    override protected val ResizerContent: VdomNode  = "⋮"

    override protected val SortableColClass: Css       = Css("pl-sortable-col")
    override protected val SortAscIndicator: VdomNode  = TableIcons.SortUp
    override protected val SortDescIndicator: VdomNode = TableIcons.SortDown

object PrimeTable:
  private val component = HTMLTableRenderer.componentBuilder[Any, HTMLTable](baseHTMLRenderer)

object PrimeVirtualizedTable:
  private val component =
    HTMLTableRenderer.componentBuilderVirtualized[Any, HTMLVirtualizedTable](baseHTMLRenderer)

case class AutoHeightPrimeVirtualizedTable[T](
  table:               raw.mod.Table[T],
  estimateRowHeightPx: Int => Int,
  // Table options
  outerContainerClass: Css = Css.Empty,
  innerContainerClass: Css = Css.Empty,
  tableClass:          Css = Css.Empty,
  rowClassFn:          (Int, T) => Css = (_, _: T) => Css.Empty,
  // Virtual options
  overscan:            js.UndefOr[Int] = js.undefined,
  getItemKey:          js.UndefOr[Int => rawVirtual.mod.Key] = js.undefined
) extends ReactFnProps(AutoHeightPrimeVirtualizedTable.component)

object AutoHeightPrimeVirtualizedTable:
  private type Props[T] = AutoHeightPrimeVirtualizedTable[T]

  import react.common.*

  private val VirtualizedOuterContainer: Css = Css("pl-react-table-virtualized-outer-container")
  private val VirtualizedInnerContainer: Css = Css("pl-react-table-virtualized-inner-container")

  private def componentBuilder[T] = ScalaFnComponent[Props[T]](props =>
    // We use this trick to get a component whose height adjusts to the container.
    // See https://stackoverflow.com/a/1230666
    // We create 2 more containers: an outer one, with position: relative and height: 100%,
    // and an inner one, with position: absolute, and top: 0, bottom: 0.
    <.div(VirtualizedOuterContainer |+| props.outerContainerClass)(
      PrimeVirtualizedTable(
        table = props.table,
        estimateRowHeightPx = props.estimateRowHeightPx,
        containerClass = VirtualizedInnerContainer |+| props.innerContainerClass,
        tableClass = props.tableClass,
        rowClassFn = props.rowClassFn,
        overscan = props.overscan,
        getItemKey = props.getItemKey
      )
    )
  )

  private def component = componentBuilder[Any]
