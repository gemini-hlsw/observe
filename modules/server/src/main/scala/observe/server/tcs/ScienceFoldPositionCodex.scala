// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import atto.Atto.*
import atto._
import cats.syntax.all.*
import lucuma.core.enums.LightSinkName
import observe.server.EpicsCodex.DecodeEpicsValue
import observe.server.EpicsCodex.EncodeEpicsValue
import observe.server.tcs.ScienceFold.Parked
import observe.server.tcs.ScienceFold.Position
import observe.server.tcs.TcsController.LightSource
import observe.server.tcs.TcsController.LightSource.*

// Decoding and encoding the science fold position require some common definitions, therefore I
// put them inside an object
private[server] trait ScienceFoldPositionCodex {

  private val AO_PREFIX   = "ao2"
  private val GCAL_PREFIX = "gcal2"
  private val PARK_POS    = "park-pos"

  val lightSink: Parser[LightSinkName] = LightSinkName.all.foldMap(x => string(x.name).as(x))

  def prefixed(p: String, s: LightSource): Parser[ScienceFold] =
    (string(p) ~> lightSink ~ int).map { case (ls, port) => Position(s, ls, port) }

  val park: Parser[ScienceFold] =
    (string(PARK_POS) <~ many(anyChar)).as(Parked)

  given DecodeEpicsValue[String, Option[ScienceFold]] =
    DecodeEpicsValue((t: String) =>
      (park | prefixed(AO_PREFIX, AO) | prefixed(GCAL_PREFIX, GCAL) | prefixed("", Sky))
        .parseOnly(t)
        .option
    )

  given EncodeEpicsValue[Position, String] = EncodeEpicsValue {
    (a: Position) =>
      val instAGName = a.sink.name + a.port.toString

      a.source match {
        case Sky  => instAGName
        case AO   => AO_PREFIX + instAGName
        case GCAL => GCAL_PREFIX + instAGName
      }
  }

}

object ScienceFoldPositionCodex extends ScienceFoldPositionCodex
