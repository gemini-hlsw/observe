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

object ObsQueriesGQL {

  @GraphQL
  trait ActiveObservationIdsQuery extends GraphQLOperation[ObservationDB] {
    val document = """
      query {
        observations(WHERE: { status: { eq: { EQ: READY } } }) {
          matches {
            id
            title
          }
        }
      }
    """
  }

  @GraphQL
  trait ObsQuery extends GraphQLOperation[ObservationDB] {
    val document = """
      query($obsId: ObservationId!) {
        observation(observationId: $obsId) {
          id
          title
          status
          activeStatus
          program {
            id
            name
          }
          targetEnvironment {
            firstScienceTarget {
              targetId: id
              targetName: name
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
              ... on GmosNorthExecutionConfig {
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
              ... on GmosSouthExecutionConfig {
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
        ... on Science {
          offset { ...offsetFields }
          guiding
        }
        ... on SmartGcal {
          smartGcalType
        }
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

    object Data {
      object Observation {
        type Target        = model.Target
        type ConstraintSet = model.ConstraintSet
        type TimingWindows = model.TimingWindow
        object Execution {
          type Config = InstrumentExecutionConfig
        }
      }
    }
  }

  @GraphQL
  trait ProgramObservationsEditSubscription extends GraphQLOperation[ObservationDB] {
    val document = """
      subscription {
        observationEdit(programId:"p-2") {
          id
        }
      }
    """
  }

  @GraphQL
  trait ObservationEditSubscription extends GraphQLOperation[ObservationDB] {
    val document = """
      subscription($obsId: ObservationId!) {
        observationEdit(observationId: $obsId) {
          id
        }
      }
    """
  }

  @GraphQL
  trait AddSequenceEventMutation extends GraphQLOperation[ObservationDB] {
    val document = """
      mutation($vId: VisitId!, $obsId: ObservationId!, $cmd: SequenceCommand!) {
        addSequenceEvent(input: { visitId: $vId, location: { observationId: $obsId }, payload: { command: $cmd } } ) {
          event {
            visitId
            received
          }
        }
      }
      """
  }

  @GraphQL
  trait AddStepEventMutation extends GraphQLOperation[ObservationDB] {
    val document = """
      mutation($vId: VisitId!, $obsId: ObservationId!, $stpId: StepId!, $seqType: SequenceType!, $stg: StepStage!)  {
        addStepEvent(input: { visitId: $vId, location: { observationId: $obsId, stepId: $stpId }, payload: { sequenceType: $seqType, stage: $stg } } ) {
          event {
            received
          }
        }
      }
      """
  }

  @GraphQL
  trait AddDatasetEventMutation extends GraphQLOperation[ObservationDB] {
    val document = """
      mutation($vId: VisitId!, $obsId: ObservationId!, $stpId: StepId!, $dtIdx: PosInt!, $stg: DatasetStage!, $flName: DatasetFilename)  {
        addDatasetEvent(input: { visitId: $vId, location: { observationId: $obsId, stepId: $stpId, index: $dtIdx }, payload: { datasetStage: $stg, filename: $flName } } ) {
          event {
            received
          }
        }
      }
      """
  }

  @GraphQL
  trait RecordGmosNorthVisitMutation extends GraphQLOperation[ObservationDB] {
    val document = """
      mutation($obsId: ObservationId!, $staticCfg: GmosNorthStaticInput!) {
        recordGmosNorthVisit(input: { observationId: $obsId, static: $staticCfg } ) {
          visit {
            id
          }
        }
      }
      """
  }

  @GraphQL
  trait RecordGmosSouthVisitMutation extends GraphQLOperation[ObservationDB] {
    val document =
      """
      mutation($obsId: ObservationId!, $staticCfg: GmosSouthStaticInput!) {
        recordGmosSouthVisit(input: { observationId: $obsId, static: $staticCfg } ) {
          visit {
            id
          }
        }
      }
      """
  }

}
