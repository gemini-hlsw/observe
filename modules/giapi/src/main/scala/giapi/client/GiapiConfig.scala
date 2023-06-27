// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package giapi.client

// Produce a configuration string for Giapi.
trait GiapiConfig[T] {
  def configValue(t: T): String
}

object GiapiConfig {
  given GiapiConfig[String] = t => t
  given GiapiConfig[Int]    = _.toString
  given GiapiConfig[Double] = d => f"$d%1.6f"
  given GiapiConfig[Float]  = d => f"$d%1.6f"

  @inline
  def apply[A](using instance: GiapiConfig[A]): GiapiConfig[A] = instance

  def instance[A](f: A => String): GiapiConfig[A] = new GiapiConfig[A] {
    def configValue(t: A) = f(t)
  }
}
