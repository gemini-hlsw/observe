// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.data.NonEmptyList
import cats.syntax.all.*
import lucuma.core.model.sequence.{Atom, StepConfig}
import lucuma.core.model.sequence.gmos.{DynamicConfig, StaticConfig}
import mouse.all.*
import observe.engine.Action
import observe.engine.ActionCoordsInSeq
import observe.engine.ActionIndex
import observe.engine.ExecutionIndex
import observe.engine.ParallelActions
import observe.engine.Step as EngineStep
import observe.model.Observation
import observe.model.SystemOverrides
import observe.model.StepId
import observe.model.dhs.DataId
import observe.model.dhs.ImageFileId
import observe.model.enums.Instrument
import observe.model.enums.Resource
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation as OdbObservation

/*
 * SequenceGen keeps all the information extracted from the ODB sequence.
 * It is combined with header parameters to build an engine.Sequence. It allows to rebuild the
 * engine sequence whenever any of those parameters change.
 */
final case class SequenceGen[F[_]](
  obsData:    OdbObservation,
  instrument: Instrument,
  staticCfg:  StaticConfig,
  atomId:     Atom.Id,
  steps:      List[SequenceGen.StepGen[F]]
) {
  val resources: Set[Resource] = steps
    .collect { case p: SequenceGen.PendingStepGen[F] =>
      p.resources
    }
    .foldMap(identity)

  def configActionCoord(stepId: StepId, r: Resource): Option[ActionCoordsInSeq] =
    steps
      .find(_.id === stepId)
      .collect { case p: SequenceGen.PendingStepGen[F] => p }
      .flatMap(_.generator.configActionCoord(r))
      .map { case (ex, ac) => ActionCoordsInSeq(stepId, ex, ac) }

  def resourceAtCoords(c: ActionCoordsInSeq): Option[Resource] =
    steps
      .find(_.id === c.stepId)
      .collect { case p: SequenceGen.PendingStepGen[F] => p }
      .flatMap(_.generator.resourceAtCoords(c.execIdx, c.actIdx))

  def stepIndex(stepId: StepId): Option[Int] = SequenceGen.stepIndex(steps, stepId)
}

object SequenceGen {

  trait StepStatusGen

  object StepStatusGen {
    object Null extends StepStatusGen
  }

  sealed trait StepGen[F[_]] {
    val id: StepId
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

  final case class StepActionsGen[F[_]](
    configs: Map[Resource, SystemOverrides => Action[F]],
    post:    (HeaderExtraData, SystemOverrides) => List[ParallelActions[F]]
  ) {
    def generate(ctx: HeaderExtraData, overrides: SystemOverrides): List[ParallelActions[F]] =
      NonEmptyList.fromList(configs.values.toList.map(_(overrides))).toList ++
        post(ctx, overrides)

    def configActionCoord(r: Resource): Option[(ExecutionIndex, ActionIndex)]   = {
      val i = configs.keys.toIndexedSeq.indexOf(r)
      (i >= 0)
        .option(i)
        .map(i => (ExecutionIndex(0), ActionIndex(i.toLong)))
    }
    def resourceAtCoords(ex: ExecutionIndex, ac: ActionIndex): Option[Resource] =
      if (ex.self === 0) configs.keys.toList.get(ac.self)
      else None

  }

  final case class PendingStepGen[F[_]](
    override val id:         StepId,
    override val dataId:     DataId,
    resources:               Set[Resource],
    obsControl:              SystemOverrides => InstrumentSystem.ObserveControl[F],
    generator:               StepActionsGen[F],
    override val genData:    StepStatusGen = StepStatusGen.Null,
    override val instConfig: DynamicConfig,
    override val config:     StepConfig
  ) extends StepGen[F]

  final case class SkippedStepGen[F[_]](
    override val id:         StepId,
    override val dataId:     DataId,
    override val genData:    StepStatusGen = StepStatusGen.Null,
    override val instConfig: DynamicConfig,
    override val config:     StepConfig
  ) extends StepGen[F]

  // Receiving a sequence from the ODB with a completed step without an image file id would be
  // weird, but I still use an Option just in case
  final case class CompletedStepGen[F[_]](
    override val id:         StepId,
    override val dataId:     DataId,
    fileId:                  Option[ImageFileId],
    override val genData:    StepStatusGen = StepStatusGen.Null,
    override val instConfig: DynamicConfig,
    override val config:     StepConfig
  ) extends StepGen[F]

  def stepIndex[F[_]](
    steps:  List[SequenceGen.StepGen[F]],
    stepId: StepId
  ): Option[Int] =
    steps.zipWithIndex.find(_._1.id === stepId).map(_._2)

}
