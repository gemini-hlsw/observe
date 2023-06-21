// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import scala.concurrent.duration.Duration
import cats.Show
import cats.syntax.all.*
import lucuma.core.enums.{GmosEOffsetting, GmosRoi, GmosXBinning, GmosYBinning}
import lucuma.core.math.{Offset, Wavelength}
import lucuma.core.util.Enumerated
import observe.common.ObsQueriesGQL.ObsQuery.GmosSite
import observe.model.GmosParameters
import observe.model.GmosParameters.*
import observe.model.dhs.ImageFileId
import observe.model.enums.Guiding
import observe.model.enums.NodAndShuffleStage
import observe.model.enums.ObserveCommandResult
import observe.server.InstrumentSystem.ElapsedTime
import observe.server.*
import observe.server.gmos.GmosController.Config.{DCConfig, DarkOrBias, NSConfig}
import shapeless.tag

trait GmosController[F[_], T <: GmosSite] {
  import GmosController._

  def applyConfig(config: GmosConfig[T]): F[Unit]

  def observe(fileId: ImageFileId, expTime: Duration): F[ObserveCommandResult]

  // endObserve is to notify the completion of the observation, not to cause its end.
  def endObserve: F[Unit]

  def stopObserve: F[Unit]

  def abortObserve: F[Unit]

  def pauseObserve: F[Unit]

  def resumePaused(expTime: Duration): F[ObserveCommandResult]

  def stopPaused: F[ObserveCommandResult]

  def abortPaused: F[ObserveCommandResult]

  def observeProgress(total: Duration, elapsed: ElapsedTime): fs2.Stream[F, Progress]

  def nsCount: F[Int]

}

object GmosController {
  sealed abstract class Config[T <: GmosSite] {
    import Config._

    case class BuiltInFPU(fpu: T#BuiltInFpu) extends GmosFPU

    sealed trait GmosDisperser extends Product with Serializable
    object GmosDisperser {
      case object Mirror                      extends GmosDisperser
      case class Order0(disperser: T#Grating) extends GmosDisperser
      case class OrderN(disperser: T#Grating, order: DisperserOrder, lambda: Wavelength)
          extends GmosDisperser
    }

    final case class StandardCCConfig(
      filter:              Option[T#Filter],
      disperser:           GmosDisperser,
      fpu:                 Option[GmosFPU],
      stage:               T#StageMode,
      dtaX:                DTAX,
      adc:                 ADC,
      useElectronicOffset: ElectronicOffset
    ) extends CCConfig

  }

  object Config {
    type DTAX           = lucuma.core.enums.GmosDtax
    type ADC            = lucuma.core.enums.GmosAdc
    type DisperserOrder = lucuma.core.enums.GmosGratingOrder
    type BinningX       = lucuma.core.enums.GmosXBinning
    type BinningY       = lucuma.core.enums.GmosYBinning
    type AmpReadMode    = lucuma.core.enums.GmosAmpReadMode
    type AmpGain        = lucuma.core.enums.GmosAmpGain
    type AmpCount       = lucuma.core.enums.GmosAmpCount
    type BuiltinROI     = lucuma.core.enums.GmosRoi
    type ROI            = ROIDescription
    type ExposureTime   = Duration
    type PosAngle       = lucuma.core.math.Angle

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

    sealed trait GmosFPU extends Product with Serializable

    final case object UnknownFPU extends GmosFPU

    final case class CustomMaskFPU(mask: String) extends GmosFPU

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
        val nsPairs: GmosParameters.NsPairs                   = tag[NsPairsI][Int](0)
        val nsRows: GmosParameters.NsRows                     = tag[NsRowsI][Int](0)
        val exposureDivider: GmosParameters.NsExposureDivider = tag[NsExposureDividerI][Int](1)
        val nsState: NodAndShuffleState                       = NodAndShuffleState.Classic
      }

      final case class NodAndShuffle(
        cycles:       NsCycles,
        rows:         NsRows,
        positions:    Vector[NSPosition],
        exposureTime: Duration
      ) extends NSConfig {
        val nsPairs: GmosParameters.NsPairs                   =
          tag[NsPairsI][Int](cycles * NodAndShuffleStage.NsSequence.length / 2)
        val nsRows: GmosParameters.NsRows                     =
          tag[NsRowsI][Int](Gmos.rowsToShuffle(NodAndShuffleStage.NsSequence.head, rows))
        val exposureDivider: GmosParameters.NsExposureDivider = tag[NsExposureDividerI][Int](2)
        val nsState: NodAndShuffleState                       = NodAndShuffleState.NodShuffle
        val totalExposureTime: Duration                           =
          cycles * exposureTime / exposureDivider.toDouble
        val nodExposureTime: Duration                             =
          exposureTime / exposureDivider.toDouble
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
          case _                                           => new RegionsOfInterest((GmosRoi.FullFrame:GmosRoi).asLeft) {}
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

    sealed trait CCConfig

    case object DarkOrBias extends CCConfig

  }

  final class SouthConfigTypes extends Config[GmosSite.South]
  val southConfigTypes: SouthConfigTypes = new SouthConfigTypes

  final class NorthConfigTypes extends Config[GmosSite.North]

  val northConfigTypes: NorthConfigTypes = new NorthConfigTypes

  // This is a trick to allow using a type from a class parameter to define the type of another class parameter
  final case class GmosConfig[T <: GmosSite] private (
    cc: Config.CCConfig,
    dc: DCConfig,
    c:  Config[T],
    ns: NSConfig
  ) {
    def this(c: Config[T])(cc: Config.CCConfig, dc: DCConfig, ns: NSConfig) = this(cc, dc, c, ns)
  }

  given [T <: GmosSite]:Show[GmosConfig[T]] =
    Show.show { config =>
      val ccShow = config.cc match {
        case DarkOrBias => "DarkOrBias"
        case cc: Config[T]#StandardCCConfig => s"${cc.filter}, ${cc.disperser}, ${cc.fpu}, ${cc.stage}, ${cc.stage}, ${cc.dtaX}, ${cc.adc}, ${cc.useElectronicOffset}"
      }
      s"($ccShow, ${config.dc.t}, ${config.dc.s}, ${config.dc.bi}, ${config.dc.roi.rois} ${config.ns})"
    }

}
