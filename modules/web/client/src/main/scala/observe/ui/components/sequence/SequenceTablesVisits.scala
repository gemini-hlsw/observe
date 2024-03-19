// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import lucuma.ui.format.{DurationFormatter, UtcFormatter}
import lucuma.ui.display.given
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.SequenceType
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.syntax.all.given
import lucuma.core.util.Timestamp
import lucuma.schemas.model.AtomRecord
import lucuma.schemas.model.Visit

import java.time.Duration
import lucuma.ui.sequence.*
import eu.timepit.refined.types.numeric.PosInt
import cats.data.NonEmptyList

trait SequenceTablesVisits[D <: DynamicConfig]:

  case class VisitData(
    created:      Timestamp,
    sequenceType: SequenceType,
    steps:        NonEmptyList[SequenceTableRow],
    datasetRange: Option[(Short, Short)]
  )

  protected def renderVisitHeader(visit: VisitData): VdomNode =
    <.div(SequenceStyles.VisitHeader)( // Steps is non-empty => head is safe
      <.span(UtcFormatter.format(visit.created.toInstant)),
      <.span(visit.sequenceType.shortName),
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
    atoms:        List[AtomRecord[D]],
    sequenceType: SequenceType,
    startIndex:   StepIndex = StepIndex(PosInt.unsafeFrom(1))
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
      .foldLeft((List.empty[VisitData], StepIndex(PosInt.unsafeFrom(1))))((accum, visit) =>
        val (seqs, index) = accum

        // Acquisition indices restart at 1 in each visit.
        // Science indices continue from one visit to the next.
        val acquisition          = sequenceRows(visit.acquisitionAtoms, SequenceType.Acquisition)._1
        val (science, nextIndex) = sequenceRows(visit.scienceAtoms, SequenceType.Science, index)

        (
          seqs ++ List(acquisition, science).flattenOption,
          nextIndex
        )
      )
