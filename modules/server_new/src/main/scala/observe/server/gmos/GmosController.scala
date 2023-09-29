// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.Show
import cats.syntax.all.*
import fs2.Stream
import lucuma.core.enums.GmosEOffsetting
import lucuma.core.enums.GmosRoi
import lucuma.core.enums.GmosXBinning
import lucuma.core.enums.GmosYBinning
import lucuma.core.math.Offset
import lucuma.core.math.Wavelength
import lucuma.core.refined.numeric.NonZeroInt
import lucuma.core.util.Enumerated
import lucuma.core.util.TimeSpan
import lucuma.core.util.TimeSpan.*
import observe.model.GmosParameters
import observe.model.GmosParameters.*
import observe.model.dhs.ImageFileId
import observe.model.enums.Guiding
import observe.model.enums.NodAndShuffleStage
import observe.model.enums.ObserveCommandResult
import observe.server.InstrumentSystem.ElapsedTime
import observe.server.*
import observe.server.gmos.GmosController.Config.DCConfig
import observe.server.gmos.GmosController.Config.DarkOrBias
import observe.server.gmos.GmosController.Config.NSConfig

import scala.concurrent.duration.*

import GmosController.*

trait GmosController[F[_], T <: GmosSite] {
  import GmosController._

  def applyConfig(config: GmosConfig[T]): F[Unit]

  def observe(fileId: ImageFileId, expTime: FiniteDuration): F[ObserveCommandResult]

  // endObserve is to notify the completion of the observation, not to cause its end.
  def endObserve: F[Unit]

  def stopObserve: F[Unit]

  def abortObserve: F[Unit]

  def pauseObserve: F[Unit]

  def resumePaused(expTime: FiniteDuration): F[ObserveCommandResult]

  def stopPaused: F[ObserveCommandResult]

  def abortPaused: F[ObserveCommandResult]

  def observeProgress(total: FiniteDuration, elapsed: ElapsedTime): Stream[F, Progress]

  def nsCount: F[Int]

}

object GmosController {

  object Config {
    type DTAX         = lucuma.core.enums.GmosDtax
    type ADC          = lucuma.core.enums.GmosAdc
    type GratingOrder = lucuma.core.enums.GmosGratingOrder
    type BinningX     = lucuma.core.enums.GmosXBinning
    type BinningY     = lucuma.core.enums.GmosYBinning
    type AmpReadMode  = lucuma.core.enums.GmosAmpReadMode
    type AmpGain      = lucuma.core.enums.GmosAmpGain
    type AmpCount     = lucuma.core.enums.GmosAmpCount
    type BuiltinROI   = lucuma.core.enums.GmosRoi
    type ROI          = ROIDescription
    type ExposureTime = TimeSpan
    type PosAngle     = lucuma.core.math.Angle

    sealed trait CCConfig[T <: GmosSite] { self =>
      val isDarkOrBias: Boolean = self match {
        case StandardCCConfig(_, _, _, _, _, _, _) => false
        case DarkOrBias()                          => true
      }
    }

    // TODO: Replace by lucuma type when it exists.
    sealed case class ROIDescription(getXStart: Int, getYStart: Int, xSize: Int, ySize: Int) {
      def getXSize(bvalue: BinningX = GmosXBinning.One): Int =
        xSize / bvalue.count

      def getYSize(bvalue: BinningY = GmosYBinning.One): Int =
        ySize / bvalue.count
    }

    // Used for the shutterState
    sealed abstract class ShutterState(val tag: String) extends Product with Serializable

    object ShutterState {
      case object UnsetShutter extends ShutterState("UnsetShutter")
      case object OpenShutter  extends ShutterState("OpenShutter")
      case object CloseShutter extends ShutterState("CloseShutter")

      /** @group Typeclass Instances */
      given Enumerated[ShutterState] =
        Enumerated.from(UnsetShutter, OpenShutter, CloseShutter).withTag(_.tag)
    }

    sealed abstract class Beam(val tag: String) extends Product with Serializable

    object Beam {
      case object InBeam    extends Beam("InBeam")
      case object OutOfBeam extends Beam("OutOfBeam")

      /** @group Typeclass Instances */
      given Enumerated[Beam] =
        Enumerated.from(InBeam, OutOfBeam).withTag(_.tag)
    }

    type ElectronicOffset = GmosEOffsetting

    object ElectronicOffset {

      def fromBoolean(v: Boolean): ElectronicOffset =
        if (v) GmosEOffsetting.On else GmosEOffsetting.Off
    }

    sealed trait GmosFPU[T <: GmosSite] extends Product with Serializable

//    case object UnknownFPU extends GmosFPU

    final case class CustomMaskFPU[T <: GmosSite](mask: String) extends GmosFPU[T]

    final case class BuiltInFPU[T <: GmosSite](fpu: GmosSite.FPU[T]) extends GmosFPU[T]

    sealed trait GmosGrating[T <: GmosSite] extends Product with Serializable

    object GmosGrating {
      case class Mirror[T <: GmosSite]() extends GmosGrating[T]

      case class Order0[T <: GmosSite](disperser: GmosSite.Grating[T]) extends GmosGrating[T]

      case class OrderN[T <: GmosSite](
        disperser: GmosSite.Grating[T],
        order:     GratingOrder,
        lambda:    Wavelength
      ) extends GmosGrating[T]
    }

    final case class StandardCCConfig[T <: GmosSite](
      filter:              Option[GmosSite.Filter[T]],
      disperser:           GmosGrating[T],
      fpu:                 Option[GmosFPU[T]],
      stage:               GmosSite.StageMode[T],
      dtaX:                DTAX,
      adc:                 ADC,
      useElectronicOffset: ElectronicOffset
    ) extends CCConfig[T]

