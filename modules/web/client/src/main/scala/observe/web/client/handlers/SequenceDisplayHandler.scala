// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import cats.syntax.all.*
import diode.ActionHandler
import diode.ActionResult
import diode.ModelRW
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.web.client.actions.*
import observe.web.client.circuit.*

/**
 * Handles actions related to the changing the selection of the displayed sequence
 */
class SequenceDisplayHandler[M](modelRW: ModelRW[M, SequencesFocus])
    extends ActionHandler(modelRW)
    with Handlers[M, SequencesFocus] {
  def handleSelectSequenceDisplay: PartialFunction[Any, ActionResult[M]] = {
    case SelectIdToDisplay(i, id, _) =>
      updatedL(SequencesFocus.sod.modify(_.focusOnSequence(i, id).hideStepConfig))

    case SelectSequencePreview(i, id, _) =>
      val seq = SequencesQueue
        .queueItemG[SequenceView](_.idName.id === id)
        .get(value.sequences)
      seq
        .map { s =>
          updatedL(SequencesFocus.sod.modify(_.previewSequence(i, s)))
        }
        .getOrElse {
          noChange
        }

    case SelectCalibrationQueue =>
      updatedL(SequencesFocus.sod.modify(_.focusOnDayCal))

  }

  private def handleShowHideStep: PartialFunction[Any, ActionResult[M]] = {
    case ShowPreviewStepConfig(i, id, stepId) =>
      val seq = SequencesQueue
        .queueItemG[SequenceView](_.idName.id === id)
        .get(value.sequences)
      seq
        .map { s =>
          updatedL(SequencesFocus.sod.modify(_.previewSequence(i, s).showStepConfig(id, stepId)))
        }
        .getOrElse {
          noChange
        }

    case ShowStepConfig(i, id, stepId) =>
      updatedL(SequencesFocus.sod.modify(_.focusOnSequence(i, id).showStepConfig(id, stepId)))

  }

  private def handleClean: PartialFunction[Any, ActionResult[M]] = { case CleanSequences =>
    noChange
  }

  private def handleLoadFailed: PartialFunction[Any, ActionResult[M]] = {
    case SequenceLoadFailed(idName) =>
      updatedL(SequencesFocus.sod.modify(_.loadingFailed(idName.id)))
  }

  override def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleSelectSequenceDisplay, handleShowHideStep, handleLoadFailed, handleClean).combineAll
}
