// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.syntax.all.*
import lucuma.core.model.Observation
import lucuma.core.util.Enumerated
import observe.model.ClientId
import observe.model.Observer
import observe.model.enums.Instrument

object ClientIDVar {
  def unapply(str: String): Option[ClientId] =
    Either.catchNonFatal(ClientId(java.util.UUID.fromString(str))).toOption
}

trait EnumeratedVar[A: Enumerated] {
  def unapply(str: String): Option[A] =
    Enumerated[A].all.find(a => Enumerated[A].fromTag(str).exists(_ === a))
}

object InstrumentVar extends EnumeratedVar[Instrument]

object ObsIdVar {
  def unapply(str: String): Option[Observation.Id] =
    Observation.Id.parse(str)
}

object ObserverVar {
  def unapply(str: String): Option[Observer] =
    Observer(str).some
}
