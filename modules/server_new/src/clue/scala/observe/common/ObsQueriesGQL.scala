// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.common

import clue.GraphQLOperation
import clue.annotation.GraphQL
import lucuma.schemas.ObservationDB
import lucuma.core.model
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.schemas.odb.Flamingos2DynamicConfigSubquery
import lucuma.schemas.odb.GmosSouthDynamicConfigSubquery
import lucuma.schemas.odb.GmosNorthDynamicConfigSubquery

// gql: import io.circe.refined.*
// gql: import lucuma.schemas.decoders.given
// gql: import lucuma.odb.json.all.query.given

object ObsQueriesGQL:

  @GraphQL
  trait ObsQuery                 extends GraphQLOperation[ObservationDB]:
    val document = s"""
      query($$obsId: ObservationId!) {
        observation(observationId: $$obsId) {
          id
          title
          program {
            id
            name
            goa { proprietaryMonths }
          }
          targetEnvironment {
            firstScienceTarget {
              targetId: id
              targetName: name
            }
            guideEnvironment {
              guideTargets { probe }
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
                  period { milliseconds }
                  times
                }
              }
            }
          }
          itc { ...itcFields }
        }

        executionConfig(observationId: $$obsId, futureLimit: 100) {
          instrument
          gmosNorth {
            static {
              stageMode
              detector
              mosPreImaging
              nodAndShuffle { ...nodAndShuffleFields }
            }
            acquisition { ...gmosNorthSequenceFields }
            science { ...gmosNorthSequenceFields }
          }
          gmosSouth {
            static {
              stageMode
              detector
              mosPreImaging
              nodAndShuffle { ...nodAndShuffleFields }
            }
            acquisition { ...gmosSouthSequenceFields }
            science { ...gmosSouthSequenceFields }
          }
          flamingos2 {
            static {
              mosPreImaging
              useElectronicOffsetting
            }
            acquisition { ...flamingos2SequenceFields }
            science { ...flamingos2SequenceFields }
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
          instrumentConfig $GmosNorthDynamicConfigSubquery
          stepConfig { ...stepConfigFields }
          telescopeConfig { ...telescopeConfigFields }
          estimate { ...stepEstimateFields }
          observeClass
          breakpoint
        }
      }

      fragment gmosNorthSequenceFields on GmosNorthExecutionSequence {
        nextAtom { ...gmosNorthAtomFields }
        possibleFuture { ...gmosNorthAtomFields }
        hasMore
      }

      fragment gmosSouthAtomFields on GmosSouthAtom {
        id
        description
        steps {
          id
          instrumentConfig $GmosSouthDynamicConfigSubquery
          stepConfig { ...stepConfigFields }
          telescopeConfig { ...telescopeConfigFields }
          estimate { ...stepEstimateFields }
          observeClass
          breakpoint
        }
      }

      fragment gmosSouthSequenceFields on GmosSouthExecutionSequence {
        nextAtom { ...gmosSouthAtomFields }
        possibleFuture { ...gmosSouthAtomFields }
        hasMore
      }

      fragment flamingos2AtomFields on Flamingos2Atom {
        id
        description
        steps {
          id
          instrumentConfig $Flamingos2DynamicConfigSubquery
          stepConfig { ...stepConfigFields }
          telescopeConfig {
            offset { ...offsetFields }
            guiding
          }
          estimate { ...stepEstimateFields }
          observeClass
          breakpoint
        }
      }

      fragment flamingos2SequenceFields on Flamingos2ExecutionSequence {
        nextAtom { ...flamingos2AtomFields }
        possibleFuture { ...flamingos2AtomFields }
        hasMore
      }      

      fragment offsetFields on Offset {
        p { microarcseconds }
        q { microarcseconds }
      }

      fragment itcFields on Itc {
          acquisition {
            selected {
              signalToNoiseAt { single }
            }
          }
          science {
            selected {
              signalToNoiseAt { single }
            }
          }
        }
    """

    object Data:
      object Observation:
        type ConstraintSet = model.ConstraintSet
        type TimingWindows = model.TimingWindow
      type ExecutionConfig = InstrumentExecutionConfig
  @GraphQL
  trait ResetAcquisitionMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($obsId: ObservationId!) {
        resetAcquisition(input: { observationId: $obsId } ) {
          observation { id }
        }
      }
      """

  @GraphQL
  trait ObsEditSubscription extends GraphQLOperation[ObservationDB]:
    val document = """
      subscription($input: ObservationEditInput!) {
        observationEdit(input: $input) {
          observationId
        }
      }
    """
