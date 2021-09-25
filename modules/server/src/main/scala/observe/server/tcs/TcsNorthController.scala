// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.Show
import cats.data.NonEmptySet
import cats.implicits._
import observe.model.enum.NodAndShuffleStage
import observe.server.altair.Altair
import observe.server.altair.AltairController
import observe.server.tcs.TcsController.{
  AoGuide,
  AoTcsConfig,
  BasicTcsConfig,
  GuiderConfig,
  InstrumentOffset,
  Subsystem,
  TcsConfig
}
import shapeless.tag.@@

trait TcsNorthController[F[_]] {
  import TcsNorthController._

  def applyConfig(
    subsystems: NonEmptySet[TcsController.Subsystem],
    gaos:       Option[Altair[F]],
    tc:         TcsNorthConfig
  ): F[Unit]

  def notifyObserveStart: F[Unit]

  def notifyObserveEnd: F[Unit]

  def nod(
    subsystems: NonEmptySet[Subsystem],
    tcsConfig:  TcsNorthConfig
  )(stage:      NodAndShuffleStage, offset: InstrumentOffset, guided: Boolean): F[Unit]

}

object TcsNorthController {

  type TcsNorthConfig   = TcsConfig[GuiderConfig @@ AoGuide, AltairController.AltairConfig]
  type TcsNorthAoConfig = AoTcsConfig[GuiderConfig @@ AoGuide, AltairController.AltairConfig]

  implicit val aoGuideShow: Show[GuiderConfig @@ AoGuide] =
    Show.show(_.asInstanceOf[GuiderConfig].show)

  implicit val tcsNorthConfigShow: Show[TcsNorthConfig]   = Show.show {
    case x: BasicTcsConfig   => x.show
    case x: TcsNorthAoConfig => x.show
  }

}
