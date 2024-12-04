// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.Order
import cats.derived.*
import cats.effect.IO
import io.circe.Decoder
import io.circe.Encoder
import observe.model.enums.ObserveLogLevel
import org.typelevel.cats.time.given

import java.time.Instant

case class LogMessage(level: ObserveLogLevel, timestamp: Instant, msg: String)
    derives Eq,
      Encoder.AsObject,
      Decoder

object LogMessage:
  given Order[LogMessage] = Order.by(_.timestamp)

  def now(level: ObserveLogLevel, msg: String): IO[LogMessage] =
    IO.realTime.map: time =>
      LogMessage(level, Instant.ofEpochMilli(time.toMillis), msg)
