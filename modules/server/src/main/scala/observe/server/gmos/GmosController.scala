// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import scala.concurrent.duration.Duration
import cats.Show
import cats.syntax.all._
import lucuma.core.enums.{ GmosRoi, GmosXBinning, GmosYBinning }
import lucuma.core.math.Offset
import lucuma.core.util.Enumerated
import observe.model.GmosParameters
import observe.model.GmosParameters._
import observe.model.dhs.ImageFileId
import observe.model.enum.Guiding
import observe.model.enum.NodAndShuffleStage
import observe.model.enum.ObserveCommandResult
import observe.server.InstrumentSystem.ElapsedTime
import observe.server.ObserveFailure.Unexpected
import observe.server._
import observe.server.gmos.GmosController.Config.DCConfig
import observe.server.gmos.GmosController.Config.NSConfig
import shapeless.tag
import squants.Length
import squants.Time

trait GmosController[F[_], T <: GmosController.SiteDependentTypes] {
  import GmosController._

  def applyConfig(config: GmosConfig[T]): F[Unit]

  def observe(fileId: ImageFileId, expTime: Time): F[ObserveCommandResult]

  // endObserve is to notify the completion of the observation, not to cause its end.
  def endObserve: F[Unit]

  def stopObserve: F[Unit]

  def abortObserve: F[Unit]

  def pauseObserve: F[Unit]

  def resumePaused(expTime: Time): F[ObserveCommandResult]

  def stopPaused: F[ObserveCommandResult]

  def abortPaused: F[ObserveCommandResult]

  def observeProgress(total: Time, elapsed: ElapsedTime): fs2.Stream[F, Progress]

  def nsCount: F[Int]

}

object GmosController {
  sealed abstract class Config[T <: SiteDependentTypes] {
    import Config._

    case class BuiltInFPU(fpu: T#FPU) extends GmosFPU

    sealed trait GmosDisperser extends Product with Serializable
    object GmosDisperser {
      case object Mirror                      extends GmosDisperser
      case class Order0(disperser: T#Grating) extends GmosDisperser
      case class OrderN(disperser: T#Grating, order: DisperserOrder, lambda: Length)
          extends GmosDisperser
    }

    case class CCConfig(
      filter:              Option[T#Filter],
      disperser:           GmosDisperser,
      fpu:                 Option[GmosFPU],
      stage:               T#GmosStageMode,
      dtaX:                DTAX,
      adc:                 ADC,
      useElectronicOffset: ElectronicOffset,
      isDarkOrBias:        Boolean
    )

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
    sealed trait ShutterState extends Product with Serializable

    object ShutterState {
      case object UnsetShutter extends ShutterState
      case object OpenShutter  extends ShutterState
      case object CloseShutter extends ShutterState

      /** @group Typeclass Instances */
      implicit val ShutterStateEnumerated: Enumerated[ShutterState] =
        Enumerated.of(UnsetShutter, OpenShutter, CloseShutter)
    }

    sealed trait Beam extends Product with Serializable

    object Beam {
      case object InBeam    extends Beam
      case object OutOfBeam extends Beam

      /** @group Typeclass Instances */
      implicit val BeamEnumerated: Enumerated[Beam] =
        Enumerated.of(InBeam, OutOfBeam)
    }

    sealed trait ElectronicOffset extends Product with Serializable

    object ElectronicOffset {
      case object On  extends ElectronicOffset
      case object Off extends ElectronicOffset

      def fromBoolean(v: Boolean): ElectronicOffset =
        if (v) On else Off

      /** @group Typeclass Instances */
      implicit val ElectronicOffsetEnumerated: Enumerated[ElectronicOffset] =
        Enumerated.of(On, Off)
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
        exposureTime: Time
      ) extends NSConfig {
        val nsPairs: GmosParameters.NsPairs                   =
          tag[NsPairsI][Int](cycles * NodAndShuffleStage.NsSequence.length / 2)
        val nsRows: GmosParameters.NsRows                     =
          tag[NsRowsI][Int](Gmos.rowsToShuffle(NodAndShuffleStage.NsSequence.head, rows))
        val exposureDivider: GmosParameters.NsExposureDivider = tag[NsExposureDividerI][Int](2)
        val nsState: NodAndShuffleState                       = NodAndShuffleState.NodShuffle
        val totalExposureTime: Time                           =
          cycles * exposureTime / exposureDivider.toDouble
        val nodExposureTime: Time                             =
          exposureTime / exposureDivider.toDouble
      }
    }

    object RegionsOfInterest {
      def fromOCS(
        builtIn: BuiltinROI,
        custom:  List[ROI]
      ): Either[ObserveFailure, RegionsOfInterest] =
        (builtIn, custom) match {
          case (b, r) if b =!= GmosRoi.Custom && r.isEmpty =>
            new RegionsOfInterest(b.asLeft) {}.asRight
          case (GmosRoi.Custom, r) if r.nonEmpty           => new RegionsOfInterest(r.asRight) {}.asRight
          case _                                           => Unexpected("Inconsistent values for GMOS regions of interest").asLeft
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

  }

  sealed trait SiteDependentTypes {
    type Filter
    type FPU
    type GmosStageMode
    type Grating
  }

  final class SouthTypes extends SiteDependentTypes {
    override type Filter        = lucuma.core.enums.GmosSouthFilter
    override type FPU           = lucuma.core.enums.GmosSouthFpu
    override type GmosStageMode = lucuma.core.enums.GmosSouthStageMode
    override type Grating       = lucuma.core.enums.GmosSouthGrating
  }

  final class SouthConfigTypes extends Config[SouthTypes]
  val southConfigTypes: SouthConfigTypes = new SouthConfigTypes

  final class NorthTypes extends SiteDependentTypes {
    override type Filter        = lucuma.core.enums.GmosNorthFilter
    override type FPU           = lucuma.core.enums.GmosNorthFpu
    override type GmosStageMode = lucuma.core.enums.GmosNorthStageMode
    override type Grating       = lucuma.core.enums.GmosNorthGrating
  }

  final class NorthConfigTypes extends Config[NorthTypes]

  val northConfigTypes: NorthConfigTypes = new NorthConfigTypes

  // This is a trick to allow using a type from a class parameter to define the type of another class parameter
  final case class GmosConfig[T <: SiteDependentTypes] private (
    cc: Config[T]#CCConfig,
    dc: DCConfig,
    c:  Config[T],
    ns: NSConfig
  ) {
    def this(c: Config[T])(cc: c.CCConfig, dc: DCConfig, ns: NSConfig) = this(cc, dc, c, ns)
  }

  implicit def configShow[T <: SiteDependentTypes]: Show[GmosConfig[T]] =
    Show.show { config =>
      val ccShow =
        if (config.cc.isDarkOrBias) "DarkOrBias"
        else
          s"${config.cc.filter}, ${config.cc.disperser}, ${config.cc.fpu}, ${config.cc.stage}, ${config.cc.stage}, ${config.cc.dtaX}, ${config.cc.adc}, ${config.cc.useElectronicOffset}"

      s"($ccShow, ${config.dc.t}, ${config.dc.s}, ${config.dc.bi}, ${config.dc.roi.rois} ${config.ns})"
    }

}
