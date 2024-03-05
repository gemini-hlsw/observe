// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Applicative
import cats.ApplicativeThrow
import cats.data.NonEmptySet
import cats.effect.Async
import cats.effect.Ref
import cats.effect.Sync
import cats.effect.Temporal
import cats.syntax.all.*
import eu.timepit.refined.types.numeric.PosInt
import fs2.Stream
import lucuma.core.enums.Instrument
import lucuma.core.enums.ObserveClass
import lucuma.core.enums.SequenceType
import lucuma.core.enums.Site
import lucuma.core.math.Wavelength
import lucuma.core.model.sequence
import lucuma.core.model.sequence.ExecutionConfig
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.StaticConfig
import lucuma.core.model.sequence.{Step => OdbStep}
import lucuma.core.util.TimeSpan
import mouse.all.*
import observe.common.ObsQueriesGQL.ObsQuery.Data.{Observation => OdbObservation}
import observe.engine.Action.ActionState
import observe.engine.*
import observe.model.Observation
import observe.model.*
import observe.model.dhs.*
import observe.model.enums.Resource
import observe.server.InstrumentSystem.*
import observe.server.ObserveFailure.Unexpected
import observe.server.SequenceGen.StepActionsGen
import observe.server.SequenceGen.StepGen
import observe.server.altair.Altair
import observe.server.altair.AltairController
import observe.server.altair.AltairControllerDisabled
import observe.server.gcal.*
import observe.server.gems.Gems
import observe.server.gems.GemsController
import observe.server.gems.GemsControllerDisabled
import observe.server.gmos.GmosController
import observe.server.gmos.GmosController.GmosSite
import observe.server.gmos.GmosControllerDisabled
import observe.server.gmos.GmosHeader
import observe.server.gmos.GmosNorth
import observe.server.gmos.GmosNorth.given
import observe.server.gmos.GmosNorthController
import observe.server.gmos.GmosObsKeywordsReader
import observe.server.gmos.GmosSouth
import observe.server.gmos.GmosSouth.given
import observe.server.gmos.GmosSouthController
import observe.server.gmos.NSObserveCommand
import observe.server.keywords._
import observe.server.tcs.TcsController.LightPath
import observe.server.tcs.TcsController.LightSource
import observe.server.tcs.*
import org.typelevel.log4cats.Logger

//trait SeqTranslate[F[_]] extends ObserveActions {
trait SeqTranslate[F[_]] {
  def sequence(sequence: OdbObservation)(using
    tio: Temporal[F]
  ): F[(List[Throwable], Option[SequenceGen[F]])]

  def stopObserve(seqId: Observation.Id, graceful: Boolean)(using
    tio: Temporal[F]
  ): EngineState[F] => Option[Stream[F, EventType[F]]]

  def abortObserve(seqId: Observation.Id)(using
    tio: Temporal[F]
  ): EngineState[F] => Option[Stream[F, EventType[F]]]

  def pauseObserve(seqId: Observation.Id, graceful: Boolean)(using
    tio: Temporal[F]
  ): EngineState[F] => Option[Stream[F, EventType[F]]]

  def resumePaused(seqId: Observation.Id)(using
    tio: Temporal[F]
  ): EngineState[F] => Option[Stream[F, EventType[F]]]

}

object SeqTranslate {

