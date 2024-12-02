// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import cats.Monad
import cats.data.Nested
import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.Site
import lucuma.core.enums.StepGuideState
import lucuma.core.model.ElevationRange
import observe.common.ObsQueriesGQL.RecordDatasetMutation.Data.RecordDataset.Dataset
import observe.model.Observation.Id
import observe.model.Observer
import observe.model.Operator
import observe.model.dhs.ImageFileId
import observe.model.enums.KeywordName
import observe.server.OcsBuildInfo
import observe.server.tcs.TargetKeywordsReader
import observe.server.tcs.TcsController
import observe.server.tcs.TcsKeywordsReader
import org.typelevel.log4cats.Logger

import java.time.LocalDate

import ConditionOps.*

final case class StateKeywordsReader[F[_]: Monad](
  conditions: ConditionSetReader[F],
  operator:   Option[Operator],
  observer:   Option[Observer],
  site:       Site
) {

  def observerName: F[String]        = observer.map(_.value.value).getOrElse("observer").pure[F]
  def operatorName: F[String]        = operator.map(_.value.value).getOrElse("ssa").pure[F]
  def rawImageQuality: F[String]     = conditions.imageQualityStr
  def rawCloudExtinction: F[String]  = conditions.cloudExtinctionStr
  def rawWaterVapor: F[String]       = conditions.waterVaporStr
  def rawBackgroundLight: F[String]  = conditions.backgroundLightStr
  def rawImageQualityD: F[Double]    = conditions.imageQualityDbl
  def rawCloudExtinctionD: F[Double] = conditions.cloudExtinctionDbl
  def rawWaterVaporD: F[Double]      = conditions.waterVaporDbl
  def rawBackgroundLightD: F[Double] = conditions.backgroundLightDbl
}

