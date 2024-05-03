// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

object all
    extends ArbRunningStep
    with ArbNotification
    with ArbObserveStep
    with ArbStandardStep
    with ArbNodAndShuffleStep
    with ArbStepState
    with ArbStepConfig
    with ArbDhsTypes
    with ArbNsSubexposure
    with ArbGmosParameters
    with ArbNsRunningState
    with ArbObsRecordedIds
    with ArbObservationProgress
    with ArbUserPrompt
