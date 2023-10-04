// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.derived.*
import io.circe.Decoder
import lucuma.ui.enums.ExecutionEnvironment
import lucuma.ui.sso.SSOConfig
import org.http4s.Uri
import org.http4s.circe.*

final case class AppConfig(
  hostName:    String,
  environment: ExecutionEnvironment,
  odbURI:      Uri,
  sso:         SSOConfig
) derives Eq,
      Decoder
