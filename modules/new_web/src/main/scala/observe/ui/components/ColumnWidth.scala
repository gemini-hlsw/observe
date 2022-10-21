// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import lucuma.react.SizePx
import lucuma.react.table.ColumnDef

import scalajs.js.JSConverters.*

enum ColumnWidth:
  case Fixed(width: SizePx) extends ColumnWidth
  case Resizeable(initial: SizePx, min: Option[SizePx] = None, max: Option[SizePx] = None)
      extends ColumnWidth

object ColumnWidth:
  extension [T, V](col: ColumnDef.Single[T, V])
    def withWidth(width: ColumnWidth): ColumnDef.Single[T, V] = width match
      case Fixed(width)                  =>
        col.copy(size = width, enableResizing = false)
      case Resizeable(initial, min, max) =>
        col.copy(
          size = initial,
          minSize = min.orUndefined,
          maxSize = max.orUndefined,
          enableResizing = true
        )

  extension [T, V](col: ColumnDef.Group[T])
    def withWidth(width: ColumnWidth): ColumnDef.Group[T] = width match
      case Fixed(width)                  =>
        col.copy(size = width, enableResizing = false)
      case Resizeable(initial, min, max) =>
        col.copy(
          size = initial,
          minSize = min.orUndefined,
          maxSize = max.orUndefined,
          enableResizing = true
        )
