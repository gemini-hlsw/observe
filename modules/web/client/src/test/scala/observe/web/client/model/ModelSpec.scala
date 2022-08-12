// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats.kernel.laws.discipline._
import cats.tests.CatsSuite
import diode.data._
import lucuma.core.util.arb.ArbEnumerated._
import lucuma.core.util.arb.ArbGid._
import lucuma.core.util.arb.ArbUid._
import monocle.law.discipline.LensTests
import monocle.law.discipline.PrismTests
import monocle.law.discipline.OptionalTests
import monocle.law.discipline.TraversalTests
import org.scalajs.dom.WebSocket
import observe.web.client.components.sequence.steps.StepConfigTable
import observe.web.client.components.SessionQueueTable
import observe.web.client.circuit.StepsTableTypeSelection
import observe.web.client.model._
import observe.web.client.model.Formatting.OffsetsDisplay
import web.client.table.TableState

/**
 * Tests Client typeclasses
 */
final class ModelSpec extends CatsSuite with ArbitrariesWebClient {

  checkAll("Eq[OffsetsDisplay]", EqTests[OffsetsDisplay].eqv)
  checkAll("Eq[WebSocket]", EqTests[WebSocket].eqv)
  checkAll("Eq[Pot[A]]", EqTests[Pot[Int]].eqv)
  checkAll("Eq[WebSocketConnection]", EqTests[WebSocketConnection].eqv)
  checkAll("Eq[ClientStatus]", EqTests[ClientStatus].eqv)
  checkAll("Eq[AvailableTab]", EqTests[AvailableTab].eqv)
  checkAll("Eq[TabSelected]", EqTests[TabSelected].eqv)
  checkAll("Eq[ObserveTabActive]", EqTests[ObserveTabActive].eqv)
  checkAll("Eq[CalibrationQueueTab]", EqTests[CalibrationQueueTab].eqv)
  checkAll("Eq[InstrumentSequenceTab]", EqTests[InstrumentSequenceTab].eqv)
  checkAll("Eq[PreviewSequenceTab]", EqTests[PreviewSequenceTab].eqv)
  checkAll("Eq[Pages.ObservePages]", EqTests[Pages.ObservePages].eqv)
  checkAll("Eq[ObserveTab]", EqTests[ObserveTab].eqv)
  checkAll("Eq[SequencesOnDisplay]", EqTests[SequencesOnDisplay].eqv)
  checkAll("Eq[GlobalLog]", EqTests[GlobalLog].eqv)
  checkAll("Eq[UserNotificationState]", EqTests[UserNotificationState].eqv)
  checkAll("Eq[CalibrationQueues]", EqTests[CalibrationQueues].eqv)
  checkAll("Eq[AllObservationsProgressState]", EqTests[AllObservationsProgressState].eqv)
  checkAll("Eq[SessionQueueFilter]", EqTests[SessionQueueFilter].eqv)
  checkAll("Eq[SectionVisibilityState]", EqTests[SectionVisibilityState].eqv)
  checkAll("Eq[TableState[StepConfigTable.TableColumn]",
           EqTests[TableState[StepConfigTable.TableColumn]].eqv
  )
  checkAll("Eq[TableState[SessionQueueTable.TableColumn]",
           EqTests[TableState[SessionQueueTable.TableColumn]].eqv
  )
  checkAll("Eq[SoundSelection]", EqTests[SoundSelection].eqv)
  checkAll("Eq[StepsTableTypeSelection]", EqTests[StepsTableTypeSelection].eqv)
  checkAll("Eq[ObserveUIModel]", EqTests[ObserveUIModel].eqv)
  checkAll("Eq[ObserveAppRootModel]", EqTests[ObserveAppRootModel].eqv)
  checkAll("Eq[RunOperation]", EqTests[RunOperation].eqv)
  checkAll("Eq[SyncOperation]", EqTests[SyncOperation].eqv)
  checkAll("Eq[TabOperations]", EqTests[TabOperations].eqv)
  checkAll("Eq[AlignAndCalibStep]", EqTests[AlignAndCalibStep].eqv)
  checkAll("Eq[AppTableStates]", EqTests[AppTableStates].eqv)

  // lenses
  checkAll("Lens[SequenceTab, Option[Int]]", LensTests(SequenceTab.stepConfigL))

  checkAll("ObserveTab.previewTab", PrismTests(ObserveTab.previewTab))
  checkAll("ObserveTab.instrumentTab", PrismTests(ObserveTab.instrumentTab))
  checkAll("ObserveTab.sequenceTab", PrismTests(ObserveTab.sequenceTab))
  checkAll("SequencesOn.focusSequence", OptionalTests(SequencesOnDisplay.focusSequence))
  checkAll("SequencesOnDisplay.previewTab", TraversalTests(SequencesOnDisplay.previewTab))
}
