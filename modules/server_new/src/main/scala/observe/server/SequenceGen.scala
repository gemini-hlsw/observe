// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.data.NonEmptyList
import cats.syntax.all.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.math.SignalToNoise
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.TelescopeConfig as CoreTelescopeConfig
import lucuma.core.model.sequence.flamingos2.Flamingos2DynamicConfig
import lucuma.core.model.sequence.flamingos2.Flamingos2StaticConfig
import lucuma.core.model.sequence.gmos
import monocle.Focus
import monocle.Lens
import monocle.Prism
import monocle.macros.GenPrism
import mouse.all.*
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation as OdbObservation
import observe.model.SystemOverrides
import observe.model.dhs.DataId
import observe.model.dhs.ImageFileId
import observe.model.enums.Resource
import observe.server.engine.Action
import observe.server.engine.ActionCoordsInSeq
import observe.server.engine.ActionIndex
import observe.server.engine.EngineStep
import observe.server.engine.ExecutionIndex
import observe.server.engine.ParallelActions

/*
 * SequenceGen keeps all the information extracted from the ODB sequence.
 * It is combined with header parameters to build an engine.Sequence. It allows to rebuild the
 * engine sequence whenever any of those parameters change.
 */
sealed trait SequenceGen[F[_]] {
  type S
  type D
  def instrument: Instrument

  def obsData: OdbObservation
  def staticCfg: S
  def nextAtom: SequenceGen.AtomGen[F]

  val resources: Set[Resource | Instrument] = nextAtom.steps
    .collect { case SequenceGen.PendingStepGen(_, _, resources, _, _, _, _, _, _, _, _) =>
      resources
    }
    .foldMap(identity)

  def configActionCoord(stepId: Step.Id, r: Resource | Instrument): Option[ActionCoordsInSeq] =
    nextAtom.steps
      .find(_.id === stepId)
      .collect { case p @ SequenceGen.PendingStepGen(_, _, _, _, _, _, _, _, _, _, _) => p }
      .flatMap(_.generator.configActionCoord(r))
      .map { case (ex, ac) => ActionCoordsInSeq(stepId, ex, ac) }

  def resourceAtCoords(c: ActionCoordsInSeq): Option[Resource | Instrument] =
    nextAtom.steps
      .find(_.id === c.stepId)
      .collect { case p @ SequenceGen.PendingStepGen(_, _, _, _, _, _, _, _, _, _, _) => p }
      .flatMap(_.generator.resourceAtCoords(c.execIdx, c.actIdx))

  def stepIndex(stepId: Step.Id): Option[Int] = SequenceGen.stepIndex(nextAtom.steps, stepId)
}

object SequenceGen {
  case class GmosNorth[F[_]](
    obsData:   OdbObservation,
    staticCfg: gmos.StaticConfig.GmosNorth,
    nextAtom:  SequenceGen.AtomGen.GmosNorth[F]
  ) extends SequenceGen[F] {
    type S = gmos.StaticConfig.GmosNorth
    type D = gmos.DynamicConfig.GmosNorth

    val instrument: Instrument = Instrument.GmosNorth
  }

  object GmosNorth {
    def nextAtom[F[_]]: Lens[GmosNorth[F], AtomGen.GmosNorth[F]] = Focus[GmosNorth[F]](_.nextAtom)
  }

  case class GmosSouth[F[_]](
    obsData:   OdbObservation,
    staticCfg: gmos.StaticConfig.GmosSouth,
    nextAtom:  SequenceGen.AtomGen.GmosSouth[F]
  ) extends SequenceGen[F] {
    type S = gmos.StaticConfig.GmosSouth
    type D = gmos.DynamicConfig.GmosSouth

    val instrument: Instrument = Instrument.GmosSouth
  }

  object GmosSouth {
    def nextAtom[F[_]]: Lens[GmosSouth[F], AtomGen.GmosSouth[F]] = Focus[GmosSouth[F]](_.nextAtom)
  }

  case class Flamingos2[F[_]](
    obsData:   OdbObservation,
    staticCfg: Flamingos2StaticConfig,
    nextAtom:  SequenceGen.AtomGen.Flamingos2[F]
  ) extends SequenceGen[F] {
    type S = Flamingos2StaticConfig
    type D = Flamingos2DynamicConfig

    val instrument: Instrument = Instrument.Flamingos2
  }

  object Flamingos2 {
    def nextAtom[F[_]]: Lens[Flamingos2[F], AtomGen.Flamingos2[F]] =
      Focus[Flamingos2[F]](_.nextAtom)
  }

  sealed trait AtomGen[F[_]] {
    type D

    def atomId: Atom.Id
    def sequenceType: SequenceType
    def steps: List[SequenceGen.StepGen[F, D]]
  }

  object AtomGen {
    case class GmosNorth[F[_]](
      atomId:       Atom.Id,
      sequenceType: SequenceType,
      steps:        List[SequenceGen.StepGen[F, gmos.DynamicConfig.GmosNorth]]
    ) extends AtomGen[F] {
      type D = gmos.DynamicConfig.GmosNorth
    }

    case class GmosSouth[F[_]](
      atomId:       Atom.Id,
      sequenceType: SequenceType,
      steps:        List[SequenceGen.StepGen[F, gmos.DynamicConfig.GmosSouth]]
    ) extends AtomGen[F] {
      type D = gmos.DynamicConfig.GmosSouth
    }

    case class Flamingos2[F[_]](
      atomId:       Atom.Id,
      sequenceType: SequenceType,
      steps:        List[SequenceGen.StepGen[F, Flamingos2DynamicConfig]]
    ) extends AtomGen[F] {
      type D = Flamingos2DynamicConfig
    }
  }

