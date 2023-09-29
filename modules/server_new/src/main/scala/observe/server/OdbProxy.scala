// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Applicative
import cats.MonadThrow
import cats.effect.Sync
import cats.syntax.all.*
import clue.ClientAppliedF.*
import clue.FetchClient
import clue.data.syntax.*
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.enums.DatasetStage
import lucuma.core.enums.SequenceCommand
import lucuma.core.enums.StepStage
import lucuma.core.model.Observation
import lucuma.core.model.Visit
import lucuma.core.model.sequence.gmos.StaticConfig
import lucuma.schemas.ObservationDB
import lucuma.schemas.ObservationDB.Enums.SequenceType
import lucuma.schemas.ObservationDB.Scalars.VisitId
import observe.common.ObsQueriesGQL.*
import observe.model.StepId
import observe.model.dhs.*
import observe.server.ObsQueryInput.*
import observe.server.given
import org.typelevel.log4cats.Logger

sealed trait OdbEventCommands[F[_]] {
  def datasetStart(
    visitId:      VisitId,
    obsId:        Observation.Id,
    stepId:       StepId,
    datasetIndex: PosInt,
    fileId:       ImageFileId
  ): F[Boolean]
  def datasetComplete(
    visitId:      VisitId,
    obsId:        Observation.Id,
    stepId:       StepId,
    datasetIndex: PosInt,
    fileId:       ImageFileId
  ): F[Boolean]
  def obsAbort(visitId:    VisitId, obsId:            Observation.Id, reason: String): F[Boolean]
  def sequenceEnd(visitId: VisitId, obsId:            Observation.Id): F[Boolean]
  def sequenceStart(obsId: Observation.Id, staticCfg: StaticConfig): F[VisitId]
  def obsContinue(visitId: VisitId, obsId:            Observation.Id): F[Boolean]
  def obsPause(visitId:    VisitId, obsId:            Observation.Id, reason: String): F[Boolean]
  def obsStop(visitId:     VisitId, obsId:            Observation.Id, reason: String): F[Boolean]
  def stepStartStep(
    visitId:      VisitId,
    obsId:        Observation.Id,
    stepId:       StepId,
    sequenceType: SequenceType
  ): F[Boolean]
  def stepEndStep(
    visitId:      VisitId,
    obsId:        Observation.Id,
    stepId:       StepId,
    sequenceType: SequenceType
  ): F[Boolean]
  def stepStartConfigure(
    visitId:      VisitId,
    obsId:        Observation.Id,
    stepId:       StepId,
    sequenceType: SequenceType
  ): F[Boolean]
  def stepEndConfigure(
    visitId:      VisitId,
    obsId:        Observation.Id,
    stepId:       StepId,
    sequenceType: SequenceType
  ): F[Boolean]
  def stepStartObserve(
    visitId:      VisitId,
    obsId:        Observation.Id,
    stepId:       StepId,
    sequenceType: SequenceType
  ): F[Boolean]
  def stepEndObserve(
    visitId:      VisitId,
    obsId:        Observation.Id,
    stepId:       StepId,
    sequenceType: SequenceType
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
        visitId:      VisitId,
        obsId:        Observation.Id,
        stepId:       StepId,
        datasetIndex: PosInt,
        fileId:       ImageFileId
      ): F[Boolean] =
        evCmds.datasetStart(visitId, obsId, stepId, datasetIndex, fileId)
      def datasetComplete(
        visitId:      VisitId,
        obsId:        Observation.Id,
        stepId:       StepId,
        datasetIndex: PosInt,
        fileId:       ImageFileId
      ): F[Boolean] =
        evCmds.datasetComplete(visitId, obsId, stepId, datasetIndex, fileId)
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
        visitId:      VisitId,
        obsId:        Observation.Id,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepStartStep(visitId, obsId, stepId, sequenceType)

