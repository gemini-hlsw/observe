// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.config

import cats.effect.IO
import lucuma.core.enums.Site
import java.nio.file.Paths
import org.http4s.Uri
import org.http4s.implicits.*
import pureconfig.*
import scala.concurrent.duration.*
import observe.model.config.*
import shapeless.tag
import munit.CatsEffectSuite

class ConfigurationLoaderSpec extends CatsEffectSuite {
  val gcal   =
    SmartGcalConfiguration(uri"gsodbtest.gemini.edu", Paths.get("/tmp/smartgcal"))
  val tls    = TLSConfig(Paths.get("file.jks"), "key", "cert")
  val auth   = AuthenticationConfig(2.hour,
                                  "ObserveToken",
                                  "somekey",
                                  false,
                                  List(uri"ldap://sbfdc-wv1.gemini.edu:3268")
  )
  val ws     = WebServerConfiguration("0.0.0.0", 7070, 7071, "localhost", Some(tls))
  val server = ObserveEngineConfiguration(
    uri"localhost",
    uri"http://cpodhsxx:9090/axis2/services/dhs/images",
    SystemsControlConfiguration(
      altair = ControlStrategy.Simulated,
      gems = ControlStrategy.Simulated,
      dhs = ControlStrategy.Simulated,
      f2 = ControlStrategy.Simulated,
      gcal = ControlStrategy.Simulated,
      gmos = ControlStrategy.Simulated,
      gnirs = ControlStrategy.Simulated,
      gpi = ControlStrategy.Simulated,
      gpiGds = ControlStrategy.Simulated,
      ghost = ControlStrategy.Simulated,
      ghostGds = ControlStrategy.Simulated,
      gsaoi = ControlStrategy.Simulated,
      gws = ControlStrategy.Simulated,
      nifs = ControlStrategy.Simulated,
      niri = ControlStrategy.Simulated,
      tcs = ControlStrategy.Simulated
    ),
    true,
    false,
    2,
    3.seconds,
    GpiUriSettings(uri"vm://gpi?marshal=false&broker.persistent=false"),
    GpiUriSettings(uri"http://localhost:8888/xmlrpc"),
    GhostUriSettings(uri"vm://ghost?marshal=false&broker.persistent=false"),
    GhostUriSettings(uri"http://localhost:8888/xmlrpc"),
    "tcs=tcs:, ao=ao:, gm=gm:, gc=gc:, gw=ws:, m2=m2:, oiwfs=oiwfs:, ag=ag:, f2=f2:, gsaoi=gsaoi:, aom=aom:, myst=myst:, rtc=rtc:",
    Some("127.0.0.1"),
    0,
    5.seconds,
    10.seconds
  )
  val ref    = ObserveConfiguration(Site.GS, Mode.Development, server, ws, gcal, auth)

  test("read config") {
    loadConfiguration[IO](ConfigSource.string(conf)).map(assertEquals(_, ref))
  }

  val conf = """
#
# Observe server configuration for development mode
#

# mode can be dev in which case fake authentication is supported and the UI provides some extra tools
mode = dev
site = GS

# Authentication related settings
authentication {
    # Indicates how long a session is valid in hrs
    session-life-hrs = 2 hours
    # Name of the cookie to store the session
    cookie-name = "ObserveToken"
    # Secret key for JWT tokens
    secret-key = "somekey"
    use-ssl = false
    # List of LDAP servers, the list is used in a failover fashion
    ldap-urls = ["ldap://sbfdc-wv1.gemini.edu:3268"]
}

# Web server related configuration
web-server {
    # Interface to listen on, 0.0.0.0 listens in all interfaces, production instances should be more restrictive
    host = "0.0.0.0"
    # Port to serve https requests
    port = 7070
    # Port for redirects to https
    insecure-port = 7071
    # External url used for redirects
    external-base-url = "localhost"
    tls {
        key-store = "file.jks"
        key-store-pwd = "key"
        cert-pwd = "cert"
    }
}

smart-gcal {
    # We normally always use GS for smartGCalDir
    smart-gcal-host = "gsodbtest.gemini.edu"
    # Tmp file for development
    smart-gcal-dir = "/tmp/smartgcal"
}

# Configuration of the observe engine
observe-engine {
    # host for the odb
    odb = localhost
    dhs-server = "http://cpodhsxx:9090/axis2/services/dhs/images"
    # Tells Observe how to interact with a system:
    #   full: connect and command the system
    #   readOnly: connect, but only to read values
    #   simulated: don't connect, simulate internally
    system-control {
        dhs = simulated
        f-2 = simulated
        gcal = simulated
        ghost = simulated
        ghost-gds = simulated
        gmos = simulated
        gnirs = simulated
        gpi = simulated
        gpi-gds = simulated
        gsaoi = simulated
        gws = simulated
        nifs = simulated
        niri = simulated
        tcs = simulated
        altair = simulated
        gems = simulated
    }
    odb-notifications = true
    # Set to true on development to simulate errors on f2
    inst-force-error = false
    # if instForceError is true fail at the given iteration
    fail-at = 2
    odb-queue-polling-interval = 3 seconds
    tops = "tcs=tcs:, ao=ao:, gm=gm:, gc=gc:, gw=ws:, m2=m2:, oiwfs=oiwfs:, ag=ag:, f2=f2:, gsaoi=gsaoi:, aom=aom:, myst=myst:, rtc=rtc:"
    epics-ca-addr-list = 127.0.0.1
    read-retries = 0
    io-timeout = 5 seconds
    dhs-timeout = 10 seconds
    gpi-url = "vm://gpi?marshal=false&broker.persistent=false"
    gpi-gds = "http://localhost:8888/xmlrpc"
    ghost-url = "vm://ghost?marshal=false&broker.persistent=false"
    ghost-gds = "http://localhost:8888/xmlrpc"
}

"""
}
