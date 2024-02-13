// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
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
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.StaticConfig
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
case class SequenceGen[F[_]](
  obsData:      OdbObservation,
  instrument:   Instrument,
  sequenceType: SequenceType,
  staticCfg:    StaticConfig,
  atomId:       Atom.Id,
  steps:        List[SequenceGen.StepGen[F]]
) {
  val resources: Set[Resource | Instrument] = steps
    .collect { case p: SequenceGen.PendingStepGen[F] =>
      p.resources
    }
    .foldMap(identity)

  def configActionCoord(stepId: Step.Id, r: Resource | Instrument): Option[ActionCoordsInSeq] =
    steps
      .find(_.id === stepId)
      .collect { case p: SequenceGen.PendingStepGen[F] => p }
      .flatMap(_.generator.configActionCoord(r))
      .map { case (ex, ac) => ActionCoordsInSeq(stepId, ex, ac) }

  def resourceAtCoords(c: ActionCoordsInSeq): Option[Resource | Instrument] =
    steps
      .find(_.id === c.stepId)
      .collect { case p: SequenceGen.PendingStepGen[F] => p }
      .flatMap(_.generator.resourceAtCoords(c.execIdx, c.actIdx))

  def stepIndex(stepId: Step.Id): Option[Int] = SequenceGen.stepIndex(steps, stepId)
}

object SequenceGen {

  trait StepStatusGen

  object StepStatusGen {
    object Null extends StepStatusGen
  }

  sealed trait StepGen[F[_]] {
    val id: Step.Id
    val dataId: DataId
    val genData: StepStatusGen
    val instConfig: DynamicConfig
    val config: StepConfig
  }

  object StepGen {
    def generate[F[_]](
      stepGen:         StepGen[F],
      systemOverrides: SystemOverrides,
      ctx:             HeaderExtraData
    ): EngineStep[F] =
      stepGen match {
        case p: PendingStepGen[F]                =>
          EngineStep.init[F](stepGen.id, p.generator.generate(ctx, systemOverrides))
        case SkippedStepGen(id, _, _, _, _)      =>
          EngineStep.skippedL[F].replace(true)(EngineStep.init[F](id, Nil))
        case CompletedStepGen(id, _, _, _, _, _) => EngineStep.init[F](id, Nil)
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

  case class PendingStepGen[F[_]](
    id:         Step.Id,
    dataId:     DataId,
    resources:  Set[Resource | Instrument],
    obsControl: SystemOverrides => InstrumentSystem.ObserveControl[F],
    generator:  StepActionsGen[F],
    genData:    StepStatusGen = StepStatusGen.Null,
    instConfig: DynamicConfig,
    config:     StepConfig,
    breakpoint: Breakpoint
  ) extends StepGen[F]

  case class SkippedStepGen[F[_]](
    id:         Step.Id,
    dataId:     DataId,
    genData:    StepStatusGen = StepStatusGen.Null,
    instConfig: DynamicConfig,
    config:     StepConfig
  ) extends StepGen[F]

  // Receiving a sequence from the ODB with a completed step without an image file id would be
  // weird, but I still use an Option just in case
  case class CompletedStepGen[F[_]](
    id:         Step.Id,
    dataId:     DataId,
    fileId:     Option[ImageFileId],
    genData:    StepStatusGen = StepStatusGen.Null,
    instConfig: DynamicConfig,
    config:     StepConfig
  ) extends StepGen[F]

  def stepIndex[F[_]](
    steps:  List[SequenceGen.StepGen[F]],
    stepId: Step.Id
  ): Option[Int] =
    steps.zipWithIndex.find(_._1.id === stepId).map(_._2)

}
