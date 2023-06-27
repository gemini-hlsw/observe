// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.Eq
import cats.derived.*
import cats.kernel.Order
import lucuma.core.enums.{Instrument => InstrumentEnum}

enum Resource(val label: String) derives Eq:
  case P1                                     extends Resource("P1")
  case OI                                     extends Resource("OI")
  case TCS                                    extends Resource("TCS")
  case Gcal                                   extends Resource("Gcal")
  case Gems                                   extends Resource("Gems")
  case Altair                                 extends Resource("Altair")
  case Instrument(instrument: InstrumentEnum) extends Resource(instrument.shortName)

object Resource:
  val values: List[Resource] =
    List(P1, OI, TCS, Gcal, Gems, Altair) ++ InstrumentEnum.all.map(Instrument.apply)

  private val ordinalMap: Map[Resource, Int] = values.zipWithIndex.toMap

  given Order[Resource] = Order.by(ordinalMap)
