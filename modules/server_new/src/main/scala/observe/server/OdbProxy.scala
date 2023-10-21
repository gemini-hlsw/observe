// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Applicative
import cats.MonadThrow
import cats.effect.Sync
import cats.syntax.all.*
import clue.ClientAppliedF.*
import clue.FetchClient
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.enums.DatasetStage
import lucuma.core.enums.SequenceCommand
import lucuma.core.enums.StepStage
import lucuma.core.model.Observation
import lucuma.core.model.Visit
import lucuma.core.model.sequence.Dataset
import lucuma.core.model.sequence.gmos.StaticConfig
import lucuma.schemas.ObservationDB
import lucuma.schemas.ObservationDB.Scalars.DatasetId
import lucuma.schemas.ObservationDB.Scalars.VisitId
import observe.common.ObsQueriesGQL.*
import observe.model.StepId
import observe.model.dhs.*
import observe.server.ObsQueryInput.*
import observe.server.given
import org.typelevel.log4cats.Logger

sealed trait OdbEventCommands[F[_]] {
  def datasetStart(
    obsId:  Observation.Id,
    stepId: StepId,
    fileId: ImageFileId
  ): F[Boolean]
  def datasetComplete(
    datasetId: DatasetId,
    obsId:     Observation.Id,
    fileId:    ImageFileId
  ): F[Boolean]
  def obsAbort(visitId:    VisitId, obsId:            Observation.Id, reason: String): F[Boolean]
  def sequenceEnd(visitId: VisitId, obsId:            Observation.Id): F[Boolean]
  def sequenceStart(obsId: Observation.Id, staticCfg: StaticConfig): F[VisitId]
  def obsContinue(visitId: VisitId, obsId:            Observation.Id): F[Boolean]
  def obsPause(visitId:    VisitId, obsId:            Observation.Id, reason: String): F[Boolean]
  def obsStop(visitId:     VisitId, obsId:            Observation.Id, reason: String): F[Boolean]
  def stepStartStep(
    obsId:  Observation.Id,
    stepId: StepId
  ): F[Boolean]
  def stepEndStep(
    obsId:  Observation.Id,
    stepId: StepId
  ): F[Boolean]
  def stepStartConfigure(
    obsId:  Observation.Id,
    stepId: StepId
  ): F[Boolean]
  def stepEndConfigure(
    obsId:  Observation.Id,
    stepId: StepId
  ): F[Boolean]
  def stepStartObserve(
    obsId:  Observation.Id,
    stepId: StepId
  ): F[Boolean]
  def stepEndObserve(
    obsId:  Observation.Id,
    stepId: StepId
  ): F[Boolean]
}

sealed trait OdbProxy[F[_]] extends OdbEventCommands[F] {
  def read(oid: Observation.Id): F[ObsQuery.Data.Observation]
  def queuedSequences: F[List[Observation.Id]]
}

