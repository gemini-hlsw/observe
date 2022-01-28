// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all._
import clue.TransactionalClient
import clue.data.Assign
import edu.gemini.seqexec.odb.SeqexecSequence
import lucuma.core.`enum`.{ DatasetStage, SequenceCommand, StepStage }
import observe.common.ObsQueriesGQL
import observe.common.ObsQueriesGQL._
import org.typelevel.log4cats.Logger
import observe.model.{ Observation, StepId }
import observe.model.dhs._
import lucuma.schemas.ObservationDB
import lucuma.schemas.ObservationDB.Enums.SequenceType
import observe.common.ObsQueriesGQL.ObsQuery.Data
import observe.model.Observation.Id

import java.time.Instant

sealed trait OdbEventCommands[F[_]] {
  def datasetStart(
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    datasetIndex:          Int,
    fileId:                ImageFileId
  ): F[Boolean]
  def datasetComplete(
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    datasetIndex:          Int,
    fileId:                ImageFileId
  ): F[Boolean]
  def obsAbort(obsId:      Observation.IdName, reason: String): F[Boolean]
  def sequenceEnd(obsId:   Observation.IdName): F[Boolean]
  def sequenceStart(obsId: Observation.IdName): F[Boolean]
  def obsContinue(obsId:   Observation.IdName): F[Boolean]
  def obsPause(obsId:      Observation.IdName, reason: String): F[Boolean]
  def obsStop(obsId:       Observation.IdName, reason: String): F[Boolean]
  def stepStartStep(
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    sequenceType:          SequenceType
  ): F[Boolean]
  def stepEndStep(
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    sequenceType:          SequenceType
  ): F[Boolean]
  def stepStartConfigure(
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    sequenceType:          SequenceType
  ): F[Boolean]
  def stepEndConfigure(
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    sequenceType:          SequenceType
  ): F[Boolean]
  def stepStartObserve(
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    sequenceType:          SequenceType
  ): F[Boolean]
  def stepEndObserve(
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    sequenceType:          SequenceType
  ): F[Boolean]
}

sealed trait OdbProxy[F[_]] extends OdbEventCommands[F] {
  def read(oid: Observation.Id): F[ObsQuery.Data.Observation]
  def queuedSequences: F[List[Observation.Id]]
}

