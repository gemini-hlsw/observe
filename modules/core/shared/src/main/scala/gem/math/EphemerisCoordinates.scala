// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem.math

/** A coordinate along with a rate of change in RA and Dec for some time unit,
  * expressed as an offset in p and q.  In reality the velocity information
  * comes from horizons and is always arcseconds per hour in horizons data.
  *
  * @param coord coordinates
  * @param delta rate of change in RA and dec, where the delta(RA)/time has been
  *              multiplied by the cosine of the dec
  */
final case class EphemerisCoordinates(
                   coord: Coordinates,
                   delta: Offset /* per time unit */) {

  /** Interpolates the position and rate of change at a point between this
    * coordinate and the given coordinate.
    */
  def interpolate(that: EphemerisCoordinates, f: Double): EphemerisCoordinates = {
    def interpolateAngle(a: Angle, b: Angle): Angle =
      Angle.fromMicroarcseconds(
        (a.toMicroarcseconds.toDouble * (1 - f) + b.toMicroarcseconds * f).round
      )

    val coordʹ = coord.interpolate(that.coord, f)
    val pʹ     = interpolateAngle(delta.p.toAngle, that.delta.p.toAngle)
    val qʹ     = interpolateAngle(delta.q.toAngle, that.delta.q.toAngle)

    EphemerisCoordinates(coordʹ, Offset(Offset.P(pʹ), Offset.Q(qʹ)))
  }

  /** Directionless velocity at this coordinate, expressed as an angular
    * separation per time unit of the change in RA and Dec.
    */
  def velocity: Angle = {
    val p = delta.p.toAngle.toMicroarcseconds.toDouble
    val q = delta.q.toAngle.toMicroarcseconds.toDouble
    Angle.fromMicroarcseconds(Math.sqrt(p*p + q*q).round)
  }
}

