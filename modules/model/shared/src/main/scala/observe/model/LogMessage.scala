// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Order
import cats.effect.IO
import eu.timepit.refined.types.string.NonEmptyString
import observe.model.enums.ObserveLogLevel
import org.typelevel.cats.time.*

import java.time.Instant

case class LogMessage(level: ObserveLogLevel, timestamp: Instant, msg: NonEmptyString)

object LogMessage:
  given Order[LogMessage] = Order.by(_.timestamp)

  def now(level: ObserveLogLevel, msg: NonEmptyString): IO[LogMessage] =
    IO.realTime.map: time =>
      LogMessage(level, Instant.ofEpochMilli(time.toMillis), msg)
