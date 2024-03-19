// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.data.NonEmptyList
import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.SequenceType
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.syntax.all.given
import lucuma.core.util.Timestamp
import lucuma.react.table.RowId
import lucuma.schemas.model.AtomRecord
import lucuma.schemas.model.Visit
import lucuma.ui.display.given
import lucuma.ui.format.DurationFormatter
import lucuma.ui.format.UtcFormatter
import lucuma.ui.sequence.*

import java.time.Duration

// Methods for building visits rows on the sequence table
trait SequenceTableVisits[D <: DynamicConfig]:

  case class VisitData(
    visitId:      Visit.Id,
    created:      Timestamp,
    sequenceType: SequenceType,
    steps:        NonEmptyList[SequenceTableRow],
    datasetRange: Option[(Short, Short)]
  ):
    val rowId: RowId = RowId(s"$visitId-sequenceType")

  protected def renderVisitHeader(visit: VisitData): VdomNode =
    <.div(SequenceStyles.VisitHeader)( // Steps is non-empty => head is safe
      <.span(
        s"${visit.sequenceType.shortName} Visit on ${UtcFormatter.format(visit.created.toInstant)}"
      ),
      <.span(s"Steps: ${visit.steps.head.index} - ${visit.steps.last.index}"),
      <.span(
        "Files: " + visit.datasetRange
          .map((min, max) => s"$min - $max")
          .getOrElse("---")
      ),
      <.span(
        DurationFormatter(
          visit.steps
            .map(_.step.exposureTime.orEmpty.toDuration)
            .reduce(_.plus(_))
        )
      )
    )

  private def sequenceRows(
    visitId:      Visit.Id,
    atoms:        List[AtomRecord[D]],
    sequenceType: SequenceType,
    startIndex:   StepIndex = StepIndex.One
  ): (Option[VisitData], StepIndex) =
    atoms
      .flatMap(_.steps)
      .some
      .filter(_.nonEmpty)
      .map: steps =>
        val datasetIndices = steps.flatMap(_.datasets).map(_.index.value)

        (
          steps.head.created,
          steps
            .map(SequenceRow.Executed.ExecutedStep(_, none)) // TODO Add SignalToNoise
            .zipWithStepIndex(startIndex),
          datasetIndices.minOption.map(min => (min, datasetIndices.max))
        )
      .map: (created, zipResult, datasetRange) =>
        val (rows, nextIndex) = zipResult

        (VisitData(
           visitId,
           created,
           sequenceType,
           NonEmptyList.fromListUnsafe(rows.map(SequenceTableRow(_, _))),
           datasetRange
         ).some,
         nextIndex
        )
      .getOrElse:
        (none, startIndex)

  def visitsSequences(visits: List[Visit[D]]): (List[VisitData], StepIndex) =
    visits
      .foldLeft((List.empty[VisitData], StepIndex.One))((accum, visit) =>
        val (seqs, index) = accum

        // Acquisition indices restart at 1 in each visit.
        // Science indices continue from one visit to the next.
        val acquisition          =
          sequenceRows(visit.id, visit.acquisitionAtoms, SequenceType.Acquisition)._1
        val (science, nextIndex) =
          sequenceRows(visit.id, visit.scienceAtoms, SequenceType.Science, index)

        (
          seqs ++ List(acquisition, science).flattenOption,
          nextIndex
        )
      )
