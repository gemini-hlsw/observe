// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Endo
import cats.effect.Async
import cats.syntax.all._
import edu.gemini.spModel.obscomp.InstConstants
import edu.gemini.spModel.seqcomp.SeqConfigNames.OCS_KEY
import observe.engine.Event
import observe.engine.Sequence
import observe.model.Observation
import observe.model.SystemOverrides
import ConfigUtilOps._
import SeqEvent._
import ObserveFailure.ObserveException
import ObserveFailure.UnrecognizedInstrument
import lucuma.core.model.Program
import observe.server.transition.OcsOdbTranslator
import observe.common.ObsQueriesGQL.ObsQuery.Data.{Observation => OdbObservation}

final class ODBSequencesLoader[F[_]: Async](
  odbProxy:            OdbProxy[F],
  translators:          List[SeqTranslate[F]]
)(implicit execEngine: ExecEngineType[F]) {

  private def unloadEvent(seqId: Observation.Id): EventType[F] =
    Event.modifyState[F, EngineState[F], SeqEvent](
      { st: EngineState[F] =>
        if (execEngine.canUnload(seqId)(st)) {
          (EngineState.sequences[F].modify(ss => ss - seqId) >>>
            EngineState.selected.modify(ss =>
              ss.toList.filter { case (_, x) => x =!= seqId }.toMap
            ) >>>
            EngineState.queues.modify(
              _.view.mapValues(ExecutionQueue.queue.modify(_.filterNot(_ === seqId))).toMap
            ))(st)
        } else st
      }.withEvent(UnloadSequence(seqId)).toHandle
    )

  private def tryTranslate(sequence: OdbObservation): Option[Either[List[Throwable], SequenceGen[F]]] = {
    translators.foldLeft(none[Either[List[Throwable], SequenceGen[F]]])( (a, b) => a match {
      case Some(_) => a
      case None => b.sequence(sequence)
    } )
  }

  def loadEvents(seqId: Observation.Id): F[List[EventType[F]]] = {
    // Three ways of handling errors are mixed here: java exceptions, Either and MonadError
    val t: F[Option[Either[List[Throwable], SequenceGen[F]]]] =
      odbProxy.read(seqId).map(tryTranslate)

    def loadSequenceEvent(seqg: SequenceGen[F]): EventType[F] =
      Event.modifyState[F, EngineState[F], SeqEvent]({ st: EngineState[F] =>
        st.sequences
          .get(seqId)
          .fold(ODBSequencesLoader.loadSequenceEndo(seqId, seqg, execEngine))(_ =>
            ODBSequencesLoader.reloadSequenceEndo(seqId, seqg, execEngine)
          )(st)
      }.withEvent(LoadSequence(seqId)).toHandle)

    t.map {
      case None =>
        Nil
      case Some(Left(err :: _))                      =>
        val explanation = explain(err)
        List(Event.logDebugMsgF[F, EngineState[F], SeqEvent](explanation))
      case Some(Right(seq))                      => loadSequenceEvent(seq).pure[F]
      case _                                      => Nil
    }.recover { case e => List(Event.logDebugMsgF(explain(e))) }
      .map(_.sequence)
      .flatten
  }

  private def explain(err: Throwable): String =
    err match {
      case s: ObserveFailure => ObserveFailure.explain(s)
      case _                 => ObserveFailure.explain(ObserveException(err))
    }

  def refreshSequenceList(
    odbList: List[Observation.Id],
    st:      EngineState[F]
  ): F[List[EventType[F]]] = {
    val observeList = st.sequences.keys.toList

    val loads = odbList.diff(observeList).traverse(loadEvents).map(_.flatten)

    val unloads = observeList.diff(odbList).map(unloadEvent)

    loads.map(_ ++ unloads)
  }

}

object ODBSequencesLoader {

  private def toEngineSequence[F[_]](
    id:        Observation.Id,
    overrides: SystemOverrides,
    seq:       SequenceGen[F],
    d:         HeaderExtraData
  ): Sequence[F] = Sequence(id, toStepList(seq, overrides, d))

  private[server] def loadSequenceEndo[F[_]](
    seqId:      Observation.Id,
    seqg:       SequenceGen[F],
    execEngine: ExecEngineType[F]
  ): Endo[EngineState[F]] =
    st =>
      EngineState
        .sequences[F]
        .modify(ss =>
          ss + (seqId -> SequenceData[F](
            seqg.name,
            none,
            none,
            SystemOverrides.AllEnabled,
            seqg,
            execEngine.load(
              toEngineSequence(
                seqId,
                SystemOverrides.AllEnabled,
                seqg,
                HeaderExtraData(st.conditions, st.operator, None, None)
              )
            ),
            none
          ))
        )(st)

  private[server] def reloadSequenceEndo[F[_]](
    seqId:      Observation.Id,
    seqg:       SequenceGen[F],
    execEngine: ExecEngineType[F]
  ): Endo[EngineState[F]] =
    st =>
      EngineState
        .atSequence[F](seqId)
        .modify(sd =>
          sd.copy(
            seqGen = seqg,
            seq = execEngine.reload(
              sd.seq,
              toStepList(
                seqg,
                sd.overrides,
                HeaderExtraData(st.conditions, st.operator, sd.observer, sd.visitId)
              )
            )
          )
        )(st)

}
