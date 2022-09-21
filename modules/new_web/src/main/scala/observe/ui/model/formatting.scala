// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import lucuma.core.math.Angle
import observe.model.OffsetFormat
import observe.ui.utils.*

object formatting:
  def offsetAxis[A](using show: OffsetFormat[A]): String =
    s"${show.format}:"

  def offsetNSNod[T](using show: OffsetFormat[T]): String =
    s"${show.format}"

  def offsetAngle(off: Angle): String =
    f" ${Angle.signedDecimalArcseconds.get(off).toDouble}%03.2f″"

  def axisLabelWidth[A](implicit show: OffsetFormat[A]): Double =
    tableTextWidth(offsetAxis[A])

  def nsNodLabelWidth[A](implicit show: OffsetFormat[A]): Double =
    tableTextWidth(offsetNSNod[A])
