// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.Order
import cats.data.NonEmptySet
import cats.syntax.all.*
import mouse.boolean.*
import observe.model.enums.Instrument
import observe.server.tcs.TcsController.InstrumentOffset
import observe.server.tcs.TcsController.Subsystem
import squants.Ratio
import squants.Time
import squants.space.Angle
import squants.space.AngleConversions.*
import squants.time.TimeConversions.*

object TcsSettleTimeCalculator {

  trait SettleTimeCalculator {
    def calc(displacement: Angle): Time
  }

  def constantSettleTime(cnst: Time): SettleTimeCalculator = (_: Angle) => cnst

  // Settle time proportional to displacement
  def linearSettleTime(scale: SettleTimeScale): SettleTimeCalculator = (displacement: Angle) =>
    scale * displacement

  final case class SettleTimeScale(time: Time, angle: Angle) extends Ratio[Time, Angle] {
    override def base: Time = time

    override def counter: Angle = angle

    def times(a: Angle): Time = convertToBase(a)
    def *(a:     Angle): Time = times(a)
  }

  // We are using constant values for now. Values are taken from old Observe
  val settleTimeCalculators: Map[Subsystem, SettleTimeCalculator] = Map(
    Subsystem.Mount -> constantSettleTime(1.seconds),
    Subsystem.PWFS1 -> constantSettleTime(1.seconds),
    Subsystem.PWFS2 -> constantSettleTime(1.seconds)
  )

  val oiwfsSettleTimeCalculators: Map[Instrument, SettleTimeCalculator] = Map(
    Instrument.GmosN -> constantSettleTime(1.seconds),
    Instrument.GmosS -> constantSettleTime(1.seconds),
    Instrument.F2    -> constantSettleTime(1.seconds),
    Instrument.Nifs  -> constantSettleTime(4.seconds),
    Instrument.Niri  -> constantSettleTime(4.seconds),
    Instrument.Gnirs -> constantSettleTime(4.seconds)
  )

  def calcDisplacement(startOffset: InstrumentOffset, endOffset: InstrumentOffset): Angle =
    math
      .sqrt(
        math.pow((endOffset.p - startOffset.p).toArcseconds, 2.0) +
          math.pow((endOffset.q - startOffset.q).toArcseconds, 2.0)
      )
      .arcseconds

  given Order[Time] = Order.fromLessThan((a: Time, b: Time) => a < b)

  def calc(
    startOffset: InstrumentOffset,
    endOffset:   InstrumentOffset,
    subsystems:  NonEmptySet[Subsystem],
    inst:        Instrument
  ): Time = {
    val displacement = calcDisplacement(startOffset, endOffset)
    (subsystems.contains(Subsystem.OIWFS).option(oiwfsSettleTimeCalculators(inst))
      :: subsystems.toList.map(settleTimeCalculators.get)).flattenOption
      .map(_.calc(displacement))
      .maximumOption
      .getOrElse(0.seconds)
  }

}
