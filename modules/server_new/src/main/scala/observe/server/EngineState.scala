// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Endo
import cats.syntax.all.*
import lucuma.core.enums.Instrument
import monocle.Focus
import monocle.Lens
import monocle.Optional
import monocle.syntax.all.*
import observe.model.CalibrationQueueId
import observe.model.CalibrationQueueName
import observe.model.Conditions
import observe.model.Observation
import observe.model.Operator
import observe.server.engine.Engine
import observe.server.engine.Sequence

case class EngineState[F[_]](
  queues:     ExecutionQueues,
  selected:   Selected[F],
  conditions: Conditions,
  operator:   Option[Operator]
) {
  lazy val sequences: Map[Observation.Id, SequenceData[F]] =
    List(selected.gmosNorth, selected.gmosSouth, selected.flamingos2).flattenOption
      .map(x => x.seqGen.obsData.id -> x)
      .toMap

  lazy val sequencesByInstrument: Map[Instrument, SequenceData[F]] =
    List(selected.gmosNorth, selected.gmosSouth, selected.flamingos2).flattenOption
      .map(x => x.seqGen.instrument -> x)
      .toMap
}

object EngineState {
  private def selectedGmosSouth[F[_]]: Lens[EngineState[F], Option[SequenceData[F]]] =
    Focus[EngineState[F]](_.selected.gmosSouth)

  private def selectedGmosNorth[F[_]]: Lens[EngineState[F], Option[SequenceData[F]]] =
    Focus[EngineState[F]](_.selected.gmosNorth)

  private def selectedFlamingos2[F[_]]: Lens[EngineState[F], Option[SequenceData[F]]] =
    Focus[EngineState[F]](_.selected.flamingos2)

  def default[F[_]]: EngineState[F] =
    EngineState[F](
      Map(CalibrationQueueId -> ExecutionQueue.init(CalibrationQueueName)),
      Selected.none,
      Conditions.Default,
      None
    )

  def instrumentLoaded[F[_]](
    instrument: Instrument
  ): Lens[EngineState[F], Option[SequenceData[F]]] = instrument match {
    case Instrument.GmosSouth  => EngineState.selectedGmosSouth
    case Instrument.GmosNorth  => EngineState.selectedGmosNorth
    case Instrument.Flamingos2 => EngineState.selectedFlamingos2
    case i                     => sys.error(s"Unexpected instrument $i")
  }

  def atSequence[F[_]](sid: Observation.Id): Optional[EngineState[F], SequenceData[F]] =
    Focus[EngineState[F]](_.selected)
      .andThen(
        Optional[Selected[F], SequenceData[F]] { s =>
          s.gmosNorth
            .find(_.seqGen.obsData.id === sid)
            .orElse(s.gmosSouth.find(_.seqGen.obsData.id === sid))
            .orElse(s.flamingos2.find(_.seqGen.obsData.id === sid))
        } { d => s =>
          if (s.gmosNorth.exists(_.seqGen.obsData.id === sid))
            s.focus(_.gmosNorth).replace(d.some)
          else if (s.gmosSouth.exists(_.seqGen.obsData.id === sid))
            s.focus(_.gmosSouth).replace(d.some)
          else if (s.flamingos2.exists(_.seqGen.obsData.id === sid))
            s.focus(_.flamingos2).replace(d.some)
          else s
        }
      )

  def gmosNorthSequence[F[_]]: Optional[EngineState[F], SequenceData[F]] =
    Optional[EngineState[F], SequenceData[F]] {
      _.selected.gmosNorth
    } { d => s =>
      s.focus(_.selected.gmosNorth).replace(d.some)
    }

  def gmosSouthSequence[F[_]]: Optional[EngineState[F], SequenceData[F]] =
    Optional[EngineState[F], SequenceData[F]] {
      _.selected.gmosSouth
    } { d => s =>
      s.focus(_.selected.gmosSouth).replace(d.some)
    }

  def flamingos2Sequence[F[_]]: Optional[EngineState[F], SequenceData[F]] =
    Optional[EngineState[F], SequenceData[F]] {
      _.selected.flamingos2
    } { d => s =>
      s.focus(_.selected.flamingos2).replace(d.some)
    }

  def sequenceDataAt[F[_]](obsId: Observation.Id): Optional[EngineState[F], SequenceData[F]] =
    Optional[EngineState[F], SequenceData[F]](s =>
      s.selected.gmosSouth
        .filter(_.seqGen.obsData.id === obsId)
        .orElse(s.selected.gmosNorth.filter(_.seqGen.obsData.id === obsId))
        .orElse(s.selected.flamingos2.filter(_.seqGen.obsData.id === obsId))
    )(sd =>
      es =>
        if (es.selected.gmosSouth.exists(_.seqGen.obsData.id === obsId))
          es.copy(selected = es.selected.copy(gmosSouth = sd.some))
        else if (es.selected.gmosNorth.exists(_.seqGen.obsData.id === obsId))
          es.copy(selected = es.selected.copy(gmosNorth = sd.some))
        else if (es.selected.flamingos2.exists(_.seqGen.obsData.id === obsId))
          es.copy(selected = es.selected.copy(flamingos2 = sd.some))
        else es
    )

  def sequenceStateAt[F[_]](obsId: Observation.Id): Optional[EngineState[F], Sequence.State[F]] =
    sequenceDataAt(obsId).andThen(SequenceData.seq)

  def engineState[F[_]]: Engine.State[F, EngineState[F]] =
    (obsId: Observation.Id) => EngineState.sequenceStateAt(obsId)

  extension [F[_]](f: Endo[EngineState[F]])
    def withEvent(ev: SeqEvent): EngineState[F] => (EngineState[F], SeqEvent) = f >>> { (_, ev) }

  def queues[F[_]]: Lens[EngineState[F], ExecutionQueues] = Focus[EngineState[F]](_.queues)

  def selected[F[_]]: Lens[EngineState[F], Selected[F]] = Focus[EngineState[F]](_.selected)

  def conditions[F[_]]: Lens[EngineState[F], Conditions] = Focus[EngineState[F]](_.conditions)

  def operator[F[_]]: Lens[EngineState[F], Option[Operator]] = Focus[EngineState[F]](_.operator)
}
