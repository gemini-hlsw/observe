// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.Eq
import cats.Show
import cats.syntax.all.*
import lucuma.core.model.AltairConfig
import lucuma.core.model.GemsConfig
import lucuma.core.util.TimeSpan
import observe.server.tcs.Gaos.PauseCondition.FixedPauseCondition
import observe.server.tcs.Gaos.PauseCondition.OffsetMove
import observe.server.tcs.Gaos.ResumeCondition.FixedResumeCondition
import observe.server.tcs.Gaos.ResumeCondition.OffsetReached
import observe.server.tcs.TcsController.FocalPlaneOffset

/*
 * Interface to control AO systems. Implemented by Altair and GeMS
 */
trait Gaos[F[_]] {

  /*
   * Notify GAOS system of the start of the observation
   */
  def observe(config: Either[AltairConfig, GemsConfig], expTime: TimeSpan): F[Unit]

  /*
   * Notify GAOS system of the end of the observation
   */
  def endObserve(config: Either[AltairConfig, GemsConfig]): F[Unit]

}

object Gaos {

  sealed trait PauseCondition extends Product with Serializable

  object PauseCondition {
    // Telescope offset will be changed
    final case class OffsetMove(from: FocalPlaneOffset, to: FocalPlaneOffset) extends PauseCondition

    sealed trait FixedPauseCondition extends PauseCondition

    // OI will be turned off
    case object OiOff          extends FixedPauseCondition
    // PI will be turned off
    case object P1Off          extends FixedPauseCondition
    // GAOS NGS will be turned off
    case object GaosGuideOff   extends FixedPauseCondition
    // Instrument config (affects ODGW)
    case object InstConfigMove extends FixedPauseCondition

    given Eq[OffsetMove] = Eq.by(_.to)

    given Eq[FixedPauseCondition] = Eq.fromUniversalEquals

    given Eq[PauseCondition] = Eq.instance {
      case (a: OffsetMove, b: OffsetMove)                   => a === b
      case (a: FixedPauseCondition, b: FixedPauseCondition) => a === b
      case _                                                => false
    }

  }

  // Non-repeatable collection of PauseCondition that admits only one OffsetMove
  final case class PauseConditionSet private (
    offsetO: Option[OffsetMove],
    fixed:   Set[FixedPauseCondition]
  ) {

    def +(v: PauseCondition): PauseConditionSet = v match {
      case a: OffsetMove          => PauseConditionSet(a.some, fixed)
      case b: FixedPauseCondition => PauseConditionSet(offsetO, fixed + b)
    }

    def -(v: PauseCondition): PauseConditionSet = v match {
      case _: OffsetMove          => PauseConditionSet(none, fixed)
      case b: FixedPauseCondition => PauseConditionSet(offsetO, fixed - b)
    }

    def contains(v: FixedPauseCondition): Boolean = fixed.contains(v)

  }

  object PauseConditionSet {

    def fromList(ss: List[PauseCondition]): PauseConditionSet = ss.foldLeft(empty) { case (xx, e) =>
      xx + e
    }

    val empty: PauseConditionSet = PauseConditionSet(None, Set.empty)

    given Eq[PauseConditionSet] = Eq.by(x => (x.offsetO, x.fixed))

    given Show[PauseConditionSet] =
      Show.show(x => (x.offsetO.toList ++ x.fixed.toList).mkString("(", ", ", ")"))

  }

  sealed trait ResumeCondition extends Product with Serializable

  object ResumeCondition {
    // Telescope offset will be changed
    final case class OffsetReached(newOffset: FocalPlaneOffset) extends ResumeCondition

    sealed trait FixedResumeCondition extends ResumeCondition

    // OI will be turn off
    case object OiOn                extends FixedResumeCondition
    // PI will be turn off
    case object P1On                extends FixedResumeCondition
    // Guided step
    case object GaosGuideOn         extends FixedResumeCondition
    // Instrument config (affects ODGW)
    case object InstConfigCompleted extends FixedResumeCondition

    given Eq[OffsetReached] = Eq.by(_.newOffset)

    given Eq[FixedResumeCondition] = Eq.instance {
      case (OiOn, OiOn)                               => true
      case (P1On, P1On)                               => true
      case (GaosGuideOn, GaosGuideOn)                 => true
      case (InstConfigCompleted, InstConfigCompleted) => true
      case _                                          => false
    }

    given Eq[ResumeCondition] = Eq.instance {
      case (a: OffsetReached, b: OffsetReached)               => a === b
      case (a: FixedResumeCondition, b: FixedResumeCondition) => a === b
      case _                                                  => false
    }
  }

  // Non-repeatable collection of ResumeCondition that admits only one OffsetMove
  final case class ResumeConditionSet private (
    offsetO: Option[OffsetReached],
    fixed:   Set[FixedResumeCondition]
  ) {

    def +(v: ResumeCondition): ResumeConditionSet = v match {
      case a: OffsetReached        => ResumeConditionSet(a.some, fixed)
      case b: FixedResumeCondition => ResumeConditionSet(offsetO, fixed + b)
    }

    def -(v: ResumeCondition): ResumeConditionSet = v match {
      case _: OffsetReached        => ResumeConditionSet(none, fixed)
      case b: FixedResumeCondition => ResumeConditionSet(offsetO, fixed - b)
    }

    def contains(v: FixedResumeCondition): Boolean = fixed.contains(v)

  }

  object ResumeConditionSet {

    def fromList(ss: List[ResumeCondition]): ResumeConditionSet = ss.foldLeft(empty) {
      case (xx, e) => xx + e
    }

    val empty: ResumeConditionSet = ResumeConditionSet(None, Set.empty)

    given Eq[ResumeConditionSet] = Eq.by(x => (x.offsetO, x.fixed))

    given Show[ResumeConditionSet] =
      Show.show(x => (x.offsetO.toList ++ x.fixed.toList).mkString("(", ", ", ")"))

  }

  // Describes which loops can stay closed
  sealed case class GuideCapabilities(canGuideM2: Boolean, canGuideM1: Boolean)

  sealed case class PauseResume[F[_]](
    pause:  Option[F[Unit]],
    resume: Option[F[Unit]]
  )

}