  class SeqTranslateImpl[F[_]: Async: Logger](
    site:                             Site,
    systemss:                         Systems[F],
    gmosNsCmd:                        Ref[F, Option[NSObserveCommand]],
    @annotation.unused conditionsRef: Ref[F, Conditions]
  ) extends SeqTranslate[F] {

    private val overriddenSystems = new OverriddenSystems[F](systemss)

    private def step[S <: StaticConfig, D <: DynamicConfig](
      obsCfg:     OdbObservation,
      step:       OdbStep[D],
      dataIdx:    PosInt,
      stepType:   StepType,
      insSpec:    InstrumentSpecifics[S, D],
      instf:      SystemOverrides => InstrumentSystem[F],
      instHeader: KeywordsClient[F] => Header[F]
    ): StepGen[F] = {

      def buildStep(
        dataId:    DataId,
        otherSysf: Map[Resource, SystemOverrides => System[F]],
        headers:   SystemOverrides => HeaderExtraData => List[Header[F]],
        stepType:  StepType
      ): SequenceGen.StepGen[F] = {

        val configs: Map[Resource | Instrument, SystemOverrides => Action[F]] =
          otherSysf.map { case (r, sf) =>
            val kind = ActionType.Configure(r)
            r -> { (ov: SystemOverrides) =>
              sf(ov).configure.as(Response.Configured(r)).toAction(kind)
            }
          } + (insSpec.instrument -> { (ov: SystemOverrides) =>
            instf(ov).configure
              .as(Response.Configured(insSpec.instrument))
              .toAction(ActionType.Configure(insSpec.instrument))
          })

        def rest(ctx: HeaderExtraData, ov: SystemOverrides): List[ParallelActions[F]] = {
          val inst = instf(ov)
          val env  = ObserveEnvironment(
            systemss.odb,
            overriddenSystems.dhs(ov),
            stepType,
            obsCfg.id,
            instf(ov),
            otherSysf.values.toList.map(_(ov)),
            headers(ov),
            ctx
          )
          // Request the instrument to build the observe actions and merge them with the progress
          // Also catches any errors in the process of running an observation
          inst.instrumentActions.observeActions(env)
        }

        SequenceGen.PendingStepGen[F](
          step.id,
          dataId,
          otherSysf.keys.toSet + insSpec.instrument,
          (ov: SystemOverrides) => instf(ov).observeControl,
          StepActionsGen(
            systemss.odb
              .stepStartStep(obsCfg.id, step.instrumentConfig, step.stepConfig, step.observeClass)
              .as(Response.Ignored)
              .toAction(ActionType.OdbEvent),
            systemss.odb
              .stepStartConfigure(obsCfg.id)
              .as(Response.Ignored)
              .toAction(ActionType.OdbEvent),
            configs,
            systemss.odb
              .stepEndConfigure(obsCfg.id)
              .as(Response.Ignored)
              .toAction(ActionType.OdbEvent),
            systemss.odb
              .stepStartObserve(obsCfg.id)
              .as(Response.Ignored)
              .toAction(ActionType.OdbEvent),
            rest,
            systemss.odb
              .stepEndObserve(obsCfg.id)
              .as(Response.Ignored)
              .toAction(ActionType.OdbEvent),
            systemss.odb
              .stepEndStep(obsCfg.id)
              .as(Response.Ignored)
              .toAction(ActionType.OdbEvent)
          ),
          instConfig = step.instrumentConfig,
          config = step.stepConfig,
          breakpoint = step.breakpoint
        )

      }

      buildStep(
        DataId(s"${obsCfg.title}-$dataIdx"),
        calcSystems(obsCfg, step.stepConfig, step.instrumentConfig, stepType, insSpec),
        (ov: SystemOverrides) =>
          calcHeaders(obsCfg, step, stepType, instHeader)(instf(ov).keywordsClient),
        stepType
      )
    }

    override def sequence(sequence: OdbObservation)(using
      tio: Temporal[F]
    ): F[(List[Throwable], Option[SequenceGen[F]])] = sequence.execution.config match {
      case Some(c @ InstrumentExecutionConfig.GmosNorth(_)) => buildSequenceGmosN(sequence, c)
      case Some(c @ InstrumentExecutionConfig.GmosSouth(_)) => buildSequenceGmosS(sequence, c)
      case _                                                => ApplicativeThrow[F].raiseError(new Exception("Unknown sequence type"))
    }

    private def buildSequence[S <: StaticConfig, D <: DynamicConfig](
      sequence:   OdbObservation,
      data:       ExecutionConfig[S, D],
      insSpec:    InstrumentSpecifics[S, D],
      instf:      (SystemOverrides, StepType, D) => InstrumentSystem[F],
      instHeader: D => KeywordsClient[F] => Header[F]
    ): F[(List[Throwable], Option[SequenceGen[F]])] = {
      val nextAtom         = data.acquisition.map(_.nextAtom).orElse(data.science.map(_.nextAtom))
      val startIdx: PosInt = PosInt.unsafeFrom(1)

      val sequenceType =
        if (data.acquisition.isDefined) SequenceType.Acquisition else SequenceType.Science

      nextAtom
        .map(atom =>
          val (a, b) = atom.steps.toList.zipWithIndex.map { case (x, i) =>
            insSpec
              .calcStepType(
                x.stepConfig,
                data.static,
                x.instrumentConfig,
                x.observeClass
              )
              .map { t =>
                step(
                  sequence,
                  x,
                  PosInt.unsafeFrom(startIdx.value + i),
                  t,
                  insSpec,
                  (ov: SystemOverrides) => instf(ov, t, x.instrumentConfig),
                  instHeader(x.instrumentConfig)
                )
              }
          }.separate

          (a,
           b.nonEmpty
             .option(b)
             .map(
               SequenceGen(
                 sequence,
                 Instrument.GmosNorth,
                 sequenceType,
                 data.static,
                 atom.id,
                 _
               )
             )
          )
        )
        .getOrElse((List.empty, none))
        .pure[F]
    }

    private def buildSequenceGmosN(
      obsCfg: OdbObservation,
      data:   InstrumentExecutionConfig.GmosNorth
    ): F[(List[Throwable], Option[SequenceGen[F]])] =
      buildSequence(
        obsCfg,
        data.executionConfig,
        GmosNorth.specifics,
        (ov: SystemOverrides, t: StepType, d: DynamicConfig.GmosNorth) =>
          GmosNorth.build(
            overriddenSystems.gmosNorth(ov),
            overriddenSystems.dhs(ov),
            gmosNsCmd,
            t,
            data.executionConfig.static,
            d
          ),
        (d: DynamicConfig.GmosNorth) =>
          (kwClient: KeywordsClient[F]) =>
            GmosHeader.header(
              kwClient,
              GmosObsKeywordsReader(data.executionConfig.static, d),
              systemss.gmosKeywordReader,
              systemss.tcsKeywordReader
            )
      )

    private def buildSequenceGmosS(
      obsCfg: OdbObservation,
      data:   InstrumentExecutionConfig.GmosSouth
    ): F[(List[Throwable], Option[SequenceGen[F]])] =
      buildSequence(
        obsCfg,
        data.executionConfig,
        GmosSouth.specifics,
        (ov: SystemOverrides, t: StepType, d: DynamicConfig.GmosSouth) =>
          GmosSouth.build(
            overriddenSystems.gmosSouth(ov),
            overriddenSystems.dhs(ov),
            gmosNsCmd,
            t,
            data.executionConfig.static,
            d
          ),
        (d: DynamicConfig.GmosSouth) =>
          (kwClient: KeywordsClient[F]) =>
            GmosHeader.header(
              kwClient,
              GmosObsKeywordsReader(data.executionConfig.static, d),
              systemss.gmosKeywordReader,
              systemss.tcsKeywordReader
            )
      )

    private def deliverObserveCmd(seqId: Observation.Id, f: ObserveControl[F] => F[Unit])(
      st: EngineState[F]
    ): Option[Stream[F, EventType[F]]] = {

      def isObserving(v: Action[F]): Boolean =
        v.kind === ActionType.Observe && v.state.runState.started

      for {
        obsSeq <- st.sequences.get(seqId)
        if obsSeq.seq.current.execution
          .exists(isObserving)
        stId   <- obsSeq.seq.currentStep.map(_.id)
        curStp <- obsSeq.seqGen.steps.find(_.id === stId)
        obsCtr <- curStp.some.collect {
                    case SequenceGen.PendingStepGen(_, _, _, obsControl, _, _, _, _, _) =>
                      obsControl
                  }
      } yield Stream.eval(
        f(obsCtr(obsSeq.overrides)).attempt
          .flatMap(handleError)
      )
    }

    private def handleError: Either[Throwable, Unit] => F[EventType[F]] = {
      case Left(e: ObserveFailure) => Event.logErrorMsgF(ObserveFailure.explain(e))
      case Left(e: Throwable)      =>
        Event.logErrorMsgF(ObserveFailure.explain(ObserveFailure.ObserveException(e)))
      case _                       => Event.nullEvent[F, EngineState[F], SeqEvent].pure[F].widen[EventType[F]]
    }

    override def stopObserve(seqId: Observation.Id, graceful: Boolean)(using
      tio: Temporal[F]
    ): EngineState[F] => Option[Stream[F, EventType[F]]] = st => {
      def f(oc: ObserveControl[F]): F[Unit] = oc match {
        case CompleteControl(StopObserveCmd(stop), _, _, _, _, _) => stop(graceful)
        case UnpausableControl(StopObserveCmd(stop), _)           => stop(graceful)
        case _                                                    => Applicative[F].unit
      }
      deliverObserveCmd(seqId, f)(st).orElse(stopPaused(seqId).apply(st))
    }

    override def abortObserve(seqId: Observation.Id)(using
      tio: Temporal[F]
    ): EngineState[F] => Option[Stream[F, EventType[F]]] = st => {
      def f(oc: ObserveControl[F]): F[Unit] = oc match {
        case CompleteControl(_, AbortObserveCmd(abort), _, _, _, _) => abort
        case UnpausableControl(_, AbortObserveCmd(abort))           => abort
        case _                                                      => Applicative[F].unit
      }

      deliverObserveCmd(seqId, f)(st).orElse(abortPaused(seqId).apply(st))
    }

    override def pauseObserve(seqId: Observation.Id, graceful: Boolean)(using
      tio: Temporal[F]
    ): EngineState[F] => Option[Stream[F, EventType[F]]] = {
      def f(oc: ObserveControl[F]): F[Unit] = oc match {
        case CompleteControl(_, _, PauseObserveCmd(pause), _, _, _) => pause(graceful)
        case _                                                      => Applicative[F].unit
      }
      deliverObserveCmd(seqId, f)
    }

    override def resumePaused(seqId: Observation.Id)(using
      tio: Temporal[F]
    ): EngineState[F] => Option[Stream[F, EventType[F]]] = (st: EngineState[F]) => {
      val observeIndex: Option[(ObserveContext[F], Option[TimeSpan], Int)] =
        st.sequences
          .get(seqId)
          .flatMap(
            _.seq.current.execution.zipWithIndex.find(_._1.kind === ActionType.Observe).flatMap {
              case (a, i) =>
                a.state.runState match {
                  case ActionState.Paused(c: ObserveContext[F] @unchecked) =>
                    (c,
                     a.state.partials.collectFirst { case x: Progress =>
                       x.progress
                     },
                     i
                    ).some
                  case _                                                   => none
                }
            }
          )

      observeIndex.map { case (obCtx, t, i) =>
        Stream.emit[F, EventType[F]](
          Event.actionResume[F, EngineState[F], SeqEvent](
            seqId,
            i,
            obCtx
              .progress(ElapsedTime(t.getOrElse(TimeSpan.Zero)))
              .mergeHaltR(obCtx.resumePaused(obCtx.expTime))
              .handleErrorWith(catchObsErrors[F])
          )
        )
      }
    }

    private def endPaused(seqId: Observation.Id, l: ObserveContext[F] => Stream[F, Result])(
      st: EngineState[F]
    ): Option[Stream[F, EventType[F]]] =
      st.sequences
        .get(seqId)
        .flatMap(
          _.seq.current.execution.zipWithIndex.find(_._1.kind === ActionType.Observe).flatMap {
            case (a, i) =>
              a.state.runState match {
                case ActionState.Paused(c: ObserveContext[F] @unchecked) =>
                  Stream
                    .eval(
                      Event.actionResume(seqId, i, l(c).handleErrorWith(catchObsErrors[F])).pure[F]
                    )
                    .some
                case _                                                   => none
              }
          }
        )

    private def stopPaused(
      seqId: Observation.Id
    ): EngineState[F] => Option[Stream[F, EventType[F]]] =
      endPaused(seqId, _.stopPaused)

    private def abortPaused(
      seqId: Observation.Id
    ): EngineState[F] => Option[Stream[F, EventType[F]]] =
      endPaused(seqId, _.abortPaused)

    import TcsController.Subsystem.*

    private def flatOrArcTcsSubsystems(inst: Instrument): NonEmptySet[TcsController.Subsystem] =
      NonEmptySet.of(AGUnit, (if (inst.hasOI) List(OIWFS) else List.empty)*)

    private def extractWavelength(s: DynamicConfig): Option[Wavelength] = s match {
      case DynamicConfig.GmosNorth(_, _, _, _, gratingConfig, filter, _) =>
        gratingConfig.map(_.wavelength).orElse(filter.map(_.wavelength))
      case DynamicConfig.GmosSouth(_, _, _, _, gratingConfig, filter, _) =>
        gratingConfig.map(_.wavelength).orElse(filter.map(_.wavelength))
    }

    private def getTcs[S <: StaticConfig, D <: DynamicConfig](
      subs:          NonEmptySet[TcsController.Subsystem],
      useGaos:       Boolean,
      inst:          InstrumentSpecifics[S, D],
      lsource:       LightSource,
      obsConfig:     OdbObservation,
      stepConfig:    StepConfig,
      dynamicConfig: D
    ): SystemOverrides => System[F] = site match {
      case Site.GS =>
        if (useGaos) { (ov: SystemOverrides) =>
          TcsSouth.fromConfig[F](
            overriddenSystems.tcsSouth(ov),
            subs,
            Gems.fromConfig[F](overriddenSystems.gems(ov), systemss.guideDb, obsConfig).some,
            inst,
            systemss.guideDb
          )(
            obsConfig.targetEnvironment,
            stepConfig,
            LightPath(lsource, inst.sfName(dynamicConfig)),
            extractWavelength(dynamicConfig)
          ): System[F]
        } else { (ov: SystemOverrides) =>
          TcsSouth
            .fromConfig[F](overriddenSystems.tcsSouth(ov), subs, None, inst, systemss.guideDb)(
              obsConfig.targetEnvironment,
              stepConfig,
              LightPath(lsource, inst.sfName(dynamicConfig)),
              extractWavelength(dynamicConfig)
            ): System[F]
        }

      case Site.GN =>
        if (useGaos) { (ov: SystemOverrides) =>
          TcsNorth.fromConfig[F](overriddenSystems.tcsNorth(ov),
                                 subs,
                                 Altair(overriddenSystems.altair(ov)).some,
                                 inst,
                                 systemss.guideDb
          )(
            obsConfig.targetEnvironment,
            stepConfig,
            LightPath(lsource, inst.sfName(dynamicConfig)),
            extractWavelength(dynamicConfig)
          ): System[F]
        } else { (ov: SystemOverrides) =>
          TcsNorth
            .fromConfig[F](overriddenSystems.tcsNorth(ov), subs, none, inst, systemss.guideDb)(
              obsConfig.targetEnvironment,
              stepConfig,
              LightPath(lsource, inst.sfName(dynamicConfig)),
              extractWavelength(dynamicConfig)
            ): System[F]
        }
    }

    private def calcSystems[S <: StaticConfig, D <: DynamicConfig](
      obsConfig:     OdbObservation,
      stepConfig:    StepConfig,
      dynamicConfig: D,
      stepType:      StepType,
      instSpec:      InstrumentSpecifics[S, D]
    ): Map[Resource, SystemOverrides => System[F]] = {

      def adaptGcal(b: GcalController[F] => Gcal[F])(ov: SystemOverrides): Gcal[F] = b(
        overriddenSystems.gcal(ov)
      )
      def defaultGcal: SystemOverrides => Gcal[F]                                  = adaptGcal(Gcal.defaultGcal)

      stepType match {
        case StepType.CelestialObject(inst) =>
          Map(
            Resource.TCS  -> getTcs(
              inst.hasOI.fold(allButGaos, allButGaosNorOi),
              useGaos = false,
              instSpec,
              TcsController.LightSource.Sky,
              obsConfig,
              stepConfig,
              dynamicConfig
            ),
            Resource.Gcal -> defaultGcal
          )

        case StepType.NodAndShuffle(inst) =>
          Map(
            Resource.TCS  -> getTcs(
              inst.hasOI.fold(allButGaos, allButGaosNorOi),
              useGaos = false,
              instSpec,
              TcsController.LightSource.Sky,
              obsConfig,
              stepConfig,
              dynamicConfig
            ),
            Resource.Gcal -> defaultGcal
          )

        case StepType.FlatOrArc(inst, gcalCfg) =>
          Map(
            Resource.TCS  -> getTcs(
              flatOrArcTcsSubsystems(inst),
              useGaos = false,
              instSpec,
              TcsController.LightSource.GCAL,
              obsConfig,
              stepConfig,
              dynamicConfig
            ),
            Resource.Gcal -> adaptGcal(Gcal.fromConfig(site === Site.GS, gcalCfg))
          )

        case StepType.NightFlatOrArc(_, gcalCfg) =>
          Map(
            Resource.TCS  -> getTcs(
              NonEmptySet.of(AGUnit, OIWFS, M2, M1, Mount),
              useGaos = false,
              instSpec,
              TcsController.LightSource.GCAL,
              obsConfig,
              stepConfig,
              dynamicConfig
            ),
            Resource.Gcal -> adaptGcal(Gcal.fromConfig(site === Site.GS, gcalCfg))
          )

        case StepType.DarkOrBias(_) => Map.empty[Resource, SystemOverrides => System[F]]

        case StepType.ExclusiveDarkOrBias(_) | StepType.DarkOrBiasNS(_) =>
          Map[Resource, SystemOverrides => System[F]](
            Resource.Gcal -> defaultGcal
          )

        case StepType.AltairObs(inst) =>
          Map(
            Resource.TCS  -> getTcs(
              inst.hasOI.fold(allButGaos, allButGaosNorOi).add(Gaos),
              useGaos = true,
              instSpec,
              TcsController.LightSource.AO,
              obsConfig,
              stepConfig,
              dynamicConfig
            ),
            Resource.Gcal -> defaultGcal
          )

        // case StepType.AlignAndCalib => Map.empty[Resource, SystemOverrides => System[F]]

        case StepType.Gems(inst) =>
          Map(
            Resource.TCS  -> getTcs(
              inst.hasOI.fold(allButGaos, allButGaosNorOi).add(Gaos),
              useGaos = true,
              instSpec,
              TcsController.LightSource.AO,
              obsConfig,
              stepConfig,
              dynamicConfig
            ),
            Resource.Gcal -> defaultGcal
          )
      }
    }

//    private def calcInstHeader(
//      config:     CleanConfig,
//      instrument: Instrument,
//      kwClient:   KeywordsClient[F]
//    ): Header[F] =
//      instrument match {
//        case Instrument.F2                       =>
//          Flamingos2Header.header[F](kwClient,
//                                     Flamingos2Header.ObsKeywordsReaderODB(config),
//                                     systemss.tcsKeywordReader
//          )
//        case Instrument.GmosS | Instrument.GmosN =>
//          GmosHeader.header[F](kwClient,
//                               GmosObsKeywordsReader(config),
//                               systemss.gmosKeywordReader,
//                               systemss.tcsKeywordReader
//          )
//        case Instrument.Gnirs                    =>
//          GnirsHeader.header[F](kwClient, systemss.gnirsKeywordReader, systemss.tcsKeywordReader)
//        case Instrument.Gpi                      =>
//          GpiHeader.header[F](systemss.gpi.gdsClient,
//                              systemss.tcsKeywordReader,
//                              ObsKeywordReader[F](config, site)
//          )
//        case Instrument.Ghost                    =>
//          GhostHeader.header[F]
//        case Instrument.Niri                     =>
//          NiriHeader.header[F](kwClient, systemss.niriKeywordReader, systemss.tcsKeywordReader)
//        case Instrument.Nifs                     =>
//          NifsHeader.header[F](kwClient, systemss.nifsKeywordReader, systemss.tcsKeywordReader)
//        case Instrument.Gsaoi                    =>
//          GsaoiHeader.header[F](kwClient, systemss.tcsKeywordReader, systemss.gsaoiKeywordReader)
//      }
//
    private def commonHeaders[D <: DynamicConfig](
      obsCfg:        OdbObservation,
      stepCfg:       OdbStep[D],
      tcsSubsystems: List[TcsController.Subsystem],
      kwClient:      KeywordsClient[F]
    )(ctx: HeaderExtraData): Header[F] =
      new StandardHeader(
        kwClient,
        ObsKeywordReader[F, D](obsCfg, stepCfg, site),
        systemss.tcsKeywordReader,
        StateKeywordsReader[F](ctx.conditions, ctx.operator, ctx.observer),
        tcsSubsystems
      )
//
//    private def gwsHeaders(kwClient: KeywordsClient[F]): Header[F] =
//      GwsHeader.header(kwClient, systemss.gwsKeywordReader)

    private def gcalHeader(kwClient: KeywordsClient[F]): Header[F] =
      GcalHeader.header(kwClient, systemss.gcalKeywordReader)

//    private def altairHeader(kwClient: KeywordsClient[F]): Header[F] =
//      AltairHeader.header[F](
//        kwClient,
//        systemss.altairKeywordReader,
//        systemss.tcsKeywordReader
//      )
//
//    private def altairLgsHeader(guideStar: GuideStarType, kwClient: KeywordsClient[F]): Header[F] =
//      if (guideStar === GuideStarType.LGS) {
//        AltairLgsHeader.header(kwClient, systemss.altairKeywordReader)
//      } else {
//        dummyHeader[F]
//      }
//
//    private def gemsHeaders(
//      kwClient:   KeywordsClient[F],
//      obsKReader: ObsKeywordsReader[F],
//      tcsKReader: TcsKeywordsReader[F]
//    ): Header[F] = GemsHeader.header[F](
//      kwClient,
//      systemss.gemsKeywordsReader,
//      obsKReader,
//      tcsKReader
//    )
//
    private def calcHeaders[D <: DynamicConfig](
      obsCfg:     OdbObservation,
      stepCfg:    OdbStep[D],
      stepType:   StepType,
      instHeader: KeywordsClient[F] => Header[F]
    ): KeywordsClient[F] => HeaderExtraData => List[Header[F]] = stepType match {
      case StepType.CelestialObject(_) | StepType.NodAndShuffle(_) =>
        (kwClient: KeywordsClient[F]) =>
          (ctx: HeaderExtraData) =>
            List(
              commonHeaders(obsCfg, stepCfg, allButGaos.toList, kwClient)(ctx),
              // gwsHeaders(kwClient),
              instHeader(kwClient)
            )

      case StepType.AltairObs(_) =>
        (kwClient: KeywordsClient[F]) =>
          (ctx: HeaderExtraData) =>
            // Order is important
            List(
              commonHeaders(obsCfg, stepCfg, allButGaos.toList, kwClient)(ctx),
//              altairHeader(kwClient),
//              altairLgsHeader(Altair.guideStarType(obsCfg), kwClient),
//              gwsHeaders(kwClient),
              instHeader(kwClient)
            )

      case StepType.FlatOrArc(inst, _) =>
        (kwClient: KeywordsClient[F]) =>
          (ctx: HeaderExtraData) =>
            List(
              commonHeaders(obsCfg, stepCfg, flatOrArcTcsSubsystems(inst).toList, kwClient)(ctx),
              gcalHeader(kwClient),
//            gwsHeaders(kwClient),
              instHeader(kwClient)
            )

      case StepType.NightFlatOrArc(_, _) =>
        (kwClient: KeywordsClient[F]) =>
          (ctx: HeaderExtraData) =>
            List(
              commonHeaders(obsCfg, stepCfg, List(AGUnit, OIWFS, M2, M1, Mount), kwClient)(ctx),
              gcalHeader(kwClient),
//            gwsHeaders(kwClient),
              instHeader(kwClient)
            )

      case StepType.DarkOrBias(_) | StepType.DarkOrBiasNS(_) | StepType.ExclusiveDarkOrBias(_) =>
        (kwClient: KeywordsClient[F]) =>
          (ctx: HeaderExtraData) =>
            List(
              commonHeaders(obsCfg, stepCfg, Nil, kwClient)(ctx),
//            gwsHeaders(kwClient),
              instHeader(kwClient)
            )

//      case StepType.AlignAndCalib =>
//        (_: KeywordsClient[F]) =>
//          (_: HeaderExtraData) => List.empty[Header[F]] // No headers for A&C

      case StepType.Gems(_) =>
        (kwClient: KeywordsClient[F]) =>
          (ctx: HeaderExtraData) =>
            List(
              commonHeaders(obsCfg, stepCfg, allButGaos.toList, kwClient)(ctx),
//            gwsHeaders(kwClient),
//            gemsHeaders(kwClient, ObsKeywordReader[F](config, site), systemss.tcsKeywordReader),
              instHeader(kwClient)
            )
    }
  }

