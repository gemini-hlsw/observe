// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config

import cats.Eq
import cats.derived.*
import org.http4s.Uri

import java.security.PublicKey

/**
 * Parameeters to configure SSO
 *
 * @param serviceToken
 */
case class LucumaSSOConfiguration(
  serviceToken: String,
  publicKey:    PublicKey,
  ssoUrl:       Uri
)

object LucumaSSOConfiguration {
  private given Eq[PublicKey] = Eq.fromUniversalEquals

  given Eq[LucumaSSOConfiguration] = Eq.derived[LucumaSSOConfiguration]
}
