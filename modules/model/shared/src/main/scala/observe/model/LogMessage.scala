// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Functor
import cats.Order
import cats.effect.Clock
import cats.syntax.functor.*
import io.circe.Decoder
import io.circe.Encoder
import observe.model.enums.ObserveLogLevel
import org.typelevel.cats.time.given

import java.time.Instant

case class LogMessage(level: ObserveLogLevel, timestamp: Instant, msg: String)
    derives Encoder.AsObject,
      Decoder

object LogMessage:
  given Order[LogMessage] = Order.by(_.timestamp)

  def now[F[_]: Clock: Functor](level: ObserveLogLevel, msg: String): F[LogMessage] =
    Clock[F].realTime.map: time =>
      LogMessage(level, Instant.ofEpochMilli(time.toMillis), msg)
