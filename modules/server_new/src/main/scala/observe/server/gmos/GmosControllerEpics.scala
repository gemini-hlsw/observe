// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.Applicative
import cats.ApplicativeError
import cats.effect.Async
import cats.syntax.all.*
import edu.gemini.epics.acm.CarStateGeneric
import fs2.Stream
import lucuma.core.enums.GmosAmpCount
import lucuma.core.enums.GmosAmpReadMode
import lucuma.core.enums.GmosEOffsetting
import lucuma.core.enums.GmosGratingOrder
import lucuma.core.math.Wavelength
import lucuma.core.util.TimeSpan
import mouse.all.*
import observe.model.GmosParameters.*
import observe.model.NsSubexposure
import observe.model.ObserveStage
import observe.model.dhs.ImageFileId
import observe.model.enums.NodAndShuffleStage.*
import observe.model.enums.ObserveCommandResult
import observe.server.EpicsCodex.*
import observe.server.EpicsCommandBase
import observe.server.EpicsUtil
import observe.server.EpicsUtil.*
import observe.server.InstrumentSystem.ElapsedTime
import observe.server.NsProgress
import observe.server.ObserveFailure
import observe.server.Progress
import observe.server.RemainingTime
import observe.server.gmos.GmosController.Config
import observe.server.gmos.GmosController.Config.*
import observe.server.gmos.GmosController.GmosSite
import org.typelevel.log4cats.Logger

import java.time.temporal.ChronoUnit
import scala.jdk.DurationConverters.*

trait GmosEncoders {
  given EncodeEpicsValue[AmpReadMode, String] = EncodeEpicsValue {
    case GmosAmpReadMode.Slow => "SLOW"
    case GmosAmpReadMode.Fast => "FAST"
  }

  given EncodeEpicsValue[ShutterState, String] = EncodeEpicsValue {
    case ShutterState.OpenShutter  => "OPEN"
    case ShutterState.CloseShutter => "CLOSED"
  }

  given EncodeEpicsValue[AmpCount, String] = EncodeEpicsValue {
    // gmosAmpCount.lut
    case GmosAmpCount.Three  => ""
    case GmosAmpCount.Six    => "BEST"
    case GmosAmpCount.Twelve => "ALL"
  }

  given EncodeEpicsValue[BinningX, Int] = EncodeEpicsValue(_.count.value)

  given EncodeEpicsValue[BinningY, Int] = EncodeEpicsValue(_.count.value)

  given disperserOrderEncoder: EncodeEpicsValue[GratingOrder, String] = EncodeEpicsValue(
    _.shortName
  )

  given disperserOrderEncoderInt: EncodeEpicsValue[GratingOrder, Int] = EncodeEpicsValue(
    _.count
  )

  given EncodeEpicsValue[NodAndShuffleState, String] = EncodeEpicsValue {
    case NodAndShuffleState.Classic    => "CLASSIC"
    case NodAndShuffleState.NodShuffle => "NOD_SHUFFLE"
  }

  given DecodeEpicsValue[String, Option[NodAndShuffleState]] =
    DecodeEpicsValue {
      case "CLASSIC"     => NodAndShuffleState.Classic.some
      case "NOD_SHUFFLE" => NodAndShuffleState.NodShuffle.some
      case _             => none
    }

  given EncodeEpicsValue[ExposureTime, Int] = EncodeEpicsValue(
    _.toSeconds.toInt
  )

  given EncodeEpicsValue[Wavelength, Double] =
    EncodeEpicsValue((l: Wavelength) => l.toNanometers.value.value.toDouble)

  given EncodeEpicsValue[ElectronicOffset, Int] =
    EncodeEpicsValue {
      case GmosEOffsetting.On  => 1
      case GmosEOffsetting.Off => 0
    }

  val InBeamVal: String                             = "IN-BEAM"
  val OutOfBeamVal: String                          = "OUT-OF-BEAM"
  given beamEncoder: EncodeEpicsValue[Beam, String] = EncodeEpicsValue {
    case Beam.OutOfBeam => OutOfBeamVal
    case Beam.InBeam    => InBeamVal
  }

  def inBeamDecode(v: Int): String =
    if (v === 0) InBeamVal else OutOfBeamVal

  // TODO: define Enum type for disperserMode
  val disperserMode0 = "WLEN"
  val disperserMode1 = "SEL"

