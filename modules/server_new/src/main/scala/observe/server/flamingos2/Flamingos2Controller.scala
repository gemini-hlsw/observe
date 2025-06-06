// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.flamingos2

import cats.Show
import cats.kernel.Eq
import fs2.Stream
import lucuma.core.enums.Flamingos2Decker
import lucuma.core.enums.Flamingos2Filter
import lucuma.core.enums.Flamingos2LyotWheel
import lucuma.core.enums.Flamingos2ReadoutMode
import lucuma.core.enums.Flamingos2Reads
import lucuma.core.enums.Flamingos2WindowCover
import lucuma.core.util.NewType
import lucuma.core.util.TimeSpan
import observe.model.dhs.ImageFileId
import observe.model.enums.ObserveCommandResult
import observe.server.Progress

trait Flamingos2Controller[F[_]] {
  import Flamingos2Controller._

  def applyConfig(config: Flamingos2Config): F[Unit]

  def observe(fileId: ImageFileId, expTime: TimeSpan): F[ObserveCommandResult]

  def endObserve: F[Unit]

  def observeProgress(total: TimeSpan): Stream[F, Progress]
}

object Flamingos2Controller {

  sealed trait FocalPlaneUnit extends Product with Serializable
  object FocalPlaneUnit {
    case object Open                      extends FocalPlaneUnit
    case object GridSub1Pix               extends FocalPlaneUnit
    case object Grid2Pix                  extends FocalPlaneUnit
    case object Slit1Pix                  extends FocalPlaneUnit
    case object Slit2Pix                  extends FocalPlaneUnit
    case object Slit3Pix                  extends FocalPlaneUnit
    case object Slit4Pix                  extends FocalPlaneUnit
    case object Slit6Pix                  extends FocalPlaneUnit
    case object Slit8Pix                  extends FocalPlaneUnit
    final case class Custom(mask: String) extends FocalPlaneUnit
    implicit val equal: Eq[FocalPlaneUnit] = Eq.fromUniversalEquals
  }

  sealed trait Grism
  object Grism {
    object Open    extends Grism
    object R1200JH extends Grism
    object R1200HK extends Grism
    object R3000   extends Grism
    object Dark    extends Grism
  }

  // opaque? newtype?
  type ExposureTime = TimeSpan

  object BiasMode extends NewType[Flamingos2Decker]
  type BiasMode = BiasMode.Type

  final case class CCConfig(
    windowCover: Flamingos2WindowCover,
    decker:      Flamingos2Decker,
    fpu:         FocalPlaneUnit,
    filter:      Flamingos2Filter,
    lyotWheel:   Flamingos2LyotWheel,
    grism:       Grism
  ) {
    def setWindowCover(windowCover: Flamingos2WindowCover): CCConfig =
      this.copy(windowCover = windowCover)
    def setDecker(decker:       Flamingos2Decker): CCConfig    = this.copy(decker = decker)
    def setFPU(focalPlaneUnit:  FocalPlaneUnit): CCConfig      = this.copy(fpu = focalPlaneUnit)
    def setFilter(filter:       Flamingos2Filter): CCConfig    = this.copy(filter = filter)
    def setLyotWheel(lyotWheel: Flamingos2LyotWheel): CCConfig = this.copy(lyotWheel = lyotWheel)
    def setGrism(grism:         Grism): CCConfig               = this.copy(grism = grism)
  }

  final case class DCConfig(
    exposureTime: ExposureTime,
    reads:        Flamingos2Reads,
    readoutMode:  Flamingos2ReadoutMode,
    decker:       Flamingos2Decker
  ) {
    def setExposureTime(exposureTime: ExposureTime): DCConfig =
      this.copy(exposureTime = exposureTime)
    def setReads(reads: Flamingos2Reads): DCConfig = this.copy(reads = reads)
    def setReadoutMode(readoutMode: Flamingos2ReadoutMode): DCConfig =
      this.copy(readoutMode = readoutMode)
    def setDecker(decker: Flamingos2Decker): DCConfig = this.copy(decker = decker)
  }

  final case class Flamingos2Config(cc: CCConfig, dc: DCConfig) {
    def setCCConfig(ccConfig: CCConfig): Flamingos2Config = this.copy(cc = ccConfig)
    def setDCConfig(dcConfig: DCConfig): Flamingos2Config = this.copy(dc = dcConfig)
  }

  given Show[Flamingos2Config] = Show.fromToString
}
