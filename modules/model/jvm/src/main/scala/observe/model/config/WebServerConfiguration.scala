// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config

import cats.Eq
import cats.derived.*

import java.nio.file.Path

private given Eq[Path] = Eq.fromUniversalEquals

/**
 * Configuration for the TLS server
 * @param keyStore
 *   Location where to find the keystore
 * @param keyStorePwd
 *   Password for the keystore
 * @param certPwd
 *   Password for the certificate used for TLS
 */
case class TLSConfig(keyStore: Path, keyStorePwd: String, certPwd: String) derives Eq

/**
 * Configuration for the web server side of the observe
 * @param host
 *   Host name to listen, typicall 0.0.0.0
 * @param port
 *   Port to listen for web requestes
 * @param insecurePort
 *   Port where we setup a redirect server to send to https
 * @param externalBaseUrl
 *   Redirects need an external facing name
 * @param tls
 *   Configuration of TLS, optional
 */
case class WebServerConfiguration(
  host:            String,
  port:            Int,
  insecurePort:    Int,
  externalBaseUrl: String,
  tls:             Option[TLSConfig]
) derives Eq