  def apply[F[_]: Async: Logger](
    site:          Site,
    systems:       Systems[F],
    conditionsRef: Ref[F, Conditions]
  ): F[SeqTranslate[F]] =
    Ref
      .of[F, Option[NSObserveCommand]](none)
      .map(new SeqTranslateImpl(site, systems, _, conditionsRef))

//  def dataIdFromConfig[F[_]: MonadThrow](config: CleanConfig): F[DataId] =
//    EitherT
//      .fromEither[F](
//        config
//          .extractObsAs[String](DATA_LABEL_PROP)
//          .map(toDataId)
//          .leftMap(e => ObserveFailure.Unexpected(ConfigUtilOps.explain(e)))
//      )
//      .widenRethrowT

  class OverriddenSystems[F[_]: Sync: Logger](systems: Systems[F]) {

    private val tcsSouthDisabled: TcsSouthController[F]   = new TcsSouthControllerDisabled[F]
    private val tcsNorthDisabled: TcsNorthController[F]   = new TcsNorthControllerDisabled[F]
    private val gemsDisabled: GemsController[F]           = new GemsControllerDisabled[F]
    private val altairDisabled: AltairController[F]       = new AltairControllerDisabled[F]
    private val dhsDisabled: DhsClientProvider[F]         = (_: String) => new DhsClientDisabled[F]
    private val gcalDisabled: GcalController[F]           = new GcalControllerDisabled[F]
//    private val flamingos2Disabled: Flamingos2Controller[F] = new Flamingos2ControllerDisabled[F]
    private val gmosSouthDisabled: GmosSouthController[F] =
      new GmosControllerDisabled[F, GmosSite.South.type]("GMOS-S")
    private val gmosNorthDisabled: GmosNorthController[F] =
      new GmosControllerDisabled[F, GmosSite.North.type]("GMOS-N")
//    private val gsaoiDisabled: GsaoiController[F]           = new GsaoiControllerDisabled[F]
//    private val gpiDisabled: GpiController[F]               = new GpiControllerDisabled[F](systems.gpi.statusDb)
//    private val ghostDisabled: GhostController[F]           = new GhostControllerDisabled[F]
//    private val nifsDisabled: NifsController[F]             = new NifsControllerDisabled[F]
//    private val niriDisabled: NiriController[F]             = new NiriControllerDisabled[F]
//    private val gnirsDisabled: GnirsController[F]           = new GnirsControllerDisabled[F]

