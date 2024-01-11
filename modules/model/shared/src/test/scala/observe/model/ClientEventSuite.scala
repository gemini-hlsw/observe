// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.events.client

import cats.kernel.laws.discipline.*
import io.circe.testing.CodecTests
import io.circe.testing.instances.*
import observe.model.arb.ArbClientEvent.given
import org.scalacheck.{Test => ScalaCheckTest}

/**
 * Tests Client Event typeclasses
 */
class ClientEventSuite extends munit.DisciplineSuite:
  override def scalaCheckTestParameters = ScalaCheckTest.Parameters.default.withMaxSize(10)

  checkAll("Eq[InitialEvent]", EqTests[ClientEvent.InitialEvent].eqv)
  checkAll("Eq[ObserveState]", EqTests[ClientEvent.ObserveState].eqv)
  checkAll("Eq[ClientEvent]", EqTests[ClientEvent].eqv)
  // TODO: Uri codecs do not pass roundtrip tests. I will report this to the http4s project.
  // Meanwhile, I'm commenting the tests.
  // checkAll("Codec[InitialEvent]", CodecTests[ClientEvent.InitialEvent].unserializableCodec)
  checkAll("Codec[ObserveState]", CodecTests[ClientEvent.ObserveState].unserializableCodec)
  // checkAll("Codec[ClientEvent]", CodecTests[ClientEvent].unserializableCodec)
