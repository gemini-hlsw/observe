// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import scala.concurrent.duration._
import cats._
import cats.data.EitherT
import cats.data.NonEmptySet
import cats.effect.{ Async, Ref, Sync, Temporal }
import cats.syntax.all._
import edu.gemini.seqexec.odb.SeqexecSequence
import edu.gemini.spModel.core.Wavelength
import edu.gemini.spModel.gemini.altair.AltairParams.GuideStarType
import edu.gemini.spModel.obscomp.InstConstants.DATA_LABEL_PROP
import edu.gemini.spModel.obscomp.InstConstants.OBSERVE_TYPE_PROP
import edu.gemini.spModel.obscomp.InstConstants.SCIENCE_OBSERVE_TYPE
import eu.timepit.refined.types.numeric.PosInt
import fs2.Stream
import org.typelevel.log4cats.Logger
import lucuma.core.enums.Site
import lucuma.schemas.ObservationDB.Enums.SequenceType
import mouse.all._
import observe.engine.Action.ActionState
import observe.engine._
import observe.model.Observation
import observe.model.dhs._
import observe.model.enum.Instrument
import observe.model.enum.Resource
import observe.model.{ Progress => _, _ }
import observe.server.CleanConfig.extractItem
import observe.server.ConfigUtilOps._
import observe.server.InstrumentSystem._
import observe.server.SequenceConfiguration._
import observe.server.SequenceGen.StepActionsGen
import observe.server.SequenceGen.StepGen
import observe.server.altair.Altair
import observe.server.altair.AltairController
import observe.server.altair.AltairControllerDisabled
import observe.server.altair.AltairHeader
import observe.server.altair.AltairLgsHeader
import observe.server.flamingos2.Flamingos2
import observe.server.flamingos2.Flamingos2Controller
import observe.server.flamingos2.Flamingos2ControllerDisabled
import observe.server.flamingos2.Flamingos2Header
import observe.server.gcal._
import observe.server.gems.Gems
import observe.server.gems.GemsController
import observe.server.gems.GemsControllerDisabled
import observe.server.gems.GemsHeader
import observe.server.ghost.Ghost
import observe.server.ghost.GhostController
import observe.server.ghost.GhostControllerDisabled
import observe.server.ghost.GhostHeader
import observe.server.gmos.GmosController
import observe.server.gmos.GmosControllerDisabled
import observe.server.gmos.GmosHeader
import observe.server.gmos.GmosNorth
import observe.server.gmos.GmosNorthController
import observe.server.gmos.GmosObsKeywordsReader
import observe.server.gmos.GmosSouth
import observe.server.gmos.GmosSouthController
import observe.server.gmos.NSObserveCommand
import observe.server.gnirs._
import observe.server.gpi.Gpi
import observe.server.gpi.GpiController
import observe.server.gpi.GpiControllerDisabled
import observe.server.gpi.GpiHeader
import observe.server.gsaoi._
import observe.server.gws.GwsHeader
import observe.server.keywords._
import observe.server.nifs._
import observe.server.niri._
import observe.server.tcs.TcsController.LightPath
import observe.server.tcs.TcsController.LightSource
import observe.server.tcs._
import observe.server.transition.{ SEQUENCE_TYPE_NAME, STEP_ID_NAME }
import squants.Time
import squants.time.TimeConversions._
import edu.gemini.spModel.seqcomp.SeqConfigNames.OCS_KEY

trait SeqTranslate[F[_]] extends ObserveActions {

  def sequence(obsId: Observation.Id, sequence: SeqexecSequence)(implicit
    tio:              Temporal[F]
  ): F[(List[Throwable], Option[SequenceGen[F]])]

  def stopObserve(seqId: Observation.Id, graceful: Boolean)(implicit
    tio:                 Temporal[F]
  ): EngineState[F] => Option[Stream[F, EventType[F]]]

  def abortObserve(seqId: Observation.Id)(implicit
    tio:                  Temporal[F]
  ): EngineState[F] => Option[Stream[F, EventType[F]]]

  def pauseObserve(seqId: Observation.Id, graceful: Boolean)(implicit
    tio:                  Temporal[F]
  ): EngineState[F] => Option[Stream[F, EventType[F]]]

