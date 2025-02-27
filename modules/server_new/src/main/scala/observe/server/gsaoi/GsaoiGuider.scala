// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gsaoi

import lucuma.core.util.Enumerated

trait GsaoiGuider[F[_]] {
  import GsaoiGuider.*
  def currentState: F[StepGuideState]
  def guide: F[Unit]
  def endGuide: F[Unit]
}

object GsaoiGuider {

  enum OdgwId(val tag: String) derives Enumerated {
    case Odgw1 extends OdgwId("Odgw1")
    case Odgw2 extends OdgwId("Odgw2")
    case Odgw3 extends OdgwId("Odgw3")
    case Odgw4 extends OdgwId("Odgw4")
  }

  trait StepGuideState {
    def isGuideActive: Boolean
    def isOdgwGuiding(odgwId: OdgwId): Boolean
  }
}