    def tcsSouth(overrides: SystemOverrides): TcsSouthController[F] =
      if (overrides.isTcsEnabled.value) systems.tcsSouth
      else tcsSouthDisabled

    def tcsNorth(overrides: SystemOverrides): TcsNorthController[F] =
      if (overrides.isTcsEnabled.value) systems.tcsNorth
      else tcsNorthDisabled

    def gems(overrides: SystemOverrides): GemsController[F] =
      if (overrides.isTcsEnabled.value) systems.gems
      else gemsDisabled

    def altair(overrides: SystemOverrides): AltairController[F] =
      if (overrides.isTcsEnabled.value) systems.altair
      else altairDisabled

    def dhs(overrides: SystemOverrides): DhsClientProvider[F] =
      if (overrides.isDhsEnabled.value) systems.dhs
      else dhsDisabled

    def gcal(overrides: SystemOverrides): GcalController[F] =
      if (overrides.isGcalEnabled.value) systems.gcal
      else gcalDisabled

//    def flamingos2(overrides: SystemOverrides): Flamingos2Controller[F] =
//      if (overrides.isInstrumentEnabled) systems.flamingos2
//      else flamingos2Disabled

    def gmosNorth(overrides: SystemOverrides): GmosNorthController[F] =
      if (overrides.isInstrumentEnabled.value) systems.gmosNorth
      else gmosNorthDisabled