    final case class CCDReadout(
      ampReadMode: AmpReadMode,
      ampGain:     AmpGain,
      ampCount:    AmpCount,
      gainSetting: Double
    )

    final case class CCDBinning(x: BinningX, y: BinningY)

    sealed abstract class RegionsOfInterest(val rois: Either[BuiltinROI, List[ROI]])

    // Node and shuffle positions
    final case class NSPosition(stage: NodAndShuffleStage, offset: Offset, guide: Guiding)

    // Node and shuffle options
    sealed trait NSConfig extends Product with Serializable {
      def nsPairs: NsPairs
      def nsRows: NsRows
      def exposureDivider: NsExposureDivider
      def nsState: NodAndShuffleState
    }

    object NSConfig {
      case object NoNodAndShuffle extends NSConfig {
        val nsPairs: GmosParameters.NsPairs                   = GmosParameters.NsPairs(0)
        val nsRows: GmosParameters.NsRows                     = GmosParameters.NsRows(0)
        val exposureDivider: GmosParameters.NsExposureDivider =
          GmosParameters.NsExposureDivider(NonZeroInt.unsafeFrom(1))
        val nsState: NodAndShuffleState                       = NodAndShuffleState.Classic
      }

      final case class NodAndShuffle(
        cycles:       NsCycles,
        rows:         NsRows,
        positions:    Vector[NSPosition],
        exposureTime: TimeSpan
      ) extends NSConfig {
        val nsPairs: GmosParameters.NsPairs                   =
          GmosParameters.NsPairs(cycles.value * NodAndShuffleStage.NsSequence.length / 2)
        val nsRows: GmosParameters.NsRows                     =
          GmosParameters.NsRows(Gmos.rowsToShuffle(NodAndShuffleStage.NsSequence.head, rows))
        val exposureDivider: GmosParameters.NsExposureDivider =
          GmosParameters.NsExposureDivider(NonZeroInt.unsafeFrom(2))
        val nsState: NodAndShuffleState                       = NodAndShuffleState.NodShuffle
        val totalExposureTime: TimeSpan                       =
          exposureTime.*|(cycles.value) /| exposureDivider.value
        val nodExposureTime: TimeSpan                         =
          exposureTime /| exposureDivider.value
      }
    }

    object RegionsOfInterest {
      def fromOCS(
        builtIn: BuiltinROI,
        custom:  List[ROI]
      ): RegionsOfInterest =
        (builtIn, custom) match {
          case (b, r) if b =!= GmosRoi.Custom && r.isEmpty =>
            new RegionsOfInterest(b.asLeft) {}
          case (GmosRoi.Custom, r) if r.nonEmpty           => new RegionsOfInterest(r.asRight) {}
          case _                                           => new RegionsOfInterest((GmosRoi.FullFrame: GmosRoi).asLeft) {}
        }

      def unapply(r: RegionsOfInterest): Option[Either[BuiltinROI, List[ROI]]] = r.rois.some
    }

    final case class DCConfig(
      t:   ExposureTime,
      s:   ShutterState,
      r:   CCDReadout,
      bi:  CCDBinning,
      roi: RegionsOfInterest
    )

    final case class DarkOrBias[T <: GmosSite]() extends CCConfig[T]

  }

  sealed trait GmosSite

  object GmosSite {

    case object South extends GmosSite
    case object North extends GmosSite

    type Filter[T <: GmosSite] = T match {
      case South.type => lucuma.core.enums.GmosSouthFilter
      case North.type => lucuma.core.enums.GmosNorthFilter
    }

    type FPU[T <: GmosSite] = T match {
      case South.type => lucuma.core.enums.GmosSouthFpu
      case North.type => lucuma.core.enums.GmosNorthFpu
    }

    type StageMode[T <: GmosSite] = T match {
      case South.type => lucuma.core.enums.GmosSouthStageMode
      case North.type => lucuma.core.enums.GmosNorthStageMode
    }

    type Grating[T <: GmosSite] = T match {
      case South.type => lucuma.core.enums.GmosSouthGrating
      case North.type => lucuma.core.enums.GmosNorthGrating
    }
  }

//  final class SouthConfigTypes extends Config[GmosSite.South.type]
//  val southConfigTypes: SouthConfigTypes = new SouthConfigTypes
//
//  final class NorthConfigTypes extends Config[GmosSite.North.type]
//
//  val northConfigTypes: NorthConfigTypes = new NorthConfigTypes

  // This is a trick to allow using a type from a class parameter to define the type of another class parameter
  final case class GmosConfig[T <: GmosSite](
    cc: Config.CCConfig[T],
    dc: DCConfig,
    ns: NSConfig
  )
//  {
//    def this(c: Config[T])(cc: Config.CCConfig, dc: DCConfig, ns: NSConfig) = this(cc, dc, c, ns)
//  }

  given [T <: GmosSite]: Show[GmosConfig[T]] =
    Show.show { config =>
      val ccShow = config.cc match {
        case _: Config.DarkOrBias[T]        => "DarkOrBias"
        case cc: Config.StandardCCConfig[T] =>
          s"${cc.filter}, ${cc.disperser}, ${cc.fpu}, ${cc.stage}, ${cc.stage}, ${cc.dtaX}, ${cc.adc}, ${cc.useElectronicOffset}"
      }
      s"($ccShow, ${config.dc.t}, ${config.dc.s}, ${config.dc.bi}, ${config.dc.roi.rois} ${config.ns})"
    }

}
