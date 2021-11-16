// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all._
import clue.TransactionalClient
import edu.gemini.seqexec.odb.SeqexecSequence
import observe.common.ObsQueriesGQL
import observe.common.ObsQueriesGQL._
import org.typelevel.log4cats.Logger
import observe.model.Observation
import observe.model.dhs._
import lucuma.schemas.ObservationDB

sealed trait OdbEventCommands[F[_]] {
  def datasetStart(obsId:  Observation.IdName, dataId: DataId, fileId: ImageFileId): F[Boolean]
  def datasetComplete(
    obsIdName:             Observation.IdName,
    dataId:                DataId,
    fileId:                ImageFileId
  ): F[Boolean]
  def obsAbort(obsId:      Observation.IdName, reason: String): F[Boolean]
  def sequenceEnd(obsId:   Observation.IdName): F[Boolean]
  def sequenceStart(obsId: Observation.IdName, dataId: DataId): F[Boolean]
  def obsContinue(obsId:   Observation.IdName): F[Boolean]
  def obsPause(obsId:      Observation.IdName, reason: String): F[Boolean]
  def obsStop(obsId:       Observation.IdName, reason: String): F[Boolean]
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

      override def queuedSequences: F[List[Observation.Id]]                        =
        ObsQueriesGQL.ActiveObservationIdsQuery.query()(client).map(_.observations.nodes.map(_.id))

      def datasetStart(
        obsIdName: Observation.IdName,
        dataId:    DataId,
        fileId:    ImageFileId
      ): F[Boolean] =
        evCmds.datasetStart(obsIdName, dataId, fileId)
      def datasetComplete(
        obsIdName: Observation.IdName,
        dataId:    DataId,
        fileId:    ImageFileId
      ): F[Boolean] =
        evCmds.datasetComplete(obsIdName, dataId, fileId)
      def obsAbort(obsIdName: Observation.IdName, reason: String): F[Boolean]      =
        evCmds.obsAbort(obsIdName, reason)
      def sequenceEnd(obsIdName: Observation.IdName): F[Boolean]                   = evCmds.sequenceEnd(obsIdName)
      def sequenceStart(obsIdName: Observation.IdName, dataId: DataId): F[Boolean] =
        evCmds.sequenceStart(obsIdName, dataId)
      def obsContinue(obsIdName: Observation.IdName): F[Boolean]                   = evCmds.obsContinue(obsIdName)
      def obsPause(obsIdName: Observation.IdName, reason: String): F[Boolean]      =
        evCmds.obsPause(obsIdName, reason)
      def obsStop(obsIdName: Observation.IdName, reason: String): F[Boolean]       =
        evCmds.obsStop(obsIdName, reason)

    }

  final class DummyOdbCommands[F[_]: Applicative] extends OdbEventCommands[F] {
    override def datasetStart(
      obsIdName: Observation.IdName,
      dataId:    DataId,
      fileId:    ImageFileId
    ): F[Boolean] = true.pure[F]
    override def datasetComplete(
      obsIdName: Observation.IdName,
      dataId:    DataId,
      fileId:    ImageFileId
    ): F[Boolean] = true.pure[F]
    override def obsAbort(obsIdName: Observation.IdName, reason: String): F[Boolean]      = false.pure[F]
    override def sequenceEnd(obsIdName: Observation.IdName): F[Boolean]                   = false.pure[F]
    override def sequenceStart(obsIdName: Observation.IdName, dataId: DataId): F[Boolean] =
      false.pure[F]
    override def obsContinue(obsIdName: Observation.IdName): F[Boolean]                   = false.pure[F]
    override def obsPause(obsIdName: Observation.IdName, reason: String): F[Boolean]      = false.pure[F]
    override def obsStop(obsIdName: Observation.IdName, reason: String): F[Boolean]       = false.pure[F]
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

    override def datasetStart(
      obsIdName: Observation.IdName,
      dataId:    DataId,
      fileId:    ImageFileId
    ): F[Boolean] =
      L.debug(
        s"Send ODB event datasetStart for obsId: ${obsIdName.name.format} and dataId: $dataId, with fileId: $fileId"
      ) *>
        Sync[F]
          .raiseError(ObserveFailure.Unexpected("OdbCommandsImpl.read: Not implemented."))
          .as(false) <*
        L.debug("ODB event datasetStart sent")

    override def datasetComplete(
      obsIdName: Observation.IdName,
      dataId:    DataId,
      fileId:    ImageFileId
    ): F[Boolean] =
      L.debug(
        s"Send ODB event datasetComplete for obsId: ${obsIdName.name.format} and dataId: $dataId, with fileId: $fileId"
      ) *>
        Sync[F]
          .raiseError(
            ObserveFailure.Unexpected("OdbCommandsImpl.datasetComplete: Not implemented.")
          )
          .as(false) <*
        L.debug("ODB event datasetComplete sent")

    override def obsAbort(obsIdName: Observation.IdName, reason: String): F[Boolean] =
      L.debug(
        s"Send ODB event observationAbort for obsId: ${obsIdName.name.format} reason: $reason"
      ) *>
        Sync[F]
          .raiseError(ObserveFailure.Unexpected("OdbCommandsImpl.read: Not implemented."))
          .as(false) <*
        L.debug("ODB event observationAbort sent")

    override def sequenceEnd(obsIdName: Observation.IdName): F[Boolean] =
      L.debug(s"Send ODB event sequenceEnd for obsId: ${obsIdName.name.format}") *>
        Sync[F]
          .raiseError(
            ObserveFailure.Unexpected("OdbCommandsImpl.obsAbort: Not implemented.")
          )
          .as(false) <*
        L.debug("ODB event sequenceEnd sent")

    override def sequenceStart(obsIdName: Observation.IdName, dataId: DataId): F[Boolean] =
      L.debug(
        s"Send ODB event sequenceStart for obsId: ${obsIdName.name.format} and dataId: $dataId"
      ) *>
        Sync[F]
          .raiseError(
            ObserveFailure.Unexpected("OdbCommandsImpl.sequenceStart: Not implemented.")
          )
          .as(false) <*
        L.debug("ODB event sequenceStart sent")

    override def obsContinue(obsIdName: Observation.IdName): F[Boolean] =
      L.debug(s"Send ODB event observationContinue for obsId: ${obsIdName.name.format}") *>
        Sync[F]
          .raiseError(
            ObserveFailure.Unexpected("OdbCommandsImpl.obsContinue: Not implemented.")
          )
          .as(false) <*
        L.debug("ODB event observationContinue sent")

    override def obsPause(obsIdName: Observation.IdName, reason: String): F[Boolean] =
      L.debug(s"Send ODB event observationPause for obsId: ${obsIdName.name.format} $reason") *>
        Sync[F]
          .raiseError(
            ObserveFailure.Unexpected("OdbCommandsImpl.obsPause: Not implemented.")
          )
          .as(false) <*
        L.debug("ODB event observationPause sent")

    override def obsStop(obsIdName: Observation.IdName, reason: String): F[Boolean] =
      L.debug(s"Send ODB event observationStop for obsID: ${obsIdName.name.format} $reason") *>
        Sync[F]
          .raiseError(
            ObserveFailure.Unexpected("OdbCommandsImpl.obsStop: Not implemented.")
          )
          .as(false) <*
        L.debug("ODB event observationStop sent")
  }

}
