// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.syntax.option.*
import lucuma.core.enums.Instrument
import lucuma.core.enums.Site

object extensions:
  extension (i: Instrument)
    def hasOI: Boolean = i match
      //      case Instrument.F2    => true
      case Instrument.GmosSouth => true
      case Instrument.GmosNorth => true
      case _                    => false
    //      case Instrument.Nifs  => true
    //      case Instrument.Niri  => true
    //      case Instrument.Gnirs => true
    //      case Instrument.Gsaoi => false
    //      case Instrument.Gpi   => true
    //      case Instrument.Ghost => false

    def site: Option[Site] = i match
      // GS
      case Instrument.GmosSouth  => Site.GS.some
      case Instrument.Flamingos2 => Site.GS.some
      case Instrument.Ghost      => Site.GS.some
      case Instrument.Gpi        => Site.GS.some
      case Instrument.Gsaoi      => Site.GS.some
      // GN
      case Instrument.GmosNorth  => Site.GN.some
      case Instrument.Gnirs      => Site.GN.some
      case Instrument.Niri       => Site.GN.some
      case Instrument.Nifs       => Site.GN.some
      // None
      case _                     => none

  private val SiteInstruments: Map[Site, List[Instrument]] =
    Instrument.all
      .filter(_.site.isDefined)
      .groupBy(_.site.get)

  extension (site: Site)
    def instruments: List[Instrument] =
      SiteInstruments(site)
