// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.syntax.all.*
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.util.Enumerated
import observe.model.ClientId
import observe.model.Observer
import observe.model.Operator
import observe.model.SubsystemEnabled
import observe.model.enums.Resource
import observe.model.enums.RunOverride
import observe.model.given_Enumerated_|
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

object ClientIDVar:
  def unapply(str: String): Option[ClientId] =
    Either.catchNonFatal(ClientId(java.util.UUID.fromString(str))).toOption

trait EnumeratedVar[A: Enumerated]:
  def unapply(str: String): Option[A] =
    Enumerated[A].fromTag(str)

object InstrumentVar           extends EnumeratedVar[Instrument]
object ResourceVar             extends EnumeratedVar[Resource]
object ResourceOrInstrumentVar extends EnumeratedVar[Resource | Instrument]

object ObsIdVar:
  def unapply(str: String): Option[Observation.Id] =
    Observation.Id.parse(str)

object StepIdVar:
  def unapply(str: String): Option[Step.Id] =
    Step.Id.parse(str)

private object NonEmptyStringVar:
  def unapply(str: String): Option[NonEmptyString] =
    NonEmptyString.from(str).toOption

object ObserverVar:
  def unapply(str: String): Option[Observer] =
    NonEmptyStringVar.unapply(str).map(Observer(_))

object OperatorVar:
  def unapply(str: String): Option[Operator] =
    NonEmptyStringVar.unapply(str).map(Operator(_))

private given QueryParamDecoder[RunOverride] =
  QueryParamDecoder[Boolean].map:
    case true => RunOverride.Override
    case _    => RunOverride.Default

object OptionalRunOverride
    extends OptionalQueryParamDecoderMatcher[RunOverride]("overrideTargetCheck")

private trait BooleanBasedVar[A]:
  def unapply(str: String): Option[A] =
    str.toLowerCase match
      case "true"  => item(true).some
      case "false" => item(false).some
      case _       => none

  def item(b: Boolean): A

object BooleanVar extends BooleanBasedVar[Boolean]:
  def item(b: Boolean) = b

object BreakpointVar extends BooleanBasedVar[Breakpoint]:
  def item(b: Boolean) = if b then Breakpoint.Enabled else Breakpoint.Disabled

object SubsystemEnabledVar extends BooleanBasedVar[SubsystemEnabled]:
  def item(b: Boolean) = if b then SubsystemEnabled.Enabled else SubsystemEnabled.Disabled

object SequenceTypeVar:
  def unapply(str: String): Option[SequenceType] = Enumerated[SequenceType].fromTag(str)
