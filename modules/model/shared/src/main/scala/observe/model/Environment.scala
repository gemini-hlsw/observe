// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import cats.derived.*
import cats.syntax.all.*
import eu.timepit.refined.cats.given
import io.circe.Decoder
import io.circe.Encoder
import io.circe.refined.given
import lucuma.core.enums.Site

final case class Environment(site: Site, clientId: ClientId, version: Version)
    derives Eq,
      Encoder.AsObject,
      Decoder
