package observe.server

import coulomb.*
import coulomb.define.*

package object gws {

  export coulomb.units.accepted.{Millibar, ctx_unit_Millibar}

  final type Bar
  given unit_Millibar: DerivedUnit[Bar, 1000 * Millibar, "Bar", "Bar"] = DerivedUnit()

  final type Athmosphere
  given unit_Athmosphere: DerivedUnit[Athmosphere, 1013.25 * Millibar, "Athmosphere", "atm"] =
    DerivedUnit()

  final type MillimeterOfMercury
  given unit_MillimeterOfMercury
    : DerivedUnit[MillimeterOfMercury, 1013.25 * Millibar, "Millimeter of mercury", "mmHg"] =
    DerivedUnit()

}
