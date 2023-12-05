package observe.server

import cats.Eq
import cats.syntax.all.*
import edu.gemini.observe.server.tcs.BinaryEnabledDisabled
import edu.gemini.observe.server.tcs.BinaryOnOff
import edu.gemini.observe.server.tcs.BinaryYesNo
import edu.gemini.observe.server.tcs.ParkState
import lucuma.core.util.TimeSpan
import squants.Angle
import squants.Length
import squants.Ratio
import squants.space.Arcseconds
import squants.space.Millimeters

import java.time.temporal.ChronoUnit

package tcs {

  final class FocalPlaneScale(angle: Angle, length: Length) extends Ratio[Angle, Length] {

    override def base: Angle = angle

    override def counter: Length = length

    def times(l: Length): Angle = convertToBase(l)
    def *(l:     Length): Angle = times(l)

    def divide(a: Angle): Length = convertToCounter(a)
  }

  object FocalPlaneScale {

    implicit class AngleOps(a: Angle) {
      def dividedBy(fps: FocalPlaneScale): Length = fps.divide(a)
      def /(fps:         FocalPlaneScale): Length = dividedBy(fps)
    }

    implicit class LengthOps(l: Length) {
      def times(fps: FocalPlaneScale): Angle = fps * l
      def *(fps:     FocalPlaneScale): Angle = times(fps)
    }

  }

  sealed case class WithDebug[A](self: A, debug: String) {
    def mapDebug(f: String => String): WithDebug[A] = this.copy(debug = f(debug))
  }

}

package object tcs {
  val BottomPort: Int  = 1
  val InvalidPort: Int = 0

  val tcsTimeout: TimeSpan = TimeSpan.unsafeFromDuration(90, ChronoUnit.SECONDS)
  val agTimeout: TimeSpan  = TimeSpan.unsafeFromDuration(90, ChronoUnit.SECONDS)

  val NonStopExposures = -1

  // Focal plane scale, expressed with squants quantities.
  val FOCAL_PLANE_SCALE = new FocalPlaneScale(Arcseconds(1.61144), Millimeters(1))

  val pwfs1OffsetThreshold: Length = Arcseconds(0.01) / FOCAL_PLANE_SCALE
  val pwfs2OffsetThreshold: Length = Arcseconds(0.01) / FOCAL_PLANE_SCALE

  val AoOffsetThreshold: Length = Arcseconds(0.01) / FOCAL_PLANE_SCALE

  given ooEq: Eq[BinaryOnOff]              = Eq.by[BinaryOnOff, Int](_.ordinal())
  given ynEq: Eq[BinaryYesNo]              = Eq.by[BinaryYesNo, Int](_.ordinal())
  given endisEq: Eq[BinaryEnabledDisabled] = Eq.by[BinaryEnabledDisabled, Int](_.ordinal())
  given parkEq: Eq[ParkState]              = Eq.by[ParkState, Int](_.ordinal())

  extension [A](v: A) {
    def withDebug(msg: String): WithDebug[A] = WithDebug(v, msg)
  }

}
