// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.config

import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.Site
import observe.model.config.*
import org.http4s.Uri
import pureconfig.*
import pureconfig.error.*
import pureconfig.generic.derivation.default.*
import pureconfig.module.catseffect.syntax.*
import pureconfig.module.http4s.*
import pureconfig.module.ip4s.*

import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration

case class SiteValueUnknown(site: String)         extends FailureReason {
  def description: String = s"site '$site' invalid"
}
case class ModeValueUnknown(mode: String)         extends FailureReason {
  def description: String = s"mode '$mode' invalid"
}
case class StrategyValueUnknown(strategy: String) extends FailureReason {
  def description: String = s"strategy '$strategy' invalid"
}

/**
 * Settings and decoders to parse the configuration files
 */

given ConfigReader[Site] = ConfigReader.fromCursor[Site] { cf =>
  cf.asString.flatMap {
    case "GS" => Site.GS.asRight
    case "GN" => Site.GN.asRight
    case s    => cf.failed(SiteValueUnknown(s))
  }
}

given ConfigReader[Mode] = ConfigReader.fromCursor[Mode] { cf =>
  cf.asString.flatMap {
    case "production" => Mode.Production.asRight
    case "dev"        => Mode.Development.asRight
    case s            => cf.failed(ModeValueUnknown(s))
  }
}

given ConfigReader[ControlStrategy] =
  ConfigReader.fromCursor[ControlStrategy] { cf =>
    cf.asString.flatMap { c =>
      ControlStrategy.fromString(c) match {
        case Some(x) => x.asRight
        case _       => cf.failed(StrategyValueUnknown(c))
      }
    }
  }

given ConfigReader[SystemsControlConfiguration] = ConfigReader.derived

given ConfigReader[GpiUriSettings] = ConfigReader[Uri].map(GpiUriSettings.apply(_))

given ConfigReader[GhostUriSettings] = ConfigReader[Uri].map(GhostUriSettings.apply(_))

given ConfigReader[ObserveEngineConfiguration] = ConfigReader.derived

given ConfigReader[TLSConfig] = ConfigReader.derived

given ConfigReader[WebServerConfiguration] = ConfigReader.derived

given ConfigReader[AuthenticationConfig] = ConfigReader.derived

given ConfigReader[LucumaSSOConfiguration] = ConfigReader.derived

given ConfigReader[ObserveConfiguration] = ConfigReader.derived

def loadConfiguration[F[_]: Sync](config: ConfigObjectSource): F[ObserveConfiguration] =
  config.loadF[F, ObserveConfiguration]()
