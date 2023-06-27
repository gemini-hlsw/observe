// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package web.client.table

import cats.Eq
import japgolly.scalajs.react.Reusability

/**
 * Metadata for a column
 */
final case class ColumnMeta[A](
  column:     A,
  name:       String,
  label:      String,
  visible:    Boolean,
  width:      ColumnWidth,
  grow:       Int = 1,
  removeable: Int = 0
) {
  def isVariable: Boolean = width match {
    case _: FixedColumnWidth => false
    case _                   => true
  }
}

object ColumnMeta {
  given [A: Eq]: Eq[ColumnMeta[A]] =
    Eq.by(x => (x.column, x.name, x.label, x.visible, x.width, x.grow, x.removeable))

  given [A: Reusability]: Reusability[ColumnMeta[A]] =
    Reusability.derive[ColumnMeta[A]]
}
