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
import lucuma.core.enums.ExecutionEnvironment
import lucuma.core.enums.Site
import lucuma.core.model.ObservationReference
import monocle.Focus
import monocle.Lens
import org.http4s.Uri
import org.http4s.circe.given

case class ClientConfig(
  site:           Site,
  environment:    ExecutionEnvironment,
  odbUri:         Uri,
  ssoUri:         Uri,
  exploreBaseUri: Uri,
  clientId:       ClientId,
  version:        Version
) derives Eq,
      Encoder.AsObject,
      Decoder:
  def linkToExploreObs(obsRef: ObservationReference): Uri =
    exploreBaseUri / obsRef.label

object ClientConfig:
  val clientId: Lens[ClientConfig, ClientId] = Focus[ClientConfig](_.clientId)
