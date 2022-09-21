// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.extensions

import lucuma.core.math.Angle
import observe.model.ExecutionStep
import observe.model.StandardStep
import observe.model.NodAndShuffleStep
import observe.model.OffsetType
import lucuma.core.math.Axis
import observe.ui.model.extensions.*
import observe.ui.utils.*
import observe.ui.model.formatting.*
import cats.syntax.all.*

import scala.math.max
import observe.ui.model.enums.OffsetsDisplay

private def maxWidth(angles: List[Angle]): Double =
  angles.map(angle => tableTextWidth(offsetAngle(angle))).maximumOption.orEmpty

extension (steps: List[ExecutionStep])
  // Calculate the widest offset step, widest axis label and widest NS nod label
  def sequenceOffsetMaxWidth: (Double, Double, Double) =
    steps
      .collect {
        case s @ StandardStep(_, _, _, _, _, _, _, _)         =>
          (
            maxWidth(
              List(
                s.offset[OffsetType.Telescope, Axis.P].toAngle,
                s.offset[OffsetType.Telescope, Axis.Q].toAngle
              )
            ),
            max(axisLabelWidth[Axis.P], axisLabelWidth[Axis.Q]),
            0.0
          )
        case s @ NodAndShuffleStep(_, _, _, _, _, _, _, _, _) =>
          (
            maxWidth(
              List(
                s.offset[OffsetType.NSNodB, Axis.P].toAngle,
                s.offset[OffsetType.NSNodB, Axis.Q].toAngle,
                s.offset[OffsetType.NSNodA, Axis.P].toAngle,
                s.offset[OffsetType.NSNodA, Axis.Q].toAngle
              )
            ),
            max(axisLabelWidth[Axis.P], axisLabelWidth[Axis.Q]),
            max(nsNodLabelWidth[OffsetType.NSNodB], nsNodLabelWidth[OffsetType.NSNodA])
          )
      }
      .foldLeft((0.0, 0.0, 0.0)) { case ((ow1, aw1, nw1), (ow2, aw2, nw2)) =>
        ((ow1.max(ow2), aw1.max(aw2), nw1.max(nw2)))
      }

  def offsetsDisplay: OffsetsDisplay =
    (OffsetsDisplay.DisplayOffsets.apply _).tupled(steps.sequenceOffsetMaxWidth)
