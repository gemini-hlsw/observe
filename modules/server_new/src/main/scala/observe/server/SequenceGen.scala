// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.data.NonEmptyList
import cats.syntax.all.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.TelescopeConfig as CoreTelescopeConfig
import mouse.all.*
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation as OdbObservation
import observe.engine.Action
import observe.engine.ActionCoordsInSeq
import observe.engine.ActionIndex
import observe.engine.EngineStep
import observe.engine.ExecutionIndex
import observe.engine.ParallelActions
import observe.model.SystemOverrides
import observe.model.dhs.DataId
import observe.model.dhs.ImageFileId
import observe.model.enums.Resource

/*
 * SequenceGen keeps all the information extracted from the ODB sequence.
 * It is combined with header parameters to build an engine.Sequence. It allows to rebuild the
 * engine sequence whenever any of those parameters change.
 */
trait InstrumentSequenceGen[F[_]] {
  type Static
  type Dynamic

  def obsData: OdbObservation
  def instrument: Instrument
  def staticCfg: Static
  def nextAtom: SequenceGen.AtomGen[F, Dynamic]
}

case class SequenceGen[F[_], S, D](
  obsData:    OdbObservation,
  instrument: Instrument,
  staticCfg:  S,
  nextAtom:   SequenceGen.AtomGen[F, D]
) extends InstrumentSequenceGen[F] {
  type Static  = S
  type Dynamic = D

  val resources: Set[Resource | Instrument] = nextAtom.steps
    .collect { case SequenceGen.PendingStepGen(_, _, resources, _, _, _, _, _, _, _) =>
      resources
    }
    .foldMap(identity)

  def configActionCoord(stepId: Step.Id, r: Resource | Instrument): Option[ActionCoordsInSeq] =
    nextAtom.steps
      .find(_.id === stepId)
      .collect { case p @ SequenceGen.PendingStepGen(_, _, _, _, _, _, _, _, _, _) => p }
      .flatMap(_.generator.configActionCoord(r))
      .map { case (ex, ac) => ActionCoordsInSeq(stepId, ex, ac) }

  def resourceAtCoords(c: ActionCoordsInSeq): Option[Resource | Instrument] =
    nextAtom.steps
      .find(_.id === c.stepId)
      .collect { case p @ SequenceGen.PendingStepGen(_, _, _, _, _, _, _, _, _, _) => p }
      .flatMap(_.generator.resourceAtCoords(c.execIdx, c.actIdx))

  def stepIndex(stepId: Step.Id): Option[Int] = SequenceGen.stepIndex(nextAtom.steps, stepId)
}

object SequenceGen {

  trait InstrumentAtomGen[F[_]] {
    type Dynamic

    def atomId: Atom.Id
    def sequenceType: SequenceType
    def steps: List[SequenceGen.StepGen[F, Dynamic]]
  }

  case class AtomGen[F[_], D](
    atomId:       Atom.Id,
    sequenceType: SequenceType,
    steps:        List[SequenceGen.StepGen[F, D]]
  ) extends InstrumentAtomGen[F] {
    type Dynamic = D
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
  }

  sealed trait StepGen[F[_], D] extends InstrumentStepGen[F] {
    type Dynamic = D

    val id: Step.Id
    val dataId: DataId
    val genData: StepStatusGen
    val instConfig: D
    val config: StepConfig
    val telescopeConfig: CoreTelescopeConfig
  }

  object StepGen {
    def generate[F[_], D](
      stepGen:         StepGen[F, D],
      systemOverrides: SystemOverrides,
      ctx:             HeaderExtraData
    ): EngineStep[F] =
      stepGen match {
        case p: PendingStepGen[F, ?]                =>
          EngineStep[F](stepGen.id, p.breakpoint, p.generator.generate(ctx, systemOverrides))
        case CompletedStepGen(id, _, _, _, _, _, _) =>
          EngineStep[F](id, Breakpoint.Disabled, Nil)
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
  ) extends StepGen[F, D]

  def stepIndex[F[_], D](
    steps:  List[SequenceGen.StepGen[F, D]],
    stepId: Step.Id
  ): Option[Int] =
    steps.zipWithIndex.find(_._1.id === stepId).map(_._2)

}
