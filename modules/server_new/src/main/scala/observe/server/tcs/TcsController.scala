// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import algebra.instances.all.given
import cats.*
import cats.data.NonEmptySet
import cats.data.OneAnd
import cats.derived.*
import cats.syntax.all.*
import coulomb.*
import coulomb.policy.spire.standard.given
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import coulomb.units.accepted.Millimeter
import lucuma.core.enums.*
import lucuma.core.math.Angle
import lucuma.core.math.Wavelength
import lucuma.core.model.AltairConfig
import lucuma.core.model.GemsConfig
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.NewType
import monocle.Focus
import monocle.Lens
import observe.server.InstrumentGuide
import observe.server.tcs.*
import observe.server.tcs.FocalPlaneScale.*
import observe.server.tcs.TcsSouthController.GemsGuiders

import scala.language.implicitConversions

/**
 * Created by jluhrs on 7/30/15.
 *
 * Most of the code deals with representing the state of the TCS subsystems.
 */

object TcsController {

  /** Enumerated type for beams A, B, and C. */
  sealed trait Beam extends Product with Serializable

  object Beam {
    case object A extends Beam

    case object B extends Beam

    case object C extends Beam

    given Eq[Beam] = Eq.fromUniversalEquals
  }

  /**
   * Data type for combined configuration of nod position (telescope orientation) and chop position
   * (M2 orientation)
   */
  case class NodChop(nod: Beam, chop: Beam) derives Eq

  /** Enumerated type for nod/chop tracking. */
  sealed trait NodChopTrackingOption

  object NodChopTrackingOption {

    case object NodChopTrackingOn extends NodChopTrackingOption

    case object NodChopTrackingOff extends NodChopTrackingOption

    def fromBoolean(on: Boolean): NodChopTrackingOption =
      if (on) NodChopTrackingOn else NodChopTrackingOff

    given Eq[NodChopTrackingOption] = Eq.fromUniversalEquals

  }

  import NodChopTrackingOption.* // needed below

  // TCS can be configured to update a guide probe position only for certain nod-chop positions.
  sealed trait NodChopTrackingConfig {
    def get(nodchop: NodChop): NodChopTrackingOption
  }

  // If x is of type ActiveNodChopTracking then ∃ a:NodChop ∍ x.get(a) == NodChopTrackingOn
  // How could I reflect that in the code?
  sealed trait ActiveNodChopTracking extends NodChopTrackingConfig

  object NodChopTrackingConfig {

    object AllOff extends NodChopTrackingConfig {
      def get(nodchop: NodChop): NodChopTrackingOption =
        NodChopTrackingOff
    }

    // Beam C is never used on normal configuration, and setting it to On would cause problems because no other tool
    // (TCC, Tcs engineering screens) can change it.
    object Normal extends ActiveNodChopTracking {
      def get(nodchop: NodChop): NodChopTrackingOption =
        NodChopTrackingOption.fromBoolean(nodchop.nod =!= Beam.C && nodchop.nod === nodchop.chop)
    }

    case class Special(s: OneAnd[List, NodChop]) extends ActiveNodChopTracking derives Eq {
      def get(nodchop: NodChop): NodChopTrackingOption =
        NodChopTrackingOption.fromBoolean(s.exists(_ === nodchop))
    }

    given Eq[ActiveNodChopTracking] = Eq.instance {
      case (Normal, Normal)                 => true
      case (a @ Special(_), b @ Special(_)) => a === b
      case _                                => false
    }

    given Eq[NodChopTrackingConfig] = Eq.instance {
      case (AllOff, AllOff)                                     => true
      case (a: ActiveNodChopTracking, b: ActiveNodChopTracking) => a === b
      case _                                                    => false
    }

  }

  // A probe tracking configuration is specified by its nod-chop tracking table
  // and its follow flag. The first tells TCS when to update the target track
  // followed by the probe, and the second tells the probe if it must follow
  // the target track.

  /** Enumerated type for follow on/off. */
  sealed trait FollowOption

  object FollowOption {
    case object FollowOff extends FollowOption

    case object FollowOn extends FollowOption

    given Eq[FollowOption] = Eq.fromUniversalEquals[FollowOption]
  }

  import FollowOption.*

  /** Data type for probe tracking config. */
  sealed abstract class ProbeTrackingConfig(
    val follow:     FollowOption,
    val getNodChop: NodChopTrackingConfig
  ) {
    def isActive: Boolean = follow === FollowOn && getNodChop =!= NodChopTrackingConfig.AllOff
  }

  object ProbeTrackingConfig {
    case object Parked extends ProbeTrackingConfig(FollowOff, NodChopTrackingConfig.AllOff)