object OdbProxy {
  def apply[F[_]: Sync](
    evCmds: OdbEventCommands[F]
  )(using client: FetchClient[F, ObservationDB]): OdbProxy[F] =
    new OdbProxy[F] {

      def read(oid: Observation.Id): F[ObsQuery.Data.Observation] =
        ObsQuery[F]
          .query(oid)
          .flatMap(
            _.observation.fold(
              Sync[F].raiseError[ObsQuery.Data.Observation](
                ObserveFailure.Unexpected(s"OdbProxy: Unable to read observation $oid")
              )
            )(
              _.pure[F]
            )
          )

      override def queuedSequences: F[List[Observation.Id]]                             =
        ActiveObservationIdsQuery[F]
          .query()
          .map(_.observations.matches.map(_.id))

      def datasetStart(
        obsId:  Observation.Id,
        stepId: StepId,
        fileId: ImageFileId
      ): F[Boolean] =
        evCmds.datasetStart(obsId, stepId, fileId)
      def datasetComplete(
        datasetId: DatasetId,
        obsId:     Observation.Id,
        fileId:    ImageFileId
      ): F[Boolean] =
        evCmds.datasetComplete(datasetId, obsId, fileId)
      def obsAbort(visitId: VisitId, obsId: Observation.Id, reason: String): F[Boolean] =
        evCmds.obsAbort(visitId, obsId, reason)
      def sequenceEnd(visitId: VisitId, obsId: Observation.Id): F[Boolean]              =
        evCmds.sequenceEnd(visitId, obsId)
      def sequenceStart(obsId: Observation.Id, staticCfg: StaticConfig): F[VisitId]     =
        evCmds.sequenceStart(obsId, staticCfg)
      def obsContinue(visitId: VisitId, obsId: Observation.Id): F[Boolean]              =
        evCmds.obsContinue(visitId, obsId)
      def obsPause(visitId: VisitId, obsId: Observation.Id, reason: String): F[Boolean] =
        evCmds.obsPause(visitId, obsId, reason)
      def obsStop(visitId: VisitId, obsId: Observation.Id, reason: String): F[Boolean]  =
        evCmds.obsStop(visitId, obsId, reason)

      override def stepStartStep(
        obsId:  Observation.Id,
        stepId: StepId
      ): F[Boolean] = evCmds.stepStartStep(obsId, stepId)

      override def stepEndStep(
        obsId:  Observation.Id,
        stepId: StepId
      ): F[Boolean] = evCmds.stepEndStep(obsId, stepId)

      override def stepStartConfigure(
        obsId:  Observation.Id,
        stepId: StepId
      ): F[Boolean] = evCmds.stepStartConfigure(obsId, stepId)

      override def stepEndConfigure(
        obsId:  Observation.Id,
        stepId: StepId
      ): F[Boolean] = evCmds.stepEndConfigure(obsId, stepId)

      override def stepStartObserve(
        obsId:  Observation.Id,
        stepId: StepId
      ): F[Boolean] = evCmds.stepStartObserve(obsId, stepId)

      override def stepEndObserve(
        obsId:  Observation.Id,
        stepId: StepId
      ): F[Boolean] = evCmds.stepEndObserve(obsId, stepId)
    }

  class DummyOdbCommands[F[_]: Applicative] extends OdbEventCommands[F] {
    override def datasetStart(
      obsId:  Observation.Id,
      stepId: StepId,
      fileId: ImageFileId
    ): F[Boolean] = true.pure[F]
    override def datasetComplete(
      datasetI: DatasetId,
      obsId:    Observation.Id,
      fileId:   ImageFileId
    ): F[Boolean] = true.pure[F]
    override def obsAbort(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] = false.pure[F]
    override def sequenceEnd(visitId: VisitId, obsId: Observation.Id): F[Boolean] =
      false.pure[F]
    override def sequenceStart(
      obsId:     Observation.Id,
      staticCfg: StaticConfig
    ): F[VisitId] =
      Visit.Id(PosLong.unsafeFrom(12345678)).pure[F]
    override def obsContinue(visitId: VisitId, obsId: Observation.Id): F[Boolean] =
      false.pure[F]
    override def obsPause(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] = false.pure[F]
    override def obsStop(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] = false.pure[F]

    override def stepStartStep(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] = false.pure[F]

    override def stepEndStep(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] = false.pure[F]

    override def stepStartConfigure(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] = false.pure[F]

    override def stepEndConfigure(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] = false.pure[F]

    override def stepStartObserve(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] = false.pure[F]

    override def stepEndObserve(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] = false.pure[F]
  }

