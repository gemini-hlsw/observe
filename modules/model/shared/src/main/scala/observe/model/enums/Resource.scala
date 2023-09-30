// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.Show
import cats.syntax.all.*
import cats.data.NonEmptyList
import lucuma.core.util.Enumerated
import lucuma.core.enums.Instrument

/** A Observe resource represents any system that can be only used by one single agent. */
enum Resource(val label: String, val instrument: Option[Instrument]) derives Enumerated:
  val tag: String           = label
  val isInstrument: Boolean = instrument.isDefined

  case P1     extends Resource("P1", none)
  case OI     extends Resource("OI", none)
  case TCS    extends Resource("TCS", none)
  case Gcal   extends Resource("Gcal", none)
  case Gems   extends Resource("Gems", none)
  case Altair extends Resource("Altair", none)
  case F2     extends Resource("F2", Instrument.Flamingos2.some)
  case Ghost  extends Resource("Ghost", Instrument.Ghost.some)
  case GmosS  extends Resource("GmosS", Instrument.GmosSouth.some)
  case GmosN  extends Resource("GmosN", Instrument.GmosNorth.some)
  case Gnirs  extends Resource("Gnirs", Instrument.Gnirs.some)
  case Gpi    extends Resource("Gpi", Instrument.Gpi.some)
  case Gsaoi  extends Resource("Gsaoi", Instrument.Gsaoi.some)
  case Niri   extends Resource("Niri", Instrument.Niri.some)
  case Nifs   extends Resource("Nifs", Instrument.Nifs.some)

  // Mount and science fold cannot be controlled independently. Maybe in the future.
  // For now, I replaced them with TCS
  //  case Mount extends Resource
  //  case  ScienceFold extends Resource

object Resource:
  given Show[Resource] = Show.show(_.label)

  /** @group Typeclass Instances */
  given Enumerated[Resource] =
    Enumerated.from(Instrument.allResources.head, Instrument.allResources.tail: _*).withTag(_.tag)
}

sealed abstract class Instrument(tag: String, ordinal: Int, label: String)
    extends Resource(tag, ordinal, label) {
  override def isInstrument: Boolean = true
}

object Instrument {

//  case object F2    extends Instrument("F2", 11, "Flamingos2")
//  case object Ghost extends Instrument("Ghost", 12, "GHOST")
  case object GmosS extends Instrument("GmosS", 13, "GMOS-S")
  case object GmosN extends Instrument("GmosN", 14, "GMOS-N")
//  case object Gnirs extends Instrument("Gnirs", 15, "GNIRS")
//  case object Gpi   extends Instrument("Gpi", 16, "GPI")
//  case object Gsaoi extends Instrument("Gsaoi", 17, "GSAOI")
//  case object Niri  extends Instrument("Niri", 18, "NIRI")
//  case object Nifs  extends Instrument("Nifs", 19, "NIFS")

  given Show[Instrument] =
    Show.show(_.label)

  val gsInstruments: NonEmptyList[Instrument] =
    NonEmptyList.of(GmosS)
//    NonEmptyList.of(F2, Ghost, GmosS, Gpi, Gsaoi)

  val gnInstruments: NonEmptyList[Instrument] =
    NonEmptyList.of(GmosN)
//    NonEmptyList.of(GmosN, Gnirs, Niri, Nifs)

  def fromInstrument(instrument: Instrument): Option[Resource] =
    AllInstruments.find(_.instrument.contains_(instrument))