object OdbProxy {
  def apply[F[_]: Sync](
    client: TransactionalClient[F, ObservationDB],
    evCmds: OdbEventCommands[F]
  ): OdbProxy[F] =
    new OdbProxy[F] {
      def read(oid: Observation.Id): F[ObsQuery.Data.Observation] =
        ObsQueriesGQL.ObsQuery
          .query(oid)(client)
          .flatMap(
            _.observation.fold(
              Sync[F].raiseError[ObsQuery.Data.Observation](
                ObserveFailure.Unexpected(s"OdbProxy: Unable to read observation $oid")
              )
            )(
              _.pure[F]
            )
          )

      override def queuedSequences: F[List[Observation.Id]]                   =
        ObsQueriesGQL.ActiveObservationIdsQuery.query()(client).map(_.observations.nodes.map(_.id))

      def datasetStart(
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        datasetIndex: Int,
        fileId:       ImageFileId
      ): F[Boolean] =
        evCmds.datasetStart(obsIdName, stepId, datasetIndex, fileId)
      def datasetComplete(
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        datasetIndex: Int,
        fileId:       ImageFileId
      ): F[Boolean] =
        evCmds.datasetComplete(obsIdName, stepId, datasetIndex, fileId)
      def obsAbort(obsIdName: Observation.IdName, reason: String): F[Boolean] =
        evCmds.obsAbort(obsIdName, reason)
      def sequenceEnd(obsIdName: Observation.IdName): F[Boolean]              = evCmds.sequenceEnd(obsIdName)
      def sequenceStart(obsIdName: Observation.IdName): F[Boolean]            = evCmds.sequenceStart(obsIdName)
      def obsContinue(obsIdName: Observation.IdName): F[Boolean]              = evCmds.obsContinue(obsIdName)
      def obsPause(obsIdName: Observation.IdName, reason: String): F[Boolean] =
        evCmds.obsPause(obsIdName, reason)
      def obsStop(obsIdName: Observation.IdName, reason: String): F[Boolean]  =
        evCmds.obsStop(obsIdName, reason)

      override def stepStartStep(
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepStartStep(obsIdName, stepId, sequenceType)

      override def stepEndStep(
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepEndStep(obsIdName, stepId, sequenceType)

      override def stepStartConfigure(
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepStartConfigure(obsIdName, stepId, sequenceType)

      override def stepEndConfigure(
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepEndConfigure(obsIdName, stepId, sequenceType)

      override def stepStartObserve(
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepStartObserve(obsIdName, stepId, sequenceType)

      override def stepEndObserve(
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepEndObserve(obsIdName, stepId, sequenceType)
    }

  final class DummyOdbCommands[F[_]: Applicative] extends OdbEventCommands[F] {
    override def datasetStart(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      datasetIndex: Int,
      fileId:       ImageFileId
    ): F[Boolean] = true.pure[F]
    override def datasetComplete(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      datasetIndex: Int,
      fileId:       ImageFileId
    ): F[Boolean] = true.pure[F]
    override def obsAbort(obsIdName: Observation.IdName, reason: String): F[Boolean] = false.pure[F]
    override def sequenceEnd(obsIdName: Observation.IdName): F[Boolean]              = false.pure[F]
    override def sequenceStart(obsIdName: Observation.IdName): F[Boolean]            = false.pure[F]
    override def obsContinue(obsIdName: Observation.IdName): F[Boolean]              = false.pure[F]
    override def obsPause(obsIdName: Observation.IdName, reason: String): F[Boolean] = false.pure[F]
    override def obsStop(obsIdName: Observation.IdName, reason: String): F[Boolean]  = false.pure[F]

    override def stepStartStep(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndStep(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepStartConfigure(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndConfigure(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepStartObserve(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndObserve(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]
  }

  implicit class SeqexecSequenceOps(val s: SeqexecSequence) extends AnyVal {
    def stepsCount: Int          = Option(s.config.getAllSteps).foldMap(_.length)
    def executedCount: Int       = s.datasets.size
    def unExecutedSteps: Boolean = stepsCount =!= executedCount
  }

  final case class OdbCommandsImpl[F[_]](client: TransactionalClient[F, ObservationDB])(implicit
    val F:                                       Sync[F],
    L:                                           Logger[F]
  ) extends OdbEventCommands[F] {

    implicit val cl: TransactionalClient[F, ObservationDB] = client

    private val fitsFileExtension                           = ".fits"
    private def normalizeFilename(fileName: String): String = if (
      fileName.endsWith(fitsFileExtension)
    ) fileName
    else fileName + fitsFileExtension

    override def datasetStart(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      datasetIndex: Int,
      fileId:       ImageFileId
    ): F[Boolean] =
      L.debug(
        s"Send ODB event datasetStart for obsId: ${obsIdName.name}, stepId: $stepId, datasetIndex: $datasetIndex, with fileId: $fileId"
      ) *> Sync[F]
        .delay(Instant.now())
        .flatMap { t =>
          AddDatasetEventMutation.execute(
            obsId = obsIdName.id,
            t = t,
            stpId = stepId,
            dtIdx = datasetIndex,
            flName = Assign(normalizeFilename(fileId)),
            stg = DatasetStage.StartObserve
          )
        }
        .as(true) <*
        L.debug("ODB event datasetStart sent")

    override def datasetComplete(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      datasetIndex: Int,
      fileId:       ImageFileId
    ): F[Boolean] =
      L.debug(
        s"Send ODB event datasetComplete for obsId: ${obsIdName.name} stepId: $stepId, datasetIndex: $datasetIndex, with fileId: $fileId"
      ) *>
        Sync[F]
          .delay(Instant.now())
          .flatMap { t =>
            AddDatasetEventMutation.execute(
              obsId = obsIdName.id,
              t = t,
              stpId = stepId,
              dtIdx = datasetIndex,
              flName = Assign(normalizeFilename(fileId)),
              stg = DatasetStage.EndObserve
            )
          }
          .as(true) <*
        L.debug("ODB event datasetComplete sent")

    override def obsAbort(obsIdName: Observation.IdName, reason: String): F[Boolean] =
      L.debug(s"Send ODB event observationAbort for obsId: ${obsIdName.name}") *>
        Sync[F]
          .delay(Instant.now())
          .flatMap { t =>
            AddSequenceEventMutation.execute(
              obsId = obsIdName.id,
              t = t,
              cmd = SequenceCommand.Abort
            )
          }
          .as(true) <*
        L.debug("ODB event observationAbort sent")

    override def sequenceEnd(obsIdName: Observation.IdName): F[Boolean] =
      L.debug(s"Skipped sending ODB event sequenceEnd for obsId: ${obsIdName.name}")
        .as(true)

    override def sequenceStart(obsIdName: Observation.IdName): F[Boolean] =
      L.debug(
        s"Send ODB event sequenceStart for obsId: ${obsIdName.name}"
      ) *>
        Sync[F]
          .delay(Instant.now())
          .flatMap { t =>
            AddSequenceEventMutation.execute(obsId = obsIdName.id,
                                             t = t,
                                             cmd = SequenceCommand.Start
            )
          }
          .as(true) <*
        L.debug("ODB event sequenceStart sent")

    override def obsContinue(obsIdName: Observation.IdName): F[Boolean] =
      L.debug(s"Send ODB event observationContinue for obsId: ${obsIdName.name}") *>
        Sync[F]
          .delay(Instant.now())
          .flatMap { t =>
            AddSequenceEventMutation.execute(obsId = obsIdName.id,
                                             t = t,
                                             cmd = SequenceCommand.Continue
            )
          }
          .as(true) <*
        L.debug("ODB event observationContinue sent")

    override def obsPause(obsIdName: Observation.IdName, reason: String): F[Boolean] =
      L.debug(s"Send ODB event observationPause for obsId: ${obsIdName.name}") *>
        Sync[F]
          .delay(Instant.now())
          .flatMap { t =>
            AddSequenceEventMutation.execute(obsId = obsIdName.id,
                                             t = t,
                                             cmd = SequenceCommand.Pause
            )
          }
          .as(true) <*
        L.debug("ODB event observationPause sent")

    override def obsStop(obsIdName: Observation.IdName, reason: String): F[Boolean] =
      L.debug(s"Send ODB event observationStop for obsId: ${obsIdName.name}") *>
        Sync[F]
          .delay(Instant.now())
          .flatMap { t =>
            AddSequenceEventMutation.execute(obsId = obsIdName.id,
                                             t = t,
                                             cmd = SequenceCommand.Stop
            )
          }
          .as(true) <*
        L.debug("ODB event observationStop sent")

    override def stepStartStep(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepStartStep for obsId: ${obsIdName.name}"
      ) *>
        Sync[F]
          .delay(Instant.now())
          .flatMap { t =>
            AddStepEventMutation.execute(
              obsId = obsIdName.id,
              t = t,
              stpId = stepId,
              stg = StepStage.StartStep,
              seqType = sequenceType
            )
          }
          .as(true) <*
        L.debug("ODB event stepStartStep sent")

    override def stepEndStep(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepEndStep for obsId: ${obsIdName.name}"
      ) *>
        Sync[F]
          .delay(Instant.now())
          .flatMap { t =>
            AddStepEventMutation.execute(
              obsId = obsIdName.id,
              t = t,
              stpId = stepId,
              stg = StepStage.EndStep,
              seqType = sequenceType
            )
          }
          .as(true) <*
        L.debug("ODB event stepEndStep sent")

    override def stepStartConfigure(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepStartConfigure for obsId: ${obsIdName.name}"
      ) *>
        Sync[F]
          .delay(Instant.now())
          .flatMap { t =>
            AddStepEventMutation.execute(
              obsId = obsIdName.id,
              t = t,
              stpId = stepId,
              stg = StepStage.StartConfigure,
              seqType = sequenceType
            )
          }
          .as(true) <*
        L.debug("ODB event stepStartConfigure sent")

    override def stepEndConfigure(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepEndConfigure for obsId: ${obsIdName.name}"
      ) *>
        Sync[F]
          .delay(Instant.now())
          .flatMap { t =>
            AddStepEventMutation.execute(
              obsId = obsIdName.id,
              t = t,
              stpId = stepId,
              stg = StepStage.EndConfigure,
              seqType = sequenceType
            )
          }
          .as(true) <*
        L.debug("ODB event stepEndConfigure sent")

    override def stepStartObserve(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepStartObserve for obsId: ${obsIdName.name}"
      ) *>
        Sync[F]
          .delay(Instant.now())
          .flatMap { t =>
            AddStepEventMutation.execute(
              obsId = obsIdName.id,
              t = t,
              stpId = stepId,
              stg = StepStage.StartObserve,
              seqType = sequenceType
            )
          }
          .as(true) <*
        L.debug("ODB event stepStartObserve sent")

    override def stepEndObserve(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepEndObserve for obsId: ${obsIdName.name}"
      ) *>
        Sync[F]
          .delay(Instant.now())
          .flatMap { t =>
            AddStepEventMutation.execute(
              obsId = obsIdName.id,
              t = t,
              stpId = stepId,
              stg = StepStage.EndObserve,
              seqType = sequenceType
            )
          }
          .as(true) <*
        L.debug("ODB event stepEndObserve sent")
  }

  final class TestOdbProxy[F[_]: Sync] extends OdbProxy[F] {
    val evCmds = new DummyOdbCommands[F]

    override def read(oid: Id): F[Data.Observation] = Sync[F]
      .raiseError(
        ObserveFailure.Unexpected("TestOdbProxy.read: Not implemented.")
      )

    override def queuedSequences: F[List[Id]] = List.empty[Id].pure[F]

    override def datasetStart(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      datasetIndex: Int,
      fileId:       ImageFileId
    ): F[Boolean] =
      evCmds.datasetStart(obsIdName, stepId, datasetIndex, fileId)
    override def datasetComplete(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      datasetIndex: Int,
      fileId:       ImageFileId
    ): F[Boolean] =
      evCmds.datasetComplete(obsIdName, stepId, datasetIndex, fileId)
    override def obsAbort(obsIdName: Observation.IdName, reason: String): F[Boolean] =
      evCmds.obsAbort(obsIdName, reason)
    override def sequenceEnd(obsIdName: Observation.IdName): F[Boolean]              =
      evCmds.sequenceEnd(obsIdName)
    override def sequenceStart(obsIdName: Observation.IdName): F[Boolean]            =
      evCmds.sequenceStart(obsIdName)
    override def obsContinue(obsIdName: Observation.IdName): F[Boolean]              =
      evCmds.obsContinue(obsIdName)
    override def obsPause(obsIdName: Observation.IdName, reason: String): F[Boolean] =
      evCmds.obsPause(obsIdName, reason)
    override def obsStop(obsIdName: Observation.IdName, reason: String): F[Boolean]  =
      evCmds.obsStop(obsIdName, reason)

    override def stepStartStep(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndStep(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepStartConfigure(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndConfigure(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepStartObserve(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndObserve(
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]
  }

}
