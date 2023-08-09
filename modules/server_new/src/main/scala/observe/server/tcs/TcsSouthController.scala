// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.Show
import cats.data.NonEmptySet
import cats.implicits.*
import lucuma.core.util.NewType
import observe.model.enums.NodAndShuffleStage
import observe.server.gems.Gems
import observe.server.gems.GemsController.GemsConfig
import observe.server.tcs.TcsController.*
import lucuma.core.enums.Site

trait TcsSouthController[F[_]] {
  import TcsSouthController.*

  def applyConfig(
    subsystems: NonEmptySet[Subsystem],
    gaos:       Option[Gems[F]],
    tc:         TcsSouthConfig
  ): F[Unit]

  def notifyObserveStart: F[Unit]

  def notifyObserveEnd: F[Unit]

  def nod(
    subsystems: NonEmptySet[Subsystem],
    tcsConfig:  TcsSouthConfig
  )(stage: NodAndShuffleStage, offset: InstrumentOffset, guided: Boolean): F[Unit]

}

object TcsSouthController {

  object CWFS1Config extends NewType[GuiderConfig]
  object CWFS2Config extends NewType[GuiderConfig]
  object CWFS3Config extends NewType[GuiderConfig]
  object ODGW1Config extends NewType[GuiderConfig]
  object ODGW2Config extends NewType[GuiderConfig]
  object ODGW3Config extends NewType[GuiderConfig]
  object ODGW4Config extends NewType[GuiderConfig]

  final case class GemsGuiders(
    cwfs1: CWFS1Config.Type,
    cwfs2: CWFS2Config.Type,
    cwfs3: CWFS3Config.Type,
    odgw1: ODGW1Config.Type,
    odgw2: ODGW2Config.Type,
    odgw3: ODGW3Config.Type,
    odgw4: ODGW4Config.Type
  )

  type TcsSouthConfig   = TcsConfig[Site.GS.type]
  type TcsSouthAoConfig = AoTcsConfig[Site.GS.type]

  given Show[GemsGuiders] = Show.show { x =>
    s"(cwfs1 = ${x.cwfs1.value.show}, cwfs2 = ${x.cwfs2.value.show}, cwfs3 = ${x.cwfs3.value.show}, odgw1 = ${x.odgw1.value.show}, odgw2 = ${x.odgw2.value.show}, odgw3 = ${x.odgw3.value.show}, odgw4 = ${x.odgw4.value.show})"
  }

  given Show[TcsSouthConfig] = Show.show {
    case x: BasicTcsConfig[Site.GS.type] => x.show
    case x: TcsSouthAoConfig             => x.show
  }

}