  trait StepStatusGen

  object StepStatusGen {
    object Null extends StepStatusGen
  }

  sealed trait InstrumentStepGen[F[_]] {
    type Dynamic

    def id: Step.Id
    def dataId: DataId
    def genData: StepStatusGen
    def instConfig: Dynamic
    def config: StepConfig
    def telescopeConfig: CoreTelescopeConfig
    def signalToNoise: Option[SignalToNoise]
  }

  sealed trait StepGen[F[_], D] extends InstrumentStepGen[F] {
    type Dynamic = D
  }

  object StepGen {
    def generate[F[_], D](
      stepGen:         StepGen[F, D],
      systemOverrides: SystemOverrides,
      ctx:             HeaderExtraData
    ): (EngineStep[F], Breakpoint) =
      stepGen match {
        case p: PendingStepGen[F, ?]                =>
          (EngineStep[F](stepGen.id, p.generator.generate(ctx, systemOverrides)), p.breakpoint)
        case CompletedStepGen(id, _, _, _, _, _, _) =>
          (EngineStep[F](id, Nil), Breakpoint.Disabled)
      }
  }

  case class StepActionsGen[F[_]](
    preStep:     Action[F],
    preConfig:   Action[F],
    configs:     Map[Resource | Instrument, SystemOverrides => Action[F]],
    postConfig:  Action[F],
    preObserve:  Action[F],
    post:        (HeaderExtraData, SystemOverrides) => List[ParallelActions[F]],
    postObserve: Action[F],
    postStep:    Action[F]
  ) {
    def generate(ctx: HeaderExtraData, overrides: SystemOverrides): List[ParallelActions[F]] =
      List(
        NonEmptyList.one(preStep),
        NonEmptyList.one(preConfig)
      ) ++
        NonEmptyList.fromList(configs.values.toList.map(_(overrides))).toList ++
        List(
          NonEmptyList.one(postConfig),
          NonEmptyList.one(preObserve)
        ) ++
        post(ctx, overrides) ++
        List(
          NonEmptyList.one(postObserve),
          NonEmptyList.one(postStep)
        )

    val ConfigsExecutionIndex: Int                                                         = 2
    def configActionCoord(r: Resource | Instrument): Option[(ExecutionIndex, ActionIndex)] = {
      val i = configs.keys.toIndexedSeq.indexOf(r)
      (i >= 0)
        .option(i)
        .map(i => (ExecutionIndex(ConfigsExecutionIndex), ActionIndex(i.toLong)))
    }

    def resourceAtCoords(ex: ExecutionIndex, ac: ActionIndex): Option[Resource | Instrument] =
      if (ex.value === ConfigsExecutionIndex) configs.keys.toList.get(ac.value)
      else None

  }

  case class PendingStepGen[F[_], D](
    id:              Step.Id,
    dataId:          DataId,
    resources:       Set[Resource | Instrument],
    obsControl:      SystemOverrides => InstrumentSystem.ObserveControl[F],
    generator:       StepActionsGen[F],
    genData:         StepStatusGen = StepStatusGen.Null,
    instConfig:      D,
    config:          StepConfig,
    telescopeConfig: CoreTelescopeConfig,
    signalToNoise:   Option[SignalToNoise],
    breakpoint:      Breakpoint
  ) extends StepGen[F, D]

  // Receiving a sequence from the ODB with a completed step without an image file id would be
  // weird, but I still use an Option just in case
  case class CompletedStepGen[F[_], D](
    id:              Step.Id,
    dataId:          DataId,
    fileId:          Option[ImageFileId],
    genData:         StepStatusGen = StepStatusGen.Null,
    instConfig:      D,
    config:          StepConfig,
    telescopeConfig: CoreTelescopeConfig
  ) extends StepGen[F, D]:
    val signalToNoise: Option[SignalToNoise] = none

  def stepIndex[F[_], D](
    steps:  List[SequenceGen.StepGen[F, D]],
    stepId: Step.Id
  ): Option[Int] =
    steps.zipWithIndex.find(_._1.id === stepId).map(_._2)

  def gmosNorth[F[_]]: Prism[SequenceGen[F], GmosNorth[F]]   = GenPrism[SequenceGen[F], GmosNorth[F]]
  def gmosSouth[F[_]]: Prism[SequenceGen[F], GmosSouth[F]]   = GenPrism[SequenceGen[F], GmosSouth[F]]
  def flamingos2[F[_]]: Prism[SequenceGen[F], Flamingos2[F]] =
    GenPrism[SequenceGen[F], Flamingos2[F]]

  def replaceNextAtom[F[_]](atom: AtomGen[F])(seq: SequenceGen[F]): SequenceGen[F] =
    (seq, atom) match
      case (s @ SequenceGen.GmosNorth[F](_, _, _), a @ AtomGen.GmosNorth[F](_, _, _))   =>
        gmosNorth.andThen(GmosNorth.nextAtom).replace(a)(s)
      case (s @ SequenceGen.GmosSouth[F](_, _, _), a @ AtomGen.GmosSouth[F](_, _, _))   =>
        gmosSouth.andThen(GmosSouth.nextAtom).replace(a)(s)
      case (s @ SequenceGen.Flamingos2[F](_, _, _), a @ AtomGen.Flamingos2[F](_, _, _)) =>
        flamingos2.andThen(Flamingos2.nextAtom).replace(a)(s)
      case _                                                                            =>
        throw new IllegalArgumentException:
          s"Instrument mismatch when replacing atom in sequence. Atom: [$atom], Sequence: [$seq]."
}
