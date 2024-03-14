// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import algebra.instances.all.given
import cats.data.NonEmptySet
import cats.syntax.all.*
import coulomb.*
import coulomb.policy.spire.standard.given
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import lucuma.core.enums.Instrument
import lucuma.core.util.TimeSpan
import mouse.boolean.*
import observe.server.tcs.TcsController.InstrumentOffset
import observe.server.tcs.TcsController.Subsystem

import scala.language.implicitConversions

object TcsSettleTimeCalculator {

  trait SettleTimeCalculator {
    def calc(displacement: Quantity[Double, ArcSecond]): TimeSpan
  }

  def constantSettleTime(cnst: TimeSpan): SettleTimeCalculator = (_: Quantity[Double, ArcSecond]) =>
    cnst

  // Settle time proportional to displacement
  def linearSettleTime(scale: SettleTimeScale): SettleTimeCalculator =
    (displacement: Quantity[Double, ArcSecond]) => scale * displacement

  case class SettleTimeScale(time: TimeSpan, angle: Quantity[Double, ArcSecond]) {
    def base: TimeSpan = time

    def counter: Quantity[Double, ArcSecond] = angle

    def times(a: Quantity[Double, ArcSecond]): TimeSpan =
      TimeSpan
        .fromMicroseconds(
          (time.toMicroseconds * (a / counter).value).toLong
        )
        .get

    def *(a: Quantity[Double, ArcSecond]): TimeSpan = times(a)
  }

  private val s1 = TimeSpan.fromSeconds(1.0).get

  // We are using constant values for now. Values are taken from old Observe
  val settleTimeCalculators: Map[Subsystem, SettleTimeCalculator] = Map(
    Subsystem.Mount -> constantSettleTime(s1),
    Subsystem.PWFS1 -> constantSettleTime(s1),
    Subsystem.PWFS2 -> constantSettleTime(s1)
  )

  val oiwfsSettleTimeCalculators: Map[Instrument, SettleTimeCalculator] = Map(
    Instrument.GmosNorth -> constantSettleTime(s1),
    Instrument.GmosSouth -> constantSettleTime(s1)
//    Instrument.F2    -> constantSettleTime(1.seconds),
//    Instrument.Nifs  -> constantSettleTime(4.seconds),
//    Instrument.Niri  -> constantSettleTime(4.seconds),
//    Instrument.Gnirs -> constantSettleTime(4.seconds)
  )

  def calcDisplacement(
    startOffset: InstrumentOffset,
    endOffset:   InstrumentOffset
  ): Quantity[Double, ArcSecond] =
    math
      .sqrt(
        (
          (endOffset.p.value - startOffset.p.value).pow[2] +
            (endOffset.q.value - startOffset.q.value).pow[2]
        ).value
      )
      .withUnit[ArcSecond]

  def calc(
    startOffset: InstrumentOffset,
    endOffset:   InstrumentOffset,
    subsystems:  NonEmptySet[Subsystem],
    inst:        Instrument
  ): TimeSpan = {
    val displacement = calcDisplacement(startOffset, endOffset)
    (subsystems.contains(Subsystem.OIWFS).option(oiwfsSettleTimeCalculators(inst))
      :: subsystems.toList.map(settleTimeCalculators.get)).flattenOption
      .map(_.calc(displacement))
      .maximumOption
      .orEmpty
  }

}
