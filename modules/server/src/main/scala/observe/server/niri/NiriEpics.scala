// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.niri

import java.lang.{Double => JDouble}
import cats.effect.Async
import cats.effect.IO
import cats.effect.Sync
import cats.syntax.all.*
import edu.gemini.epics.acm.*
import edu.gemini.observe.server.niri.{
  BeamSplitter,
  BuiltInROI,
  Camera,
  DetectorState,
  Disperser,
  Mask,
  ReadMode
}
import observe.server.{EpicsCommandBase, EpicsSystem, ObserveCommandBase}
import observe.server.EpicsCommandBase.setParameter
import observe.server.EpicsUtil.safeAttributeF
import observe.server.EpicsUtil.safeAttributeSDoubleF
import observe.server.EpicsUtil.safeAttributeSIntF

class NiriEpics[F[_]: Async](epicsService: CaService, tops: Map[String, String]) {
  val sysName: String = "NIRI"
  val NiriTop: String = tops.getOrElse("niri", "niri:")
  val NisTop: String  = tops.getOrElse("nis", "NIS:")

  object configCmd extends EpicsCommandBase[F](sysName) {
    override protected val cs: Option[CaCommandSender] =
      Option(epicsService.getCommandSender("nis::config"))

    private val disperser: Option[CaParameter[Disperser]] = cs.flatMap(cmd =>
      Option(cmd.addEnum("disperser", s"${NisTop}grism:menu", classOf[Disperser], false))
    )
    def setDisperser(v: Disperser): F[Unit] = setParameter(disperser, v)

    private val readMode: Option[CaParameter[ReadMode]] = cs.flatMap(cmd =>
      Option(cmd.addEnum("readmode", s"${NisTop}readmode:menu", classOf[ReadMode], false))
    )
    def setReadMode(v: ReadMode): F[Unit] = setParameter(readMode, v)

    private val coadds: Option[CaParameter[Integer]] =
      cs.flatMap(cmd => Option(cmd.getInteger("numCoAdds")))
    def setCoadds(v: Int): F[Unit] = setParameter(coadds, Integer.valueOf(v))

    private val mask: Option[CaParameter[Mask]] =
      cs.flatMap(cmd => Option(cmd.addEnum("mask", s"${NisTop}fpmask:menu", classOf[Mask], false)))
    def setMask(v: Mask): F[Unit] = setParameter(mask, v)

    private val camera: Option[CaParameter[Camera]] = cs.flatMap(cmd =>
      Option(cmd.addEnum("camera", s"${NisTop}camera:menu", classOf[Camera], false))
    )
    def setCamera(v: Camera): F[Unit] = setParameter(camera, v)

    private val beamSplitter: Option[CaParameter[BeamSplitter]] = cs.flatMap(cmd =>
      Option(cmd.addEnum("beamSplitter", s"${NisTop}beamsplit:menu", classOf[BeamSplitter], false))
    )
    def setBeamSplitter(v: BeamSplitter): F[Unit] = setParameter(beamSplitter, v)

    private val exposureTime: Option[CaParameter[JDouble]] =
      cs.flatMap(cmd => Option(cmd.getDouble("exposureTime")))
    def setExposureTime(v: Double): F[Unit] = setParameter(exposureTime, JDouble.valueOf(v))

    private val builtInROI: Option[CaParameter[BuiltInROI]] = cs.flatMap(cmd =>
      Option(cmd.addEnum("builtinROI", s"${NisTop}roi:menu", classOf[BuiltInROI], false))
    )
    def setBuiltInROI(v: BuiltInROI): F[Unit] = setParameter(builtInROI, v)

    private val filter: Option[CaParameter[String]] =
      cs.flatMap(cmd => Option(cmd.getString("filter")))
    def setFilter(v: String): F[Unit] = setParameter(filter, v)

    private val focus: Option[CaParameter[String]] =
      cs.flatMap(cmd => Option(cmd.getString("focus")))
    def setFocus(v: String): F[Unit] = setParameter(focus, v)

  }

