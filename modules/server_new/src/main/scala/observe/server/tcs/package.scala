package observe.server.tcs

import algebra.instances.all.given
import cats.Eq
import cats.syntax.all.*
import coulomb.*
import coulomb.policy.standard.given
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import coulomb.units.accepted.Millimeter
import edu.gemini.observe.server.tcs.BinaryEnabledDisabled
import edu.gemini.observe.server.tcs.BinaryOnOff
import edu.gemini.observe.server.tcs.BinaryYesNo
import edu.gemini.observe.server.tcs.ParkState
import lucuma.core.math.Angle
import lucuma.core.math.Offset
import lucuma.core.util.TimeSpan
import observe.server.tcs.TcsController.InstrumentOffset
import observe.server.tcs.TcsController.OffsetP
import observe.server.tcs.TcsController.OffsetQ

import java.time.temporal.ChronoUnit

private class FocalPlaneScale(
  angle:  Quantity[Double, ArcSecond],
  length: Quantity[Double, Millimeter]
) {

  def base: Quantity[Double, ArcSecond] = angle

  def counter: Quantity[Double, Millimeter] = length

  def times(l: Quantity[Double, Millimeter]): Quantity[Double, ArcSecond] =
    (l / length) * angle

  def *(l: Quantity[Double, Millimeter]): Quantity[Double, ArcSecond] = times(l)

  def divide(a: Quantity[Double, ArcSecond]): Quantity[Double, Millimeter] =
    counter * (a / base)

}

val tcsTimeout: TimeSpan = TimeSpan.unsafeFromDuration(90, ChronoUnit.SECONDS)
val agTimeout: TimeSpan  = TimeSpan.unsafeFromDuration(90, ChronoUnit.SECONDS)

object FocalPlaneScale:
  extension (l: Quantity[Double, Millimeter])
    def times(fps: FocalPlaneScale): Quantity[Double, ArcSecond] = fps * l
    def :*(fps:    FocalPlaneScale): Quantity[Double, ArcSecond] = times(fps)

  extension (a: Quantity[Double, ArcSecond])
    def dividedBy(fps: FocalPlaneScale): Quantity[Double, Millimeter] = fps.divide(a)
    def :\(fps:        FocalPlaneScale): Quantity[Double, Millimeter] = dividedBy(fps)

extension (a: Angle) {
  def iop: Quantity[Double, ArcSecond] =
    Angle.signedDecimalMilliarcseconds.get(a).withUnit[ArcSecond]

  def ioq: Quantity[Double, ArcSecond] =
    Angle.signedDecimalMilliarcseconds.get(a).withUnit[ArcSecond]
}

extension (o: Offset) {
  def toInstrumentOffset: InstrumentOffset =
    InstrumentOffset(OffsetP(o.p.toAngle.iop), OffsetQ(o.q.toAngle.ioq))
}

sealed case class WithDebug[A](self: A, debug: String) {
  def mapDebug(f: String => String): WithDebug[A] = this.copy(debug = f(debug))
}

val BottomPort: Int  = 1
val InvalidPort: Int = 0

val NonStopExposures = -1

// Focal plane scale, expressed with coulomb quantities.
val FOCAL_PLANE_SCALE =
  new FocalPlaneScale(1.61144.withUnit[ArcSecond], 1.0.withUnit[Millimeter])
import FocalPlaneScale.*

val pwfs1OffsetThreshold: Quantity[Double, Millimeter] =
  0.01.withUnit[ArcSecond] :\ FOCAL_PLANE_SCALE

val pwfs2OffsetThreshold: Quantity[Double, Millimeter] =
  0.01.withUnit[ArcSecond] :\ FOCAL_PLANE_SCALE

val AoOffsetThreshold: Quantity[Double, Millimeter] =
  0.01.withUnit[ArcSecond] :\ FOCAL_PLANE_SCALE

given Eq[BinaryOnOff]           = Eq.by[BinaryOnOff, Int](_.ordinal())
given Eq[BinaryYesNo]           = Eq.by[BinaryYesNo, Int](_.ordinal())
given Eq[BinaryEnabledDisabled] = Eq.by[BinaryEnabledDisabled, Int](_.ordinal())
given Eq[ParkState]             = Eq.by[ParkState, Int](_.ordinal())

extension [A](v: A) {
  def withDebug(msg: String): WithDebug[A] = WithDebug(v, msg)
}

type SquaredMillis = Millimeter * Millimeter
