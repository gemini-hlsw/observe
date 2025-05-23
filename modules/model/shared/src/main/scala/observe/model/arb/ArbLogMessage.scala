// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.util.arb.ArbEnumerated.given
import observe.model.LogMessage
import observe.model.enums.ObserveLogLevel
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

import java.time.Instant

trait ArbLogMessage:
  given Arbitrary[LogMessage] = Arbitrary:
    for
      level     <- arbitrary[ObserveLogLevel]
      timestamp <- arbitrary[Instant]
      msg       <- arbitrary[String]
    yield LogMessage(level, timestamp, msg)

  given Cogen[LogMessage] =
    Cogen[(ObserveLogLevel, Instant, String)].contramap(x => (x.level, x.timestamp, x.msg))

object ArbLogMessage extends ArbLogMessage