    def gmosSouth(overrides: SystemOverrides): GmosSouthController[F] =
      if (overrides.isInstrumentEnabled.value) systems.gmosSouth
      else gmosSouthDisabled

//    def gsaoi(overrides: SystemOverrides): GsaoiController[F] =
//      if (overrides.isInstrumentEnabled) systems.gsaoi
//      else gsaoiDisabled
//
//    def gpi(overrides: SystemOverrides): GpiController[F] =
//      if (overrides.isInstrumentEnabled) systems.gpi
//      else gpiDisabled
//
//    def ghost(overrides: SystemOverrides): GhostController[F] =
//      if (overrides.isInstrumentEnabled) systems.ghost
//      else ghostDisabled
//
//    def nifs(overrides: SystemOverrides): NifsController[F] =
//      if (overrides.isInstrumentEnabled) systems.nifs
//      else nifsDisabled
//
//    def niri(overrides: SystemOverrides): NiriController[F] =
//      if (overrides.isInstrumentEnabled) systems.niri
//      else niriDisabled
//
//    def gnirs(overrides: SystemOverrides): GnirsController[F] =
//      if (overrides.isInstrumentEnabled) systems.gnirs
//      else gnirsDisabled

  }

  def calcStepType(
    inst:       Instrument,
    stepConfig: StepConfig,
    obsClass:   ObserveClass
  ): Either[ObserveFailure, StepType] = stepConfig match {
    case StepConfig.Bias | StepConfig.Dark   => StepType.DarkOrBias(inst).asRight
    case c: StepConfig.Gcal                  =>
      if (obsClass =!= ObserveClass.DayCal && inst.hasOI) StepType.NightFlatOrArc(inst, c).asRight
      else StepType.FlatOrArc(inst, c).asRight
    case StepConfig.Science(offset, guiding) =>
      // TODO: Here goes the logic to differentiate between a non GAOS, GeMS ot Altair observation.
      StepType.CelestialObject(inst).asRight
    case StepConfig.SmartGcal(smartGcalType) => Unexpected("Smart GCAL is not supported").asLeft
  }

}
