// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.config

import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.Site
import lucuma.core.util.Enumerated
import lucuma.sso.client.util.GpgPublicKeyReader
import observe.model.config.*
import org.http4s.Uri
import pureconfig.*
import pureconfig.error.*
import pureconfig.module.catseffect.syntax.*
import pureconfig.module.http4s.*
import pureconfig.module.ip4s.*

import java.nio.file.Path
import java.security.PublicKey
import scala.concurrent.duration.FiniteDuration

case class EnumValueUnknown(value: String, available: List[String]) extends FailureReason {
  def description: String =
    s"enumerated value '$value' invalid. Should be one of ${available.mkString("[", ", ", "]")}"
}

case class StrategyValueUnknown(strategy: String) extends FailureReason {
  def description: String = s"strategy '$strategy' invalid"
}
case class PublicKeyUnknown(value: String)        extends FailureReason {
  def description: String = s"publicKey '$value' invalid"
}

/**
 * Settings and decoders to parse the configuration files
 */
given [A: Enumerated]: ConfigReader[A] = ConfigReader.fromCursor[A]: cf =>
  cf.asString.flatMap: c =>
    Enumerated[A].fromTag(c) match
      case Some(x) => x.asRight
      case _       => cf.failed(EnumValueUnknown(c, Enumerated[A].all.map(_.toString)))

given ConfigReader[ControlStrategy] =
  ConfigReader.fromCursor[ControlStrategy] { cf =>
    cf.asString.flatMap { c =>
      ControlStrategy.fromString(c) match {
        case Some(x) => x.asRight
        case _       => cf.failed(StrategyValueUnknown(c))
      }
    }
  }

given ConfigReader[PublicKey] =
  ConfigReader.fromCursor[PublicKey] { cf =>
    cf.asString.flatMap { c =>
      GpgPublicKeyReader
        .publicKey(c)
        .leftFlatMap(_ => cf.failed(PublicKeyUnknown(c)))
    }
  }

given ConfigReader[SystemsControlConfiguration] = ConfigReader.derived

given ConfigReader[GpiUriSettings] = ConfigReader[Uri].map(GpiUriSettings.apply(_))

given ConfigReader[GhostUriSettings] = ConfigReader[Uri].map(GhostUriSettings.apply(_))

given ConfigReader[ObserveEngineConfiguration] = ConfigReader.derived

given ConfigReader[TLSConfig] = ConfigReader.derived

given ConfigReader[WebServerConfiguration] = ConfigReader.derived

given ConfigReader[LucumaSSOConfiguration] = ConfigReader.derived

given ConfigReader[ObserveConfiguration] = ConfigReader.derived

def loadConfiguration[F[_]: Sync](config: ConfigObjectSource): F[ObserveConfiguration] =
  config.loadF[F, ObserveConfiguration]()
