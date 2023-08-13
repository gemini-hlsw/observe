// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.*
import lucuma.react.fa.FontAwesomeIcon
import lucuma.react.fa.Rotation
import observe.model.enums.StepState
import observe.ui.Icons
import observe.ui.ObserveStyles

/**
 * Component to display an icon for the state
 */
case class StepIconCell(
  status:    StepState,
  skip:      Boolean,
  nextToRun: Boolean
) extends ReactFnProps(StepIconCell.component)

object StepIconCell:
  private type Props = StepIconCell

  private def stepIcon(props: Props): Option[FontAwesomeIcon] =
    props.status match
      case StepState.Completed  => Icons.Check.some
      case StepState.Running    => Icons.CircleNotch.withFixedWidth().withSpin(true).some
      case StepState.Failed(_)  => Icons.CircleExclamation.withFixedWidth().some
      case StepState.Skipped    => Icons.Reply.withFixedWidth().withRotation(Rotation.Rotate270).some
      case _ if props.skip      => Icons.Reply.withFixedWidth().withRotation(Rotation.Rotate270).some
      case _ if props.nextToRun => Icons.ChevronRight.withFixedWidth().some
      case _                    => none

  private def stepStyle(props: Props): Css =
    props.status match
      case StepState.Skipped => ObserveStyles.SkippedIconCell
      case _                 => Css.Empty

  private val component = ScalaFnComponent[Props](props =>
    <.div(ObserveStyles.IconCell |+| stepStyle(props), stepIcon(props))
  )
