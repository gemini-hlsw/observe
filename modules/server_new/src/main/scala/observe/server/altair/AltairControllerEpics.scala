// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import algebra.instances.all.given
import cats.Eq
import cats.*
import cats.effect.Async
import cats.effect.Sync
import cats.syntax.all.*
import coulomb.*
import coulomb.policy.standard.given
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import coulomb.units.accepted.Millimeter
import edu.gemini.epics.acm.CarStateGEM5
import edu.gemini.observe.server.altair.LgsSfoControl
import lucuma.core.enums.Instrument
import lucuma.core.model.AltairConfig
import lucuma.core.model.AltairConfig.*
import lucuma.core.util.TimeSpan
import monocle.Focus
import mouse.boolean.*
import observe.model.enums.ApplyCommandResult
import observe.server.ObserveFailure
import observe.server.altair.AltairController.*
import observe.server.tcs.FocalPlaneScale.*
import observe.server.tcs.Gaos.PauseCondition.GaosGuideOff
import observe.server.tcs.Gaos.PauseCondition.OiOff
import observe.server.tcs.Gaos.PauseCondition.P1Off
import observe.server.tcs.Gaos.ResumeCondition.GaosGuideOn
import observe.server.tcs.Gaos.ResumeCondition.OiOn
import observe.server.tcs.Gaos.ResumeCondition.P1On
import observe.server.tcs.Gaos.*
import observe.server.tcs.TcsController.FocalPlaneOffset
import observe.server.tcs.*
import org.typelevel.log4cats.Logger

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.annotation.unused
import scala.language.implicitConversions

object AltairControllerEpics {
  case class EpicsAltairConfig(
    currentMatrixCoords:  (Quantity[Double, Millimeter], Quantity[Double, Millimeter]),
    preparedMatrixCoords: (Quantity[Double, Millimeter], Quantity[Double, Millimeter]),
    strapRTStatus:        Boolean,
    strapTempStatus:      Boolean,
    stapHVoltStatus:      Boolean,
    strapLoop:            Boolean,
    sfoLoop:              LgsSfoControl,
    strapGate:            Int,
    aoLoop:               Boolean
  )