  /*
   * For some reason the window cover is not include in the IS configuration parameters. It is
   * applied by the IS apply command, nevertheless. This command exists only to set the parameter.
   */
  object windowCoverConfig extends EpicsCommandBase[F](sysName) {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("niri:config"))

    private val windowCover: Option[CaParameter[String]] =
      cs.flatMap(cmd => Option(cmd.getString("windowCover")))
    def setWindowCover(v: String): F[Unit] = setParameter(windowCover, v)
  }

  object endObserveCmd extends EpicsCommandBase[F](sysName) {
    override val cs: Option[CaCommandSender] = Option(
      epicsService.getCommandSender("niri::endObserve")
    )
  }

  object configDCCmd extends EpicsCommandBase[F](sysName) {
    override protected val cs: Option[CaCommandSender] =
      Option(epicsService.getCommandSender("niri::obsSetup"))
  }

  private val stopCS: Option[CaCommandSender]  = Option(epicsService.getCommandSender("niri::stop"))
  private val observeAS: Option[CaApplySender] = Option(
    epicsService.createObserveSender("niri::observeCmd",
                                     s"${NiriTop}dc:apply",
                                     s"${NiriTop}dc:applyC",
                                     s"${NiriTop}dc:observeC",
                                     true,
                                     s"${NiriTop}dc:stop",
                                     s"${NiriTop}dc:abort",
                                     ""
    )
  )

  object stopCmd extends EpicsCommandBase[F](sysName) {
    override protected val cs: Option[CaCommandSender] = stopCS
  }

  object stopAndWaitCmd extends ObserveCommandBase[F](sysName) {
    override protected val cs: Option[CaCommandSender] = stopCS
    override protected val os: Option[CaApplySender]   = observeAS
  }

  private val abortCS: Option[CaCommandSender] = Option(
    epicsService.getCommandSender("niri::abort")
  )

  object abortCmd extends EpicsCommandBase[F](sysName) {
    override protected val cs: Option[CaCommandSender] = abortCS
  }

  object abortAndWait extends ObserveCommandBase[F](sysName) {
    override protected val cs: Option[CaCommandSender] = abortCS
    override protected val os: Option[CaApplySender]   = observeAS
  }

  object observeCmd extends ObserveCommandBase[F](sysName) {
    override protected val cs: Option[CaCommandSender] = Option(
      epicsService.getCommandSender("niri::observe")
    )
    override protected val os: Option[CaApplySender]   = observeAS

    private val label: Option[CaParameter[String]] = cs.map(_.getString("label"))
    def setLabel(v: String): F[Unit] = setParameter(label, v)
  }

  private val status: CaStatusAcceptor = epicsService.getStatusAcceptor("niri::status")

  def beamSplitter: F[String] = safeAttributeF(status.getStringAttribute("BEAMSPLT"))

  def focus: F[String] = safeAttributeF(status.getStringAttribute("FOCUSNAM"))

  def focusPosition: F[Double] = safeAttributeSDoubleF(status.getDoubleAttribute("FOCUSPOS"))

  def mask: F[String] = safeAttributeF(status.getStringAttribute("FPMASK"))

  def pupilViewer: F[String] = safeAttributeF(status.getStringAttribute("PVIEW"))

  def camera: F[String] = safeAttributeF(status.getStringAttribute("CAMERA"))

  def windowCover: F[String] = safeAttributeF(status.getStringAttribute("WINDCOVR"))

  def filter1: F[String] = safeAttributeF(status.getStringAttribute("FILTER1"))

  def filter2: F[String] = safeAttributeF(status.getStringAttribute("FILTER2"))

  def filter3: F[String] = safeAttributeF(status.getStringAttribute("FILTER3"))

  private val dcStatus: CaStatusAcceptor = epicsService.getStatusAcceptor("niri::dcstatus")

  def dhsConnected: F[Boolean] =
    safeAttributeSIntF(dcStatus.getIntegerAttribute("dhcConnected")).map(_ === 1)

  private val arrayActiveAttr: CaAttribute[DetectorState] =
    dcStatus.addEnum("arrayState", s"${NiriTop}dc:activate", classOf[DetectorState])

  def arrayActive: F[Boolean] = safeAttributeF(arrayActiveAttr).map(_.getActive)

  def minIntegration: F[Double] = safeAttributeSDoubleF(dcStatus.getDoubleAttribute("minInt"))

  def integrationTime: F[Double] = safeAttributeSDoubleF(dcStatus.getDoubleAttribute("intTime"))

  def coadds: F[Int] = safeAttributeSIntF(dcStatus.getIntegerAttribute("numCoAdds"))

  def detectorTemp: F[Double] = safeAttributeSDoubleF(dcStatus.getDoubleAttribute("TDETABS"))

  def µcodeName: F[String] = safeAttributeF(dcStatus.getStringAttribute("UCODENAM"))

  def µcodeType: F[Int] = safeAttributeSIntF(dcStatus.getIntegerAttribute("UCODETYP"))

  def framesPerCycle: F[Int] = safeAttributeSIntF(dcStatus.getIntegerAttribute("FRMSPCYCL"))

  def detectorVDetBias: F[Double] = safeAttributeSDoubleF(dcStatus.getDoubleAttribute("VDET"))

  def detectorVSetBias: F[Double] = safeAttributeSDoubleF(dcStatus.getDoubleAttribute("VSET"))

  def obsEpoch: F[Double] = safeAttributeSDoubleF(dcStatus.getDoubleAttribute("OBSEPOCH"))

  def mountTemp: F[Double] = safeAttributeSDoubleF(dcStatus.getDoubleAttribute("TMOUNT"))

  def digitalAverageCount: F[Int] = safeAttributeSIntF(dcStatus.getIntegerAttribute("NDAVGS"))

  def vggCl1: F[Double] = safeAttributeSDoubleF(dcStatus.getDoubleAttribute("VGGCL1"))

  def vddCl1: F[Double] = safeAttributeSDoubleF(dcStatus.getDoubleAttribute("VDDCL1"))

  def vggCl2: F[Double] = safeAttributeSDoubleF(dcStatus.getDoubleAttribute("VGGCL2"))

  def vddCl2: F[Double] = safeAttributeSDoubleF(dcStatus.getDoubleAttribute("VDDCL2"))

  def vddUc: F[Double] = safeAttributeSDoubleF(dcStatus.getDoubleAttribute("VDDUC"))

  def lnrs: F[Int] = safeAttributeSIntF(dcStatus.getIntegerAttribute("LNRS"))

  def hdrTiming: F[Int] = safeAttributeSIntF(dcStatus.getIntegerAttribute("hdrtiming"))

  def arrayType: F[String] = safeAttributeF(dcStatus.getStringAttribute("ARRAYTYP"))

  def arrayId: F[String] = safeAttributeF(dcStatus.getStringAttribute("ARRAYID"))

  def mode: F[Int] = safeAttributeSIntF(dcStatus.getIntegerAttribute("MODE"))

  def dcIsPreparing: F[Boolean] =
    safeAttributeSIntF(dcStatus.getIntegerAttribute("prepObs")).map(_ =!= 0)

  def dcIsAcquiring: F[Boolean] =
    safeAttributeSIntF(dcStatus.getIntegerAttribute("acqObs")).map(_ =!= 0)

  def dcIsReadingOut: F[Boolean] =
    safeAttributeSIntF(dcStatus.getIntegerAttribute("readingOut")).map(_ =!= 0)
}

object NiriEpics extends EpicsSystem[NiriEpics[IO]] {

  override val className: String      = getClass.getName
  override val CA_CONFIG_FILE: String = "/Niri.xml"

  override def build[F[_]: Sync](service: CaService, tops: Map[String, String]): F[NiriEpics[IO]] =
    Sync[F].delay(new NiriEpics[IO](service, tops))

}
