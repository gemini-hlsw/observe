// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gcal

import org.scalacheck.Arbitrary.*
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen
import observe.server.gcal.GcalController.*

trait GcalArbitraries {
  given Arbitrary[GcalController.LampState] =
    Arbitrary(Gen.oneOf(GcalController.LampState.On, GcalController.LampState.Off))

  given Cogen[GcalController.LampState] =
    Cogen[String].contramap(_.productPrefix)

  given Arbitrary[GcalController.ArLampState] =
    Arbitrary(arbitrary[GcalController.LampState].map(ArLampState.apply))

  given Cogen[GcalController.ArLampState] =
    Cogen[GcalController.LampState].contramap(_.self)

  given Arbitrary[GcalController.CuArLampState] =
    Arbitrary(arbitrary[GcalController.LampState].map(CuArLampState.apply))

  given Cogen[GcalController.CuArLampState] =
    Cogen[GcalController.LampState].contramap(_.self)

  given Arbitrary[GcalController.QH5WLampState] =
    Arbitrary(arbitrary[GcalController.LampState].map(QH5WLampState.apply))

  given Cogen[GcalController.QH5WLampState] =
    Cogen[GcalController.LampState].contramap(_.self)

  given Arbitrary[GcalController.QH100WLampState] =
    Arbitrary(arbitrary[GcalController.LampState].map(QH100WLampState.apply))

  given Cogen[GcalController.QH100WLampState] =
    Cogen[GcalController.LampState].contramap(_.self)

  given Arbitrary[GcalController.ThArLampState] =
    Arbitrary(arbitrary[GcalController.LampState].map(ThArLampState.apply))

  given Cogen[GcalController.ThArLampState] =
    Cogen[GcalController.LampState].contramap(_.self)

  given Arbitrary[GcalController.XeLampState] =
    Arbitrary(arbitrary[GcalController.LampState].map(XeLampState.apply))

  given Cogen[GcalController.XeLampState] =
    Cogen[GcalController.LampState].contramap(_.self)

  given Arbitrary[GcalController.IrLampState] =
    Arbitrary(arbitrary[GcalController.LampState].map(IrLampState.apply))

  given Cogen[GcalController.IrLampState] =
    Cogen[GcalController.LampState].contramap(_.self)
}
