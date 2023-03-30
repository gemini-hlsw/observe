// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.Show
import cats.data.NonEmptyList
import lucuma.core.util.Enumerated

/** A Observe resource represents any system that can be only used by one single agent. */
sealed abstract class Resource(val tag: String, val ordinal: Int, val label: String)
    extends Product
    with Serializable {
  def isInstrument: Boolean = false
}

object Resource {

  case object P1     extends Resource("P1", 1, "P1")
  case object OI     extends Resource("OI", 2, "OI")
  case object TCS    extends Resource("TCS", 3, "TCS")
  case object Gcal   extends Resource("Gcal", 4, "Gcal")
  case object Gems   extends Resource("Gems", 5, "Gems")
  case object Altair extends Resource("Altair", 6, "Altair")

  // Mount and science fold cannot be controlled independently. Maybe in the future.
  // For now, I replaced them with TCS
  //  case object Mount extends Resource
  //  case object ScienceFold extends Resource
  implicit val show: Show[Resource] =
    Show.show(_.label)

  val common: List[Resource] = List(TCS, Gcal)

  /** @group Typeclass Instances */
  implicit val ResourceEnumerated: Enumerated[Resource] =
    Enumerated.from(Instrument.allResources.head, Instrument.allResources.tail: _*).withTag(_.tag)
}

sealed abstract class Instrument(tag: String, ordinal: Int, label: String) extends Resource(tag, ordinal, label) {
  override def isInstrument: Boolean = true
}

object Instrument {

  case object F2    extends Instrument("F2", 11, "Flamingos2")
  case object Ghost extends Instrument("Ghost", 12, "GHOST")
  case object GmosS extends Instrument("GmosS", 13, "GMOS-S")
  case object GmosN extends Instrument("GmosN", 14, "GMOS-N")
  case object Gnirs extends Instrument("Gnirs", 15, "GNIRS")
  case object Gpi   extends Instrument("Gpi", 16, "GPI")
  case object Gsaoi extends Instrument("Gsaoi", 17, "GSAOI")
  case object Niri  extends Instrument("Niri", 18, "NIRI")
  case object Nifs  extends Instrument("Nifs", 19, "NIFS")

  implicit val show: Show[Instrument] =
    Show.show(_.label)

  val gsInstruments: NonEmptyList[Instrument] =
    NonEmptyList.of(F2, Ghost, GmosS, Gpi, Gsaoi)

  val gnInstruments: NonEmptyList[Instrument] =
    NonEmptyList.of(GmosN, Gnirs, Niri, Nifs)

  val all: NonEmptyList[Instrument] =
    gsInstruments.concatNel(gnInstruments)

  val allResources: NonEmptyList[Resource] =
    NonEmptyList.of(Resource.P1,
                    Resource.OI,
                    Resource.TCS,
                    Resource.Gcal,
                    Resource.Gems,
                    Resource.Altair
    ) ::: Instrument.all

  /** @group Typeclass Instances */
  implicit val InstrumentEnumerated: Enumerated[Instrument] =
    Enumerated.from(all.head, all.tail: _*).withTag(_.tag)
}