  def disperserModeDecode(v: Int): String = if (v === 0) disperserMode0 else disperserMode1

}

private[gmos] final case class GmosDCEpicsState(
  shutterState: String,
  exposureTime: Int,
  ampReadMode:  String,
  gainSetting:  Int,
  ampCount:     String,
  roiNumUsed:   Int,
  ccdXBinning:  Int,
  ccdYBinning:  Int
)

private[gmos] final case class GmosNSEpicsState(
  nsPairs: NsPairs,
  nsRows:  NsRows,
  nsState: String
)

private[gmos] final case class GmosCCEpicsState(
  filter1:                 String,
  filter2:                 String,
  disperserMode:           Int,
  disperser:               String,
  disperserParked:         Boolean,
  disperserOrder:          Int,
  disperserWavel:          Double,
  fpu:                     String,
  inBeam:                  Int,
  stageMode:               String,
  dtaXOffset:              Double,
  dtaXCenter:              Double,
  useElectronicOffsetting: Int
)

/**
 * Captures the current epics state of GMOS
 */
private[gmos] final case class GmosEpicsState(
  dc: GmosDCEpicsState,
  cc: GmosCCEpicsState,
  ns: GmosNSEpicsState
)

object GmosControllerEpics extends GmosEncoders {

  val DhsConnected: String = "CONNECTED"

