// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.parse.Parser
import cats.parse.Parser0
import cats.parse.Rfc5234.char
import cats.syntax.all.*
import lucuma.core.enums.LightSinkName
import lucuma.core.enums.parser.EnumParsers
import lucuma.core.parser.MiscParsers.int
import observe.server.EpicsCodex.DecodeEpicsValue
import observe.server.EpicsCodex.EncodeEpicsValue
import observe.server.tcs.ScienceFold.Parked
import observe.server.tcs.ScienceFold.Position
import observe.server.tcs.TcsController.LightSource
import observe.server.tcs.TcsController.LightSource.*

// Decoding and encoding the science fold position require some common definitions, therefore I
// put them inside an object
private[server] trait ScienceFoldPositionCodex:

  private val AO_PREFIX   = "ao2"
  private val GCAL_PREFIX = "gcal2"
  private val PARK_POS    = "park-pos"

  val lightSink: Parser[LightSinkName] = EnumParsers.enumBy[LightSinkName](_.name)

  def prefixed(p: String, s: LightSource): Parser0[ScienceFold] =
    (Parser.string0(p) *> lightSink ~ int)
      .map: (ls, port) =>
        Position(s, ls, port)
      .widen[ScienceFold]

  val park: Parser[ScienceFold] =
    (Parser.string(PARK_POS) <* char.rep.?).as(Parked)

  given DecodeEpicsValue[String, Option[ScienceFold]] = (t: String) =>
    (park | prefixed(AO_PREFIX, AO) | prefixed(GCAL_PREFIX, GCAL) | prefixed("", Sky))
      .parseAll(t)
      .toOption

  given EncodeEpicsValue[Position, String] = (a: Position) =>
    val instAGName = a.sink.name + a.port.toString

    a.source match {
      case Sky  => instAGName
      case AO   => AO_PREFIX + instAGName
      case GCAL => GCAL_PREFIX + instAGName
    }

object ScienceFoldPositionCodex extends ScienceFoldPositionCodex
