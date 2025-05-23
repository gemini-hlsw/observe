// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import cats.ApplicativeThrow
import cats.Eq
import cats.effect.Sync
import lucuma.core.enums.Instrument
import lucuma.core.model.AltairConfig
import lucuma.core.model.AltairConfig.*
import lucuma.core.model.GemsConfig
import lucuma.core.util.TimeSpan
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation
import observe.model.enums.Resource
import observe.server.altair.AltairController.*
import observe.server.tcs.Gaos
import observe.server.tcs.Gaos.PauseConditionSet
import observe.server.tcs.Gaos.ResumeConditionSet
import observe.server.tcs.TcsController.FocalPlaneOffset

trait Altair[F[_]] extends Gaos[F] {

  def pauseResume(
    config:        AltairConfig,
    currOffset:    FocalPlaneOffset,
    instrument:    Instrument,
    pauseReasons:  PauseConditionSet,
    resumeReasons: ResumeConditionSet
  ): F[AltairPauseResume[F]]

  val resource: Resource

  def usesP1(guide: AltairConfig): Boolean

  def usesOI(guide: AltairConfig): Boolean

  def isFollowing: F[Boolean]

  // Are we using a NGS, either for NGS mode or LGS + NGS ?
  def hasTarget(guide: AltairConfig): Boolean

}

object Altair {

  private class AltairImpl[F[_]: Sync](controller: AltairController[F]) extends Altair[F] {
    override def pauseResume(
      config:        AltairConfig,
      currOffset:    FocalPlaneOffset,
      instrument:    Instrument,
      pauseReasons:  PauseConditionSet,
      resumeReasons: ResumeConditionSet
    ): F[AltairPauseResume[F]] =
      controller.pauseResume(pauseReasons, resumeReasons, currOffset, instrument)(config)

    override def observe(config: Either[AltairConfig, GemsConfig], expTime: TimeSpan): F[Unit] =
      config.swap.map(controller.observe(expTime)(_)).getOrElse(Sync[F].unit)

    override def endObserve(config: Either[AltairConfig, GemsConfig]): F[Unit] =
      config.swap.map(controller.endObserve).getOrElse(Sync[F].unit)

    override val resource: Resource = Resource.Altair

    override def usesP1(guide: AltairConfig): Boolean = guide match {
      case LgsWithP1 => true
      case _         => false
    }

    override def usesOI(guide: AltairConfig): Boolean = guide match {
      case LgsWithOi | Ngs(true, _) => true
      case _                        => false
    }

    override def isFollowing: F[Boolean] = controller.isFollowing

    override def hasTarget(guide: AltairConfig): Boolean = guide match {
      case Lgs(st, sf, _) => st || sf
      case LgsWithOi      => false
      case LgsWithP1      => false
      case Ngs(_, _)      => true
      case AltairOff      => false
    }

  }

  def apply[F[_]: Sync](controller: AltairController[F]): Altair[F] = new AltairImpl[F](controller)

  sealed trait GuideStarType extends Product with Serializable
  object GuideStarType {
    case object Ngs extends GuideStarType
    case object Lgs extends GuideStarType

    given Eq[GuideStarType] = Eq.instance {
      case (Ngs, Ngs) => true
      case (Lgs, Lgs) => true
      case _          => false
    }
  }

  def guideStarType[F[_]: ApplicativeThrow](
    obsCfg: Observation
  ): GuideStarType = GuideStarType.Ngs

}