  def apply[F[_]: Async, T <: GmosSite](
    sys: => GmosEpics[F]
  )(using
    e:   Encoders[T],
    L:   Logger[F]
  ): GmosController[F, T] =
    new GmosController[F, T] {
      private val CC = sys.configCmd
      private val DC = sys.configDCCmd

      // Read the current state of the
      private def retrieveState: F[GmosEpicsState] =
        for {
          dc <- retrieveDCState
          cc <- retrieveCCState
          ns <- retrieveNSState
        } yield GmosEpicsState(dc, cc, ns)

      private def retrieveNSState: F[GmosNSEpicsState] =
        (sys.nsPairs.map(NsPairs.apply(_)), sys.nsRows.map(NsRows.apply(_)), sys.nsState)
          .mapN(GmosNSEpicsState.apply)

      private def retrieveDCState: F[GmosDCEpicsState] =
        for {
          shutterState <- sys.shutterState
          exposureTime <- sys.exposureTime
          ampReadMode  <- sys.ampReadMode
          gainSetting  <- sys.gainSetting
          ampCount     <- sys.ampCount
          roiNumUsed   <- sys.roiNumUsed
          ccdXBinning  <- sys.ccdXBinning
          ccdYBinning  <- sys.ccdYBinning
        } yield GmosDCEpicsState(shutterState,
                                 exposureTime,
                                 ampReadMode,
                                 gainSetting,
                                 ampCount,
                                 roiNumUsed,
                                 ccdXBinning,
                                 ccdYBinning
        )

      private def retrieveCCState: F[GmosCCEpicsState] =
        for {
          filter1                 <- sys.filter1
          filter2                 <- sys.filter2
          disperserMode           <- sys.disperserMode
          disperser               <- sys.disperser
          disperserParked         <- sys.disperserParked
          disperserOrder          <- sys.disperserOrder
          disperserWavel          <- sys.disperserWavel
          fpu                     <- sys.fpu
          inBeam                  <- sys.inBeam
          stageMode               <- sys.stageMode
          dtaXOffset              <- sys.dtaXOffset
          dtaXCenter              <- sys.dtaXCenter
          useElectronicOffsetting <- sys.electronicOffset
        } yield GmosCCEpicsState(
          filter1,
          filter2,
          disperserMode,
          disperser,
          disperserParked,
          disperserOrder,
          disperserWavel,
          fpu,
          inBeam,
          stageMode,
          dtaXOffset,
          dtaXCenter,
          useElectronicOffsetting
        )

      private def setShutterState(s: GmosDCEpicsState, dc: DCConfig): Option[F[Unit]] =
        applyParam(s.shutterState, encode(dc.s), DC.setShutterState)

      private def setGainSetting(
        state: GmosDCEpicsState,
        rm:    AmpReadMode,
        g:     AmpGain
      ): Option[F[Unit]] = {
        val encodedVal = Encoders[T].autoGain.encode((rm, g))
        applyParam(state.gainSetting, encodedVal, DC.setGainSetting)
      }

      private def roiNumUsed(s: RegionsOfInterest): Int =
        s.rois.map(_.length).getOrElse(1)

      private def setROI(binning: CCDBinning, s: RegionsOfInterest): List[F[Unit]] =
        s.rois match {
          case Left(b)     => roiParameters(binning, 1, Encoders[T].builtInROI.encode(b)).toList
          case Right(rois) =>
            rois.zipWithIndex.flatMap { case (roi, i) =>
              roiParameters(binning, i + 1, ROIValues.fromOCS(roi))
            }
        }

      private def roiParameters(
        binning: CCDBinning,
        index:   Int,
        roi:     Option[ROIValues]
      ): Option[F[Unit]] = (roi, DC.rois.get(index)).mapN { (roi, r) =>
        r.setCcdXstart1(roi.xStart.value) *>
          r.setCcdXsize1(roi.xSize.value / binning.x.count.value) *>
          r.setCcdYstart1(roi.yStart.value) *>
          r.setCcdYsize1(roi.ySize.value / binning.y.count.value)
      }

      private def setFilters(
        state: GmosCCEpicsState,
        f:     Option[GmosSite.Filter[T]]
      ): List[Option[F[Unit]]] = {
        val (filter1, filter2) = Encoders[T].filter.encode(f)

        List(applyParam(state.filter1, filter1, CC.setFilter1),
             applyParam(state.filter2, filter2, CC.setFilter2)
        )
      }

      val mirrorEncodedVal: String = "mirror"

      def setDisperser(state: GmosCCEpicsState, d: Option[GmosSite.Grating[T]]): Option[F[Unit]] = {
        val encodedVal = d.map(Encoders[T].disperser.encode).getOrElse(mirrorEncodedVal)
        val s          = applyParam(state.disperser.toUpperCase,
                           encodedVal.toUpperCase,
                           (_: String) => CC.setDisperser(encodedVal)
        )
        // Force setting if not set and disperser is parked
        s.orElse(state.disperserParked.option(CC.setDisperser(encodedVal)))
      }

      def setOrder(state: GmosCCEpicsState, o: GratingOrder): Option[F[Unit]] =
        applyParam(state.disperserOrder,
                   disperserOrderEncoderInt.encode(o),
                   (_: Int) => CC.setDisperserOrder(disperserOrderEncoder.encode(o))
        )

      def setWavelength(state: GmosCCEpicsState, w: Wavelength): Option[F[Unit]] =
        applyParam(state.disperserWavel, encode(w), CC.setDisperserLambda)

      def setDisperserParams(
        state: GmosCCEpicsState,
        g:     GmosController.Config.GmosGrating[T]
      ): List[Option[F[Unit]]] = {
        val params: List[Option[F[Unit]]] = g match {
          case GmosGrating.Mirror()        => List(setDisperser(state, none))
          case GmosGrating.Order0(d)       =>
            val s0: Option[F[Unit]] = setDisperser(state, d.some)
            // If disperser is set, force order configuration
            if (s0.isEmpty) List(setOrder(state, GmosGratingOrder.Zero))
            else
              CC.setDisperserOrder(disperserOrderEncoder.encode(GmosGratingOrder.Zero))
                .some :: List(s0)
          case GmosGrating.OrderN(d, o, w) =>
            val s0: Option[F[Unit]] =
              setDisperser(state, d.some) // None means disperser is already in the right position
            // If disperser is set, the order has to be set regardless of current value.
            if (s0.isEmpty) {
              List(
                setOrder(state, o),
                setWavelength(state, w)
              )
            } else {
              List(
                CC.setDisperserOrder(disperserOrderEncoder.encode(o)).some,
                s0,
                setWavelength(state, w)
              )
            }
        }

        // If disperser, order or wavelength are set, force mode configuration. If not, check if it needs to be set anyways
        if (params.exists(_.isDefined)) params :+ CC.setDisperserMode(disperserMode0).some
        else
          List(
            applyParam(disperserModeDecode(state.disperserMode),
                       disperserMode0,
                       CC.setDisperserMode
            )
          )
      }

      def setFPU(
        state: GmosCCEpicsState,
        cc:    Option[GmosFPU[T]]
      ): List[Option[F[Unit]]] = {
        def builtInFPU(fpu: GmosSite.FPU[T]): String = Encoders[T].fpu.encode(fpu)

        def setFPUParams(p: Option[String]): List[Option[F[Unit]]] = p
          .map(g =>
            List(
              applyParam(state.fpu, g, CC.setFpu),
              applyParam(inBeamDecode(state.inBeam), beamEncoder.encode(Beam.InBeam), CC.setInBeam)
            )
          )
          .getOrElse(
            List(
              applyParam(state.fpu, "None", CC.setFpu),
              applyParam(inBeamDecode(state.inBeam),
                         beamEncoder.encode(Beam.OutOfBeam),
                         CC.setInBeam
              )
            )
          )

        cc.map {
          case BuiltInFPU[T](fpu)  =>
            L.debug(s"Set GMOS built in fpu $fpu").some +: setFPUParams(builtInFPU(fpu).some)
          case CustomMaskFPU(name) =>
            L.debug(s"Set GMOS custom fpu $name").some +: setFPUParams(name.some)
        }.getOrElse(
          L.debug(s"Set GMOS fpu to None").some +: setFPUParams(none)
        )
      }

      private def setStage(state: GmosCCEpicsState, v: GmosSite.StageMode[T]): Option[F[Unit]] = {
        val stage = Encoders[T].stageMode.encode(v)

        applyParam(state.stageMode, stage, CC.setStageMode)
      }

      private def setDtaXOffset(state: GmosCCEpicsState, v: DTAX): Option[F[Unit]] = {
        val PixelsToMicrons = 15.0
        val Tolerance       = 0.001

        val offsetInMicrons = v.dtax.toDouble * PixelsToMicrons

        // It seems that the reported dtaXOffset is absolute, but the applied offset is relative to
        // XCenter value
        applyParamT(Tolerance)(state.dtaXOffset - state.dtaXCenter,
                               offsetInMicrons,
                               CC.setDtaXOffset
        )
      }

      private def setElectronicOffset(
        state: GmosCCEpicsState,
        e:     ElectronicOffset
      ): Option[F[Unit]] =
        applyParam(state.useElectronicOffsetting,
                   encode(e),
                   (e: Int) => CC.setElectronicOffsetting(e)
        )

      private def warnOnDHSNotConected: F[Unit] =
        sys.dhsConnected
          .map(_.trim === DhsConnected)
          .ifM(Applicative[F].unit, L.warn("GMOS is not connected to the DHS"))

      private def dcParams(state: GmosDCEpicsState, config: DCConfig): List[F[Unit]] =
        List(
          applyParam(
            state.exposureTime,
            encode(config.t),
            (_: Int) =>
              L.debug(s"Set GMOS expTime ${config.t}") *> sys.configDCCmd.setExposureTime(
                config.t.toDuration.toScala
              )
          ),
          setShutterState(state, config),
          applyParam(state.ampReadMode,
                     encode(config.r.ampReadMode),
                     sys.configDCCmd.setAmpReadMode
          ),
          setGainSetting(state, config.r.ampReadMode, config.r.ampGain),
          applyParam(state.ampCount, encode(config.r.ampCount), DC.setAmpCount),
          // TODO revert this change when FR-41232 is resolved
          // applyParam(state.roiNumUsed, roiNumUsed(config.roi), DC.setRoiNumUsed),
          DC.setRoiNumUsed(roiNumUsed(config.roi)).some,
          applyParam(state.ccdXBinning, encode(config.bi.x), DC.setCcdXBinning),
          applyParam(state.ccdYBinning, encode(config.bi.y), DC.setCcdYBinning)
        ).flattenOption ++
          // TODO these are not smart about setting them only if needed
          setROI(config.bi, config.roi)

      private def nsParams(state: GmosNSEpicsState, config: NsConfig): List[F[Unit]] =
        List(
          applyParam(state.nsPairs.value, config.nsPairs.value, (x: Int) => DC.setNsPairs(x)),
          applyParam(state.nsRows.value, config.nsRows.value, (x: Int) => DC.setNsRows(x)),
          applyParam(state.nsState, encode(config.nsState), DC.setNsState)
        ).flattenOption

      // Don't set CC if Dark or Bias
      private def ccParams(state: GmosCCEpicsState, config: Config.CCConfig[T]): List[F[Unit]] =
        config match {
          case StandardCCConfig(filter, disperser, fpu, stage, dtaX, adc, useElectronicOffset) =>
            (
              setFilters(state, filter) ++
                setDisperserParams(state, disperser) ++
                setFPU(state, fpu) ++
                List(
                  setStage(state, stage),
                  setDtaXOffset(state, dtaX),
                  setElectronicOffset(state, useElectronicOffset)
                )
            ).flattenOption
          case DarkOrBias()                                                                    => List.empty
        }

      override def applyConfig(config: GmosController.GmosConfig[T]): F[Unit] =
        retrieveState.flatMap { state =>
          val params = dcParams(state.dc, config.dc) ++
            ccParams(state.cc, config.cc) ++
            nsParams(state.ns, config.ns)

          L.debug("Start Gmos configuration") *>
            L.debug(s"Gmos configuration: ${config.show}") *>
            warnOnDHSNotConected *>
            (params.sequence *>
              sys.post(ConfigTimeout)).unlessA(params.isEmpty) *>
            L.debug("Completed Gmos configuration")
        }

      override def observe(fileId: ImageFileId, expTime: TimeSpan): F[ObserveCommandResult] =
        failOnDHSNotConected *>
          sys.observeCmd.setLabel(fileId.value) *>
          sys.observeCmd.post(expTime +| ReadoutTimeout)

      private def failOnDHSNotConected: F[Unit] =
        sys.dhsConnected
          .map(_.trim === DhsConnected)
          .ifM(Applicative[F].unit,
               ApplicativeError[F, Throwable]
                 .raiseError(ObserveFailure.Execution("GMOS is not connected to DHS"))
          )

      private def protectedObserveCommand(name: String, cmd: EpicsCommandBase[F]): F[Unit] = {
        val safetyCutoffAsDouble: Double = SafetyCutoff.toSeconds.toDouble

        (sys.dcIsAcquiring, sys.countdown).mapN { case (isAcq, timeLeft) =>
          if (!isAcq)
            L.debug(s"Gmos $name Observe canceled because it is not acquiring.")
          else if (timeLeft <= safetyCutoffAsDouble)
            L.debug(
              s"Gmos $name Observe canceled because there is less than $safetyCutoffAsDouble seconds left."
            )
          else
            L.debug(s"$name Gmos exposure") *>
              cmd.mark *>
              cmd.post(DefaultTimeout).void
        }.flatten
      }

      override def stopObserve: F[Unit] = protectedObserveCommand("Stop", sys.stopCmd)

      override def abortObserve: F[Unit] = protectedObserveCommand("Abort", sys.abortCmd)

      override def endObserve: F[Unit] =
        L.debug("Send endObserve to Gmos") *>
          sys.endObserveCmd.mark *>
          sys.endObserveCmd.post(DefaultTimeout) *>
          L.debug("endObserve sent to Gmos")

      override def pauseObserve: F[Unit] = protectedObserveCommand("Pause", sys.pauseCmd)

      override def resumePaused(expTime: TimeSpan): F[ObserveCommandResult] = for {
        _   <- L.debug("Resume Gmos observation")
        _   <- sys.continueCmd.mark
        ret <- sys.continueCmd.post(expTime +| ReadoutTimeout)
        _   <- L.debug("Completed Gmos observation")
      } yield ret

      override def stopPaused: F[ObserveCommandResult] = for {
        _   <- L.debug("Stop Gmos paused observation")
        _   <- sys.stopAndWaitCmd.mark
        ret <- sys.stopAndWaitCmd.post(DefaultTimeout)
        _   <- L.debug("Completed stopping Gmos observation")
      } yield if (ret === ObserveCommandResult.Success) ObserveCommandResult.Stopped else ret

      override def abortPaused: F[ObserveCommandResult] = for {
        _   <- L.debug("Abort Gmos paused observation")
        _   <- sys.abortAndWait.mark
        ret <- sys.abortAndWait.post(DefaultTimeout)
        _   <- L.debug("Completed aborting Gmos observation")
      } yield if (ret === ObserveCommandResult.Success) ObserveCommandResult.Aborted else ret

      // Calculate the current subexposure
      def nsSubexposure: F[NsSubexposure] =
        (sys.nsPairs, sys.currentCycle, sys.aExpCount, sys.bExpCount).mapN {
          (total, cycle, aCount, bCount) =>
            val stageIndex = (aCount + bCount) % NsSequence.length
            val sub        =
              NsSubexposure(NsCycles(total / 2), NsCycles(cycle), NsStageIndex(stageIndex))
            sub.getOrElse(NsSubexposure.Zero)
        }

      // Different progress results for classic and NS
      def gmosProgress: (TimeSpan, RemainingTime, ObserveStage) => F[Progress] =
        (time, remaining, stage) =>
          sys.nsState.flatMap {
            case "CLASSIC" => EpicsUtil.defaultProgress[F](time, remaining, stage)
            case _         => nsSubexposure.map(s => NsProgress(time, remaining, stage, s))
          }

      override def observeProgress(
        total:   TimeSpan,
        elapsed: ElapsedTime
      ): Stream[F, Progress] =
        EpicsUtil.countdown[F](
          total,
          sys.countdown.map(x => TimeSpan.unsafeFromDuration(x.toLong, ChronoUnit.SECONDS)),
          sys.observeState.widen[CarStateGeneric],
          (sys.dcIsPreparing, sys.dcIsAcquiring, sys.dcIsReadingOut)
            .mapN(ObserveStage.fromBooleans),
          gmosProgress
        )

      override def nsCount: F[Int] = for {
        a <- sys.aExpCount
        b <- sys.bExpCount
      } yield a + b
    }

