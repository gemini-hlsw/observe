// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all._
import edu.gemini.seqexec.odb.SeqexecSequence
import edu.gemini.spModel.core.Peer
import org.typelevel.log4cats.Logger
import observe.model.Observation
import observe.model.dhs._

sealed trait OdbCommands[F[_]] {
  def queuedSequences: F[List[Observation.Id]]
  def datasetStart(obsId:    Observation.Id, dataId: DataId, fileId: ImageFileId): F[Boolean]
  def datasetComplete(obsId: Observation.Id, dataId: DataId, fileId: ImageFileId): F[Boolean]
  def obsAbort(obsId:        Observation.Id, reason: String): F[Boolean]
  def sequenceEnd(obsId:     Observation.Id): F[Boolean]
  def sequenceStart(obsId:   Observation.Id, dataId: DataId): F[Boolean]
  def obsContinue(obsId:     Observation.Id): F[Boolean]
  def obsPause(obsId:        Observation.Id, reason: String): F[Boolean]
  def obsStop(obsId:         Observation.Id, reason: String): F[Boolean]
}

sealed trait OdbProxy[F[_]] extends OdbCommands[F] {
  def read(oid: Observation.Id): F[SeqexecSequence]
  def queuedSequences: F[List[Observation.Id]]
}

object OdbProxy {
  def apply[F[_]: Sync](@annotation.unused loc: Peer, cmds: OdbCommands[F]): OdbProxy[F] =
    new OdbProxy[F] {
      def read(oid: Observation.Id): F[SeqexecSequence] =
        Sync[F].raiseError(ObserveFailure.Unexpected("OdbProxy.read: Not implemented."))

      def queuedSequences: F[List[Observation.Id]] = cmds.queuedSequences
      def datasetStart(obsId:    Observation.Id, dataId: DataId, fileId:      ImageFileId): F[Boolean] =
        cmds.datasetStart(obsId, dataId, fileId)
      def datasetComplete(obsId: Observation.Id, dataId: DataId, fileId: ImageFileId): F[Boolean] =
        cmds.datasetComplete(obsId, dataId, fileId)
      def obsAbort(obsId:        Observation.Id, reason: String): F[Boolean] = cmds.obsAbort(obsId, reason)
      def sequenceEnd(obsId:     Observation.Id): F[Boolean] = cmds.sequenceEnd(obsId)
      def sequenceStart(obsId:   Observation.Id, dataId: DataId): F[Boolean] =
        cmds.sequenceStart(obsId, dataId)
      def obsContinue(obsId:     Observation.Id): F[Boolean] = cmds.obsContinue(obsId)
      def obsPause(obsId:        Observation.Id, reason: String): F[Boolean] = cmds.obsPause(obsId, reason)
      def obsStop(obsId:         Observation.Id, reason: String): F[Boolean] = cmds.obsStop(obsId, reason)
    }

  final class DummyOdbCommands[F[_]: Applicative] extends OdbCommands[F] {
    override def datasetStart(
      obsId:  Observation.Id,
      dataId: DataId,
      fileId: ImageFileId
    ): F[Boolean] = true.pure[F]
    override def datasetComplete(
      obsId:  Observation.Id,
      dataId: DataId,
      fileId: ImageFileId
    ): F[Boolean] = true.pure[F]
    override def obsAbort(obsId:      Observation.Id, reason: String): F[Boolean] = false.pure[F]
    override def sequenceEnd(obsId:   Observation.Id): F[Boolean] = false.pure[F]
    override def sequenceStart(obsId: Observation.Id, dataId: DataId): F[Boolean] = false.pure[F]
    override def obsContinue(obsId:   Observation.Id): F[Boolean] = false.pure[F]
    override def obsPause(obsId:      Observation.Id, reason: String): F[Boolean] = false.pure[F]
    override def obsStop(obsId:       Observation.Id, reason: String): F[Boolean] = false.pure[F]
    override def queuedSequences: F[List[Observation.Id]] = List.empty.pure[F]
  }

