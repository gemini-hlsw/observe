// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gsaoi

import lucuma.core.util.Enumerated

trait GsaoiGuider[F[_]] {
  import GsaoiGuider._
  def currentState: F[GuideState]
  def guide: F[Unit]
  def endGuide: F[Unit]
}

object GsaoiGuider {

  sealed abstract class OdgwId(val tag: String) extends Product with Serializable

  object OdgwId {
    case object Odgw1 extends OdgwId("Odgw1")
    case object Odgw2 extends OdgwId("Odgw2")
    case object Odgw3 extends OdgwId("Odgw3")
    case object Odgw4 extends OdgwId("Odgw4")

    implicit val odgwIdEnumerated: Enumerated[OdgwId] = Enumerated.from(Odgw1, Odgw2, Odgw3, Odgw4).withTag(_.tag)

  }

  trait GuideState {
    def isGuideActive: Boolean
    def isOdgwGuiding(odgwId: OdgwId): Boolean
  }
}