  // Parameters to define a ROI
  sealed abstract case class XStart(value: Int)
  // Make the values impossible to build with invalid values
  object XStart {
    def fromInt(v: Int): Option[XStart] = (v > 0).option(new XStart(v) {})
  }

  sealed abstract case class XSize(value: Int)
  object XSize {
    def fromInt(v: Int): Option[XSize] = (v > 0).option(new XSize(v) {})
  }

  sealed abstract case class YStart(value: Int)
  object YStart {
    def fromInt(v: Int): Option[YStart] = (v > 0).option(new YStart(v) {})
  }

  sealed abstract case class YSize(value: Int)
  object YSize {
    def fromInt(v: Int): Option[YSize] = (v > 0).option(new YSize(v) {})
  }

  sealed abstract case class ROIValues(xStart: XStart, xSize: XSize, yStart: YStart, ySize: YSize)

  object ROIValues {
    // Build out of fixed values, I wish this could be constrained a bit more
    // but these are hardcoded values according to LUTS
    // Being private we ensure it is mostly sane
    def fromInt(xStart: Int, xSize: Int, yStart: Int, ySize: Int): Option[ROIValues] =
      (XStart.fromInt(xStart), XSize.fromInt(xSize), YStart.fromInt(yStart), YSize.fromInt(ySize))
        .mapN(new ROIValues(_, _, _, _) {})

