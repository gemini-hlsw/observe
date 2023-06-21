// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.config

import cats.tests.CatsSuite
import lucuma.core.enums.Site
import pureconfig._
import pureconfig.generic.derivation.default._

/**
 * Tests of config classes
 */
final class ConfigTypesSpec extends CatsSuite {
  test("Test site config") {
    final case class TestConf(site: Site)

    given ConfigReader[TestConf] = ConfigReader.derived

    ConfigSource.string("{ site: GS }").load[TestConf] shouldEqual TestConf(Site.GS).asRight
    ConfigSource.string("{ site: GN }").load[TestConf] shouldEqual TestConf(Site.GN).asRight
    ConfigSource.string("{ site: G }").load[TestConf].isLeft shouldBe true
  }
}
