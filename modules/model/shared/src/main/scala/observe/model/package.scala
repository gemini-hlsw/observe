// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe

import cats.*
import observe.model.enums.*
import squants.time.Time
import squants.time.TimeUnit

import java.util.UUID

package model {
  case class QueueId(self: UUID)  extends AnyVal
  case class ClientId(self: UUID) extends AnyVal
}

package object model {
  type ParamName       = String
  type ParamValue      = String
  type Parameters      = Map[ParamName, ParamValue]
  type StepId          = lucuma.core.model.sequence.Step.Id
  type ObservationName = String
  type TargetName      = String

  given Eq[QueueId]                  = Eq.by(x => x.self)
  given Show[QueueId]                = Show.fromToString
  given Order[QueueId]               =
    Order.by(_.self)
  given scala.math.Ordering[QueueId] =
    Order[QueueId].toOrdering

  given Eq[ClientId]                  = Eq.by(x => x.self)
  given Show[ClientId]                = Show.fromToString
  given Order[ClientId]               =
    Order.by(_.self)
  given scala.math.Ordering[ClientId] =
    Order[ClientId].toOrdering
  val UnknownTargetName               = "None"

  val CalibrationQueueName: String = "Calibration Queue"
  val CalibrationQueueId: QueueId  =
    QueueId(UUID.fromString("7156fa7e-48a6-49d1-a267-dbf3bbaa7577"))

  given Eq[TimeUnit] =
    Eq.by(_.symbol)

  given Eq[Time] =
    Eq.by(_.toMilliseconds)

  extension (i: Instrument) {
    def hasOI: Boolean = i match {
//      case Instrument.F2    => true
      case Instrument.GmosS => true
      case Instrument.GmosN => true
//      case Instrument.Nifs  => true
//      case Instrument.Niri  => true
//      case Instrument.Gnirs => true
//      case Instrument.Gsaoi => false
//      case Instrument.Gpi   => true
//      case Instrument.Ghost => false
    }
  }
}