class StandardHeader[F[_]: Sync: Logger](
  kwClient:      KeywordsClient[F],
  obsReader:     ObsKeywordsReader[F],
  tcsReader:     TcsKeywordsReader[F],
  stateReader:   StateKeywordsReader[F],
  tcsSubsystems: List[TcsController.Subsystem]
) extends Header[F] {

  val obsObject: F[String] = for {
    obsType   <- obsReader.obsType
    obsObject <- obsReader.obsObject
    tcsObject <- tcsReader.sourceATarget.objectName
  } yield
    if (obsType === "OBJECT" && obsObject =!= "Twilight" && obsObject =!= "Domeflat") tcsObject
    else obsObject

  private def optTcsKeyword[B](s: TcsController.Subsystem)(v: F[B])(using
    d: DefaultHeaderValue[B]
  ): F[B] =
    if (tcsSubsystems.contains(s)) v else d.default.pure[F]

  private def mountTcsKeyword[B: DefaultHeaderValue](v: F[B]) =
    optTcsKeyword[B](TcsController.Subsystem.Mount)(v)

  private def m2TcsKeyword[B: DefaultHeaderValue](v: F[B]) =
    optTcsKeyword[B](TcsController.Subsystem.M2)(v)

  private def sfTcsKeyword[B: DefaultHeaderValue](v: F[B]) =
    optTcsKeyword[B](TcsController.Subsystem.AGUnit)(v)

  private def baseKeywords(dataset: Option[Dataset.Reference]): List[KeywordBag => F[KeywordBag]] =
    List(
      buildString("Observe".pure[F], KeywordName.SW_NAME),
      buildString(OcsBuildInfo.version.pure[F], KeywordName.SW_VER),
      buildString(obsObject, KeywordName.OBJECT),
      buildString(obsReader.obsType, KeywordName.OBSTYPE),
      buildString(obsReader.obsClass, KeywordName.OBSCLASS),
      buildString(dataset.map(_.observation.label.programReference.label).getOrElse("").pure[F],
                  KeywordName.GEMPRGID
      ),
      buildString(dataset.map(_.observation.label.label).getOrElse("").pure[F], KeywordName.OBSID),
      buildString(dataset.map(_.label.label).getOrElse("").pure[F], KeywordName.DATALAB),
      buildString(stateReader.observerName, KeywordName.OBSERVER),
      buildString(obsReader.observatory, KeywordName.OBSERVAT),
      buildString(obsReader.telescope, KeywordName.TELESCOP),
      buildDouble(mountTcsKeyword(tcsReader.sourceATarget.parallax), KeywordName.PARALLAX),
      buildDouble(mountTcsKeyword(tcsReader.sourceATarget.radialVelocity), KeywordName.RADVEL),
      buildDouble(mountTcsKeyword(tcsReader.sourceATarget.epoch), KeywordName.EPOCH),
      buildDouble(mountTcsKeyword(tcsReader.sourceATarget.equinox), KeywordName.EQUINOX),
      buildDouble(mountTcsKeyword(tcsReader.trackingEquinox), KeywordName.TRKEQUIN),
      buildString(stateReader.operatorName, KeywordName.SSA),
      buildDouble(mountTcsKeyword(tcsReader.sourceATarget.ra), KeywordName.RA),
      buildDouble(mountTcsKeyword(tcsReader.sourceATarget.dec), KeywordName.DEC),
      buildDouble(tcsReader.elevation, KeywordName.ELEVATIO),
      buildDouble(tcsReader.azimuth, KeywordName.AZIMUTH),
      buildDouble(tcsReader.crPositionAngle, KeywordName.CRPA),
      buildString(tcsReader.hourAngle, KeywordName.HA),
      buildString(tcsReader.localTime, KeywordName.LT),
      buildString(mountTcsKeyword(tcsReader.trackingFrame), KeywordName.TRKFRAME),
      buildDouble(mountTcsKeyword(tcsReader.trackingDec), KeywordName.DECTRACK),
      buildDouble(mountTcsKeyword(tcsReader.trackingEpoch), KeywordName.TRKEPOCH),
      buildDouble(mountTcsKeyword(tcsReader.trackingRA), KeywordName.RATRACK),
      buildString(mountTcsKeyword(tcsReader.sourceATarget.frame), KeywordName.FRAME),
      buildDouble(mountTcsKeyword(tcsReader.sourceATarget.properMotionDec), KeywordName.PMDEC),
      buildDouble(mountTcsKeyword(tcsReader.sourceATarget.properMotionRA), KeywordName.PMRA),
      buildDouble(mountTcsKeyword(tcsReader.sourceATarget.wavelength), KeywordName.WAVELENG),
      buildString(stateReader.rawImageQuality, KeywordName.RAWIQ),
      buildString(stateReader.rawCloudExtinction, KeywordName.RAWCC),
      buildString(stateReader.rawWaterVapor, KeywordName.RAWWV),
      buildString(stateReader.rawBackgroundLight, KeywordName.RAWBG),
      buildDouble(stateReader.rawImageQualityD, KeywordName.RAWDIQ),
      buildDouble(stateReader.rawCloudExtinctionD, KeywordName.RAWDCC),
      buildDouble(stateReader.rawWaterVaporD, KeywordName.RAWDWV),
      buildDouble(stateReader.rawBackgroundLightD, KeywordName.RAWDBG),
      buildString(obsReader.pIReq, KeywordName.RAWPIREQ),
      buildString(obsReader.geminiQA, KeywordName.RAWGEMQA),
      buildString(tcsReader.carouselMode, KeywordName.CGUIDMOD),
      buildString(tcsReader.ut, KeywordName.UT),
      buildString(tcsReader.date, KeywordName.DATE),
      buildString(m2TcsKeyword(tcsReader.m2Baffle), KeywordName.M2BAFFLE),
      buildString(m2TcsKeyword(tcsReader.m2CentralBaffle), KeywordName.M2CENBAF),
      buildString(tcsReader.st, KeywordName.ST),
      buildDouble(mountTcsKeyword(tcsReader.xOffset), KeywordName.XOFFSET),
      buildDouble(mountTcsKeyword(tcsReader.yOffset), KeywordName.YOFFSET),
      buildDouble(mountTcsKeyword(tcsReader.pOffset), KeywordName.POFFSET),
      buildDouble(mountTcsKeyword(tcsReader.qOffset), KeywordName.QOFFSET),
      buildDouble(mountTcsKeyword(tcsReader.raOffset), KeywordName.RAOFFSET),
      buildDouble(mountTcsKeyword(tcsReader.decOffset), KeywordName.DECOFFSE),
      buildDouble(mountTcsKeyword(tcsReader.trackingRAOffset), KeywordName.RATRGOFF),
      buildDouble(mountTcsKeyword(tcsReader.trackingDecOffset), KeywordName.DECTRGOF),
      buildDouble(mountTcsKeyword(tcsReader.instrumentPA), KeywordName.PA),
      buildDouble(mountTcsKeyword(tcsReader.instrumentAA), KeywordName.IAA),
      buildDouble(sfTcsKeyword(tcsReader.sfRotation), KeywordName.SFRT2),
      buildDouble(sfTcsKeyword(tcsReader.sfTilt), KeywordName.SFTILT),
      buildDouble(sfTcsKeyword(tcsReader.sfLinear), KeywordName.SFLINEAR),
      buildString(mountTcsKeyword(tcsReader.aoFoldName), KeywordName.AOFOLD),
      buildString(obsReader.pwfs1GuideS, KeywordName.PWFS1_ST),
      buildString(obsReader.pwfs2GuideS, KeywordName.PWFS2_ST),
      buildString(obsReader.oiwfsGuideS, KeywordName.OIWFS_ST),
      buildString(obsReader.aowfsGuideS, KeywordName.AOWFS_ST),
      buildInt32(obsReader.sciBand, KeywordName.SCIBAND),
      buildString(
        (date, obsReader.proprietaryMonths).mapN((d, m) => d.plusMonths(m.value).toString()),
        KeywordName.RELEASE
      )
    )

  def date: F[LocalDate] =
    tcsReader.date.map(LocalDate.parse)

  def timingWindows(id: ImageFileId): F[Unit] = {
    val timingWindows = obsReader.timingWindows
    val windows       = Nested(timingWindows).map { case (i, tw) =>
      List(
        KeywordName.fromTag(f"REQTWS${i + 1}%02d").map(buildString(tw.start.pure[F], _)),
        KeywordName.fromTag(f"REQTWD${i + 1}%02d").map(buildDouble(tw.duration.pure[F], _)),
        KeywordName.fromTag(f"REQTWN${i + 1}%02d").map(buildInt32(tw.repeat.pure[F], _)),
        KeywordName.fromTag(f"REQTWP${i + 1}%02d").map(buildDouble(tw.period.pure[F], _))
      ).mapFilter(identity)
    }.value

    (timingWindows.map(_.length), windows).mapN { (l, w) =>
      val windowsCount = buildInt32(l.pure[F], KeywordName.NUMREQTW)
      sendKeywords(id, kwClient, windowsCount :: w.flatten)
    }.flatten
  }

  // TODO abstract requestedConditions/requestedAirMassAngle
  def requestedConditions(id: ImageFileId): F[Unit] =
    tcsReader.elevation.flatMap { el =>
      tcsReader.sourceATarget.wavelength.flatMap { wavel =>
        sendKeywords(
          id,
          kwClient,
          List(
            buildString(obsReader.requestedConditions.imageQualityStr, KeywordName.REQIQ),
            buildString(obsReader.requestedConditions.cloudExtinctionStr, KeywordName.REQCC),
            buildString(obsReader.requestedConditions.waterVaporStr, KeywordName.REQWV),
            buildString(obsReader.requestedConditions.backgroundLightStr, KeywordName.REQBG),
            buildDouble(obsReader.requestedConditions.imageQualityDbl, KeywordName.REQDIQ),
            buildDouble(obsReader.requestedConditions.cloudExtinctionDbl, KeywordName.REQDCC),
            buildDouble(obsReader.requestedConditions.waterVaporDbl, KeywordName.REQDWV),
            buildDouble(obsReader.requestedConditions.backgroundLightDbl, KeywordName.REQDBG)
          )
        )
      }
    }

  def requestedAirMassAngle(id: ImageFileId): F[Unit] =
    obsReader.requestedConditions.elevationRange
      .map {
        case ElevationRange.AirMass(min, max)             =>
          List(
            buildDouble(max.value.toDouble.pure[F], KeywordName.REQMAXAM),
            buildDouble(min.value.toDouble.pure[F], KeywordName.REQMINAM)
          )
        case ElevationRange.HourAngle(minHours, maxHours) =>
          List(
            buildDouble(maxHours.value.toDouble.pure[F], KeywordName.REQMAXHA),
            buildDouble(minHours.value.toDouble.pure[F], KeywordName.REQMINHA)
          )
      }
      .flatMap(sendKeywords[F](id, kwClient, _))

  def guiderKeywords(
    id:        ImageFileId,
    guideWith: F[Option[StepGuideState]],
    baseName:  String,
    target:    TargetKeywordsReader[F],
    extras:    List[KeywordBag => F[KeywordBag]]
  ): F[Unit] = guideWith
    .flatMap { g =>
      val keywords: List[KeywordBag => F[KeywordBag]] = List(
        KeywordName.fromTag(s"${baseName}ARA").map(buildDouble(target.ra, _)),
        KeywordName.fromTag(s"${baseName}ADEC").map(buildDouble(target.dec, _)),
        KeywordName.fromTag(s"${baseName}ARV").map(buildDouble(target.radialVelocity, _)),
        KeywordName.fromTag(s"${baseName}AWAVEL").map(buildDouble(target.wavelength, _)),
        KeywordName.fromTag(s"${baseName}AEPOCH").map(buildDouble(target.epoch, _)),
        KeywordName.fromTag(s"${baseName}AEQUIN").map(buildDouble(target.equinox, _)),
        KeywordName.fromTag(s"${baseName}AFRAME").map(buildString(target.frame, _)),
        KeywordName.fromTag(s"${baseName}AOBJEC").map(buildString(target.objectName, _)),
        KeywordName.fromTag(s"${baseName}APMDEC").map(buildDouble(target.properMotionDec, _)),
        KeywordName.fromTag(s"${baseName}APMRA").map(buildDouble(target.properMotionRA, _)),
        KeywordName.fromTag(s"${baseName}APARAL").map(buildDouble(target.parallax, _))
      ).mapFilter(identity)

      sendKeywords[F](id, kwClient, keywords ++ extras)
        .whenA(g.exists(_ === StepGuideState.Enabled))

    }
    .handleError(_ => ()) // Errors on guideWith are caught here

  def standardGuiderKeywords(
    id:        ImageFileId,
    guideWith: F[Option[StepGuideState]],
    baseName:  String,
    target:    TargetKeywordsReader[F],
    extras:    List[KeywordBag => F[KeywordBag]]
  ): F[Unit] = {
    val ext = KeywordName
      .fromTag(s"${baseName}FOCUS")
      .map(buildDouble(tcsReader.m2UserFocusOffset, _))
      .toList ++ extras
    guiderKeywords(id, guideWith, baseName, target, ext)
  }

  override def sendBefore(
    obsId:   Id,
    id:      ImageFileId,
    dataset: Option[Dataset.Reference]
  ): F[Unit] = {
    val oiwfsKeywords = guiderKeywords(id,
                                       obsReader.oiwfsGuide,
                                       "OI",
                                       tcsReader.oiwfsTarget,
                                       List(buildDouble(tcsReader.oiwfsFreq, KeywordName.OIFREQ))
    )

    val pwfs1Keywords = standardGuiderKeywords(
      id,
      obsReader.pwfs1Guide,
      "P1",
      tcsReader.pwfs1Target,
      List(buildDouble(tcsReader.pwfs1Freq, KeywordName.P1FREQ))
    )

    val pwfs2Keywords = standardGuiderKeywords(
      id,
      obsReader.pwfs2Guide,
      "P2",
      tcsReader.pwfs2Target,
      List(buildDouble(tcsReader.pwfs2Freq, KeywordName.P2FREQ))
    )

    val aowfsKeywords =
      standardGuiderKeywords(id, obsReader.aowfsGuide, "AO", tcsReader.aowfsTarget, Nil)

    sendKeywords(id, kwClient, baseKeywords(dataset)) *>
      requestedConditions(id) *>
      requestedAirMassAngle(id) *>
      timingWindows(id) *>
      pwfs1Keywords *>
      pwfs2Keywords *>
      oiwfsKeywords *>
      aowfsKeywords
  }

  override def sendAfter(id: ImageFileId): F[Unit] = sendKeywords[F](
    id,
    kwClient,
    List(
      buildDouble(tcsReader.airMass, KeywordName.AIRMASS),
      buildDouble(tcsReader.startAirMass, KeywordName.AMSTART),
      buildDouble(tcsReader.endAirMass, KeywordName.AMEND),
      buildBoolean(obsReader.headerPrivacy,
                   KeywordName.PROP_MD,
                   DefaultHeaderValue.FalseDefaultValue
      ),
      buildString(obsReader.releaseDate, KeywordName.RELEASE)
    )
  )
}
