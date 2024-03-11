// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
// import explore.*
// import explore.components.ui.ExploreStyles
// import explore.model.AppContext
import lucuma.ui.format.{DurationFormatter, UtcFormatter}
import lucuma.ui.display.given
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.SequenceType
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.syntax.all.given
import lucuma.core.util.Timestamp
import lucuma.react.primereact.AccordionTab
import lucuma.schemas.model.AtomRecord
import lucuma.schemas.model.StepRecord
import lucuma.schemas.model.Visit

import java.time.Duration
import lucuma.react.table.ColumnDef
import lucuma.ui.sequence.*
import lucuma.ui.table.hooks.UseDynTable
import eu.timepit.refined.types.numeric.PosInt
import cats.data.NonEmptyList

trait SequenceTablesVisits[D <: DynamicConfig]:
  self: SequenceTablesDefs =>

  protected def renderTable: (
    Reusable[List[ColumnDef[SequenceTableRow, ?]]],
    List[StepRecord[D]],
    UseDynTable
  ) => VdomNode

  private def stepDuration(step: StepRecord[D]): Duration =
    step.instrumentConfig match
      case DynamicConfig.GmosNorth(exposure, _, _, _, _, _, _) => exposure.toDuration
      case DynamicConfig.GmosSouth(exposure, _, _, _, _, _, _) => exposure.toDuration

  private def renderSequence(
    sequenceType: SequenceType,
    cols:         Reusable[List[ColumnDef[SequenceTableRow, ?]]],
    atoms:        List[AtomRecord[D]],
    dynTable:     UseDynTable
  ): Option[AccordionTab] =
    atoms
      .flatMap(_.steps)
      .some
      .filter(_.nonEmpty)
      .map: steps =>
        AccordionTab(
          // clazz = ExploreStyles.VisitSection,
          header = <.div( /*ExploreStyles.VisitHeader*/ )( // Steps is non-empty => head is safe
            <.span(UtcFormatter.format(steps.head.created.toInstant)),
            <.span(sequenceType.shortName),
            <.span(s"Steps: 1 - ${steps.length}"),
            <.span {
              val datasetIndices = steps.flatMap(_.datasets).map(_.index.value)
              "Files: " + datasetIndices.minOption
                .map(min => s"$min - ${datasetIndices.max}")
                .getOrElse("---")
            },
            <.span(
              DurationFormatter(
                steps
                  .map(step => stepDuration(step))
                  .reduce(_.plus(_))
              )
            )
          )
        )(renderTable(cols, steps, dynTable))

  def renderVisits(
    cols:     Reusable[List[ColumnDef[SequenceTableRow, ?]]],
    visits:   List[Visit[D]],
    dynTable: UseDynTable
  ): List[AccordionTab] =
    visits
      .flatMap: visit =>
        renderSequence(SequenceType.Acquisition, cols, visit.acquisitionAtoms, dynTable) ++
          renderSequence(SequenceType.Science, cols, visit.scienceAtoms, dynTable)

  case class VisitData(
    created:      Timestamp,
    sequenceType: SequenceType,
    steps:        NonEmptyList[SequenceTableRow],
    datasetRange: Option[(Short, Short)]
  )
// val firstStepIndex: StepIndex = steps.head.index
// val lastStepIndex: StepIndex  = steps.last.index

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
            .map(SequenceRow.Executed.ExecutedStep(_, _ => none))
            .zipWithStepIndex(startIndex),
          datasetIndices.minOption.map(min => (min, datasetIndices.max))
        )
      .map: (created, zipResult, datasetRange) =>
        val (rows, nextIndex) = zipResult

        (VisitData(created, sequenceType,
        )
         rows.map(SequenceTableRow(_, _)), datasetRange).some,
         nextIndex
        ) // TODO Add SignalToNoise
      .getOrElse:
        (none, startIndex)

  def visitsSequences(visits: List[Visit[D]]): (List[VisitData], StepIndex) =
    visits
      .foldLeft((List.empty[List[SequenceTableRow]], StepIndex(PosInt.unsafeFrom(1))))(
        (accum, visit) =>
          val (seqs, index) = accum

          // Acquisition indices restart at 1 in each visit.
          // Science indices continue from one visit to the next.
          val acquisition          = sequenceRows(visit.acquisitionAtoms)._1
          val (science, nextIndex) = sequenceRows(visit.scienceAtoms, index)

          (
            List(acquisition, science).flattenOption,
            nextIndex
          )
      )
