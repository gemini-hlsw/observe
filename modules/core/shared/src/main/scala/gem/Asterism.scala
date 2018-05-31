// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem

import cats.Eq
import cats.data.NonEmptyList

sealed abstract class Asterism extends Product with Serializable {
  def targets: NonEmptyList[Target]
}

object Asterism {

  /** Supertype for asterisms with a single target. */
  sealed trait SingleTarget extends Asterism {
    def target: Target
    override def targets: NonEmptyList[Target] =
      NonEmptyList.one(target)
  }

  /** Asterism for Phoenix.
    * @group Constructors
    */
  final case class Phoenix(target: Target) extends SingleTarget

  /** Asterism for Michelle.
    * @group Constructors
    */
  final case class Michelle(target: Target) extends SingleTarget

  /** Asterism for Gnirs.
    * @group Constructors
    */
  final case class Gnirs(target: Target) extends SingleTarget

  /** Asterism for Niri.
    * @group Constructors
    */
  final case class Niri(target: Target) extends SingleTarget

  /** Asterism for Trecs.
    * @group Constructors
    */
  final case class Trecs(target: Target) extends SingleTarget

  /** Asterism for Nici.
    * @group Constructors
    */
  final case class Nici(target: Target) extends SingleTarget

  /** Asterism for Nifs.
    * @group Constructors
    */
  final case class Nifs(target: Target) extends SingleTarget

  /** Asterism for Gpi.
    * @group Constructors
    */
  final case class Gpi(target: Target) extends SingleTarget

  /** Asterism for Gsaoi.
    * @group Constructors
    */
  final case class Gsaoi(target: Target) extends SingleTarget

  /** Asterism for GmosS.
    * @group Constructors
    */
  final case class GmosS(target: Target) extends SingleTarget

  /** Asterism for AcqCam.
    * @group Constructors
    */
  final case class AcqCam(target: Target) extends SingleTarget

  /** Asterism for GmosN.
    * @group Constructors
    */
  final case class GmosN(target: Target) extends SingleTarget

  /** Asterism for Bhros.
    * @group Constructors
    */
  final case class Bhros(target: Target) extends SingleTarget

  /** Asterism for Visitor.
    * @group Constructors
    */
  final case class Visitor(target: Target) extends SingleTarget

  /** Asterism for Flamingos2.
    * @group Constructors
    */
  final case class Flamingos2(target: Target) extends SingleTarget

  /** Dual-target asterism for Ghost.
    * @group Constructors
    */
  final case class GhostDualTarget(ifu1: Target, ifu2: Target) extends Asterism {
    override def targets: NonEmptyList[Target] =
      NonEmptyList.of(ifu1, ifu2)
  }

  /** @group Typeclass Instances */
  implicit def EqAsterism: Eq[Asterism] =
    Eq.fromUniversalEquals

}