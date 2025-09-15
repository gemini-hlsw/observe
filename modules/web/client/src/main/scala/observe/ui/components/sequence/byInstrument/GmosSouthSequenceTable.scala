// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.byInstrument

import japgolly.scalajs.react.*
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.math.SingleSN
import lucuma.core.math.TotalSN
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Dataset
import lucuma.core.model.sequence.ExecutionConfig
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.gmos
import lucuma.react.common.*
import lucuma.schemas.model.Visit
import observe.model.ExecutionState
import observe.model.StepProgress
import observe.model.odb.RecordedVisit
import observe.ui.components.sequence.SequenceTable
import observe.ui.components.sequence.SequenceTableBuilder
import observe.ui.model.EditableQaFields
import observe.ui.model.ObservationRequests
import observe.ui.model.enums.ClientMode

case class GmosSouthSequenceTable(
  clientMode:           ClientMode,
  obsId:                Observation.Id,
  config:               ExecutionConfig.GmosSouth,
  snPerClass:           Map[SequenceType, (SingleSN, TotalSN)],
  visits:               List[Visit.GmosSouth],
  executionState:       ExecutionState,
  currentRecordedVisit: Option[RecordedVisit],
  progress:             Option[StepProgress],
  selectedStepId:       Option[Step.Id],
  setSelectedStepId:    Step.Id => Callback,
  requests:             ObservationRequests,
  isPreview:            Boolean,
  onBreakpointFlip:     (Observation.Id, Step.Id) => Callback,
  onDatasetQaChange:    Dataset.Id => EditableQaFields => Callback,
  datasetIdsInFlight:   Set[Dataset.Id]
) extends ReactFnProps(GmosSouthSequenceTable.component)
    with SequenceTable[gmos.StaticConfig.GmosSouth, gmos.DynamicConfig.GmosSouth](
      Instrument.GmosSouth
    )

object GmosSouthSequenceTable
    extends SequenceTableBuilder[gmos.StaticConfig.GmosSouth, gmos.DynamicConfig.GmosSouth](
      Instrument.GmosSouth
    )
