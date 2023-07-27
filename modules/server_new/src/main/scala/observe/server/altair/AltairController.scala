// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import cats.{Eq, Show}
import cats.syntax.all.*
import observe.model.enums.Instrument
import observe.server.tcs.Gaos.{GuideCapabilities, PauseConditionSet, ResumeConditionSet}
import observe.server.tcs.TcsController.FocalPlaneOffset
import squants.Time
import squants.space.Length

trait AltairController[F[_]] {
  import AltairController._

  def pauseResume(
    pauseReasons:  PauseConditionSet,
    resumeReasons: ResumeConditionSet,
    currentOffset: FocalPlaneOffset,
    instrument:    Instrument
  )(cfg: AltairConfig): F[AltairPauseResume[F]]

  def observe(expTime: Time)(cfg: AltairConfig): F[Unit]

  def endObserve(cfg: AltairConfig): F[Unit]

  def isFollowing: F[Boolean]

}

object AltairController {

  sealed trait AltairConfig

  case object AltairOff                                                         extends AltairConfig
  final case class Ngs(blend: Boolean, starPos: (Length, Length))               extends AltairConfig
  final case class Lgs(strap: Boolean, sfo: Boolean, starPos: (Length, Length)) extends AltairConfig
  case object LgsWithP1                                                         extends AltairConfig
  case object LgsWithOi                                                         extends AltairConfig

  sealed trait FieldLens extends Product with Serializable
  object FieldLens {
    case object In  extends FieldLens
    case object Out extends FieldLens

    given Eq[FieldLens] = Eq.instance {
      case (In, In)   => true
      case (Out, Out) => true
      case _          => false
    }
  }

  given Eq[Ngs] = Eq.by(_.blend)
  given Eq[Lgs] = Eq.by(x => (x.strap, x.sfo))

  given Eq[AltairConfig] = Eq.instance {
    case (AltairOff, AltairOff) => true
    case (a: Lgs, b: Lgs)       => a === b
    case (a: Ngs, b: Ngs)       => a === b
    case (LgsWithOi, LgsWithOi) => true
    case (LgsWithP1, LgsWithP1) => true
    case _                      => false
  }

  given Show[AltairConfig] = Show.fromToString[AltairConfig]

  sealed case class AltairPauseResume[F[_]](
    pause:             Option[F[Unit]],
    guideWhilePaused:  GuideCapabilities,
    pauseTargetFilter: Boolean,
    resume:            Option[F[Unit]],
    restoreOnResume:   GuideCapabilities,
    config:            Option[F[Unit]],
    forceFreeze:       Boolean
  )

}