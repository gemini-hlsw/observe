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
      mutation($vId: VisitId!, $cmd: SequenceCommand!, $clientId: ClientId!) {
        addSequenceEvent(input: { visitId: $vId, command: $cmd, clientId: $clientId } ) {
          event { received }
        }
      }
      """

  @GraphQL
  trait AddStepEventMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($stepId: StepId!, $stg: StepStage!, $clientId: ClientId!)  {
        addStepEvent(input: { stepId: $stepId, stepStage: $stg, clientId: $clientId } ) {
          event { id }
        }
      }
      """

  @GraphQL
  trait AddDatasetEventMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($datasetId: DatasetId!, $stg: DatasetStage!, $clientId: ClientId!)  {
        addDatasetEvent(input: { datasetId: $datasetId, datasetStage: $stg, clientId: $clientId } ) {
          event { id }
        }
      }
      """

  @GraphQL
  trait RecordDatasetMutation extends GraphQLOperation[ObservationDB]:
    // val document = """
    //   mutation($stepId: StepId!, $filename: DatasetFilename!, $clientId: ClientId!) {
    //     recordDataset(input: { stepId: $stepId, filename: $filename, clientId: $clientId } ) {
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
    //   mutation($input: RecordAtomInput!, $clientId: ClientId!) {
    //     recordAtom(input: $input, clientId: $clientId) {
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
    val document = """
      mutation($input: RecordGmosNorthStepInput!, $clientId: ClientId!) {
        recordGmosNorthStep(input: $input, clientId: $clientId) {
          stepRecord { id }
        }
      }
      """

  @GraphQL
  trait RecordGmosNorthVisitMutation extends GraphQLOperation[ObservationDB]:
    // val document = """
    //   mutation($obsId: ObservationId!, $staticCfg: GmosNorthStaticInput!, $clientId: ClientId!) {
    //     recordGmosNorthVisit(input: { observationId: $obsId, gmosNorth: $staticCfg, clientId: $clientId } ) {
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
    //   mutation($input: RecordGmosSouthStepInput!, $clientId: ClientId!) {
    //     recordGmosSouthStep(input: $input, clientId: $clientId) {
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
    //   mutation($obsId: ObservationId!, $staticCfg: GmosSouthStaticInput!, $clientId: ClientId!) {
    //     recordGmosSouthVisit(input: { observationId: $obsId, gmosSouth: $staticCfg, clientId: $clientId } ) {
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
    val document = """
      mutation($input: RecordFlamingos2StepInput!, $clientId: ClientId!) {
        recordFlamingos2Step(input: $input, clientId: $clientId) {
          stepRecord { id }
        }
      }
      """

  @GraphQL
  trait RecordFlamingos2VisitMutation extends GraphQLOperation[ObservationDB]:
    // val document =
    //   """
    //   mutation($obsId: ObservationId!, $staticCfg: Flamingos2StaticInput!, $clientId: ClientId!) {
    //     recordFlamingos2Visit(input: { observationId: $obsId, flamingos2: $staticCfg, clientId: $clientId } ) {
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