    case object Off extends ProbeTrackingConfig(FollowOff, NodChopTrackingConfig.AllOff)

    case class On(ndconfig: ActiveNodChopTracking) extends ProbeTrackingConfig(FollowOn, ndconfig)
        derives Eq

    case object Frozen extends ProbeTrackingConfig(FollowOn, NodChopTrackingConfig.AllOff)

    given Eq[ProbeTrackingConfig]   = Eq.instance {
      case (Parked, Parked)       => true
      case (Off, Off)             => true
      case (Frozen, Frozen)       => true
      case (a @ On(_), b @ On(_)) => a === b
      case _                      => false
    }
    given Show[ProbeTrackingConfig] = Show.fromToString
  }

  /** Enumerated type for HRWFS pickup position. */
  sealed trait HrwfsPickupPosition

  object HrwfsPickupPosition {
    case object IN extends HrwfsPickupPosition

    case object OUT extends HrwfsPickupPosition

    case object Parked extends HrwfsPickupPosition

    given Show[HrwfsPickupPosition] = Show.fromToString

    given Eq[HrwfsPickupPosition] = Eq.instance {
      case (IN, IN)         => true
      case (OUT, OUT)       => true
      case (Parked, Parked) => true
      case _                => false
    }

    def isInTheWay(h: HrwfsPickupPosition): Boolean = h === IN
  }

  sealed trait HrwfsConfig

  object HrwfsConfig {
    case object Auto extends HrwfsConfig

    case class Manual(pos: HrwfsPickupPosition) extends HrwfsConfig derives Eq

    given Show[HrwfsConfig] = Show.fromToString

    given Eq[HrwfsConfig] = Eq.instance {
      case (Auto, Auto)                   => true
      case (a @ Manual(_), b @ Manual(_)) => a === b
      case _                              => false
    }
  }

  /** Enumerated type for light source. */
  sealed trait LightSource

  object LightSource {
    case object Sky extends LightSource

    case object AO extends LightSource

    case object GCAL extends LightSource

    given Eq[LightSource] = Eq.fromUniversalEquals
  }

  /* Data type for science fold position. */
  case class LightPath(source: LightSource, sink: LightSinkName) derives Eq

  object LightPath {

    given Show[LightPath] = Show.fromToString

  }

  // TCS expects offsets as two length quantities (in millimeters) in the focal plane
  object OffsetX extends NewType[Quantity[Double, Millimeter]]
  type OffsetX = OffsetX.Type

  object OffsetY extends NewType[Quantity[Double, Millimeter]]
  type OffsetY = OffsetY.Type

  object OffsetP extends NewType[Quantity[Double, ArcSecond]]
  type OffsetP = OffsetP.Type

  object OffsetQ extends NewType[Quantity[Double, ArcSecond]]
  type OffsetQ = OffsetQ.Type

  case class FocalPlaneOffset(x: OffsetX, y: OffsetY) {
    def toInstrumentOffset(iaa: Angle): InstrumentOffset = InstrumentOffset(
      OffsetP((-x.value * iaa.cos + y.value * iaa.sin) :* FOCAL_PLANE_SCALE),
      OffsetQ((-x.value * iaa.sin - y.value * iaa.cos) :* FOCAL_PLANE_SCALE)
    )

  }

  object FocalPlaneOffset {

    def fromInstrumentOffset(o: InstrumentOffset, iaa: Angle): FocalPlaneOffset =
      o.toFocalPlaneOffset(iaa)

    given Eq[FocalPlaneOffset] = Eq.by(f => (f.x.value.value, f.y.value.value))
  }

  case class InstrumentOffset(p: OffsetP, q: OffsetQ) {
    def toFocalPlaneOffset(iaa: Angle): FocalPlaneOffset =
      FocalPlaneOffset(
        OffsetX(((-p.value * iaa.cos) - q.value * iaa.sin) :\ FOCAL_PLANE_SCALE),
        OffsetY((p.value * iaa.sin - q.value * iaa.cos) :\ FOCAL_PLANE_SCALE)
      )
  }

  object InstrumentOffset {

    def fromFocalPlaneOffset(o: FocalPlaneOffset, iaa: Angle): InstrumentOffset =
      o.toInstrumentOffset(iaa)

    given Eq[InstrumentOffset] = Eq.by(f => (f.p.value.value, f.q.value.value))

  }

  case class TelescopeConfig(
    offsetA: Option[InstrumentOffset],
    wavelA:  Option[Wavelength]
  ) derives Eq

  object TelescopeConfig {
    given Show[TelescopeConfig] = Show.fromToString

    val offsetA: Lens[TelescopeConfig, Option[InstrumentOffset]] =
      Focus[TelescopeConfig](_.offsetA)
  }

