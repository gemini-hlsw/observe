// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.common

import clue.GraphQLOperation
import clue.annotation.GraphQL
import lucuma.schemas.ObservationDB
import lucuma.core.model
import lucuma.core.model.sequence.ExecutionSequence
import lucuma.core.model.sequence.gmos.{DynamicConfig, StaticConfig}

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
          plannedTime {
            execution {
              microseconds
            }
          }
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
            config:executionConfig {
              ... on GmosNorthExecutionConfig {
                staticN:static {
                  stageMode
                  detector
                  mosPreImaging
                  nodAndShuffle {
                    ...nodAndShuffle
                  }
                }
                acquisitionN:acquisition {
                  ...gmosNorthExecutionSequence
                }
                scienceN:science {
                  ...gmosNorthExecutionSequence
                }
              }
              ... on GmosSouthExecutionConfig {
                staticS:static {
                  stageMode
                  detector
                  mosPreImaging
                  nodAndShuffle {
                    ...nodAndShuffle
                  }
                }
                acquisitionS: acquisition {
                  ...gmosSouthExecutionSequence
                }
                scienceS:science {
                  ...gmosSouthExecutionSequence
                }
              }
            }
          }
        }
      }

      fragment northAtomFields on GmosNorthAtom {
        id
        observeClass
        steps {
          instrumentConfig {
            exposure {
              microseconds
            }
            readout {
              ...gmosCcdMode
            }
            dtax
            roi
            gratingConfig {
              grating
              order
              wavelength {
                picometers
              }
            }
            filter
            fpu {
              builtin
              customMask {
                filename
                slitWidth
              }
            }
          }
          id
          breakpoint
          stepConfig {
            ...stepConfig
          }
          observeClass
        }
      }

      fragment southAtomFields on GmosSouthAtom {
        id
        observeClass
        steps {
          instrumentConfig {
            exposure {
              microseconds
            }
            readout {
              ...gmosCcdMode
            }
            dtax
            roi
            gratingConfig {
              grating
              order
              wavelength {
                picometers
              }
            }
            filter
            fpu {
              builtin
              customMask {
                filename
                slitWidth
              }
            }
          }
          id
          breakpoint
          stepConfig {
            ...stepConfig
          }
          observeClass
        }
      }

      fragment offset on Offset {
        p {
          microarcseconds
        }
        q {
          microarcseconds
        }
      }

      fragment nodAndShuffle on GmosNodAndShuffle {
        posA {
          ...offset
        }
        posB {
          ...offset
        }
        eOffset
        shuffleOffset
        shuffleCycles
      }

      fragment gmosNorthExecutionSequence on GmosNorthExecutionSequence {
        nextAtom {
          ...northAtomFields
        }
        possibleFuture {
          ...northAtomFields
        }
      }

      fragment gmosSouthExecutionSequence on GmosSouthExecutionSequence {
        nextAtom {
          ...southAtomFields
        }
        possibleFuture {
          ...southAtomFields
        }
      }

      fragment gmosCcdMode on GmosCcdMode {
        xBin
        yBin
        ampCount
        ampGain
        ampReadMode
      }

      fragment gcal on Gcal {
        filter
        diffuser
        shutter
      }

      fragment stepConfig on StepConfig {
        stepType
        ... on Gcal {
          ...gcal
        }
        ... on Science {
          offset {
            ...offset
          }
        }
        ... on Bias {
        }
        ... on Dark {
        }
      }

    """

    object Data {
      object Observation {
        type ConstraintSet = model.ConstraintSet
        type TimingWindows = model.TimingWindow
        object Execution {
          object Config {
            object GmosNorthExecutionConfig {
              type StaticN = StaticConfig.GmosNorth

              type AcquisitionN = ExecutionSequence[DynamicConfig.GmosNorth]

              type ScienceN = ExecutionSequence[DynamicConfig.GmosNorth]
            }

            object GmosSouthExecutionConfig {
              type StaticS = StaticConfig.GmosSouth

              type AcquisitionS = ExecutionSequence[DynamicConfig.GmosSouth]

              type ScienceS = ExecutionSequence[DynamicConfig.GmosSouth]
            }
          }
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
