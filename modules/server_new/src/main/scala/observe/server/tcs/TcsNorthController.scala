// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.Show
import cats.data.NonEmptySet
import cats.implicits.*
import observe.model.enums.NodAndShuffleStage
import observe.server.altair.{Altair, AltairController}
import observe.server.tcs.TcsController.*
import observe.server.tcs.TcsController.GuiderConfig.given

trait TcsNorthController[F[_]] {
  import TcsNorthController.*

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
  )(stage: NodAndShuffleStage, offset: InstrumentOffset, guided: Boolean): F[Unit]

}

object TcsNorthController {

  type TcsNorthConfig   = TcsConfig[AoGuide, AltairController.AltairConfig]
  type TcsNorthAoConfig = AoTcsConfig[AoGuide, AltairController.AltairConfig]

  given Show[AoGuide] =
    Show.show(_.value.show)

  given Show[TcsNorthConfig] = Show.show {
    case x: BasicTcsConfig   => x.show
    case x: TcsNorthAoConfig => x.show
  }

}
