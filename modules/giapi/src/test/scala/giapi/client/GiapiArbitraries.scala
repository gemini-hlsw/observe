// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package giapi.client

import cats.syntax.all.*
import giapi.client.commands.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

trait GiapiArbitraries {
  given configurationArb: Arbitrary[Configuration] = Arbitrary {
    for {
      m <- arbitrary[Map[String, String]]
    } yield m
      .map { case (k, v) =>
        Configuration.single(k, v)
      }
      .toList
      .combineAll
  }

  given Cogen[Configuration] =
    Cogen[Map[String, String]].contramap {
      _.config.map { case (c, v) =>
        (c.getName, v)
      }
    }
}