      override def stepEndStep(
        visitId:      VisitId,
        obsId:        Observation.Id,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepEndStep(visitId, obsId, stepId, sequenceType)

      override def stepStartConfigure(
        visitId:      VisitId,
        obsId:        Observation.Id,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepStartConfigure(visitId, obsId, stepId, sequenceType)

      override def stepEndConfigure(
        visitId:      VisitId,
        obsId:        Observation.Id,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepEndConfigure(visitId, obsId, stepId, sequenceType)

      override def stepStartObserve(
        visitId:      VisitId,
        obsId:        Observation.Id,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepStartObserve(visitId, obsId, stepId, sequenceType)

      override def stepEndObserve(
        visitId:      VisitId,
        obsId:        Observation.Id,
        stepId:       StepId,
        sequenceType: SequenceType
      ): F[Boolean] = evCmds.stepEndObserve(visitId, obsId, stepId, sequenceType)
    }

  final class DummyOdbCommands[F[_]: Applicative] extends OdbEventCommands[F] {
    override def datasetStart(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      datasetIndex: PosInt,
      fileId:       ImageFileId
    ): F[Boolean] = true.pure[F]
    override def datasetComplete(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      datasetIndex: PosInt,
      fileId:       ImageFileId
    ): F[Boolean] = true.pure[F]
    override def obsAbort(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] = false.pure[F]
    override def sequenceEnd(visitId: VisitId, obsId: Observation.Id): F[Boolean]          =
      false.pure[F]
    override def sequenceStart(obsId: Observation.Id, staticCfg: StaticConfig): F[VisitId] =
      Visit.Id(PosLong.unsafeFrom(12345678)).pure[F]
    override def obsContinue(visitId: VisitId, obsId: Observation.Id): F[Boolean]          =
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
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndStep(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepStartConfigure(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndConfigure(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepStartObserve(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndObserve(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]
  }

  final case class OdbCommandsImpl[F[_]](client: FetchClient[F, ObservationDB])(using
    val F: Sync[F],
    L:     Logger[F]
  ) extends OdbEventCommands[F] {

    given FetchClient[F, ObservationDB] = client

    private val fitsFileExtension                           = ".fits"
    private def normalizeFilename(fileName: String): String = if (
      fileName.endsWith(fitsFileExtension)
    ) fileName
    else fileName + fitsFileExtension

    override def datasetStart(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      datasetIndex: PosInt,
      fileId:       ImageFileId
    ): F[Boolean] =
      L.debug(
        s"Send ODB event datasetStart for obsId: $obsId, stepId: $stepId, datasetIndex: $datasetIndex, with fileId: $fileId"
      ) *>
        AddDatasetEventMutation[F]
          .execute(
            visitId,
            obsId = obsId,
            stpId = stepId,
            dtIdx = datasetIndex,
            flName = normalizeFilename(fileId.value).assign,
            stg = DatasetStage.StartObserve
          )
          .as(true) <*
        L.debug("ODB event datasetStart sent")

    override def datasetComplete(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      datasetIndex: PosInt,
      fileId:       ImageFileId
    ): F[Boolean] =
      L.debug(
        s"Send ODB event datasetComplete for obsId: $obsId stepId: $stepId, datasetIndex: $datasetIndex, with fileId: $fileId"
      ) *>
        AddDatasetEventMutation[F]
          .execute(
            vId = visitId,
            obsId = obsId,
            stpId = stepId,
            dtIdx = datasetIndex,
            flName = normalizeFilename(fileId.value).assign,
            stg = DatasetStage.EndObserve
          )
          .as(true) <*
        L.debug("ODB event datasetComplete sent")

    override def obsAbort(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] =
      L.debug(s"Send ODB event observationAbort for obsId: $obsId") *>
        AddSequenceEventMutation[F]
          .execute(
            vId = visitId,
            obsId = obsId,
            cmd = SequenceCommand.Abort
          )
          .as(true) <*
        L.debug("ODB event observationAbort sent")

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
            .execute(vId = vid, obsId = obsId, cmd = SequenceCommand.Start)
        _   <- L.debug("ODB event sequenceStart sent")
      } yield vid

    override def obsContinue(visitId: VisitId, obsId: Observation.Id): F[Boolean] =
      L.debug(s"Send ODB event observationContinue for obsId: $obsId") *>
        AddSequenceEventMutation[F]
          .execute(vId = visitId, obsId = obsId, cmd = SequenceCommand.Continue)
          .as(true) <*
        L.debug("ODB event observationContinue sent")

    override def obsPause(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] =
      L.debug(s"Send ODB event observationPause for obsId: $obsId") *>
        AddSequenceEventMutation[F]
          .applyP(client)
          .execute(vId = visitId, obsId = obsId, cmd = SequenceCommand.Pause)
          .as(true) <*
        L.debug("ODB event observationPause sent")

    override def obsStop(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] =
      L.debug(s"Send ODB event observationStop for obsId: $obsId") *>
        AddSequenceEventMutation[F]
          .execute(vId = visitId, obsId = obsId, cmd = SequenceCommand.Stop)
          .as(true) <*
        L.debug("ODB event observationStop sent")

    override def stepStartStep(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepStartStep for obsId: $obsId"
      ) *>
        AddStepEventMutation[F]
          .execute(vId = visitId,
                   obsId = obsId,
                   stpId = stepId,
                   stg = StepStage.StartStep,
                   seqType = sequenceType
          )
          .as(true) <*
        L.debug("ODB event stepStartStep sent")

    override def stepEndStep(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepEndStep for obsId: $obsId"
      ) *>
        AddStepEventMutation[F]
          .execute(
            vId = visitId,
            obsId = obsId,
            stpId = stepId,
            stg = StepStage.EndStep,
            seqType = sequenceType
          )
          .as(true) <*
        L.debug("ODB event stepEndStep sent")

    override def stepStartConfigure(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepStartConfigure for obsId: $obsId"
      ) *>
        AddStepEventMutation[F]
          .execute(
            vId = visitId,
            obsId = obsId,
            stpId = stepId,
            stg = StepStage.StartConfigure,
            seqType = sequenceType
          )
          .as(true) <*
        L.debug("ODB event stepStartConfigure sent")

    override def stepEndConfigure(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepEndConfigure for obsId: $obsId"
      ) *>
        AddStepEventMutation[F]
          .execute(
            vId = visitId,
            obsId = obsId,
            stpId = stepId,
            stg = StepStage.EndConfigure,
            seqType = sequenceType
          )
          .as(true) <*
        L.debug("ODB event stepEndConfigure sent")

    override def stepStartObserve(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepStartObserve for obsId: $obsId"
      ) *>
        AddStepEventMutation[F]
          .execute(
            vId = visitId,
            obsId = obsId,
            stpId = stepId,
            stg = StepStage.StartObserve,
            seqType = sequenceType
          )
          .as(true) <*
        L.debug("ODB event stepStartObserve sent")

    override def stepEndObserve(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] =
      L.debug(
        s"Send ODB event stepEndObserve for obsId: $obsId"
      ) *>
        AddStepEventMutation[F]
          .execute(
            vId = visitId,
            obsId = obsId,
            stpId = stepId,
            stg = StepStage.EndObserve,
            seqType = sequenceType
          )
          .as(true) <*
        L.debug("ODB event stepEndObserve sent")

    def recordVisit(
      obsId:     Observation.Id,
      staticCfg: StaticConfig
    ): F[VisitId] = staticCfg match {
      case s: StaticConfig.GmosNorth => recordGmosNorthVisit(obsId, s)
      case s: StaticConfig.GmosSouth => recordGmosSouthVisit(obsId, s)
    }

    def recordGmosNorthVisit(
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

    def recordGmosSouthVisit(
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
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      datasetIndex: PosInt,
      fileId:       ImageFileId
    ): F[Boolean] =
      evCmds.datasetStart(visitId, obsId, stepId, datasetIndex, fileId)
    override def datasetComplete(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      datasetIndex: PosInt,
      fileId:       ImageFileId
    ): F[Boolean] =
      evCmds.datasetComplete(visitId, obsId, stepId, datasetIndex, fileId)
    override def obsAbort(
      visitId: VisitId,
      obsId:   Observation.Id,
      reason:  String
    ): F[Boolean] =
      evCmds.obsAbort(visitId, obsId, reason)
    override def sequenceEnd(visitId: VisitId, obsId: Observation.Id): F[Boolean]          =
      evCmds.sequenceEnd(visitId, obsId)
    override def sequenceStart(obsId: Observation.Id, staticCfg: StaticConfig): F[VisitId] =
      evCmds.sequenceStart(obsId, staticCfg)
    override def obsContinue(visitId: VisitId, obsId: Observation.Id): F[Boolean]          =
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
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndStep(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepStartConfigure(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndConfigure(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepStartObserve(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]

    override def stepEndObserve(
      visitId:      VisitId,
      obsId:        Observation.Id,
      stepId:       StepId,
      sequenceType: SequenceType
    ): F[Boolean] = false.pure[F]
  }

}
