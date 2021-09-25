// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import cats.ApplicativeError
import cats.effect.Sync
import edu.gemini.spModel.gemini.altair.AltairConstants.FIELD_LENSE_PROP
import edu.gemini.spModel.gemini.altair.AltairConstants.GUIDESTAR_TYPE_PROP
import edu.gemini.spModel.gemini.altair.AltairParams.GuideStarType
import observe.model.enum.Resource
import observe.server.CleanConfig
import observe.server.ConfigUtilOps._
import observe.server.altair.AltairController._
import observe.server.gems.GemsController.GemsConfig
import observe.server.tcs.Gaos
import observe.server.tcs.Gaos.PauseConditionSet
import observe.server.tcs.Gaos.PauseResume
import observe.server.tcs.Gaos.ResumeConditionSet
import squants.Time

trait Altair[F[_]] extends Gaos[F] {
  def pauseResume(
    config:        AltairConfig,
    pauseReasons:  PauseConditionSet,
    resumeReasons: ResumeConditionSet
  ): F[PauseResume[F]]

  val resource: Resource

  def usesP1(guide: AltairConfig): Boolean

  def usesOI(guide: AltairConfig): Boolean

  def isFollowing: F[Boolean]

  def hasTarget(guide: AltairConfig): Boolean

}

object Altair {

  private class AltairImpl[F[_]: Sync](controller: AltairController[F], fieldLens: FieldLens)
      extends Altair[F] {
    override def pauseResume(
      config:        AltairConfig,
      pauseReasons:  PauseConditionSet,
      resumeReasons: ResumeConditionSet
    ): F[PauseResume[F]] =
      controller.pauseResume(pauseReasons, resumeReasons, fieldLens)(config)

    override def observe(config: Either[AltairConfig, GemsConfig], expTime: Time): F[Unit] =
      config.swap.map(controller.observe(expTime)(_)).getOrElse(Sync[F].unit)

    override def endObserve(config: Either[AltairConfig, GemsConfig]): F[Unit]             =
      config.swap.map(controller.endObserve).getOrElse(Sync[F].unit)

    override val resource: Resource                                                        = Resource.Altair

    override def usesP1(guide: AltairConfig): Boolean = guide match {
      case LgsWithP1 => true
      case _         => false
    }

    override def usesOI(guide: AltairConfig): Boolean = guide match {
      case LgsWithOi | Ngs(true, _) => true
      case _                        => false
    }

    override def isFollowing: F[Boolean]              = controller.isFollowing

    override def hasTarget(guide: AltairConfig): Boolean = guide match {
      case Lgs(st, sf, _) => st || sf
      case LgsWithOi      => false
      case LgsWithP1      => false
      case Ngs(_, _)      => true
      case AltairOff      => false
    }

  }

  def fromConfig[F[_]: Sync](config: CleanConfig): F[AltairController[F] => Altair[F]] =
    config
      .extractAOAs[FieldLens](FIELD_LENSE_PROP)
      .map { fieldLens => (controller: AltairController[F]) =>
        new AltairImpl[F](controller, fieldLens): Altair[F]
      }
      .toF[F]

  def guideStarType[F[_]: ApplicativeError[*[_], Throwable]](
    config: CleanConfig
  ): F[GuideStarType] =
    config.extractAOAs[GuideStarType](GUIDESTAR_TYPE_PROP).toF[F]

}
