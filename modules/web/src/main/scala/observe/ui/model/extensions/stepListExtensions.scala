// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.extensions

import cats.syntax.all.*
import lucuma.core.math.Angle
import lucuma.core.math.Axis
import observe.model.ExecutionStep
import observe.model.NodAndShuffleStep
import observe.model.OffsetType
import observe.model.StandardStep
import observe.ui.model.enums.OffsetsDisplay
import observe.ui.model.extensions.*
import observe.ui.model.formatting.*
import observe.ui.utils.*

import scala.math.max

private def maxWidth(angles: List[Angle]): Double =
  angles.map(angle => tableTextWidth(offsetAngle(angle))).maximumOption.orEmpty

extension (steps: List[ExecutionStep])
  // Calculate the widest offset step, widest axis label and widest NS nod label
  private def sequenceOffsetMaxWidth: (Double, Double, Double) =
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
