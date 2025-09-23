// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.common

import clue.GraphQLOperation
import clue.annotation.GraphQL
import lucuma.schemas.ObservationDB

// gql: import lucuma.odb.json.all.query.given

object EventsGQL:

  @GraphQL
  trait AddSequenceEventMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($vId: VisitId!, $cmd: SequenceCommand!, $idempotencyKey: IdempotencyKey!) {
        addSequenceEvent(input: { visitId: $vId, command: $cmd, idempotencyKey: $idempotencyKey } ) {
          event { received }
        }
      }
      """

  @GraphQL
  trait AddStepEventMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($stepId: StepId!, $stg: StepStage!, $idempotencyKey: IdempotencyKey!)  {
        addStepEvent(input: { stepId: $stepId, stepStage: $stg, idempotencyKey: $idempotencyKey } ) {
          event { id }
        }
      }
      """

  @GraphQL
  trait AddDatasetEventMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($datasetId: DatasetId!, $stg: DatasetStage!, $idempotencyKey: IdempotencyKey!)  {
        addDatasetEvent(input: { datasetId: $datasetId, datasetStage: $stg, idempotencyKey: $idempotencyKey } ) {
          event { id }
        }
      }
      """

  @GraphQL
  trait RecordDatasetMutation extends GraphQLOperation[ObservationDB]:
    // val document = """
    //   mutation($stepId: StepId!, $filename: DatasetFilename!, $idempotencyKey: IdempotencyKey!) {
    //     recordDataset(input: { stepId: $stepId, filename: $filename, idempotencyKey: $idempotencyKey } ) {
    //       dataset {
    //         id
    //         reference {
    //           label
    //           observation { label }
    //         }
    //       }
    //     }
    //   }
    //   """
    val document = """
      mutation($stepId: StepId!, $filename: DatasetFilename!) {
        recordDataset(input: { stepId: $stepId, filename: $filename } ) {
          dataset {
            id
            reference {
              label
              observation { label }
            }
          }
        }
      }
      """

  @GraphQL
  trait RecordAtomMutation extends GraphQLOperation[ObservationDB]:
    // val document = """
    //   mutation($input: RecordAtomInput!, $idempotencyKey: IdempotencyKey!) {
    //     recordAtom(input: $input, idempotencyKey: $idempotencyKey) {
    //       atomRecord { id }
    //     }
    //   }
    //   """
    val document = """
      mutation($input: RecordAtomInput!) {
        recordAtom(input: $input) {
          atomRecord { id }
        }
      }
      """

  @GraphQL
  trait RecordGmosNorthStepMutation extends GraphQLOperation[ObservationDB]:
    // val document = """
    //   mutation($input: RecordGmosNorthStepInput!, $idempotencyKey: IdempotencyKey!) {
    //     recordGmosNorthStep(input: $input, idempotencyKey: $idempotencyKey) {
    //       stepRecord { id }
    //     }
    //   }
    //   """
    val document = """
      mutation($input: RecordGmosNorthStepInput!) {
        recordGmosNorthStep(input: $input) {
          stepRecord { id }
        }
      }
      """

  @GraphQL
  trait RecordGmosNorthVisitMutation extends GraphQLOperation[ObservationDB]:
    // val document = """
    //   mutation($obsId: ObservationId!, $staticCfg: GmosNorthStaticInput!, $idempotencyKey: IdempotencyKey!) {
    //     recordGmosNorthVisit(input: { observationId: $obsId, gmosNorth: $staticCfg, idempotencyKey: $idempotencyKey } ) {
    //       visit { id }
    //     }
    //   }
    //   """
    val document = """
      mutation($obsId: ObservationId!, $staticCfg: GmosNorthStaticInput!) {
        recordGmosNorthVisit(input: { observationId: $obsId, gmosNorth: $staticCfg } ) {
          visit { id }
        }
      }
      """

  @GraphQL
  trait RecordGmosSouthStepMutation  extends GraphQLOperation[ObservationDB]:
    // val document = """
    //   mutation($input: RecordGmosSouthStepInput!, $idempotencyKey: IdempotencyKey!) {
    //     recordGmosSouthStep(input: $input, idempotencyKey: $idempotencyKey) {
    //       stepRecord { id }
    //     }
    //   }
    //   """
    val document = """
      mutation($input: RecordGmosSouthStepInput!) {
        recordGmosSouthStep(input: $input) {
          stepRecord { id }
        }
      }
      """
  @GraphQL
  trait RecordGmosSouthVisitMutation extends GraphQLOperation[ObservationDB]:
    // val document = """
    //   mutation($obsId: ObservationId!, $staticCfg: GmosSouthStaticInput!, $idempotencyKey: IdempotencyKey!) {
    //     recordGmosSouthVisit(input: { observationId: $obsId, gmosSouth: $staticCfg, idempotencyKey: $idempotencyKey } ) {
    //       visit { id }
    //     }
    //   }
    //   """
    val document = """
      mutation($obsId: ObservationId!, $staticCfg: GmosSouthStaticInput!) {
        recordGmosSouthVisit(input: { observationId: $obsId, gmosSouth: $staticCfg } ) {
          visit { id }
        }
      }
      """

  @GraphQL
  trait RecordFlamingos2StepMutation extends GraphQLOperation[ObservationDB]:
    // val document = """
    //   mutation($input: RecordFlamingos2StepInput!, $idempotencyKey: IdempotencyKey!) {
    //     recordFlamingos2Step(input: $input, idempotencyKey: $idempotencyKey) {
    //       stepRecord { id }
    //     }
    //   }
    //   """
    val document = """
      mutation($input: RecordFlamingos2StepInput!) {
        recordFlamingos2Step(input: $input) {
          stepRecord { id }
        }
      }
      """

  @GraphQL
  trait RecordFlamingos2VisitMutation extends GraphQLOperation[ObservationDB]:
    // val document =
    //   """
    //   mutation($obsId: ObservationId!, $staticCfg: Flamingos2StaticInput!, $idempotencyKey: IdempotencyKey!) {
    //     recordFlamingos2Visit(input: { observationId: $obsId, flamingos2: $staticCfg, idempotencyKey: $idempotencyKey } ) {
    //       visit { id }
    //     }
    //   }
    //   """
    val document =
      """
      mutation($obsId: ObservationId!, $staticCfg: Flamingos2StaticInput!) {
        recordFlamingos2Visit(input: { observationId: $obsId, flamingos2: $staticCfg } ) {
          visit { id }
        }
      }
      """