  object P1Config extends NewType[GuiderConfig]
  type P1Config = P1Config.Type

  object P2Config extends NewType[GuiderConfig]
  type P2Config = P2Config.Type

  object OIConfig extends NewType[GuiderConfig]
  type OIConfig = OIConfig.Type

  object AoGuide extends NewType[GuiderConfig]
  type AoGuide = AoGuide.Type

  sealed trait GuiderSensorOption

  case object GuiderSensorOff extends GuiderSensorOption

  case object GuiderSensorOn extends GuiderSensorOption

  object GuiderSensorOption {
    given Eq[GuiderSensorOption] = Eq.fromUniversalEquals
  }

  case class GuiderConfig(tracking: ProbeTrackingConfig, detector: GuiderSensorOption) derives Eq {
    val isActive: Boolean = tracking.isActive && detector === GuiderSensorOn
  }

  object GuiderConfig {
    given Show[GuiderConfig] = Show.fromToString[GuiderConfig]

    val tracking: Lens[GuiderConfig, ProbeTrackingConfig] = Focus[GuiderConfig](_.tracking)
    val detector: Lens[GuiderConfig, GuiderSensorOption]  = Focus[GuiderConfig](_.detector)
  }

  case class AGConfig(sfPos: LightPath, hrwfs: Option[HrwfsConfig])

  object AGConfig {
    given Show[AGConfig] = Show.show(x => s"(${x.sfPos.show}, ${x.hrwfs.show})")
  }

  sealed trait Subsystem extends Product with Serializable

  object Subsystem {
    // Instrument internal WFS
    case object OIWFS extends Subsystem

    // Peripheral WFS 1
    case object PWFS1 extends Subsystem

    // Peripheral WFS 2
    case object PWFS2 extends Subsystem

    // Internal AG mechanisms (science fold, AC arm)
    case object AGUnit extends Subsystem

    // Mount and cass-rotator
    case object Mount extends Subsystem

    // Primary mirror
    case object M1 extends Subsystem

    // Secondary mirror
    case object M2 extends Subsystem

    // Gemini Adaptive Optics System (GeMS or Altair)
    case object Gaos extends Subsystem

    val allList: List[Subsystem]                = List(PWFS1, PWFS2, OIWFS, AGUnit, Mount, M1, M2, Gaos)
    given Order[Subsystem]                      = Order.from { case (a, b) =>
      allList.indexOf(a) - allList.indexOf(b)
    }
    val allButGaos: NonEmptySet[Subsystem]      =
      NonEmptySet.of(OIWFS, PWFS1, PWFS2, AGUnit, Mount, M1, M2)
    val allButGaosNorOi: NonEmptySet[Subsystem] =
      NonEmptySet.of(PWFS1, PWFS2, AGUnit, Mount, M1, M2)

    given Show[Subsystem] = Show.show(_.productPrefix)
    given Eq[Subsystem]   = Eq.fromUniversalEquals
  }

  sealed trait GuidersConfig {
    val pwfs1: P1Config
    val pwfs2: P2Config
    val oiwfs: OIConfig
  }

  case class BasicGuidersConfig(
    pwfs1: P1Config,
    pwfs2: P2Config,
    oiwfs: OIConfig
  ) extends GuidersConfig

  object BasicGuidersConfig {
    val pwfs1: Lens[BasicGuidersConfig, P1Config] = Focus[BasicGuidersConfig](_.pwfs1)
    val pwfs2: Lens[BasicGuidersConfig, P2Config] = Focus[BasicGuidersConfig](_.pwfs2)
    val oiwfs: Lens[BasicGuidersConfig, OIConfig] = Focus[BasicGuidersConfig](_.oiwfs)
  }

  case class AoGuidersConfig[C](
    pwfs1:   P1Config,
    aoguide: C,
    oiwfs:   OIConfig
  ) extends GuidersConfig {
    override val pwfs2: P2Config =
      P2Config(GuiderConfig(ProbeTrackingConfig.Parked, GuiderSensorOff))
  }

  object AoGuidersConfig {
    def pwfs1[C]: Lens[AoGuidersConfig[C], P1Config] = Focus[AoGuidersConfig[C]](_.pwfs1)
    def aoguide[C]: Lens[AoGuidersConfig[C], C]      = Focus[AoGuidersConfig[C]](_.aoguide)
    def oiwfs[C]: Lens[AoGuidersConfig[C], OIConfig] = Focus[AoGuidersConfig[C]](_.oiwfs)
  }

  object GuidersConfig {
    given Show[P1Config] = Show.show {
      _.asInstanceOf[GuiderConfig].show
    }
    given Show[P2Config] = Show.show {
      _.asInstanceOf[GuiderConfig].show
    }
    given Show[OIConfig] = Show.show {
      _.asInstanceOf[GuiderConfig].show
    }

