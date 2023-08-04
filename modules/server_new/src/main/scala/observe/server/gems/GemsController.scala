// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gems

import cats.{Eq, Show}
import cats.implicits.*
import mouse.boolean.*
import observe.server.gems.Gems.GemsWfsState
import observe.server.tcs.Gaos.PauseConditionSet
import observe.server.tcs.Gaos.PauseResume
import observe.server.tcs.Gaos.ResumeConditionSet

trait GemsController[F[_]] {
  import GemsController._

  def pauseResume(pauseReasons: PauseConditionSet, resumeReasons: ResumeConditionSet)(
    cfg: GemsConfig
  ): F[PauseResume[F]]

  val stateGetter: GemsWfsState[F]

}

object GemsController {

  sealed trait P1Usage extends Product with Serializable
  object P1Usage {
    case object Use     extends P1Usage
    case object DontUse extends P1Usage

    given Eq[P1Usage] = Eq.fromUniversalEquals

    def fromBoolean(b: Boolean): P1Usage = if (b) Use else DontUse
  }

  sealed trait OIUsage extends Product with Serializable
  object OIUsage {
    case object Use     extends OIUsage
    case object DontUse extends OIUsage

    given Eq[OIUsage] = Eq.fromUniversalEquals

    def fromBoolean(b: Boolean): OIUsage = if (b) Use else DontUse
  }

  sealed trait GemsConfig extends Product with Serializable {
    val isCwfs1Used: Boolean
    val isCwfs2Used: Boolean
    val isCwfs3Used: Boolean
    val isOdgw1Used: Boolean
    val isOdgw2Used: Boolean
    val isOdgw3Used: Boolean
    val isOdgw4Used: Boolean
    val isP1Used: Boolean
    val isOIUsed: Boolean
  }

  object GemsConfig {
    given Show[GemsConfig] = Show.show { x =>
      List(
        x.isCwfs1Used.option("CWFS1"),
        x.isCwfs2Used.option("CWFS2"),
        x.isCwfs3Used.option("CWFS3"),
        x.isOdgw1Used.option("ODGW1"),
        x.isOdgw2Used.option("ODGW2"),
        x.isOdgw3Used.option("ODGW3"),
        x.isOdgw4Used.option("ODGW4")
      ).collect { case Some(x) => x }
        .mkString("(", ", ", ")")
    }
  }

  case object GemsOff extends GemsConfig {
    override val isCwfs1Used: Boolean = false
    override val isCwfs2Used: Boolean = false
    override val isCwfs3Used: Boolean = false
    override val isOdgw1Used: Boolean = false
    override val isOdgw2Used: Boolean = false
    override val isOdgw3Used: Boolean = false
    override val isOdgw4Used: Boolean = false
    override val isP1Used: Boolean    = false
    override val isOIUsed: Boolean    = false
  }

  sealed trait Cwfs1Usage extends Product with Serializable
  object Cwfs1Usage {
    case object Use     extends Cwfs1Usage
    case object DontUse extends Cwfs1Usage

    given Eq[Cwfs1Usage] = Eq.fromUniversalEquals

    def fromBoolean(b: Boolean): Cwfs1Usage = if (b) Use else DontUse
  }

  sealed trait Cwfs2Usage extends Product with Serializable
  object Cwfs2Usage {
    case object Use     extends Cwfs2Usage
    case object DontUse extends Cwfs2Usage

    given Eq[Cwfs2Usage] = Eq.fromUniversalEquals

    def fromBoolean(b: Boolean): Cwfs2Usage = if (b) Use else DontUse
  }

  sealed trait Cwfs3Usage extends Product with Serializable
  object Cwfs3Usage {
    case object Use     extends Cwfs3Usage
    case object DontUse extends Cwfs3Usage

    given Eq[Cwfs3Usage] = Eq.fromUniversalEquals

    def fromBoolean(b: Boolean): Cwfs3Usage = if (b) Use else DontUse
  }

  sealed trait Odgw1Usage extends Product with Serializable
  object Odgw1Usage {
    case object Use     extends Odgw1Usage
    case object DontUse extends Odgw1Usage

    given Eq[Odgw1Usage] = Eq.fromUniversalEquals

    def fromBoolean(b: Boolean): Odgw1Usage = if (b) Use else DontUse
  }

  sealed trait Odgw2Usage extends Product with Serializable
  object Odgw2Usage {
    case object Use     extends Odgw2Usage
    case object DontUse extends Odgw2Usage

    given Eq[Odgw2Usage] = Eq.fromUniversalEquals

    def fromBoolean(b: Boolean): Odgw2Usage = if (b) Use else DontUse
  }

  sealed trait Odgw3Usage extends Product with Serializable
  object Odgw3Usage {
    case object Use     extends Odgw3Usage
    case object DontUse extends Odgw3Usage

    given Eq[Odgw3Usage] = Eq.fromUniversalEquals

    def fromBoolean(b: Boolean): Odgw3Usage = if (b) Use else DontUse
  }

  sealed trait Odgw4Usage extends Product with Serializable
  object Odgw4Usage {
    case object Use     extends Odgw4Usage
    case object DontUse extends Odgw4Usage

    given Eq[Odgw4Usage] = Eq.fromUniversalEquals

    def fromBoolean(b: Boolean): Odgw4Usage = if (b) Use else DontUse
  }

  final case class GemsOn(
    cwfs1: Cwfs1Usage,
    cwfs2: Cwfs2Usage,
    cwfs3: Cwfs3Usage,
    odgw1: Odgw1Usage,
    odgw2: Odgw2Usage,
    odgw3: Odgw3Usage,
    odgw4: Odgw4Usage,
    useP1: P1Usage,
    useOI: OIUsage
  ) extends GemsConfig {

    import Cwfs1Usage.given
    override val isCwfs1Used: Boolean = cwfs1 === Cwfs1Usage.Use

    import Cwfs2Usage.given
    override val isCwfs2Used: Boolean = cwfs2 === Cwfs2Usage.Use

    import Cwfs3Usage.given
    override val isCwfs3Used: Boolean = cwfs3 === Cwfs3Usage.Use

    import Odgw1Usage.given
    override val isOdgw1Used: Boolean = odgw1 === Odgw1Usage.Use

    import Odgw2Usage.given
    override val isOdgw2Used: Boolean = odgw2 === Odgw2Usage.Use

    import Odgw3Usage.given
    override val isOdgw3Used: Boolean = odgw3 === Odgw3Usage.Use

    import Odgw4Usage.given
    override val isOdgw4Used: Boolean = odgw4 === Odgw4Usage.Use

    import P1Usage.given
    override val isP1Used: Boolean = useP1 === P1Usage.Use

    import OIUsage.given
    override val isOIUsed: Boolean = useOI === OIUsage.Use
  }

}
