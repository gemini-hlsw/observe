// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.syntax.all.*
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.util.Enumerated
import observe.model.ClientId
import observe.model.Observer
import observe.model.enums.Instrument
import observe.model.enums.Resource
import observe.model.enums.RunOverride
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

object ClientIDVar {
  def unapply(str: String): Option[ClientId] =
    Either.catchNonFatal(ClientId(java.util.UUID.fromString(str))).toOption
}

trait EnumeratedVar[A: Enumerated] {
  def unapply(str: String): Option[A] =
    Enumerated[A].fromTag(str)
}

object InstrumentVar extends EnumeratedVar[Instrument]
object ResourceVar   extends EnumeratedVar[Resource]

object ObsIdVar {
  def unapply(str: String): Option[Observation.Id] =
    Observation.Id.parse(str)
}

object StepIdVar {
  def unapply(str: String): Option[Step.Id] =
    Step.Id.parse(str)
}

object ObserverVar {
  def unapply(str: String): Option[Observer] =
    Observer(str).some
}

private given QueryParamDecoder[RunOverride] =
  QueryParamDecoder[Boolean].map {
    case true => RunOverride.Override
    case _    => RunOverride.Default
  }

object OptionalRunOverride
    extends OptionalQueryParamDecoderMatcher[RunOverride]("overrideTargetCheck")