    given Show[BasicGuidersConfig] = Show.show { x =>
      s"(pwfs1 = ${x.pwfs1.show}, pwfs2 = ${x.pwfs2.show}, oiwfs = ${x.oiwfs.show})"
    }

    given [C: Show]: Show[AoGuidersConfig[C]] = Show.show { x =>
      s"(pwfs1 = ${x.pwfs1.show}, aoguide = ${x.aoguide.show}, oiwfs = ${x.oiwfs.show})"
    }
  }

  sealed trait TcsConfig[S <: Site] {
    val gc: TelescopeGuideConfig
    val tc: TelescopeConfig
    val gds: GuidersConfig
    val agc: AGConfig
    val inst: InstrumentGuide
  }

  case class BasicTcsConfig[S <: Site](
    gc:   TelescopeGuideConfig,
    tc:   TelescopeConfig,
    gds:  BasicGuidersConfig,
    agc:  AGConfig,
    inst: InstrumentGuide
  ) extends TcsConfig[S]

  object BasicTcsConfig {
    def gds[S <: Site] = Focus[BasicTcsConfig[S]](_.gds)
    def gc[S <: Site]  = Focus[BasicTcsConfig[S]](_.gc)

    val offsetALensGS = Focus[BasicTcsConfig[Site.GS.type]](_.tc.offsetA)
    val m2GuideLensGS = Focus[BasicTcsConfig[Site.GS.type]](_.gc.m2Guide)
    val m1GuideLensGS = Focus[BasicTcsConfig[Site.GS.type]](_.gc.m1Guide)
    val pwfs1LensGS   = Focus[BasicTcsConfig[Site.GS.type]](_.gds.pwfs1)
    val pwfs2LensGS   = Focus[BasicTcsConfig[Site.GS.type]](_.gds.pwfs2)
    val oiwfsLensGS   = Focus[BasicTcsConfig[Site.GS.type]](_.gds.oiwfs)
    val instLensGS    = Focus[BasicTcsConfig[Site.GS.type]](_.inst)
  }

  case class AoTcsConfig[S <: Site](
    gc:   TelescopeGuideConfig,
    tc:   TelescopeConfig,
    gds:  AoGuidersConfig[SiteSpecifics.AoGuidersConfig[S]],
    agc:  AGConfig,
    gaos: SiteSpecifics.AoConfig[S],
    inst: InstrumentGuide
  ) extends TcsConfig[S]

  object AoTcsConfig {
    def gc[S <: Site]: Lens[AoTcsConfig[S], TelescopeGuideConfig]                               = Focus[AoTcsConfig[S]](_.gc)
    def tc[S <: Site]: Lens[AoTcsConfig[S], TelescopeConfig]                                    = Focus[AoTcsConfig[S]](_.tc)
    def gds[S <: Site]: Lens[AoTcsConfig[S], AoGuidersConfig[SiteSpecifics.AoGuidersConfig[S]]] =
      Focus[AoTcsConfig[S]](_.gds)
    def agc[S <: Site]: Lens[AoTcsConfig[S], AGConfig]                                          = Focus[AoTcsConfig[S]](_.agc)
    def gaos[S <: Site]: Lens[AoTcsConfig[S], SiteSpecifics.AoConfig[S]]                        =
      Focus[AoTcsConfig[S]](_.gaos)
    def inst[S <: Site]: Lens[AoTcsConfig[S], InstrumentGuide]                                  = Focus[AoTcsConfig[S]](_.inst)
  }

  object SiteSpecifics {

    type AoGuidersConfig[S <: Site] = S match {
      case Site.GN.type => AoGuide
      case Site.GS.type => GemsGuiders
    }

    type AoConfig[S <: Site] = S match {
      case Site.GN.type => AltairConfig
      case Site.GS.type => GemsConfig
    }
  }

  object TcsConfig {

    given [S <: Site]: Show[BasicTcsConfig[S]] = Show.show { x =>
      s"(guideConfig = ${x.gc}, telConfig = ${x.tc.show}, guidersConfig = ${x.gds.show}, A&G = ${x.agc.show})"
    }

    given [S <: Site](using
      x: Show[SiteSpecifics.AoGuidersConfig[S]],
      y: Show[SiteSpecifics.AoConfig[S]]
    ): Show[AoTcsConfig[S]] = Show.show { x =>
      s"(guideConfig = ${x.gc}, telConfig = ${x.tc.show}, guidersConfig = ${x.gds}, A&G = ${x.agc.show}, gaos = ${x.gaos})"
    }

  }
}