  def apply[F[_]: Async](
    epicsAltair: => AltairEpics[F],
    epicsTcs:    => TcsEpics[F]
  )(using L: Logger[F]): AltairController[F] = new AltairController[F] {

    private def inRangeLinear[T: Order](vMin: T, vMax: T)(v: T): Boolean =
      v > vMin && v < vMax

    private def newPosition(starPos: (Quantity[Double, Millimeter], Quantity[Double, Millimeter]))(
      offset: FocalPlaneOffset
    ): (Quantity[Double, Millimeter], Quantity[Double, Millimeter]) =
      starPos.bimap(_ + offset.x.value, _ + offset.y.value)

    val CorrectionsOn: String  = "ON"
    val CorrectionsOff: String = "OFF"

    // The OT checks this, why do it again in Observe?
    private def newPosInRange(
      newPos: (Quantity[Double, Millimeter], Quantity[Double, Millimeter])
    ): Boolean = {
      val minX = -37.2.withUnit[Millimeter]
      val maxX = 37.2.withUnit[Millimeter]
      val minY = -37.2.withUnit[Millimeter]
      val maxY = 37.2.withUnit[Millimeter]

      newPos match {
        case (x, y) =>
          inRangeLinear(minX, maxX)(x) && inRangeLinear(minY, maxY)(y)
      }
    }

    private def validControlMatrix(
      mtxPos: (Quantity[Double, Millimeter], Quantity[Double, Millimeter])
    )(newPos: (Quantity[Double, Millimeter], Quantity[Double, Millimeter])): Boolean = {
      val limit = 5.0.withUnit[ArcSecond] :\ FOCAL_PLANE_SCALE

      val diff = newPos.bimap(_ - mtxPos._1, _ - mtxPos._2)
      limit.pow[2]

      diff._1.pow[2] + diff._2.pow[2] < limit.pow[2]
    }

    private def validateCurrentControlMatrix(
      currCfg: EpicsAltairConfig,
      newPos:  (Quantity[Double, Millimeter], Quantity[Double, Millimeter])
    ): Boolean = validControlMatrix(currCfg.currentMatrixCoords)(newPos)

    private def validatePreparedControlMatrix(
      currCfg: EpicsAltairConfig,
      newPos:  (Quantity[Double, Millimeter], Quantity[Double, Millimeter])
    ): Boolean = validControlMatrix(currCfg.preparedMatrixCoords)(newPos)

    private def prepareMatrix(
      newPos: (Quantity[Double, Millimeter], Quantity[Double, Millimeter])
    ): F[Unit] =
      epicsTcs.aoPrepareControlMatrix.setX(newPos._1.value) *>
        epicsTcs.aoPrepareControlMatrix.setY(newPos._2.value)

    private def pauseNgsMode(
      position:   (Quantity[Double, Millimeter], Quantity[Double, Millimeter]),
      currCfg:    EpicsAltairConfig,
      instrument: Instrument
    )(pauseReasons: PauseConditionSet): PauseReturn[F] = {
      // There are two reasons to stop NGS:
      // 1. This is an unguided step
      // 2. The current control matrix will not be valid for the end position after applying an offset (i.e. the offset
      // is too big).

      val guidedStep    = !pauseReasons.contains(PauseCondition.GaosGuideOff)
      val currMatrixOk  = validateCurrentControlMatrix(currCfg, position)
      val prepMatrixOk  = validatePreparedControlMatrix(currCfg, position)
      val isSmallOffset = pauseReasons.offsetO.forall(canGuideWhileOffseting(_, instrument))

      val needsToStop = !guidedStep || !currMatrixOk || !isSmallOffset

      val mustPrepareMatrix = (!currCfg.aoLoop || needsToStop) && guidedStep && !prepMatrixOk

      // Prepare action will mark prepare the command but not run it. That will be triggered as part of the TCS
      // configuration
      val configActions = mustPrepareMatrix.option(prepareMatrix(position))

      val pauseAction =
        L.debug(
          s"Pausing Altair NGS guiding because guidedStep=$guidedStep, currMatrixOk=$currMatrixOk"
        ) *>
          setCorrectionsOff *>
          L.debug("Altair guiding NGS paused") *>
          (L.debug("Flatting Altair DM") *> dmFlattenAction).whenA(!guidedStep)

      if (currCfg.aoLoop && needsToStop)
        PauseReturn[F](
          wasPaused = true,
          pauseAction.some,
          pauseTargetFilter =
            pauseReasons.offsetO.nonEmpty,                                        // if not guiding, pause target filter for offsets
          GuideCapabilities(canGuideM2 = false, canGuideM1 = false),
          configActions
        )
      else
        PauseReturn[F](
          wasPaused = false,
          L.debug(
            s"Skipped pausing Altair NGS guiding because currCfg.aoLoop=${currCfg.aoLoop}, guidedStep=$guidedStep, isSmallOffset = $isSmallOffset, currMatrixOk=$currMatrixOk"
          ).some,
          pauseTargetFilter =
            (!currCfg.aoLoop && pauseReasons.offsetO.nonEmpty) || !isSmallOffset, // pause target filter for unguided offset
          GuideCapabilities(canGuideM2 = currCfg.aoLoop, canGuideM1 = currCfg.aoLoop),
          configActions
        )

    }

    private val setCorrectionsOff: F[ApplyCommandResult] =
      epicsTcs.aoCorrect.setCorrections(CorrectionsOff) *> epicsTcs.aoCorrect.post(DefaultTimeout)

    private val setCorrectionsOn: F[ApplyCommandResult] =
      epicsTcs.aoCorrect.setCorrections(CorrectionsOn) *>
        epicsTcs.aoCorrect.setGains(1) *>
        epicsTcs.aoCorrect.post(DefaultTimeout)

    private def pauseResumeNgsMode(
      startPos:   (Quantity[Double, Millimeter], Quantity[Double, Millimeter]),
      currCfg:    EpicsAltairConfig,
      currOffset: FocalPlaneOffset,
      instrument: Instrument
    )(pauseReasons: PauseConditionSet, resumeReasons: ResumeConditionSet): AltairPauseResume[F] = {
      val newPos                = pauseReasons.offsetO
        .map(x => newPosition(startPos)(x.to))
        .getOrElse(newPosition(startPos)(currOffset))
      val forceFreeze           = !newPosInRange(newPos)
      val adjustedPauseReasons  =
        forceFreeze.fold(pauseReasons + PauseCondition.GaosGuideOff, pauseReasons)
      val adjustedResumeReasons =
        forceFreeze.fold(resumeReasons - ResumeCondition.GaosGuideOn, resumeReasons)

      val pauseResult = pauseNgsMode(newPos, currCfg, instrument)(adjustedPauseReasons)
      val resume      = resumeNgsMode(
        currCfg.aoLoop,
        currCfg.aoLoop && pauseResult.wasPaused,
        adjustedResumeReasons
      )

      AltairPauseResume(
        pauseResult.pauseAction,
        pauseResult.keepGuiding,
        pauseResult.pauseTargetFilter,
        resume.resumeAction,
        resume.keepGuiding,
        pauseResult.config,
        forceFreeze
      )
    }

    private val AoSettledTimeout  = TimeSpan.unsafeFromDuration(30, ChronoUnit.SECONDS)
    private val MatrixPrepTimeout = TimeSpan.unsafeFromDuration(10, ChronoUnit.SECONDS)

    private val dmFlattenAction: F[ApplyCommandResult] =
      epicsTcs.aoFlatten.mark *> epicsTcs.aoFlatten.post(DefaultTimeout)

    // Let's keep this check until we are sure the coordinates for the control matrix are properly estimated
    private val checkControlMatrix: F[Unit] = {
      val tolerance = 1e-3.withUnit[Millimeter]

      for {
        pmtxx <- epicsTcs.aoPreparedCMX.map(_.withUnit[Millimeter])
        pmtxy <- epicsTcs.aoPreparedCMY.map(_.withUnit[Millimeter])
        aogsx <- epicsTcs.aoGuideStarX.map(_.withUnit[Millimeter])
        aogsy <- epicsTcs.aoGuideStarY.map(_.withUnit[Millimeter])
        _     <-
          L.warn(
            s"Altair prepared matrix coordinates ($pmtxx, $pmtxy) don't match guide star coordinates ($aogsx, $aogsy)"
          ).whenA(
            (pmtxx - aogsx).pow[2] + (pmtxy - aogsy).pow[2] > tolerance.pow[2]
          )
      } yield ()
    }

    private def resumeNgsMode(
      aoOn:      Boolean,
      wasPaused: Boolean,
      reasons:   ResumeConditionSet
    ): ResumeReturn[F] = {
      val guidedStep = reasons.contains(ResumeCondition.GaosGuideOn)

      val action = if ((aoOn && !wasPaused) || !guidedStep)
        L.debug(
          s"Skipped resuming Altair NGS guiding because wasPaused=$wasPaused, guidedStep=$guidedStep"
        )
      else
        L.debug(s"Resume Altair NGS guiding because guidedStep=$guidedStep") *>
          epicsAltair.controlMatrixCalc.flatMap { x =>
            (L.debug("Altair control matrix calculation not yet ready, waiting for it") *>
              epicsAltair.waitMatrixCalc(CarStateGEM5.IDLE, MatrixPrepTimeout)).whenA(x.isBusy)
          } *>
          checkControlMatrix *>
          setCorrectionsOn *>
          L.debug("Altair NGS guiding resumed, waiting for it ti settle") *>
          epicsAltair.waitAoSettled(AoSettledTimeout) *>
          L.debug("Altair NGS guiding settled")

      ResumeReturn(
        action.some,
        GuideCapabilities(canGuideM2 = reasons.contains(ResumeCondition.GaosGuideOn),
                          canGuideM1 = reasons.contains(ResumeCondition.GaosGuideOn)
        )
      )
    }

    private def checkStrapLoopState(currCfg: EpicsAltairConfig): Either[ObserveFailure, Unit] =
      currCfg.strapRTStatus.either(
        ObserveFailure.Unexpected("Cannot start Altair STRAP loop, RT Control status is bad."),
        ()
      ) *>
        currCfg.strapTempStatus.either(
          ObserveFailure.Unexpected(
            "Cannot start Altair STRAP loop, Temperature Control status is bad."
          ),
          ()
        ) *>
        currCfg.stapHVoltStatus.either(
          ObserveFailure.Unexpected("Cannot start Altair STRAP loop, HVolt status is bad."),
          ()
        )

    private def startStrapGate(currCfg: EpicsAltairConfig): F[Unit] = (
      L.debug("Starting STRAP gate in Altair") *>
        epicsAltair.strapGateControl.setGate(1) *>
        epicsAltair.strapGateControl.post(DefaultTimeout) *>
        epicsAltair.waitForStrapGate(100, StrapGateTimeout) *>
        L.debug("STRAP gate started")
    ).unlessA(currCfg.strapGate =!= 0)

    private def stopStrapGate(currCfg: EpicsAltairConfig): F[Unit] = (
      L.debug("Stopping STRAP gate in Altair") *>
        epicsAltair.strapGateControl.setGate(0) *>
        epicsAltair.strapGateControl.post(DefaultTimeout) *>
        L.debug("STRAP gate stopped")
    ).whenA(currCfg.strapGate =!= 0)

    private val StrapLoopSettleTimeout =
      TimeSpan.unsafeFromDuration(10, ChronoUnit.SECONDS)

    private def startStrapLoop(currCfg: EpicsAltairConfig): F[Unit] = (
      L.debug("Starting STRAP loop in Altair") *>
        epicsAltair.strapControl.setActive(1) *>
        epicsAltair.strapControl.post(DefaultTimeout) *>
        epicsAltair.waitForStrapLoop(v = true, StrapLoopSettleTimeout) *>
        L.debug("STRAP loop started")
    ).unlessA(currCfg.strapLoop)

    private def stopStrapLoop(currCfg: EpicsAltairConfig): F[Unit] = (
      L.debug("Stopping STRAP loop in Altair") *>
        epicsAltair.strapControl.setActive(0) *>
        epicsAltair.strapControl.post(DefaultTimeout) *>
        L.debug("STRAP loop stopped")
    ).whenA(currCfg.strapLoop)

    given Eq[LgsSfoControl] = Eq.by(_.ordinal)

    private def startSfoLoop(currCfg: EpicsAltairConfig): F[Unit] = (
      L.debug("Start SFO loop in Altair") *>
        epicsAltair.sfoControl.setActive(LgsSfoControl.Enable) *>
        epicsAltair.sfoControl.post(DefaultTimeout) *>
        L.debug("SFO loop started")
    ).unlessA(currCfg.sfoLoop === LgsSfoControl.Enable)

    private def pauseSfoLoop(currCfg: EpicsAltairConfig): F[Unit] = (
      L.debug("Pause SFO loop in Altair") *>
        epicsAltair.sfoControl.setActive(LgsSfoControl.Pause) *>
        epicsAltair.sfoControl.post(DefaultTimeout) *>
        L.debug("SFO loop paused")
    ).whenA(currCfg.sfoLoop === LgsSfoControl.Enable)

    private def ttgsOn(strap: Boolean, sfo: Boolean, currCfg: EpicsAltairConfig): F[Unit] =
      checkStrapLoopState(currCfg).fold(ApplicativeError[F, Throwable].raiseError,
                                        Sync[F].delay(_)
      ) *>
        (startStrapGate(currCfg) *> startStrapLoop(currCfg)).whenA(strap) *>
        startSfoLoop(currCfg).whenA(sfo)

    private val ttgsOffEndo: Endo[EpicsAltairConfig] =
      Focus[EpicsAltairConfig](_.strapGate).replace(0) >>>
        Focus[EpicsAltairConfig](_.strapLoop).replace(false) >>>
        Focus[EpicsAltairConfig](_.sfoLoop).modify { v =>
          (v === LgsSfoControl.Disable).fold(LgsSfoControl.Disable, LgsSfoControl.Pause)
        }

    private def ttgsOff(currCfg: EpicsAltairConfig): F[Unit] =
      stopStrapGate(currCfg) *>
        stopStrapLoop(currCfg) *>
        pauseSfoLoop(currCfg)

    private def pauseResumeLgsMode(
      strap:      Boolean,
      sfo:        Boolean,
      startPos:   (Quantity[Double, Millimeter], Quantity[Double, Millimeter]),
      currCfg:    EpicsAltairConfig,
      currOffset: FocalPlaneOffset,
      instrument: Instrument
    )(pauseReasons: PauseConditionSet, resumeReasons: ResumeConditionSet): AltairPauseResume[F] = {
      val newPos                = pauseReasons.offsetO
        .map(x => newPosition(startPos)(x.to))
        .getOrElse(newPosition(startPos)(currOffset))
      val forceFreeze           = !newPosInRange(newPos)
      val adjustedPauseReasons  =
        forceFreeze.fold(pauseReasons + PauseCondition.GaosGuideOff, pauseReasons)
      val adjustedResumeReasons =
        forceFreeze.fold(resumeReasons - ResumeCondition.GaosGuideOn, resumeReasons)

      val pause      = pauseLgsMode(strap, sfo, currCfg, instrument)(adjustedPauseReasons)
      val updatedCfg = pause.wasPaused.fold(ttgsOffEndo(currCfg), currCfg)
      val resume     = resumeLgsMode(strap, sfo, updatedCfg, adjustedResumeReasons)

      AltairPauseResume(
        pause.pauseAction,
        pause.keepGuiding,
        pause.pauseTargetFilter,
        resume.resumeAction,
        resume.keepGuiding,
        none,
        forceFreeze
      )
    }

    private def pauseLgsMode(
      strap:      Boolean,
      sfo:        Boolean,
      currCfg:    EpicsAltairConfig,
      instrument: Instrument
    )(reasons: PauseConditionSet): PauseReturn[F] = {
      val guidedStep    = !reasons.contains(PauseCondition.GaosGuideOff)
      val isSmallOffset = reasons.offsetO.forall(canGuideWhileOffseting(_, instrument))
      val mustPauseNGS  = !(guidedStep && isSmallOffset) && (strap || sfo)
      val usingNGS      = currCfg.sfoLoop === LgsSfoControl.Enable || currCfg.strapLoop

      val pauseAction = L.debug(
        s"Pausing Altair LGS(strap = $strap, sfo = $sfo) guiding because guidedStep=$guidedStep, isSmallOffset=$isSmallOffset"
      ) *>
        ttgsOff(currCfg) *>
        setCorrectionsOff *>
        L.debug(s"Altair LGS(strap = $strap, sfo = $sfo) guiding paused") *>
        (L.debug("Flatting Altair DM") *> dmFlattenAction).whenA(!guidedStep)

      if (usingNGS && mustPauseNGS)
        PauseReturn(
          wasPaused = true,
          pauseAction.some,
          pauseTargetFilter = reasons.offsetO.nonEmpty,
          GuideCapabilities(canGuideM2 = false, canGuideM1 = false),
          none
        )
      else
        PauseReturn(
          wasPaused = false,
          L.debug(
            s"Skipped pausing Altair LGS(strap = $strap, sfo = $sfo) guiding, guidedStep=$guidedStep, isSmallOffset=$isSmallOffset"
          ).some,
          pauseTargetFilter = (!usingNGS && reasons.offsetO.nonEmpty) || !isSmallOffset,
          GuideCapabilities(canGuideM2 =
                              currCfg.sfoLoop === LgsSfoControl.Enable || currCfg.strapLoop,
                            canGuideM1 = true
          ),
          none
        )

    }

    private def resumeLgsMode(
      strap:      Boolean,
      sfo:        Boolean,
      currentCfg: EpicsAltairConfig,
      reasons:    ResumeConditionSet
    ): ResumeReturn[F] = {
      val guidedStep   = reasons.contains(ResumeCondition.GaosGuideOn)
      val alreadyThere =
        (currentCfg.sfoLoop === LgsSfoControl.Enable && sfo) && (currentCfg.strapLoop && strap)

      val action = if (!alreadyThere && guidedStep)
        L.debug(
          s"Resuming Altair LGS(strap = $strap, sfo = $sfo) guiding because guidedStep=$guidedStep"
        ) *>
          setCorrectionsOn *>
          ttgsOn(strap, sfo, currentCfg) *>
          L.debug(s"Altair LGS(strap = $strap, sfo = $sfo) guiding resumed")
      else
        L.debug(s"Skipped resuming Altair LGS(strap = $strap, sfo = $sfo) guiding")

      ResumeReturn(
        action.some,
        GuideCapabilities(
          canGuideM2 = (strap || sfo) && guidedStep,
          canGuideM1 = (strap || sfo) && guidedStep
        )
      )
    }

    private def turnOff(currentCfg: EpicsAltairConfig): AltairPauseResume[F] = {
      val pauseAction =
        if (currentCfg.aoLoop)
          L.debug("Turning Altair guiding off") *>
            setCorrectionsOff *>
            L.debug("Altair guiding turned off")
        else
          L.debug("Skipped turning Altair guiding off")

      AltairPauseResume(
        pauseAction.some,
        GuideCapabilities(canGuideM2 = false, canGuideM1 = false),
        pauseTargetFilter = true,
        none,
        GuideCapabilities(canGuideM2 = false, canGuideM1 = false),
        none,
        forceFreeze = true
      )
    }

    override def pauseResume(
      pauseReasons:  PauseConditionSet,
      resumeReasons: ResumeConditionSet,
      currentOffset: FocalPlaneOffset,
      instrument:    Instrument
    )(cfg: AltairConfig): F[AltairPauseResume[F]] = {
      val unguidedStep                                = pauseReasons.contains(GaosGuideOff)
      def guideOff(turnOff: Boolean): Option[F[Unit]] =
        (turnOff || unguidedStep).option(
          L.debug(
            s"Pausing Altair guiding"
          ) *>
            setCorrectionsOff *>
            L.debug("Altair guiding paused") *>
            (L.debug("Flatting Altair DM") *> dmFlattenAction).whenA(unguidedStep).void
        )

      retrieveConfig.map { currCfg =>
        cfg match {
          case Ngs(_, starPos)    =>
            pauseResumeNgsMode(starPos.bimap(_.toValue[Double], _.toValue[Double]),
                               currCfg,
                               currentOffset,
                               instrument
            )(
              pauseReasons,
              resumeReasons
            )
          case Lgs(str, sfo, pos) =>
            pauseResumeLgsMode(str,
                               sfo,
                               pos.bimap(_.toValue[Double], _.toValue[Double]),
                               currCfg,
                               currentOffset,
                               instrument
            )(pauseReasons, resumeReasons)
          case LgsWithP1          =>
            AltairPauseResume(
              guideOff(currCfg.aoLoop && pauseReasons.fixed.contains(P1Off)),
              GuideCapabilities(!pauseReasons.fixed.contains(P1Off),
                                canGuideM1 = !pauseReasons.fixed.contains(P1Off)
              ),
              pauseTargetFilter = false,
              (resumeReasons.contains(P1On) && resumeReasons.contains(
                GaosGuideOn
              ) && (!currCfg.aoLoop || pauseReasons
                .contains(P1Off))).option(setCorrectionsOn.void),
              GuideCapabilities(resumeReasons.fixed.contains(P1On),
                                canGuideM1 = resumeReasons.fixed.contains(P1On)
              ),
              none,
              forceFreeze = false
            )
          case LgsWithOi          =>
            AltairPauseResume(
              guideOff(currCfg.aoLoop && pauseReasons.fixed.contains(OiOff)),
              GuideCapabilities(!pauseReasons.fixed.contains(OiOff),
                                canGuideM1 = !pauseReasons.fixed.contains(OiOff)
              ),
              pauseTargetFilter = false,
              (resumeReasons.contains(OiOn) && resumeReasons.contains(
                GaosGuideOn
              ) && (!currCfg.aoLoop || pauseReasons
                .contains(OiOff))).option(setCorrectionsOn.void),
              GuideCapabilities(resumeReasons.fixed.contains(OiOn),
                                canGuideM1 = resumeReasons.fixed.contains(OiOn)
              ),
              none,
              forceFreeze = false
            )
          case AltairOff          => turnOff(currCfg)
        }
      }
    }

    override def observe(expTime: TimeSpan)(cfg: AltairConfig): F[Unit] = Sync[F]
      .delay(LocalDate.now)
      .flatMap(date =>
        (epicsTcs.aoStatistics.setTriggerTimeInterval(0.0) *>
          epicsTcs.aoStatistics.setInterval(expTime.toSeconds.toDouble) *>
          epicsTcs.aoStatistics.setSamples(1) *>
          epicsTcs.aoStatistics.setFileName(
            "aostats" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
          )).whenA(expTime.toSeconds > 5 && cfg =!= (AltairOff: AltairConfig))
      )

    override def endObserve(cfg: AltairConfig): F[Unit] = Applicative[F].unit

    def retrieveConfig: F[EpicsAltairConfig] = for {
      cmtxx <- epicsAltair.matrixStartX.map(_.withUnit[Millimeter])
      cmtxy <- epicsAltair.matrixStartY.map(_.withUnit[Millimeter])
      pmtxx <- epicsTcs.aoPreparedCMX.map(_.withUnit[Millimeter])
      pmtxy <- epicsTcs.aoPreparedCMY.map(_.withUnit[Millimeter])
      strRT <- epicsAltair.strapRTStatus
      strTm <- epicsAltair.strapTempStatus
      strHV <- epicsAltair.strapHVStatus
      strap <- epicsAltair.strapLoop
      sfo   <- epicsAltair.sfoLoop
      stGat <- epicsAltair.strapGate
      aolp  <- epicsAltair.aoLoop
    } yield EpicsAltairConfig(
      (cmtxx, cmtxy),
      (pmtxx, pmtxy),
      strRT,
      strTm,
      strHV,
      strap,
      sfo,
      stGat,
      aolp
    )

    // This is a bit convoluted. AO follow state is read from Altair, but set as part of TCS configuration
    override def isFollowing: F[Boolean] = epicsAltair.aoFollow

    // Can keep guiding while applying an offset?
    private def canGuideWhileOffseting(
      offset: PauseCondition.OffsetMove,
      inst:   Instrument
    ): Boolean = {
      val deltas =
        (offset.to.x.value - offset.from.x.value, offset.to.y.value - offset.from.y.value)
      aoOffsetThreshold(inst).exists(h => deltas._1.pow[2] + deltas._2.pow[2] < h.pow[2])
    }

  }

