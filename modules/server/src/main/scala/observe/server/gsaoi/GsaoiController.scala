// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gsaoi

import cats.Applicative
import cats.Eq
import cats.Show
import cats.syntax.all.*
import observe.model.dhs.ImageFileId
import observe.model.enums.ObserveCommandResult
import observe.server.Progress
import observe.server.gsaoi.GsaoiController.DCConfig
import observe.server.gsaoi.GsaoiController.GsaoiConfig
import shapeless.tag.@@
import squants.Time
import squants.time.TimeConversions.*

trait GsaoiController[F[_]] {

  def applyConfig(config: GsaoiConfig): F[Unit]

  def observe(fileId: ImageFileId, cfg: DCConfig): F[ObserveCommandResult]

  def endObserve: F[Unit]

  def stopObserve: F[Unit]

  def abortObserve: F[Unit]

  def observeProgress(total: Time): fs2.Stream[F, Progress]

  def calcTotalExposureTime(cfg: DCConfig)(using ev: Applicative[F]): F[Time] = {
    val readFactor  = 1.2
    val readOutTime = 15

    (cfg.coadds * cfg.exposureTime * readFactor + readOutTime.seconds).pure[F]
  }

}

trait CoaddsI
trait NumberOfFowSamplesI

sealed trait WindowCover extends Product with Serializable

object WindowCover {
  case object Closed extends WindowCover
  case object Opened extends WindowCover

  given Eq[WindowCover] = Eq.fromUniversalEquals
}

object GsaoiController {
  // DC
  type ReadMode = edu.gemini.spModel.gemini.gsaoi.Gsaoi.ReadMode
  type Roi      = edu.gemini.spModel.gemini.gsaoi.Gsaoi.Roi
  object Coadds extends NewType[Int]
  type Coadds       = Coadds.Type
  type ExposureTime = Time
  object NumberOfFowSamples extends NewType[Int]
  type NumberOfFowSamples = NumberOfFowSamples.Type

  // CC
  type Filter       = edu.gemini.spModel.gemini.gsaoi.Gsaoi.Filter
  type OdgwSize     = edu.gemini.spModel.gemini.gsaoi.Gsaoi.OdgwSize
  type UtilityWheel = edu.gemini.spModel.gemini.gsaoi.Gsaoi.UtilityWheel

  final case class DCConfig(
    readMode:           ReadMode,
    roi:                Roi,
    coadds:             Coadds,
    exposureTime:       ExposureTime,
    numberOfFowSamples: NumberOfFowSamples
  )

  object DCConfig {
    // Universal equals is fine as it is integers and java classes
    given Eq[DCConfig] = Eq.fromUniversalEquals
  }

  final case class CCConfig(
    filter:       Filter,
    odgwSize:     OdgwSize,
    utilityWheel: UtilityWheel,
    windowCover:  WindowCover
  )

  object CCConfig {
    // Universal equals is fine as it is integers and java classes
    given Eq[CCConfig] = Eq.fromUniversalEquals
  }

  final case class GsaoiConfig(cc: CCConfig, dc: DCConfig)

  given Show[GsaoiConfig] = Show.fromToString

}