  def resumePaused(seqId: Observation.Id)(implicit
    tio:                  Temporal[F]
  ): EngineState[F] => Option[Stream[F, EventType[F]]]

}

object SeqTranslate {
  private class SeqTranslateImpl[F[_]: Async: Logger](
    site:      Site,
    systemss:  Systems[F],
    gmosNsCmd: Ref[F, Option[NSObserveCommand]]
  ) extends SeqTranslate[F] {

    private val overriddenSystems = new OverriddenSystems[F](systemss)

    private def step(
      obsIdName:   Observation.IdName,
      dataIdx:     PosInt,
      config:      CleanConfig,
      imageFileId: Option[ImageFileId],
      isNightSeq:  Boolean
    ): F[StepGen[F]] = {
      def buildStep(
        stepId:       StepId,
        dataId:       DataId,
        sequenceType: SequenceType,
        instRes:      Resource,
        insSpec:      InstrumentSpecifics,
        instf:        SystemOverrides => InstrumentSystem[F],
        otherSysf:    Map[Resource, SystemOverrides => System[F]],
        headers:      SystemOverrides => HeaderExtraData => List[Header[F]],
        stepType:     StepType
      ): SequenceGen.StepGen[F] = {

        val configs: Map[Resource, SystemOverrides => Action[F]] = otherSysf.map { case (r, sf) =>
          val kind = ActionType.Configure(r)
          r -> { ov: SystemOverrides =>
            sf(ov).configure(config).as(Response.Configured(r)).toAction(kind)
          }
        } + (instRes -> { ov: SystemOverrides =>
          instf(ov)
            .configure(config)
            .as(Response.Configured(instRes))
            .toAction(ActionType.Configure(instRes))
        })

        def rest(ctx: HeaderExtraData, ov: SystemOverrides): List[ParallelActions[F]] = {
          val inst = instf(ov)
          val env  = ObserveEnvironment(
            systemss.odb,
            overriddenSystems.dhs(ov),
            config,
            stepType,
            obsIdName,
            stepId,
            dataIdx,
            sequenceType,
            instf(ov),
            insSpec,
            otherSysf.values.toList.map(_(ov)),
            headers(ov),
            ctx
          )
          // Request the instrument to build the observe actions and merge them with the progress
          // Also catches any errors in the process of running an observation
          inst.instrumentActions(config).observeActions(env)
        }

        extractStatus(config) match {
          case StepState.Pending =>
            SequenceGen.PendingStepGen(
              stepId,
              dataId,
              config,
              otherSysf.keys.toSet + instRes,
              { ov: SystemOverrides => instf(ov).observeControl(config) },
              StepActionsGen(configs, rest)
            )
          case StepState.Skipped =>
            SequenceGen.SkippedStepGen(
              stepId,
              dataId,
              config
            )
          // TODO: This case should be for completed Steps only. Fail when step status is unknown.
          case _                 =>
            SequenceGen.CompletedStepGen(
              stepId,
              dataId,
              config,
              imageFileId
            )
        }
      }

      for {
        inst     <- MonadError[F, Throwable].fromEither(extractInstrument(config))
        insSpecs  = instrumentSpecs(inst)
        stepType <-
          insSpecs.calcStepType(config, isNightSeq).fold(_.raiseError[F, StepType], _.pure[F])
        stepId   <- config.extractAs[StepId](OCS_KEY / STEP_ID_NAME).toF
        seqType  <- config.extractAs[SequenceType](OCS_KEY / SEQUENCE_TYPE_NAME).toF
        dataId   <- dataIdFromConfig[F](config)
        is        = toInstrumentSys(inst)
        systems  <- calcSystems(config, stepType, insSpecs)
        headers  <- calcHeaders(config, stepType, inst)
      } yield buildStep(
        stepId,
        dataId,
        seqType,
        inst,
        insSpecs,
        is,
        systems,
        (ov: SystemOverrides) => headers(is(ov).keywordsClient),
        stepType
      )
    }

    override def sequence(obsId: Observation.Id, sequence: SeqexecSequence)(implicit
      tio:                       Temporal[F]
    ): F[(List[Throwable], Option[SequenceGen[F]])] = {

      // TODO: Retrive sequence name from ODB sequence
      val obsName = "Dummy"

      // Step Configs are wrapped in a CleanConfig to fix some known inconsistencies that can appear in the sequence
      val configs = sequence.config.getAllSteps.toList.map(CleanConfig(_))

      val isNightSeq: Boolean = configs.exists(
        _.extractObsAs[String](OBSERVE_TYPE_PROP).exists(_ === SCIENCE_OBSERVE_TYPE)
      )

      // TODO: Retrieve step ids from the config
      val steps = configs.zipWithIndex
        .map { case (c, i) =>
          step(
            Observation.IdName(obsId, obsName),
            PosInt.unsafeFrom(i + 1),
            c,
            sequence.datasets.get(i).map(x => toImageFileId(x.filename)),
            isNightSeq
          ).attempt
        }
        .sequence
        .map(_.separate)

      val instName = configs.headOption
        .map(extractInstrument)
        .getOrElse(Either.left(ObserveFailure.UnrecognizedInstrument("UNKNOWN")))

      steps.map { sts =>
        instName.fold(e => (List(e), none),
                      i =>
                        sts match {
                          case (errs, ss) =>
                            (
                              errs,
                              ss.headOption.map { _ =>
                                SequenceGen(
                                  obsId,
                                  obsName,
                                  sequence.title,
                                  i,
                                  ss
                                )
                              }
                            )
                        }
        )
      }
    }

    private def deliverObserveCmd(seqId: Observation.Id, f: ObserveControl[F] => F[Unit])(
      st:                                EngineState[F]
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
                    case SequenceGen.PendingStepGen(_, _, _, _, obsControl, _) => obsControl
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
      case _                       => Event.nullEvent[F].pure[F].widen[EventType[F]]
    }

    override def stopObserve(seqId: Observation.Id, graceful: Boolean)(implicit
      tio:                          Temporal[F]
    ): EngineState[F] => Option[Stream[F, EventType[F]]] = st => {
      def f(oc: ObserveControl[F]): F[Unit] = oc match {
        case CompleteControl(StopObserveCmd(stop), _, _, _, _, _) => stop(graceful)
        case UnpausableControl(StopObserveCmd(stop), _)           => stop(graceful)
        case _                                                    => Applicative[F].unit
      }
      deliverObserveCmd(seqId, f)(st).orElse(stopPaused(seqId).apply(st))
    }

    override def abortObserve(seqId: Observation.Id)(implicit
      tio:                           Temporal[F]
    ): EngineState[F] => Option[Stream[F, EventType[F]]] = st => {
      def f(oc: ObserveControl[F]): F[Unit] = oc match {
        case CompleteControl(_, AbortObserveCmd(abort), _, _, _, _) => abort
        case UnpausableControl(_, AbortObserveCmd(abort))           => abort
        case _                                                      => Applicative[F].unit
      }

      deliverObserveCmd(seqId, f)(st).orElse(abortPaused(seqId).apply(st))
    }

    override def pauseObserve(seqId: Observation.Id, graceful: Boolean)(implicit
      tio:                           Temporal[F]
    ): EngineState[F] => Option[Stream[F, EventType[F]]] = {
      def f(oc: ObserveControl[F]): F[Unit] = oc match {
        case CompleteControl(_, _, PauseObserveCmd(pause), _, _, _) => pause(graceful)
        case _                                                      => Applicative[F].unit
      }
      deliverObserveCmd(seqId, f)
    }

    override def resumePaused(seqId: Observation.Id)(implicit
      tio:                           Temporal[F]
    ): EngineState[F] => Option[Stream[F, EventType[F]]] = (st: EngineState[F]) => {
      val observeIndex: Option[(ObserveContext[F], Option[Time], Int)] =
        st.sequences
          .get(seqId)
          .flatMap(
            _.seq.current.execution.zipWithIndex.find(_._1.kind === ActionType.Observe).flatMap {
              case (a, i) =>
                a.state.runState match {
                  case ActionState.Paused(c: ObserveContext[F]) =>
                    (c,
                     a.state.partials.collectFirst { case x: Progress =>
                       x.progress
                     },
                     i
                    ).some
                  case _                                        => none
                }
            }
          )

      observeIndex.map { case (obCtx, t, i) =>
        Stream.emit[F, EventType[F]](
          Event.actionResume[F, EngineState[F], SeqEvent](
            seqId,
            i,
            obCtx
              .progress(ElapsedTime(t.getOrElse(0.0.seconds)))
              .mergeHaltR(obCtx.resumePaused(obCtx.expTime))
              .handleErrorWith(catchObsErrors[F])
          )
        )
      }
    }

    private def endPaused(seqId: Observation.Id, l: ObserveContext[F] => Stream[F, Result[F]])(
      st:                        EngineState[F]
    ): Option[Stream[F, EventType[F]]] =
      st.sequences
        .get(seqId)
        .flatMap(
          _.seq.current.execution.zipWithIndex.find(_._1.kind === ActionType.Observe).flatMap {
            case (a, i) =>
              a.state.runState match {
                case ActionState.Paused(c: ObserveContext[F]) =>
                  Stream
                    .eval(
                      Event.actionResume(seqId, i, l(c).handleErrorWith(catchObsErrors[F])).pure[F]
                    )
                    .some
                case _                                        => none
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

    def toInstrumentSys(inst: Instrument): SystemOverrides => InstrumentSystem[F] = inst match {
      case Instrument.F2    =>
        ov: SystemOverrides =>
          Flamingos2(overriddenSystems.flamingos2(ov), overriddenSystems.dhs(ov)): InstrumentSystem[
            F
          ]
      case Instrument.GmosS =>
        ov: SystemOverrides =>
          GmosSouth(overriddenSystems.gmosSouth(ov),
                    overriddenSystems.dhs(ov),
                    gmosNsCmd
          ): InstrumentSystem[F]
      case Instrument.GmosN =>
        ov: SystemOverrides =>
          GmosNorth(overriddenSystems.gmosNorth(ov),
                    overriddenSystems.dhs(ov),
                    gmosNsCmd
          ): InstrumentSystem[F]
      case Instrument.Gnirs =>
        ov: SystemOverrides =>
          Gnirs(overriddenSystems.gnirs(ov), overriddenSystems.dhs(ov)): InstrumentSystem[F]
      case Instrument.Gpi   =>
        ov: SystemOverrides => Gpi(overriddenSystems.gpi(ov)): InstrumentSystem[F]
      case Instrument.Ghost =>
        ov: SystemOverrides => Ghost(overriddenSystems.ghost(ov)): InstrumentSystem[F]
      case Instrument.Niri  =>
        ov: SystemOverrides =>
          Niri(overriddenSystems.niri(ov), overriddenSystems.dhs(ov)): InstrumentSystem[F]
      case Instrument.Nifs  =>
        ov: SystemOverrides =>
          Nifs(overriddenSystems.nifs(ov), overriddenSystems.dhs(ov)): InstrumentSystem[F]
      case Instrument.Gsaoi =>
        ov: SystemOverrides =>
          Gsaoi(overriddenSystems.gsaoi(ov), overriddenSystems.dhs(ov)): InstrumentSystem[F]
    }

    def instrumentSpecs(instrument: Instrument): InstrumentSpecifics = instrument match {
      case Instrument.F2    => Flamingos2.specifics
      case Instrument.GmosS => GmosSouth.specifics
      case Instrument.GmosN => GmosNorth.specifics
      case Instrument.Gnirs => Gnirs.specifics
      case Instrument.Gpi   => Gpi.specifics
      case Instrument.Ghost => Ghost.specifics
      case Instrument.Niri  => Niri.specifics
      case Instrument.Nifs  => Nifs.specifics
      case Instrument.Gsaoi => Gsaoi.specifics
    }

    import TcsController.Subsystem._

    private def flatOrArcTcsSubsystems(inst: Instrument): NonEmptySet[TcsController.Subsystem] =
      NonEmptySet.of(AGUnit, (if (inst.hasOI) List(OIWFS) else List.empty): _*)

    private def tryWavelength(inst: Instrument, config: CleanConfig): F[Option[Wavelength]] =
      extractWavelength(config) match {
        case Left(x)  =>
          Logger[F]
            .error(s"Cannot decode the wavelength for ${inst.label}") *> MonadError[F, Throwable]
            .raiseError(
              ObserveFailure.Execution(s"Cannot decode the wavelength from the sequence $x")
            )
        case Right(w) => w.pure[F]
      }

    private def getTcs(
      subs:    NonEmptySet[TcsController.Subsystem],
      useGaos: Boolean,
      inst:    InstrumentSpecifics,
      lsource: LightSource,
      config:  CleanConfig
    ): F[SystemOverrides => System[F]] =
      tryWavelength(inst.instrument, config).flatMap { w =>
        site match {
          case Site.GS =>
            if (useGaos)
              Gems
                .fromConfig[F](systemss.guideDb, config)
                .map(a =>
                  (ov: SystemOverrides) =>
                    TcsSouth.fromConfig[F](overriddenSystems.tcsSouth(ov),
                                           subs,
                                           a(overriddenSystems.gems(ov)).some,
                                           inst,
                                           systemss.guideDb
                    )(
                      config,
                      LightPath(lsource, inst.sfName(config)),
                      w
                    ): System[F]
                )
            else { (ov: SystemOverrides) =>
              TcsSouth.fromConfig[F](overriddenSystems.tcsSouth(ov),
                                     subs,
                                     None,
                                     inst,
                                     systemss.guideDb
              )(
                config,
                LightPath(lsource, inst.sfName(config)),
                w
              ): System[F]
            }.pure[F]

          case Site.GN =>
            if (useGaos) { (ov: SystemOverrides) =>
              TcsNorth.fromConfig[F](overriddenSystems.tcsNorth(ov),
                                     subs,
                                     Altair(overriddenSystems.altair(ov)).some,
                                     inst,
                                     systemss.guideDb
              )(
                config,
                LightPath(lsource, inst.sfName(config)),
                w
              ): System[F]
            }.pure[F]
            else { (ov: SystemOverrides) =>
              TcsNorth.fromConfig[F](overriddenSystems.tcsNorth(ov),
                                     subs,
                                     none,
                                     inst,
                                     systemss.guideDb
              )(
                config,
                LightPath(lsource, inst.sfName(config)),
                w
              ): System[F]
            }.pure[F]
        }
      }

    private def calcSystems(
      config:   CleanConfig,
      stepType: StepType,
      instSpec: InstrumentSpecifics
    ): F[Map[Resource, SystemOverrides => System[F]]] = {

      def adaptGcal(b: GcalController[F] => Gcal[F])(ov: SystemOverrides): Gcal[F] = b(
        overriddenSystems.gcal(ov)
      )
      def defaultGcal: SystemOverrides => Gcal[F]                                  = adaptGcal(Gcal.defaultGcal)

      stepType match {
        case StepType.CelestialObject(inst) =>
          getTcs(
            inst.hasOI.fold(allButGaos, allButGaosNorOi),
            useGaos = false,
            instSpec,
            TcsController.LightSource.Sky,
            config
          ).map { x =>
            Map(
              Resource.TCS  -> x,
              Resource.Gcal -> defaultGcal
            )
          }

        case StepType.NodAndShuffle(inst) =>
          getTcs(
            inst.hasOI.fold(allButGaos, allButGaosNorOi),
            useGaos = false,
            instSpec,
            TcsController.LightSource.Sky,
            config
          ).map { x =>
            Map(
              Resource.TCS  -> x,
              Resource.Gcal -> defaultGcal
            )
          }

        case StepType.FlatOrArc(inst) =>
          for {
            tcs  <- getTcs(flatOrArcTcsSubsystems(inst),
                           useGaos = false,
                           instSpec,
                           TcsController.LightSource.GCAL,
                           config
                    )
            gcal <- Gcal.fromConfig(site == Site.GS, config)
          } yield Map(Resource.TCS -> tcs, Resource.Gcal -> adaptGcal(gcal) _)

        case StepType.NightFlatOrArc(_) =>
          for {
            tcs  <- getTcs(NonEmptySet.of(AGUnit, OIWFS, M2, M1, Mount),
                           useGaos = false,
                           instSpec,
                           TcsController.LightSource.GCAL,
                           config
                    )
            gcal <- Gcal.fromConfig(site == Site.GS, config)
          } yield Map(Resource.TCS -> tcs, Resource.Gcal -> adaptGcal(gcal) _)

        case StepType.DarkOrBias(_) => Map.empty[Resource, SystemOverrides => System[F]].pure[F]

        case StepType.ExclusiveDarkOrBias(_) | StepType.DarkOrBiasNS(_) =>
          Map[Resource, SystemOverrides => System[F]](
            Resource.Gcal -> defaultGcal
          ).pure[F]

        case StepType.AltairObs(inst) =>
          getTcs(
            inst.hasOI.fold(allButGaos, allButGaosNorOi).add(Gaos),
            useGaos = true,
            instSpec,
            TcsController.LightSource.AO,
            config
          ).map { x =>
            Map(
              Resource.TCS  -> x,
              Resource.Gcal -> defaultGcal
            )
          }

        case StepType.AlignAndCalib => Map.empty[Resource, SystemOverrides => System[F]].pure[F]

        case StepType.Gems(inst) =>
          getTcs(
            inst.hasOI.fold(allButGaos, allButGaosNorOi).add(Gaos),
            useGaos = true,
            instSpec,
            TcsController.LightSource.AO,
            config
          ).map { x =>
            Map(
              Resource.TCS  -> x,
              Resource.Gcal -> defaultGcal
            )
          }
      }
    }

    private def calcInstHeader(
      config:     CleanConfig,
      instrument: Instrument,
      kwClient:   KeywordsClient[F]
    ): Header[F] =
      instrument match {
        case Instrument.F2                       =>
          Flamingos2Header.header[F](kwClient,
                                     Flamingos2Header.ObsKeywordsReaderODB(config),
                                     systemss.tcsKeywordReader
          )
        case Instrument.GmosS | Instrument.GmosN =>
          GmosHeader.header[F](kwClient,
                               GmosObsKeywordsReader(config),
                               systemss.gmosKeywordReader,
                               systemss.tcsKeywordReader
          )
        case Instrument.Gnirs                    =>
          GnirsHeader.header[F](kwClient, systemss.gnirsKeywordReader, systemss.tcsKeywordReader)
        case Instrument.Gpi                      =>
          GpiHeader.header[F](systemss.gpi.gdsClient,
                              systemss.tcsKeywordReader,
                              ObsKeywordReader[F](config, site)
          )
        case Instrument.Ghost                    =>
          GhostHeader.header[F]
        case Instrument.Niri                     =>
          NiriHeader.header[F](kwClient, systemss.niriKeywordReader, systemss.tcsKeywordReader)
        case Instrument.Nifs                     =>
          NifsHeader.header[F](kwClient, systemss.nifsKeywordReader, systemss.tcsKeywordReader)
        case Instrument.Gsaoi                    =>
          GsaoiHeader.header[F](kwClient, systemss.tcsKeywordReader, systemss.gsaoiKeywordReader)
      }

    private def commonHeaders(
      config:        CleanConfig,
      tcsSubsystems: List[TcsController.Subsystem],
      kwClient:      KeywordsClient[F]
    )(ctx:           HeaderExtraData): Header[F] =
      new StandardHeader(
        kwClient,
        ObsKeywordReader[F](config, site),
        systemss.tcsKeywordReader,
        StateKeywordsReader[F](ctx.conditions, ctx.operator, ctx.observer),
        tcsSubsystems
      )

    private def gwsHeaders(kwClient: KeywordsClient[F]): Header[F] =
      GwsHeader.header(kwClient, systemss.gwsKeywordReader)

    private def gcalHeader(kwClient: KeywordsClient[F]): Header[F] =
      GcalHeader.header(kwClient, systemss.gcalKeywordReader)

    private def altairHeader(kwClient: KeywordsClient[F]): Header[F] =
      AltairHeader.header[F](
        kwClient,
        systemss.altairKeywordReader,
        systemss.tcsKeywordReader
      )

    private def altairLgsHeader(guideStar: GuideStarType, kwClient: KeywordsClient[F]): Header[F] =
      if (guideStar === GuideStarType.LGS) {
        AltairLgsHeader.header(kwClient, systemss.altairKeywordReader)
      } else {
        dummyHeader[F]
      }

    private def gemsHeaders(
      kwClient:   KeywordsClient[F],
      obsKReader: ObsKeywordsReader[F],
      tcsKReader: TcsKeywordsReader[F]
    ): Header[F] = GemsHeader.header[F](
      kwClient,
      systemss.gemsKeywordsReader,
      obsKReader,
      tcsKReader
    )

    private def calcHeaders(
      config:     CleanConfig,
      stepType:   StepType,
      instrument: Instrument
    ): F[KeywordsClient[F] => HeaderExtraData => List[Header[F]]] = stepType match {
      case StepType.CelestialObject(_) | StepType.NodAndShuffle(_) =>
        { kwClient: KeywordsClient[F] => ctx: HeaderExtraData =>
          List(
            commonHeaders(config, allButGaos.toList, kwClient)(ctx),
            gwsHeaders(kwClient),
            calcInstHeader(config, instrument, kwClient)
          )
        }.pure[F]

      case StepType.AltairObs(_) =>
        Altair.guideStarType[F](config).map {
          gst => kwClient: KeywordsClient[F] => ctx: HeaderExtraData =>
            // Order is important
            List(
              commonHeaders(config, allButGaos.toList, kwClient)(ctx),
              altairHeader(kwClient),
              altairLgsHeader(gst, kwClient),
              gwsHeaders(kwClient),
              calcInstHeader(config, instrument, kwClient)
            )
        }

      case StepType.FlatOrArc(inst) =>
        { kwClient: KeywordsClient[F] => ctx: HeaderExtraData =>
          List(
            commonHeaders(config, flatOrArcTcsSubsystems(inst).toList, kwClient)(ctx),
            gcalHeader(kwClient),
            gwsHeaders(kwClient),
            calcInstHeader(config, instrument, kwClient)
          )
        }.pure[F]

      case StepType.NightFlatOrArc(_) =>
        { kwClient: KeywordsClient[F] => ctx: HeaderExtraData =>
          List(
            commonHeaders(config, List(AGUnit, OIWFS, M2, M1, Mount), kwClient)(ctx),
            gcalHeader(kwClient),
            gwsHeaders(kwClient),
            calcInstHeader(config, instrument, kwClient)
          )
        }.pure[F]

      case StepType.DarkOrBias(_) | StepType.DarkOrBiasNS(_) | StepType.ExclusiveDarkOrBias(_) =>
        { kwClient: KeywordsClient[F] => ctx: HeaderExtraData =>
          List(
            commonHeaders(config, Nil, kwClient)(ctx),
            gwsHeaders(kwClient),
            calcInstHeader(config, instrument, kwClient)
          )
        }.pure[F]

      case StepType.AlignAndCalib =>
        ((_: KeywordsClient[F]) => (_: HeaderExtraData) => List.empty[Header[F]])
          .pure[F] // No headers for A&C

      case StepType.Gems(_) =>
        { kwClient: KeywordsClient[F] => ctx: HeaderExtraData =>
          List(
            commonHeaders(config, allButGaos.toList, kwClient)(ctx),
            gwsHeaders(kwClient),
            gemsHeaders(kwClient, ObsKeywordReader[F](config, site), systemss.tcsKeywordReader),
            calcInstHeader(config, instrument, kwClient)
          )
        }.pure[F]
    }

  }

  def apply[F[_]: Async: Logger](site: Site, systems: Systems[F]): F[SeqTranslate[F]] =
    Ref.of[F, Option[NSObserveCommand]](none).map(new SeqTranslateImpl(site, systems, _))

  def dataIdFromConfig[F[_]: MonadError[*[_], Throwable]](config: CleanConfig): F[DataId] =
    EitherT
      .fromEither[F](
        config
          .extractObsAs[String](DATA_LABEL_PROP)
          .map(toDataId)
          .leftMap(e => ObserveFailure.Unexpected(ConfigUtilOps.explain(e)))
      )
      .widenRethrowT

  class OverriddenSystems[F[_]: Sync: Logger](systems: Systems[F]) {

    private val tcsSouthDisabled: TcsSouthController[F]     = new TcsSouthControllerDisabled[F]
    private val tcsNorthDisabled: TcsNorthController[F]     = new TcsNorthControllerDisabled[F]
    private val gemsDisabled: GemsController[F]             = new GemsControllerDisabled[F]
    private val altairDisabled: AltairController[F]         = new AltairControllerDisabled[F]
    private val dhsDisabled: DhsClient[F]                   = new DhsClientDisabled[F]
    private val gcalDisabled: GcalController[F]             = new GcalControllerDisabled[F]
    private val flamingos2Disabled: Flamingos2Controller[F] = new Flamingos2ControllerDisabled[F]
    private val gmosSouthDisabled: GmosSouthController[F]   =
      new GmosControllerDisabled[F, GmosController.SouthTypes]("GMOS-S")
    private val gmosNorthDisabled: GmosNorthController[F]   =
      new GmosControllerDisabled[F, GmosController.NorthTypes]("GMOS-N")
    private val gsaoiDisabled: GsaoiController[F]           = new GsaoiControllerDisabled[F]
    private val gpiDisabled: GpiController[F]               = new GpiControllerDisabled[F](systems.gpi.statusDb)
    private val ghostDisabled: GhostController[F]           = new GhostControllerDisabled[F]
    private val nifsDisabled: NifsController[F]             = new NifsControllerDisabled[F]
    private val niriDisabled: NiriController[F]             = new NiriControllerDisabled[F]
    private val gnirsDisabled: GnirsController[F]           = new GnirsControllerDisabled[F]

    def tcsSouth(overrides: SystemOverrides): TcsSouthController[F] =
      if (overrides.isTcsEnabled) systems.tcsSouth
      else tcsSouthDisabled

    def tcsNorth(overrides: SystemOverrides): TcsNorthController[F] =
      if (overrides.isTcsEnabled) systems.tcsNorth
      else tcsNorthDisabled

    def gems(overrides: SystemOverrides): GemsController[F] =
      if (overrides.isTcsEnabled) systems.gems
      else gemsDisabled

    def altair(overrides: SystemOverrides): AltairController[F] =
      if (overrides.isTcsEnabled) systems.altair
      else altairDisabled

    def dhs(overrides: SystemOverrides): DhsClient[F] =
      if (overrides.isDhsEnabled) systems.dhs
      else dhsDisabled

    def gcal(overrides: SystemOverrides): GcalController[F] =
      if (overrides.isGcalEnabled) systems.gcal
      else gcalDisabled

    def flamingos2(overrides: SystemOverrides): Flamingos2Controller[F] =
      if (overrides.isInstrumentEnabled) systems.flamingos2
      else flamingos2Disabled

    def gmosNorth(overrides: SystemOverrides): GmosNorthController[F] =
      if (overrides.isInstrumentEnabled) systems.gmosNorth
      else gmosNorthDisabled

    def gmosSouth(overrides: SystemOverrides): GmosSouthController[F] =
      if (overrides.isInstrumentEnabled) systems.gmosSouth
      else gmosSouthDisabled

    def gsaoi(overrides: SystemOverrides): GsaoiController[F] =
      if (overrides.isInstrumentEnabled) systems.gsaoi
      else gsaoiDisabled

    def gpi(overrides: SystemOverrides): GpiController[F] =
      if (overrides.isInstrumentEnabled) systems.gpi
      else gpiDisabled

    def ghost(overrides: SystemOverrides): GhostController[F] =
      if (overrides.isInstrumentEnabled) systems.ghost
      else ghostDisabled

    def nifs(overrides: SystemOverrides): NifsController[F] =
      if (overrides.isInstrumentEnabled) systems.nifs
      else nifsDisabled

    def niri(overrides: SystemOverrides): NiriController[F] =
      if (overrides.isInstrumentEnabled) systems.niri
      else niriDisabled

    def gnirs(overrides: SystemOverrides): GnirsController[F] =
      if (overrides.isInstrumentEnabled) systems.gnirs
      else gnirsDisabled

  }

}