  implicit class SeqexecSequenceOps(val s: SeqexecSequence) extends AnyVal {
    def stepsCount: Int          = Option(s.config.getAllSteps).foldMap(_.length)
    def executedCount: Int       = s.datasets.size
    def unExecutedSteps: Boolean = stepsCount =!= executedCount
  }

  final case class OdbCommandsImpl[F[_]](host: Peer)(implicit val F: Sync[F], L: Logger[F])
      extends OdbCommands[F] {

    override def datasetStart(
      obsId:  Observation.Id,
      dataId: DataId,
      fileId: ImageFileId
    ): F[Boolean] =
      L.debug(
        s"Send ODB event datasetStart for obsId: ${obsId.format} and dataId: $dataId, with fileId: $fileId"
      ) *>
        Sync[F].raiseError(ObserveFailure.Unexpected("OdbCommandsImpl.read: Not implemented.")) <*
        L.debug("ODB event datasetStart sent")

    override def datasetComplete(
      obsId:  Observation.Id,
      dataId: DataId,
      fileId: ImageFileId
    ): F[Boolean] =
      L.debug(
        s"Send ODB event datasetComplete for obsId: ${obsId.format} and dataId: $dataId, with fileId: $fileId"
      ) *>
        Sync[F].raiseError(
          ObserveFailure.Unexpected("OdbCommandsImpl.datasetComplete: Not implemented.")
        ) <*
        L.debug("ODB event datasetComplete sent")

    override def obsAbort(obsId: Observation.Id, reason: String): F[Boolean] =
      L.debug(s"Send ODB event observationAbort for obsId: ${obsId.format} reason: $reason") *>
        Sync[F].raiseError(ObserveFailure.Unexpected("OdbCommandsImpl.read: Not implemented.")) <*
        L.debug("ODB event observationAbort sent")

    override def sequenceEnd(obsId: Observation.Id): F[Boolean] =
      L.debug(s"Send ODB event sequenceEnd for obsId: ${obsId.format}") *>
        Sync[F].raiseError(
          ObserveFailure.Unexpected("OdbCommandsImpl.obsAbort: Not implemented.")
        ) <*
        L.debug("ODB event sequenceEnd sent")

    override def sequenceStart(obsId: Observation.Id, dataId: DataId): F[Boolean] =
      L.debug(s"Send ODB event sequenceStart for obsId: ${obsId.format} and dataId: $dataId") *>
        Sync[F].raiseError(
          ObserveFailure.Unexpected("OdbCommandsImpl.sequenceStart: Not implemented.")
        ) <*
        L.debug("ODB event sequenceStart sent")

    override def obsContinue(obsId: Observation.Id): F[Boolean] =
      L.debug(s"Send ODB event observationContinue for obsId: ${obsId.format}") *>
        Sync[F].raiseError(
          ObserveFailure.Unexpected("OdbCommandsImpl.obsContinue: Not implemented.")
        ) <*
        L.debug("ODB event observationContinue sent")

    override def obsPause(obsId: Observation.Id, reason: String): F[Boolean] =
      L.debug(s"Send ODB event observationPause for obsId: ${obsId.format} $reason") *>
        Sync[F].raiseError(
          ObserveFailure.Unexpected("OdbCommandsImpl.obsPause: Not implemented.")
        ) <*
        L.debug("ODB event observationPause sent")

    override def obsStop(obsId: Observation.Id, reason: String): F[Boolean] =
      L.debug(s"Send ODB event observationStop for obsID: ${obsId.format} $reason") *>
        Sync[F].raiseError(
          ObserveFailure.Unexpected("OdbCommandsImpl.obsStop: Not implemented.")
        ) <*
        L.debug("ODB event observationStop sent")

    override def queuedSequences: F[List[Observation.Id]] =
      Sync[F].raiseError(
        ObserveFailure.Unexpected("OdbCommandsImpl.queuedSequences: Not implemented.")
      )
  }

}