    // Built from OCS ROI values
    def fromOCS(roi: ROI): Option[ROIValues] =
      (XStart.fromInt(roi.getXStart),
       XSize.fromInt(roi.getXSize()),
       YStart.fromInt(roi.getYStart),
       YSize.fromInt(roi.getYSize())
      ).mapN(new ROIValues(_, _, _, _) {})

  }

  trait Encoders[T <: GmosSite] {
    val filter: EncodeEpicsValue[Option[GmosSite.Filter[T]], (String, String)]
    val fpu: EncodeEpicsValue[GmosSite.FPU[T], String]
    val stageMode: EncodeEpicsValue[GmosSite.StageMode[T], String]
    val disperser: EncodeEpicsValue[GmosSite.Grating[T], String]
    val builtInROI: EncodeEpicsValue[BuiltinROI, Option[ROIValues]]
    val autoGain: EncodeEpicsValue[(AmpReadMode, AmpGain), Int]
  }

  object Encoders {
    @inline
    def apply[A <: GmosSite](using ev: Encoders[A]): Encoders[A] = ev
  }

  val DefaultTimeout: TimeSpan = TimeSpan.unsafeFromDuration(60, ChronoUnit.SECONDS)
  val ReadoutTimeout: TimeSpan = TimeSpan.unsafeFromDuration(120, ChronoUnit.SECONDS)
  val ConfigTimeout: TimeSpan  = TimeSpan.unsafeFromDuration(600, ChronoUnit.SECONDS)

  def dummy[A](v: Option[A]): String = v match {
    case Some(value) => value.toString
    case None        => "None"
  }

}
