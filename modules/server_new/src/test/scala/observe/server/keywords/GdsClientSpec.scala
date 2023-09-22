// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import scala.xml.XML

final class GdsClientSpec extends munit.DisciplineSuite {
  test("GDSClient should reject bad responses") {
    val xml = XML.load(getClass.getResource("/gds-bad-resp.xml"))
    assert(
      GdsClient
        .parseError(xml)
        .isLeft
    )
  }
}
