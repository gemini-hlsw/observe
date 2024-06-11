// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.Applicative
import cats.effect.Concurrent
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef
import lucuma.core.enums.MountGuideOption
import lucuma.core.enums.MountGuideOption.*
import lucuma.core.model.GuideConfig
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M1GuideConfig.*
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.M2GuideConfig.*
import lucuma.core.model.TelescopeGuideConfig
import monocle.Focus
import monocle.Lens

case class GuideConfigState(config: GuideConfig, gemsSkyPaused: Boolean)

object GuideConfigState {
  val config: Lens[GuideConfigState, GuideConfig]    = Focus[GuideConfigState](_.config)
  val gemsSkyPaused: Lens[GuideConfigState, Boolean] = Focus[GuideConfigState](_.gemsSkyPaused)
}

sealed trait GuideConfigDb[F[_]] {
  def value: F[GuideConfigState]

  def set(v: GuideConfigState): F[Unit]

  def update(f: GuideConfigState => GuideConfigState): F[Unit]

  def discrete: Stream[F, GuideConfigState]
}

object GuideConfigDb {

  val defaultGuideConfig: GuideConfigState =
    GuideConfigState(
      GuideConfig(TelescopeGuideConfig(MountGuideOff, M1GuideOff, M2GuideOff, None, None), None),
      false
    )

  def newDb[F[_]: Concurrent]: F[GuideConfigDb[F]] =
    SignallingRef[F, GuideConfigState](defaultGuideConfig).map { ref =>
      new GuideConfigDb[F] {
        override def value: F[GuideConfigState] = ref.get

        override def set(v: GuideConfigState): F[Unit] = ref.set(v)

        override def update(f: GuideConfigState => GuideConfigState): F[Unit] = ref.update(f)

        override def discrete: Stream[F, GuideConfigState] = ref.discrete
      }
    }

  def constant[F[_]: Applicative]: GuideConfigDb[F] =
    new GuideConfigDb[F] {
      override def value: F[GuideConfigState] = GuideConfigDb.defaultGuideConfig.pure[F]

      override def set(v: GuideConfigState): F[Unit] = Applicative[F].unit

      override def update(f: GuideConfigState => GuideConfigState): F[Unit] = Applicative[F].unit

      override def discrete: Stream[F, GuideConfigState] =
        Stream.emit(GuideConfigDb.defaultGuideConfig)
    }

}
