// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Endo
import cats.syntax.all.*
import lucuma.core.enums.Breakpoint
import lucuma.core.model.sequence.Atom
import monocle.Lens
import monocle.std.option
import observe.model.Observation
import observe.model.Observer
import observe.model.SystemOverrides
import observe.server.engine.Breakpoints
import observe.server.engine.BreakpointsDelta
import observe.server.engine.Engine
import observe.server.engine.EngineStep
import observe.server.engine.Sequence

import scala.annotation.unused

import odb.OdbProxy

final class ODBSequencesLoader[F[_]](
  @unused odbProxy:   OdbProxy[F],
  @unused translator: SeqTranslate[F],
  @unused execEngine: Engine[F]
) {

//  private def unloadEvent(seqId: Observation.Id): EventType[F] =
//    Event.modifyState[F, EngineState[F], SeqEvent](
//      { (st: EngineState[F]) =>
//        EngineState
//          .atSequence[F](seqId)
//          .getOption(st)
//          .map { seq =>
//            if (execEngine.canUnload(seq.seq)) {
//              EngineState.instrumentLoaded(seq.seqGen.instrument).replace(none)(st)
//            } else st
//          }
//          .getOrElse(st)
//      }.withEvent(UnloadSequence(seqId)).toHandle
//    )

//  def loadEvents(seqId: Observation.Id): F[List[EventType[F]]] = {
//    // Three ways of handling errors are mixed here: java exceptions, Either and MonadError
//    val t: F[(List[Throwable], Option[SequenceGen[F]])] =
//      odbProxy.read(seqId).flatMap(translator.sequence)
//
//    def loadSequenceEvent(seqg: SequenceGen[F]): EventType[F] =
//      Event.modifyState[F, EngineState[F], SeqEvent]({ (st: EngineState[F]) =>
//        st.sequences
//          .get(seqId)
//          .fold(ODBSequencesLoader.loadSequenceEndo(none, seqg, execEngine))(_ =>
//            ODBSequencesLoader.reloadSequenceEndo(seqId, seqg, execEngine)
//          )(st)
//      }.withEvent(LoadSequence(seqId)).toHandle)
//
//    t.map {
//      case (UnrecognizedInstrument(_) :: _, None) =>
//        List.empty
//      case (err :: _, None)                       =>
//        val explanation = explain(err)
//        List(Event.logDebugMsgF[F, EngineState[F], SeqEvent](explanation))
//      case (errs, Some(seq))                      =>
//        loadSequenceEvent(seq).pure[F] :: errs.map(e =>
//          Event.logDebugMsgF[F, EngineState[F], SeqEvent](explain(e))
//        )
//      case _                                      => Nil
//    }.recover { case e => List(Event.logDebugMsgF[F, EngineState[F], SeqEvent](explain(e))) }
//  }.map(_.sequence).flatten

  // private def explain(err: Throwable): String =
  //   err match {
  //     case s: ObserveFailure => ObserveFailure.explain(s)
  //     case _                 => ObserveFailure.explain(ObserveException(err))
  //   }

//  def refreshSequenceList(
//    odbList: List[Observation.Id],
//    st:      EngineState[F]
//  ): F[List[EventType[F]]] = {
//    val observeList = st.sequences.keys.toList
//
//    val loads = odbList.diff(observeList).traverse(loadEvents).map(_.flatten)
//
//    val unloads = observeList.diff(odbList).map(unloadEvent)
//
//    loads.map(_ ++ unloads)
//  }

}

object ODBSequencesLoader {

  private def toEngineSequence[F[_]](
    id:                   Observation.Id,
    atomId:               Atom.Id,
    overrides:            SystemOverrides,
    seq:                  SequenceGen[F],
    headerExtra:          HeaderExtraData,
    preservedBreakpoints: Breakpoints
  ): Sequence[F] =
    val stepsWithBreakpoints: List[(EngineStep[F], Breakpoint)] =
      toStepList(seq, overrides, headerExtra)
    val steps: List[EngineStep[F]]                              = stepsWithBreakpoints.map(_._1)
    val breakpoints: Breakpoints                                =
      preservedBreakpoints.merge(BreakpointsDelta.fromStepsWithBreakpoints(stepsWithBreakpoints))
    Sequence.sequence(id, atomId, steps, breakpoints)

  private[server] def loadSequenceEndo[F[_]](
    observer: Option[Observer],
    seqg:     SequenceGen[F],
    l:        Lens[EngineState[F], Option[SequenceData[F]]],
    cleanup:  F[Unit]
  ): Endo[EngineState[F]] = st =>
    l.modify(oldSeqData =>
      SequenceData[F](
        observer,
        SystemOverrides.AllEnabled,
        seqg,
        Engine.load(
          toEngineSequence(
            seqg.obsData.id,
            seqg.nextAtom.atomId,
            SystemOverrides.AllEnabled,
            seqg,
            HeaderExtraData(st.conditions, st.operator, observer),
            oldSeqData.map(_.seq.breakpoints).getOrElse(Breakpoints.empty)
          )
        ),
        none,
        cleanup
      ).some
    )(st)

  private[server] def reloadSequenceEndo[F[_]](
    seqg: SequenceGen[F],
    l:    Lens[EngineState[F], Option[SequenceData[F]]]
  ): Endo[EngineState[F]] = st =>
    l.andThen(option.some)
      .modify(sd =>
        sd.copy(
          seqGen = seqg,
          seq = Engine.reload(
            sd.seq,
            toStepList(
              seqg,
              sd.overrides,
              HeaderExtraData(st.conditions, st.operator, sd.observer)
            ).map(_._1)
          )
        )
      )(st)

}