  private def aoOffsetThreshold(
    @unused instrument: Instrument
  ): Option[Quantity[Double, Millimeter]] = none
//  instrument match {
//    case Instrument.Nifs  => (Arcseconds(0.01) / FOCAL_PLANE_SCALE).some
//    case Instrument.Niri  => (Arcseconds(3.0) / FOCAL_PLANE_SCALE).some
//    case Instrument.Gnirs => (Arcseconds(3.0) / FOCAL_PLANE_SCALE).some
//    case _                => none
//  }

  // Auxiliary class that contains all the information from a pause calculation
  private sealed case class PauseReturn[F[_]](
    wasPaused:         Boolean,           // Flag for the resume calculation
    pauseAction:       Option[F[Unit]],   // The pause action
    pauseTargetFilter: Boolean,           // Does the target filter need to be disabled ?(info for TCS configuration)
    keepGuiding:       GuideCapabilities, // What guiding to keep enabled between the pause and resume
    config:            Option[F[Unit]]    // Optional Altair configuration (to run as part of TCS configuration)
  )

  // Auxiliary class that contains all the information from a resume calculation
  private sealed case class ResumeReturn[F[_]](
    resumeAction: Option[F[Unit]],  // The resume action
    keepGuiding:  GuideCapabilities // What guiding to enable after resume
  )

  private val DefaultTimeout = TimeSpan.unsafeFromDuration(10, ChronoUnit.SECONDS)

  private val StrapGateTimeout = TimeSpan.unsafeFromDuration(5, ChronoUnit.SECONDS)

}