  case class OdbCommandsImpl[F[_]](client: FetchClient[F, ObservationDB])(using
    val F: Sync[F],
    L:     Logger[F]
  ) extends OdbEventCommands[F] {

    given FetchClient[F, ObservationDB] = client

    private val fitsFileExtension = ".fits"

    def normalizeFilename(fileName: String): String =
      if (fileName.endsWith(fitsFileExtension)) fileName else fileName + fitsFileExtension

    override def datasetStart(
      obsId:  Observation.Id,
      stepId: StepId,
      fileId: ImageFileId
    ): F[Boolean] =
      for {
        _  <-
          L.debug(
            s"Send ODB event datasetStart for obsId: $obsId, stepId: $stepId with fileId: $fileId"
          )
        ds <- RecordDatasetMutation[F].execute(stepId, fileId.value)
        _  <- AddDatasetEventMutation[F]
                .execute(
                  ds.recordDataset.dataset.id,
                  stg = DatasetStage.StartObserve
                )
        _  <- L.debug("ODB event datasetStart sent")
      } yield true

    override def datasetComplete(
      datasetId: DatasetId,
      obsId:     Observation.Id,
      fileId:    ImageFileId
    ): F[Boolean] =
      for {
        _ <-
          L.debug(
            s"Send ODB event datasetComplete for obsId: $obsId datasetId: $datasetId with fileId: $fileId"
          )
        _ <- AddDatasetEventMutation[F]
               .execute(datasetId = datasetId, stg = DatasetStage.EndObserve)
        _ <- L.debug("ODB event datasetComplete sent")
      } yield true

    override def obsAbort(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] =
      for {
        _ <- L.debug(s"Send ODB event observationAbort for obsId: $obsId")
        _ <- AddSequenceEventMutation[F].execute(vId = visitId, cmd = SequenceCommand.Abort)
        _ <- L.debug("ODB event observationAbort sent")
      } yield true

    override def sequenceEnd(visitId: VisitId, obsId: Observation.Id): F[Boolean] =
      L.debug(s"Skipped sending ODB event sequenceEnd for obsId: $obsId")
        .as(true)

    override def sequenceStart(obsId: Observation.Id, staticCfg: StaticConfig): F[VisitId] =
      for {
        vid <- recordVisit(obsId, staticCfg)
        _   <- L.debug(s"Send ODB event sequenceStart for obsId: $obsId, visitId: $vid")
        _   <-
          AddSequenceEventMutation[F]
            .applyP(client)
            .execute(vId = vid, cmd = SequenceCommand.Start)
        _   <- L.debug("ODB event sequenceStart sent")
      } yield vid

    override def obsContinue(visitId: VisitId, obsId: Observation.Id): F[Boolean] =
      for {
        _ <- L.debug(s"Send ODB event observationContinue for obsId: $obsId")
        _ <- AddSequenceEventMutation[F].execute(vId = visitId, cmd = SequenceCommand.Continue)
        _ <- L.debug("ODB event observationContinue sent")
      } yield true

    override def obsPause(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] =
      for {
        _ <- L.debug(s"Send ODB event observationPause for obsId: $obsId")
        _ <- AddSequenceEventMutation[F]
               .applyP(client)
               .execute(vId = visitId, cmd = SequenceCommand.Pause)
        _ <- L.debug("ODB event observationPause sent")
      } yield true

    override def obsStop(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] =
      for {
        _ <- L.debug(s"Send ODB event observationStop for obsId: $obsId")
        _ <- AddSequenceEventMutation[F]
               .execute(vId = visitId, cmd = SequenceCommand.Stop)
        _ <- L.debug("ODB event observationStop sent")
      } yield true

    override def stepStartStep(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] =
      for {
        _ <- L.debug(s"Send ODB event stepStartStep for obsId: $obsId, stepId: $stepId")
        _ <- AddStepEventMutation[F]
               .execute(stepId = stepId, stg = StepStage.StartStep)
        _ <- L.debug("ODB event stepStartStep sent")
      } yield true

    override def stepEndStep(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] =
      for {
        _ <- L.debug(s"Send ODB event stepEndStep for obsId: $obsId, step $stepId")
        _ <- AddStepEventMutation[F]
               .execute(stepId = stepId, stg = StepStage.EndStep)
        _ <- L.debug("ODB event stepEndStep sent")
      } yield true

    override def stepStartConfigure(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] =
      for {
        _ <- L.debug(s"Send ODB event stepStartConfigure for obsId: $obsId, step $stepId")
        _ <- AddStepEventMutation[F]
               .execute(stepId = stepId, stg = StepStage.StartConfigure)
        _ <- L.debug("ODB event stepStartConfigure sent")
      } yield true

    override def stepEndConfigure(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] =
      for {
        _ <- L.debug(s"Send ODB event stepEndConfigure for obsId: $obsId, step $stepId")
        _ <- AddStepEventMutation[F]
               .execute(stepId = stepId, stg = StepStage.EndConfigure)
        _ <- L.debug("ODB event stepEndConfigure sent")
      } yield true

    override def stepStartObserve(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] =
      for {
        _ <- L.debug(s"Send ODB event stepStartConfigure for obsId: $obsId, step $stepId")
        _ <- AddStepEventMutation[F]
               .execute(stepId = stepId, stg = StepStage.StartObserve)
        _ <- L.debug("ODB event stepStartObserve sent")
      } yield true

    override def stepEndObserve(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] =
      for {
        _ <- L.debug(s"Send ODB event stepEndConfigure for obsId: $obsId, step $stepId")
        _ <- AddStepEventMutation[F]
               .execute(stepId = stepId, stg = StepStage.EndObserve)
        _ <- L.debug("ODB event stepEndObserve sent")
      } yield true

    private def recordVisit(
      obsId:     Observation.Id,
      staticCfg: StaticConfig
    ): F[VisitId] = staticCfg match {
      case s: StaticConfig.GmosNorth => recordGmosNorthVisit(obsId, s)
      case s: StaticConfig.GmosSouth => recordGmosSouthVisit(obsId, s)
    }

    private def recordGmosNorthVisit(
      obsId:     Observation.Id,
      staticCfg: StaticConfig.GmosNorth
    ): F[VisitId] =
      RecordGmosNorthVisitMutation[F]
        .applyP(client)
        .execute(
          obsId,
          staticCfg.toInput
        )
        .map(_.recordGmosNorthVisit.visit.id)

    private def recordGmosSouthVisit(
      obsId:     Observation.Id,
      staticCfg: StaticConfig.GmosSouth
    ): F[VisitId] =
      RecordGmosSouthVisitMutation[F]
        .applyP(client)
        .execute(
          obsId,
          staticCfg.toInput
        )
        .map(_.recordGmosSouthVisit.visit.id)
  }

