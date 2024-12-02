// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.common

import clue.GraphQLOperation
import clue.annotation.GraphQL
import lucuma.schemas.ObservationDB
import lucuma.core.model
import lucuma.core.model.sequence.InstrumentExecutionConfig

// gql: import io.circe.refined.*
// gql: import lucuma.schemas.decoders.given
// gql: import lucuma.odb.json.all.query.given

object ObsQueriesGQL:

  @GraphQL
  trait ObsQuery extends GraphQLOperation[ObservationDB]:
    val document = """
      query($obsId: ObservationId!) {
        observation(observationId: $obsId) {
          id
          title
          workflow {
            state
          }
          program {
            id
            name
            goa {
              proprietaryMonths
            }
          }
          targetEnvironment {
            firstScienceTarget {
              targetId: id
              targetName: name
            }
            guideEnvironment {
              guideTargets {
                probe
              }
            }
          }
          constraintSet {
            imageQuality
            cloudExtinction
            skyBackground
            waterVapor
            elevationRange {
              airMass {
                min
                max
              }
              hourAngle {
                minHours
                maxHours
              }
            }
          }
          timingWindows {
            inclusion
            startUtc
            end {
              ... on TimingWindowEndAt {
                atUtc
              }
              ... on TimingWindowEndAfter {
                after{
                  milliseconds
                }
                repeat {
                  period {
                    milliseconds
                  }
                  times
                }
              }
            }
          }
          execution {
            config(futureLimit: 100) {
              instrument
              gmosNorth {
                static {
                  stageMode
                  detector
                  mosPreImaging
                  nodAndShuffle {
                    ...nodAndShuffleFields
                  }
                }
                acquisition {
                  ...gmosNorthSequenceFields
                }
                science {
                  ...gmosNorthSequenceFields
                }
              }
              gmosSouth {
                static {
                  stageMode
                  detector
                  mosPreImaging
                  nodAndShuffle {
                    ...nodAndShuffleFields
                  }
                }
                acquisition {
                  ...gmosSouthSequenceFields
                }
                science {
                  ...gmosSouthSequenceFields
                }
              }
            }
          }
        }
      }

      fragment nodAndShuffleFields on GmosNodAndShuffle {
        posA { ...offsetFields }
        posB { ...offsetFields }
        eOffset
        shuffleOffset
        shuffleCycles
      }

      fragment stepConfigFields on StepConfig {
        stepType
        ... on Gcal {
          continuum
          arcs
          filter
          diffuser
          shutter
        }
        ... on SmartGcal {
          smartGcalType
        }
      }

      fragment telescopeConfigFields on TelescopeConfig {
        offset { ...offsetFields }
        guiding
      }

      fragment stepEstimateFields on StepEstimate {
        configChange {
          all {
            name
            description
            estimate { microseconds }
          }
          index
        }
        detector {
          all {
            name
            description
            dataset {
              exposure { microseconds }
              readout { microseconds }
              write { microseconds }
            }
            count
          }
          index
        }
      }

      fragment gmosNorthAtomFields on GmosNorthAtom {
        id
        description
        steps {
          id
          instrumentConfig {
            exposure { microseconds }
            readout {
              xBin
              yBin
              ampCount
              ampGain
              ampReadMode
            }
            dtax
            roi
            gratingConfig {
              grating
              order
              wavelength { picometers }
            }
            filter
            fpu {
              builtin
            }
          }
          stepConfig {
            ...stepConfigFields
          }
          telescopeConfig {
            ...telescopeConfigFields
          }
          estimate {
            ...stepEstimateFields
          }
          observeClass
          breakpoint
        }
      }

      fragment gmosNorthSequenceFields on GmosNorthExecutionSequence {
        nextAtom {
          ...gmosNorthAtomFields
        }
        possibleFuture {
          ...gmosNorthAtomFields
        }
        hasMore
      }

      fragment gmosSouthAtomFields on GmosSouthAtom {
        id
        description
        steps {
          id
          instrumentConfig {
            exposure { microseconds }
            readout {
              xBin
              yBin
              ampCount
              ampGain
              ampReadMode
            }
            dtax
            roi
            gratingConfig {
              grating
              order
              wavelength { picometers }
            }
            filter
            fpu {
              builtin
            }
          }
          stepConfig {
            ...stepConfigFields
          }
          telescopeConfig {
            ...telescopeConfigFields
          }
          estimate {
            ...stepEstimateFields
          }
          observeClass
          breakpoint
        }
      }

      fragment gmosSouthSequenceFields on GmosSouthExecutionSequence {
        nextAtom {
          ...gmosSouthAtomFields
        }
        possibleFuture {
          ...gmosSouthAtomFields
        }
        hasMore
      }

      fragment offsetFields on Offset {
        p { microarcseconds }
        q { microarcseconds }
      }
    """

    object Data:
      object Observation:
        type ConstraintSet = model.ConstraintSet
        type TimingWindows = model.TimingWindow
        object Execution:
          type Config = InstrumentExecutionConfig

  @GraphQL
  trait ProgramObservationsEditSubscription extends GraphQLOperation[ObservationDB]:
    val document = """
      subscription {
        observationEdit(programId:"p-2") {
          value {
            id
          }
        }
      }
    """

  @GraphQL
  trait ObservationEditSubscription extends GraphQLOperation[ObservationDB]:
    val document = """
      subscription($obsId: ObservationId!) {
        observationEdit(observationId: $obsId) {
          value {
            id
          }
        }
      }
    """

  @GraphQL
  trait AddSequenceEventMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($vId: VisitId!, $cmd: SequenceCommand!) {
        addSequenceEvent(input: { visitId: $vId, command: $cmd } ) {
          event {
            received
          }
        }
      }
      """

  @GraphQL
  trait AddAtomEventMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($atomId: AtomId!, $stg: AtomStage!)  {
        addAtomEvent(input: { atomId: $atomId, atomStage: $stg } ) {
          event {
            id
          }
        }
      }
      """

  @GraphQL
  trait AddStepEventMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($stepId: StepId!, $stg: StepStage!)  {
        addStepEvent(input: { stepId: $stepId, stepStage: $stg } ) {
          event {
            id
          }
        }
      }
      """

  @GraphQL
  trait AddDatasetEventMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($datasetId: DatasetId!, $stg: DatasetStage!)  {
        addDatasetEvent(input: { datasetId: $datasetId, datasetStage: $stg } ) {
          event {
            id
          }
        }
      }
      """

  @GraphQL
  trait RecordDatasetMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($stepId: StepId!, $filename: DatasetFilename!) {
        recordDataset(input: { stepId: $stepId, filename: $filename } ) {
          dataset {
            id
            reference {
              label
              observation {
                label
             }
            }
          }
        }
      }
      """

  @GraphQL
  trait RecordAtomMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($input: RecordAtomInput!) {
        recordAtom(input: $input) {
          atomRecord {
            id
          }
        }
      }
      """

  @GraphQL
  trait RecordGmosNorthStepMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($input: RecordGmosNorthStepInput!) {
        recordGmosNorthStep(input: $input) {
          stepRecord {
            id
          }
        }
      }
      """

  @GraphQL
  trait RecordGmosNorthVisitMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($obsId: ObservationId!, $staticCfg: GmosNorthStaticInput!) {
        recordGmosNorthVisit(input: { observationId: $obsId, gmosNorth: $staticCfg } ) {
          visit {
            id
          }
        }
      }
      """

  @GraphQL
  trait RecordGmosSouthStepMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($input: RecordGmosSouthStepInput!) {
        recordGmosSouthStep(input: $input) {
          stepRecord {
            id
          }
        }
      }
      """

  @GraphQL
  trait RecordGmosSouthVisitMutation extends GraphQLOperation[ObservationDB]:
    val document =
      """
      mutation($obsId: ObservationId!, $staticCfg: GmosSouthStaticInput!) {
        recordGmosSouthVisit(input: { observationId: $obsId, gmosSouth: $staticCfg } ) {
          visit {
            id
          }
        }
      }
      """
