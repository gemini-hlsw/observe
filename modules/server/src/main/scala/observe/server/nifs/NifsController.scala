// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.nifs

import cats.Show
import cats.syntax.all.*
import edu.gemini.spModel.gemini.nifs.NIFSParams.{ReadMode => LegacyReadMode}
import observe.model.dhs.ImageFileId
import observe.model.enums.ObserveCommandResult
import observe.server.Progress
import observe.server.nifs.NifsController.DCConfig
import observe.server.nifs.NifsController.NifsConfig
import shapeless.tag.@@
import squants.Time
import squants.time.TimeConversions.*

trait NifsController[F[_]] {

  def applyConfig(config: NifsConfig): F[Unit]

  def observe(fileId: ImageFileId, cfg: DCConfig): F[ObserveCommandResult]

  def endObserve: F[Unit]

  def stopObserve: F[Unit]

  def abortObserve: F[Unit]

  def observeProgress(total: Time): fs2.Stream[F, Progress]

  def calcTotalExposureTime(cfg: DCConfig): Time
}

trait CentralWavelengthD
trait MaskOffsetD
trait CoaddsI
trait PeriodI
trait NumberOfFowSamplesI
trait NumberOfSamplesI
trait NumberOfResetsI
trait NumberOfPeriodsI

sealed trait WindowCover extends Product with Serializable

object WindowCover {
  case object Closed extends WindowCover
  case object Opened extends WindowCover
}

object NifsController {
  // DC
  object Coadds extends NewType[Int]
  type Coadds = Coadds.Type
  object Period extends NewType[Int]
  type Period = Period.Type
  type ExposureTime       = Time
  object NumberOfResets extends NewType[Int]
  type NumberOfResets = NumberOfResets.Type
  object NumberOfPeriods extends NewType[Int]
  type NumberOfPeriods = NumberOfPeriods.Type
  object NumberOfSamples extends NewType[Int]
  type NumberOfSamples = NumberOfSamples.Type
  // this one is calculated out of the ReadMode
  object NumberOfFowSamples extends NewType[Int]
  type NumberOfFowSamples = NumberOfFowSamples.Type
  type ReadMode           = edu.gemini.spModel.gemini.nifs.NIFSParams.ReadMode
  type EngReadMode        = edu.gemini.spModel.gemini.nifs.NIFSParams.EngReadMode

  // CC
  type Filter            = edu.gemini.spModel.gemini.nifs.NIFSParams.Filter
  type Mask              = edu.gemini.spModel.gemini.nifs.NIFSParams.Mask
  type Disperser         = edu.gemini.spModel.gemini.nifs.NIFSParams.Disperser
  type ImagingMirror     = edu.gemini.spModel.gemini.nifs.NIFSParams.ImagingMirror
  object CentralWavelength extends NewType[Double]
  type CentralWavelength = CentralWavelength.Type
  object MaskOffset extends NewType[Double]
  type MaskOffset = MaskOffset.Type

  sealed trait DCConfig extends Product with Serializable {
    val coadds: Coadds
    val period: Option[Period]
    val exposureTime: ExposureTime
    val numberOfResets: Option[NumberOfResets]
    val numberOfPeriods: Option[NumberOfPeriods]
    val numberOfSamples: Option[NumberOfSamples]
    val readMode: Either[EngReadMode, ReadMode]
  }
  final case class ArcFlatDCConfig(
    coadds:          Coadds,
    period:          Option[Period],
    exposureTime:    ExposureTime,
    numberOfResets:  Option[NumberOfResets],
    numberOfPeriods: Option[NumberOfPeriods],
    numberOfSamples: Option[NumberOfSamples]
  ) extends DCConfig {

    val readMode: Either[EngReadMode, ReadMode] =
      edu.gemini.spModel.gemini.nifs.NIFSParams.ReadMode.BRIGHT_OBJECT_SPEC.asRight
  }
  final case class StdDCConfig(
    coadds:          Coadds,
    period:          Option[Period],
    exposureTime:    ExposureTime,
    numberOfResets:  Option[NumberOfResets],
    numberOfPeriods: Option[NumberOfPeriods],
    numberOfSamples: Option[NumberOfSamples],
    readMode:        Either[EngReadMode, ReadMode]
  ) extends DCConfig

  sealed trait CCConfig extends Product with Serializable

  case object DarkCCConfig extends CCConfig
  final case class StdCCConfig(
    filter:        Filter,
    mask:          Mask,
    disperser:     Disperser,
    imagingMirror: ImagingMirror,
    wavelength:    CentralWavelength,
    maskOffset:    MaskOffset,
    windowCover:   WindowCover
  ) extends CCConfig

  final case class NifsConfig(cc: CCConfig, dc: DCConfig)

  given Show[NifsConfig] = Show.fromToString

  def calcTotalExposureTime[F[_]](cfg: DCConfig): Time = {
    val readOutTime = cfg.readMode match {
      case Right(LegacyReadMode.BRIGHT_OBJECT_SPEC) => 11.4
      case Right(LegacyReadMode.MEDIUM_OBJECT_SPEC) => 27.4
      case Right(LegacyReadMode.FAINT_OBJECT_SPEC)  => 90.6
      case Left(_)                                  => 1 // TBD What should this be?
    }

    cfg.coadds * (cfg.exposureTime + readOutTime.seconds)
  }

}
