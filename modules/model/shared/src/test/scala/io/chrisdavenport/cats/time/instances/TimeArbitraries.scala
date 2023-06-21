// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package io.chrisdavenport.cats.time.instances

import java.time.*

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

object TimeArbitraries {

  given [B, A: Arbitrary]: Arbitrary[B => A] = Arbitrary {
    for {
      a <- Arbitrary.arbitrary[A]
    } yield { (_: B) => a }
  }

  given Arbitrary[ZoneId] = Arbitrary {
    import scala.jdk.CollectionConverters._
    Gen.oneOf(ZoneId.getAvailableZoneIds.asScala.map(ZoneId.of).toSeq)
  }

  given Arbitrary[ZoneOffset] = Arbitrary {
    // Range is specified in `ofTotalSeconds` javadoc.
    Gen.choose(-64800, 64800).map(ZoneOffset.ofTotalSeconds)
  }

  given Arbitrary[Instant] = Arbitrary(
    Gen.choose(Instant.MIN.getEpochSecond, Instant.MAX.getEpochSecond).map(Instant.ofEpochSecond)
  )

  given Arbitrary[Period] = Arbitrary(
    for {
      years  <- arbitrary[Int]
      months <- arbitrary[Int]
      days   <- arbitrary[Int]
    } yield Period.of(years, months, days)
  )

  given Arbitrary[LocalDateTime] = Arbitrary(
    for {
      instant <- arbitrary[Instant]
      zoneId  <- arbitrary[ZoneId]
    } yield LocalDateTime.ofInstant(instant, zoneId)
  )

  given Arbitrary[ZonedDateTime] = Arbitrary(
    for {
      instant <- arbitrary[Instant]
      zoneId  <- arbitrary[ZoneId]
    } yield ZonedDateTime.ofInstant(instant, zoneId)
  )

  given Arbitrary[OffsetDateTime] = Arbitrary(
    for {
      instant <- arbitrary[Instant]
      zoneId  <- arbitrary[ZoneId]
    } yield OffsetDateTime.ofInstant(instant, zoneId)
  )

  given Arbitrary[LocalDate] = Arbitrary(
    arbitrary[LocalDateTime].map(_.toLocalDate)
  )

  given Arbitrary[LocalTime] = Arbitrary(
    arbitrary[LocalDateTime].map(_.toLocalTime)
  )

  given Arbitrary[OffsetTime] = Arbitrary(
    arbitrary[OffsetDateTime].map(_.toOffsetTime)
  )

  given Arbitrary[YearMonth] = Arbitrary(
    arbitrary[LocalDateTime].map(ldt => YearMonth.of(ldt.getYear, ldt.getMonth))
  )

  given Arbitrary[Year] = Arbitrary(
    arbitrary[LocalDateTime].map(ldt => Year.of(ldt.getYear))
  )

  given Arbitrary[Duration] = Arbitrary(
    for {
      first  <- arbitrary[Instant]
      second <- arbitrary[Instant]
    } yield Duration.between(first, second)
  )

  given Arbitrary[MonthDay] = Arbitrary(
    arbitrary[LocalDateTime].map(ldt => MonthDay.of(ldt.getMonth, ldt.getDayOfMonth))
  )

  given Arbitrary[Month] = Arbitrary(arbitrary[MonthDay].map(_.getMonth))
}