  class TestOdbProxy[F[_]: MonadThrow] extends OdbProxy[F] {
    val evCmds = new DummyOdbCommands[F]

    override def read(oid: Observation.Id): F[ObsQuery.Data.Observation] = MonadThrow[F]
      .raiseError(
        ObserveFailure.Unexpected("TestOdbProxy.read: Not implemented.")
      )

    override def queuedSequences: F[List[Observation.Id]] = List.empty[Observation.Id].pure[F]

    override def datasetStart(
      obsId:  Observation.Id,
      stepId: StepId,
      fileId: ImageFileId
    ): F[Boolean] =
      evCmds.datasetStart(obsId, stepId, fileId)
    override def datasetComplete(
      datasetId: DatasetId,
      obsId:     Observation.Id,
      fileId:    ImageFileId
    ): F[Boolean] =
      evCmds.datasetComplete(datasetId, obsId, fileId)
    override def obsAbort(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] =
      evCmds.obsAbort(visitId, obsId, reason)
    override def sequenceEnd(visitId: VisitId, obsId: Observation.Id): F[Boolean] =
      evCmds.sequenceEnd(visitId, obsId)
    override def sequenceStart(
      obsId:     Observation.Id,
      staticCfg: StaticConfig
    ): F[VisitId] =
      evCmds.sequenceStart(obsId, staticCfg)
    override def obsContinue(visitId: VisitId, obsId: Observation.Id): F[Boolean] =
      evCmds.obsContinue(visitId, obsId)
    override def obsPause(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] =
      evCmds.obsPause(visitId, obsId, reason)
    override def obsStop(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] =
      evCmds.obsStop(visitId, obsId, reason)

    override def stepStartStep(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] = false.pure[F]

    override def stepEndStep(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] = false.pure[F]

    override def stepStartConfigure(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] = false.pure[F]

    override def stepEndConfigure(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] = false.pure[F]

    override def stepStartObserve(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] = false.pure[F]

    override def stepEndObserve(
      obsId:  Observation.Id,
      stepId: StepId
    ): F[Boolean] = false.pure[F]
  }

}
