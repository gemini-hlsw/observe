// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.derived.*
import lucuma.core.enums.Instrument
import monocle.Iso

// TODO Eventually, we will have parameters for sharable URLs
enum Page derives Eq:
  // case Schedule, Nighttime, Daytime, Excluded
  case Observations                             extends Page
  case LoadedInstrument(instrument: Instrument) extends Page

object Page:
  object LoadedInstrument:
    val iso: Iso[Instrument, LoadedInstrument] =
      Iso[Instrument, LoadedInstrument](Page.LoadedInstrument(_))(_.instrument)
