// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all._
import clue.TransactionalClient
import clue.data.Assign
import edu.gemini.seqexec.odb.SeqexecSequence
import eu.timepit.refined.types.numeric.PosInt
import lucuma.core.enums.{DatasetStage, SequenceCommand, StepStage}
import lucuma.core.model.Visit
import observe.common.ObsQueriesGQL
import observe.common.ObsQueriesGQL._
import org.typelevel.log4cats.Logger
import observe.model.{Observation, StepId}
import observe.model.dhs._
import lucuma.schemas.ObservationDB
import lucuma.schemas.ObservationDB.Enums.SequenceType
import lucuma.schemas.ObservationDB.Scalars.VisitId
import observe.common.ObsQueriesGQL.ObsQuery.Data
import observe.model.Observation.Id

import java.util.UUID

sealed trait OdbEventCommands[F[_]] {
  def datasetStart(
    visitId:               VisitId,
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    datasetIndex:          PosInt,
    fileId:                ImageFileId
  ): F[Boolean]
  def datasetComplete(
    visitId:               VisitId,
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    datasetIndex:          PosInt,
    fileId:                ImageFileId
  ): F[Boolean]
  def obsAbort(visitId:    VisitId, obsId: Observation.IdName, reason: String): F[Boolean]
  def sequenceEnd(visitId: VisitId, obsId: Observation.IdName): F[Boolean]
  def sequenceStart(obsId: Observation.IdName): F[Option[VisitId]]
  def obsContinue(visitId: VisitId, obsId: Observation.IdName): F[Boolean]
  def obsPause(visitId:    VisitId, obsId: Observation.IdName, reason: String): F[Boolean]
  def obsStop(visitId:     VisitId, obsId: Observation.IdName, reason: String): F[Boolean]
  def stepStartStep(
    visitId:               VisitId,
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    sequenceType:          SequenceType
  ): F[Boolean]
  def stepEndStep(
    visitId:               VisitId,
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    sequenceType:          SequenceType
  ): F[Boolean]
  def stepStartConfigure(
    visitId:               VisitId,
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    sequenceType:          SequenceType
  ): F[Boolean]
  def stepEndConfigure(
    visitId:               VisitId,
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    sequenceType:          SequenceType
  ): F[Boolean]
  def stepStartObserve(
    visitId:               VisitId,
    obsIdName:             Observation.IdName,
    stepId:                StepId,
    sequenceType:          SequenceType
  ): F[Boolean]
  def stepEndObserve(
    visitId:               VisitId,
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

      override def queuedSequences: F[List[Observation.Id]]                                     =
        ObsQueriesGQL.ActiveObservationIdsQuery
          .query()(client)
          .map(_.observations.matches.map(_.id))

      def datasetStart(
        visitId:      VisitId,
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        datasetIndex: PosInt,
        fileId:       ImageFileId
      ): F[Boolean] =
        evCmds.datasetStart(visitId, obsIdName, stepId, datasetIndex, fileId)
      def datasetComplete(
        visitId:      VisitId,
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        datasetIndex: PosInt,
        fileId:       ImageFileId
      ): F[Boolean] =
        evCmds.datasetComplete(visitId, obsIdName, stepId, datasetIndex, fileId)
      def obsAbort(visitId: VisitId, obsIdName: Observation.IdName, reason: String): F[Boolean] =
        evCmds.obsAbort(visitId, obsIdName, reason)
      def sequenceEnd(visitId: VisitId, obsIdName: Observation.IdName): F[Boolean]              =
        evCmds.sequenceEnd(visitId, obsIdName)
      def sequenceStart(obsIdName: Observation.IdName): F[Option[VisitId]]                      =
        evCmds.sequenceStart(obsIdName)
      def obsContinue(visitId: VisitId, obsIdName: Observation.IdName): F[Boolean]              =
        evCmds.obsContinue(visitId, obsIdName)
      def obsPause(visitId: VisitId, obsIdName: Observation.IdName, reason: String): F[Boolean] =
        evCmds.obsPause(visitId, obsIdName, reason)
      def obsStop(visitId: VisitId, obsIdName: Observation.IdName, reason: String): F[Boolean]  =
        evCmds.obsStop(visitId, obsIdName, reason)

      override def stepStartStep(
        visitId:      VisitId,
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepStartStep(visitId, obsIdName, stepId, sequenceType)

      override def stepEndStep(
        visitId:      VisitId,
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepEndStep(visitId, obsIdName, stepId, sequenceType)

      override def stepStartConfigure(
        visitId:      VisitId,
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepStartConfigure(visitId, obsIdName, stepId, sequenceType)

      override def stepEndConfigure(
        visitId:      VisitId,
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepEndConfigure(visitId, obsIdName, stepId, sequenceType)

      override def stepStartObserve(
        visitId:      VisitId,
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepStartObserve(visitId, obsIdName, stepId, sequenceType)

      override def stepEndObserve(
        visitId:      VisitId,
        obsIdName:    Observation.IdName,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepEndObserve(visitId, obsIdName, stepId, sequenceType)
    }

  final class DummyOdbCommands[F[_]: Applicative] extends OdbEventCommands[F] {
    override def datasetStart(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      datasetIndex: PosInt,
      fileId:       ImageFileId
    ): F[Boolean] = true.pure[F]
    override def datasetComplete(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      datasetIndex: PosInt,
      fileId:       ImageFileId
    ): F[Boolean] = true.pure[F]
    override def obsAbort(
      visitId:   VisitId,
      obsIdName: Observation.IdName,
      reason:    String
    ): F[Boolean] = false.pure[F]
    override def sequenceEnd(visitId: VisitId, obsIdName: Observation.IdName): F[Boolean] =
      false.pure[F]
    override def sequenceStart(obsIdName: Observation.IdName): F[Option[VisitId]]         =
      none[VisitId].pure[F]
    override def obsContinue(visitId: VisitId, obsIdName: Observation.IdName): F[Boolean] =
      false.pure[F]
    override def obsPause(
      visitId:   VisitId,
      obsIdName: Observation.IdName,
      reason:    String
    ): F[Boolean] = false.pure[F]
    override def obsStop(
      visitId:   VisitId,
      obsIdName: Observation.IdName,
      reason:    String
    ): F[Boolean] = false.pure[F]

    override def stepStartStep(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndStep(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepStartConfigure(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndConfigure(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepStartObserve(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndObserve(
      visitId:      VisitId,
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
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      datasetIndex: PosInt,
      fileId:       ImageFileId
    ): F[Boolean] =
      L.debug(
        s"Send ODB event datasetStart for obsId: ${obsIdName.name}, stepId: $stepId, datasetIndex: $datasetIndex, with fileId: $fileId"
      ) *>
        AddDatasetEventMutation
          .execute(
            visitId,
            obsId = obsIdName.id,
            stpId = stepId,
            dtIdx = datasetIndex,
            flName = Assign(normalizeFilename(fileId)),
            stg = DatasetStage.StartObserve
          )
          .as(true) <*
        L.debug("ODB event datasetStart sent")

    override def datasetComplete(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      datasetIndex: PosInt,
      fileId:       ImageFileId
    ): F[Boolean] =
      L.debug(
        s"Send ODB event datasetComplete for obsId: ${obsIdName.name} stepId: $stepId, datasetIndex: $datasetIndex, with fileId: $fileId"
      ) *>
        AddDatasetEventMutation
          .execute(
            vId = visitId,
            obsId = obsIdName.id,
            stpId = stepId,
            dtIdx = datasetIndex,
            flName = Assign(normalizeFilename(fileId)),
            stg = DatasetStage.EndObserve
          )
          .as(true) <*
        L.debug("ODB event datasetComplete sent")

    override def obsAbort(
      visitId:   VisitId,
      obsIdName: Observation.IdName,
      reason:    String
    ): F[Boolean] =
      L.debug(s"Send ODB event observationAbort for obsId: ${obsIdName.name}") *>
        AddSequenceEventMutation
          .execute(
            vId = visitId,
            obsId = obsIdName.id,
            cmd = SequenceCommand.Abort
          )
          .as(true) <*
        L.debug("ODB event observationAbort sent")

    override def sequenceEnd(visitId: VisitId, obsIdName: Observation.IdName): F[Boolean] =
      L.debug(s"Skipped sending ODB event sequenceEnd for obsId: ${obsIdName.name}")
        .as(true)

    override def sequenceStart(obsIdName: Observation.IdName): F[Option[VisitId]] = for {
      vid <- F.delay(UUID.randomUUID())
      _   <- L.debug(s"Send ODB event sequenceStart for obsId: ${obsIdName.name}")
      r   <-
        AddSequenceEventMutation
          .execute(vId = Visit.Id.fromUuid(vid), obsId = obsIdName.id, cmd = SequenceCommand.Start)
      _   <- L.debug("ODB event sequenceStart sent")
    } yield r.addSequenceEvent.event.visitId.some

    override def obsContinue(visitId: VisitId, obsIdName: Observation.IdName): F[Boolean] =
      L.debug(s"Send ODB event observationContinue for obsId: ${obsIdName.name}") *>
        AddSequenceEventMutation
          .execute(vId = visitId, obsId = obsIdName.id, cmd = SequenceCommand.Continue)
          .as(true) <*
        L.debug("ODB event observationContinue sent")

    override def obsPause(
      visitId:   VisitId,
      obsIdName: Observation.IdName,
      reason:    String
    ): F[Boolean] =
      L.debug(s"Send ODB event observationPause for obsId: ${obsIdName.name}") *>
        AddSequenceEventMutation
          .execute(vId = visitId, obsId = obsIdName.id, cmd = SequenceCommand.Pause)
          .as(true) <*
        L.debug("ODB event observationPause sent")

    override def obsStop(
      visitId:   VisitId,
      obsIdName: Observation.IdName,
      reason:    String
    ): F[Boolean] =
      L.debug(s"Send ODB event observationStop for obsId: ${obsIdName.name}") *>
        AddSequenceEventMutation
          .execute(vId = visitId, obsId = obsIdName.id, cmd = SequenceCommand.Stop)
          .as(true) <*
        L.debug("ODB event observationStop sent")

    override def stepStartStep(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepStartStep for obsId: ${obsIdName.name}"
      ) *>
        AddStepEventMutation
          .execute(vId = visitId,
                   obsId = obsIdName.id,
                   stpId = stepId,
                   stg = StepStage.StartStep,
                   seqType = sequenceType
          )
          .as(true) <*
        L.debug("ODB event stepStartStep sent")

    override def stepEndStep(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepEndStep for obsId: ${obsIdName.name}"
      ) *>
        AddStepEventMutation
          .execute(
            vId = visitId,
            obsId = obsIdName.id,
            stpId = stepId,
            stg = StepStage.EndStep,
            seqType = sequenceType
          )
          .as(true) <*
        L.debug("ODB event stepEndStep sent")

    override def stepStartConfigure(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepStartConfigure for obsId: ${obsIdName.name}"
      ) *>
        AddStepEventMutation
          .execute(
            vId = visitId,
            obsId = obsIdName.id,
            stpId = stepId,
            stg = StepStage.StartConfigure,
            seqType = sequenceType
          )
          .as(true) <*
        L.debug("ODB event stepStartConfigure sent")

    override def stepEndConfigure(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepEndConfigure for obsId: ${obsIdName.name}"
      ) *>
        AddStepEventMutation
          .execute(
            vId = visitId,
            obsId = obsIdName.id,
            stpId = stepId,
            stg = StepStage.EndConfigure,
            seqType = sequenceType
          )
          .as(true) <*
        L.debug("ODB event stepEndConfigure sent")

    override def stepStartObserve(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepStartObserve for obsId: ${obsIdName.name}"
      ) *>
        AddStepEventMutation
          .execute(
            vId = visitId,
            obsId = obsIdName.id,
            stpId = stepId,
            stg = StepStage.StartObserve,
            seqType = sequenceType
          )
          .as(true) <*
        L.debug("ODB event stepStartObserve sent")

    override def stepEndObserve(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepEndObserve for obsId: ${obsIdName.name}"
      ) *>
        AddStepEventMutation
          .execute(
            vId = visitId,
            obsId = obsIdName.id,
            stpId = stepId,
            stg = StepStage.EndObserve,
            seqType = sequenceType
          )
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
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      datasetIndex: PosInt,
      fileId:       ImageFileId
    ): F[Boolean] =
      evCmds.datasetStart(visitId, obsIdName, stepId, datasetIndex, fileId)
    override def datasetComplete(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      datasetIndex: PosInt,
      fileId:       ImageFileId
    ): F[Boolean] =
      evCmds.datasetComplete(visitId, obsIdName, stepId, datasetIndex, fileId)
    override def obsAbort(
      visitId:   VisitId,
      obsIdName: Observation.IdName,
      reason:    String
    ): F[Boolean] =
      evCmds.obsAbort(visitId, obsIdName, reason)
    override def sequenceEnd(visitId: VisitId, obsIdName: Observation.IdName): F[Boolean] =
      evCmds.sequenceEnd(visitId, obsIdName)
    override def sequenceStart(obsIdName: Observation.IdName): F[Option[VisitId]]         =
      evCmds.sequenceStart(obsIdName)
    override def obsContinue(visitId: VisitId, obsIdName: Observation.IdName): F[Boolean] =
      evCmds.obsContinue(visitId, obsIdName)
    override def obsPause(
      visitId:   VisitId,
      obsIdName: Observation.IdName,
      reason:    String
    ): F[Boolean] =
      evCmds.obsPause(visitId, obsIdName, reason)
    override def obsStop(
      visitId:   VisitId,
      obsIdName: Observation.IdName,
      reason:    String
    ): F[Boolean] =
      evCmds.obsStop(visitId, obsIdName, reason)

    override def stepStartStep(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndStep(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepStartConfigure(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndConfigure(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepStartObserve(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndObserve(
      visitId:      VisitId,
      obsIdName:    Observation.IdName,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]
  }

}
