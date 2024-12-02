// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Monad
import cats.effect.Async
import cats.effect.IO
import cats.effect.Resource
import cats.effect.Temporal
import cats.effect.kernel.Ref
import cats.syntax.all.*
import clue.*
import clue.http4s.Http4sHttpBackend
import clue.http4s.Http4sHttpClient
import clue.websocket.ReconnectionStrategy
import edu.gemini.epics.acm.CaService
import lucuma.core.enums.Site
import lucuma.schemas.ObservationDB
import mouse.boolean.*
import observe.model.Conditions
import observe.model.config.*
import observe.model.odb.ObsRecordedIds
import observe.server.altair.*
import observe.server.gcal.*
import observe.server.gems.*
import observe.server.gmos.*
import observe.server.gsaoi.*
import observe.server.gws.*
import observe.server.keywords.*
import observe.server.odb.OdbProxy
import observe.server.odb.OdbProxy.DummyOdbProxy
import observe.server.tcs.*
import org.http4s.AuthScheme
import org.http4s.Credentials
import org.http4s.Headers
import org.http4s.client.Client
import org.http4s.client.middleware.Logger as Http4sLogger
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

case class Systems[F[_]] private[server] (
  odb:                 OdbProxy[F],
  dhs:                 DhsClientProvider[F],
  tcsSouth:            TcsSouthController[F],
  tcsNorth:            TcsNorthController[F],
  gcal:                GcalController[F],
//  flamingos2:          Flamingos2Controller[F],
  gmosSouth:           GmosSouthController[F],
  gmosNorth:           GmosNorthController[F],
  /*  gnirs:               GnirsController[F],
  gsaoi:               GsaoiController[F],
  gpi:                 GpiController[F],
  ghost:               GhostController[F],
  niri:                NiriController[F],
  nifs:                NifsController[F], */
  altair:              AltairController[F],
  gems:                GemsController[F],
  guideDb:             GuideConfigDb[F],
  tcsKeywordReader:    TcsKeywordsReader[F],
  conditionSetReader:  Conditions => ConditionSetReader[F],
  gcalKeywordReader:   GcalKeywordReader[F],
  gmosKeywordReader:   GmosKeywordReader[F],
  /*  gnirsKeywordReader:  GnirsKeywordReader[F],
  niriKeywordReader:   NiriKeywordReader[F],
  nifsKeywordReader:   NifsKeywordReader[F],
  gsaoiKeywordReader:  GsaoiKeywordReader[F],*/
  altairKeywordReader: AltairKeywordReader[F],
  gemsKeywordsReader:  GemsKeywordReader[F],
  gwsKeywordReader:    GwsKeywordReader[F]
)

object Systems {

  case class Builder(
    settings: ObserveEngineConfiguration,
    sso:      LucumaSSOConfiguration,
    service:  CaService,
    tops:     Map[String, String]
  )(using L: Logger[IO], T: Temporal[IO]) {
    val reconnectionStrategy: ReconnectionStrategy =
      (attempt, reason) =>
        // Web Socket close codes: https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent
        if (reason.toOption.flatMap(_.toOption.flatMap(_.code)).exists(_ === 1000))
          none
        else // Increase the delay to get exponential backoff with a minimum of 1s and a max of 1m
          FiniteDuration(
            math.min(60.0, math.pow(2, attempt.toDouble - 1)).toLong,
            TimeUnit.SECONDS
          ).some

    def odbProxy[F[_]: Async: Logger: Http4sHttpBackend]: F[OdbProxy[F]] =
      for
        given FetchClient[F, ObservationDB] <-
          Http4sHttpClient.of[F, ObservationDB](
            settings.odbHttp,
            "ODB",
            Headers(Authorization(Credentials.Token(AuthScheme.Bearer, sso.serviceToken)))
          )
        odbCommands                         <-
          if (settings.odbNotifications)
            Ref
              .of[F, ObsRecordedIds](ObsRecordedIds.Empty)
              .map(OdbProxy.OdbCommandsImpl[F](_))
          else new OdbProxy.DummyOdbCommands[F].pure[F]
      yield OdbProxy[F](odbCommands)

    def dhs[F[_]: Async: Logger](httpClient: Client[F]): F[DhsClientProvider[F]] =
      if (settings.systemControl.dhs.command)
        new DhsClientProvider[F] {
          override def dhsClient(instrumentName: String): DhsClient[F] = new DhsClientHttp[F](
            httpClient,
            settings.dhsServer,
            settings.dhsMaxSize,
            instrumentName
          )
        }.pure[F]
      else
        DhsClientSim.apply[F].map(x => (_: String) => x)

    // TODO make instruments controllers generalized on F
    def gcal: IO[(GcalController[IO], GcalKeywordReader[IO])] =
      if (settings.systemControl.gcal.realKeywords)
        GcalEpics
          .instance[IO](service, tops)
          .map(epicsSys =>
            (
              if (settings.systemControl.gcal.command) GcalControllerEpics(epicsSys)
              else GcalControllerSim[IO],
              GcalKeywordsReaderEpics(epicsSys)
            )
          )
      else (GcalControllerSim[IO], DummyGcalKeywordsReader[IO]).pure[IO]

    def tcsSouth(
      tcsEpicsO: => Option[TcsEpics[IO]],
      site:      Site,
      gcdb:      GuideConfigDb[IO]
    ): TcsSouthController[IO] =
      tcsEpicsO
        .map { tcsEpics =>
          if (settings.systemControl.tcs.command && site === Site.GS)
            TcsSouthControllerEpics(tcsEpics, gcdb)
          else TcsSouthControllerSim[IO]
        }
        .getOrElse(TcsSouthControllerSim[IO])

    def tcsNorth(tcsEpicsO: => Option[TcsEpics[IO]], site: Site): TcsNorthController[IO] =
      tcsEpicsO
        .map { tcsEpics =>
          if (settings.systemControl.tcs.command && site === Site.GN)
            TcsNorthControllerEpics(tcsEpics)
          else TcsNorthControllerSim[IO]
        }
        .getOrElse(TcsNorthControllerSim[IO])

    def altair(
      tcsEpicsO: => Option[TcsEpics[IO]]
    ): IO[(AltairController[IO], AltairKeywordReader[IO])] =
      if (settings.systemControl.altair.realKeywords)
        AltairEpics.instance[IO](service, tops).map { altairEpics =>
          tcsEpicsO
            .map { tcsEpics =>
              if (settings.systemControl.altair.command && settings.systemControl.tcs.command)
                AltairControllerEpics.apply(altairEpics, tcsEpics)
              else
                AltairControllerSim[IO]
            }
            .map((_, AltairKeywordReaderEpics(altairEpics)))
            .getOrElse((AltairControllerSim[IO], AltairKeywordReaderEpics(altairEpics)))
        }
      else
        (AltairControllerSim[IO], AltairKeywordReaderDummy[IO]).pure[IO]

    def tcsObjects(gcdb: GuideConfigDb[IO], site: Site): IO[
      (
        TcsNorthController[IO],
        TcsSouthController[IO],
        TcsKeywordsReader[IO],
        AltairController[IO],
        AltairKeywordReader[IO],
        Conditions => ConditionSetReader[IO]
      )
    ] =
      for {
        tcsEpicsO            <- settings.systemControl.tcs.realKeywords
                                  .option(TcsEpics.instance[IO](service, tops))
                                  .sequence
        a                    <- altair(tcsEpicsO)
        (altairCtr, altairKR) = a
        tcsNCtr               = tcsNorth(tcsEpicsO, site)
        tcsSCtr               = tcsSouth(tcsEpicsO, site, gcdb)
        tcsKR                 = tcsEpicsO.map(TcsKeywordsReaderEpics[IO]).getOrElse(DummyTcsKeywordsReader[IO])
        condsR                = tcsEpicsO
                                  .map(ConditionSetReaderEpics.apply(site, _))
                                  .getOrElse(DummyConditionSetReader(site))
      } yield (
        tcsNCtr,
        tcsSCtr,
        tcsKR,
        altairCtr,
        altairKR,
        condsR
      )

    def gems(
      gsaoiController: GsaoiGuider[IO],
      gsaoiEpicsO:     => Option[GsaoiEpics[IO]]
    ): IO[(GemsController[IO], GemsKeywordReader[IO])] =
      if (settings.systemControl.gems.realKeywords)
        GemsEpics.instance[IO](service, tops).map { gemsEpics =>
          gsaoiEpicsO
            .map { gsaoiEpics =>
              (
                if (settings.systemControl.gems.command && settings.systemControl.tcs.command)
                  GemsControllerEpics(gemsEpics, gsaoiController)
                else
                  GemsControllerSim[IO],
                GemsKeywordReaderEpics[IO](gemsEpics, gsaoiEpics)
              )
            }
            .getOrElse(
              (GemsControllerEpics(gemsEpics, gsaoiController), GemsKeywordReaderDummy[IO])
            )
        }
      else (GemsControllerSim[IO], GemsKeywordReaderDummy[IO]).pure[IO]

    def gsaoi(
      gsaoiEpicsO: => Option[GsaoiEpics[IO]]
    ): IO[(GsaoiFullHandler[IO], GsaoiKeywordReader[IO])] =
      gsaoiEpicsO
        .map { gsaoiEpics =>
          /*if (settings.systemControl.gsaoi.command) GsaoiControllerEpics(gsaoiEpics).pure[IO]
            else*/
          GsaoiControllerSim[IO]
            .map((_, GsaoiKeywordReaderEpics(gsaoiEpics)))
        }
        .getOrElse(GsaoiControllerSim[IO].map((_, GsaoiKeywordReaderDummy[IO])))

    def gemsObjects: IO[
      (GemsController[IO], GemsKeywordReader[IO], GsaoiController[IO], GsaoiKeywordReader[IO])
    ] =
      for {
        gsaoiEpicsO        <- settings.systemControl.gsaoi.realKeywords
                                .option(GsaoiEpics.instance[IO](service, tops))
                                .sequence
        a                  <- gsaoi(gsaoiEpicsO)
        (gsaoiCtr, gsaoiKR) = a
        b                  <- gems(gsaoiCtr, gsaoiEpicsO)
        (gemsCtr, gemsKR)   = b
      } yield (gemsCtr, gemsKR, gsaoiCtr, gsaoiKR)

    /*
     * Type parameters are
     * E: Instrument EPICS class
     * C: Instrument controller class
     * K: Instrument keyword reader class
     */
    def instObjects[F[_]: Monad, E, C, K](
      ctrl:                 ControlStrategy,
      epicsBuilder:         (CaService, Map[String, String]) => F[E],
      realCtrlBuilder:      (=> E) => C,
      simCtrlBuilder:       => F[C],
      realKeyReaderBuilder: E => K,
      simKeyReaderBuilder:  => K
    ): F[(C, K)] =
      if (ctrl.realKeywords)
        epicsBuilder(service, tops).flatMap(epicsSys =>
          (
            if (ctrl.command) realCtrlBuilder(epicsSys).pure[F]
            else simCtrlBuilder
          ).map((_, realKeyReaderBuilder(epicsSys)))
        )
      else
        simCtrlBuilder.map((_, simKeyReaderBuilder))

    //    def gnirs: IO[(GnirsController[IO], GnirsKeywordReader[IO])] =
    //      instObjects(
    //        settings.systemControl.gnirs,
    //        GnirsEpics.instance[IO],
    //        GnirsControllerEpics.apply[IO],
    //        GnirsControllerSim.apply[IO],
    //        GnirsKeywordReaderEpics[IO],
    //        GnirsKeywordReaderDummy[IO]
    //      )
    //
    //    def niri: IO[(NiriController[IO], NiriKeywordReader[IO])] =
    //      instObjects(
    //        settings.systemControl.niri,
    //        NiriEpics.instance[IO],
    //        NiriControllerEpics.apply[IO],
    //        NiriControllerSim.apply[IO],
    //        NiriKeywordReaderEpics[IO],
    //        NiriKeywordReaderDummy[IO]
    //      )
    //
    //    def nifs: IO[(NifsController[IO], NifsKeywordReader[IO])] =
    //      instObjects(
    //        settings.systemControl.nifs,
    //        NifsEpics.instance[IO],
    //        NifsControllerEpics.apply[IO],
    //        NifsControllerSim.apply[IO],
    //        NifsKeywordReaderEpics[IO],
    //        NifsKeywordReaderDummy[IO]
    //      )

    def gmosSouth(gmosEpicsO: Option[GmosEpics[IO]], site: Site): IO[GmosSouthController[IO]] =
      gmosEpicsO
        .filter(_ => settings.systemControl.gmos.command && site === Site.GS)
        .map(GmosSouthControllerEpics.apply[IO](_).pure[IO])
        .getOrElse(GmosControllerSim.south[IO])

    def gmosNorth(gmosEpicsO: Option[GmosEpics[IO]], site: Site): IO[GmosNorthController[IO]] =
      gmosEpicsO
        .filter(_ => settings.systemControl.gmos.command && site === Site.GN)
        .map(GmosNorthControllerEpics.apply[IO](_).pure[IO])
        .getOrElse(GmosControllerSim.north[IO])

    def gmosObjects(
      site: Site
    ): IO[(GmosSouthController[IO], GmosNorthController[IO], GmosKeywordReader[IO])] =
      for {
        gmosEpicsO   <- settings.systemControl.gmos.realKeywords
                          .option(GmosEpics.instance[IO](service, tops))
                          .sequence
        gmosSouthCtr <- gmosSouth(gmosEpicsO, site)
        gmosNorthCtr <- gmosNorth(gmosEpicsO, site)
        gmosKR        = gmosEpicsO.map(GmosKeywordReaderEpics[IO]).getOrElse(GmosKeywordReaderDummy[IO])
      } yield (gmosSouthCtr, gmosNorthCtr, gmosKR)

    //    def flamingos2: IO[Flamingos2Controller[IO]] =
    //      if (settings.systemControl.f2.command)
    //        Flamingos2Epics.instance[IO](service, tops).map(Flamingos2ControllerEpics(_))
    //      else if (settings.instForceError) Flamingos2ControllerSimBad[IO](settings.failAt)
    //      else Flamingos2ControllerSim[IO]
    //
    //    def gpi[F[_]: Async: Logger](
    //      httpClient: Client[F]
    //    ): Resource[F, GpiController[F]] = {
    //      def gpiClient: Resource[F, GpiClient[F]] =
    //        if (settings.systemControl.gpi.command)
    //          GpiClient.gpiClient[F](settings.gpiUrl.renderString, GpiStatusApply.statusesToMonitor)
    //        else GpiClient.simulatedGpiClient[F]
    //
    //      def gpiGDS(httpClient: Client[F]): Resource[F, GdsClient[F]] =
    //        Resource.pure[F, GdsClient[F]](
    //          GdsClient(if (settings.systemControl.gpiGds.command) httpClient
    //                    else GdsClient.alwaysOkClient[F],
    //                    settings.gpiGDS
    //          )
    //        )
    //
    //      (gpiClient, gpiGDS(httpClient)).mapN(GpiController(_, _))
    //    }
    //
    //    def ghost[F[_]: Async: Logger](
    //      httpClient: Client[F]
    //    ): Resource[F, GhostController[F]] = {
    //      def ghostClient: Resource[F, GhostClient[F]] =
    //        if (settings.systemControl.ghost.command)
    //          GhostClient.ghostClient[F](settings.ghostUrl.renderString)
    //        else GhostClient.simulatedGhostClient
    //
    //      def ghostGDS(httpClient: Client[F]): Resource[F, GdsClient[F]] =
    //        Resource.pure[F, GdsClient[F]](
    //          GdsClient(if (settings.systemControl.ghostGds.command) httpClient
    //                    else GdsClient.alwaysOkClient[F],
    //                    settings.ghostGDS
    //          )
    //        )
    //
    //      (ghostClient, ghostGDS(httpClient)).mapN(GhostController(_, _))
    //    }
    //
    def gws: IO[GwsKeywordReader[IO]] =
      if (settings.systemControl.gws.realKeywords)
        GwsEpics.instance[IO](service, tops).map(GwsKeywordsReaderEpics[IO])
      else GwsKeywordsReaderDummy[IO].pure[IO]

    def build(site: Site, httpClient: Client[IO]): Resource[IO, Systems[IO]] =
      for {
        httpClient                                        <-
          EmberClientBuilder
            .default[IO]
            .withLogger(Logger[IO])
            .build
            .map:
              Http4sLogger(
                logHeaders = true,
                logBody = true,
                logAction = ((s: String) => Logger[IO].trace(s)).some
              )(_)
        given Http4sHttpBackend[IO]                        = Http4sHttpBackend(httpClient)
        odbProxy                                          <- Resource.eval[IO, OdbProxy[IO]](odbProxy[IO])
        dhsClient                                         <- Resource.eval(dhs[IO](httpClient))
        gcdb                                              <- Resource.eval(GuideConfigDb.newDb[IO])
        gcals                                             <- Resource.eval(gcal)
        (gcalCtr, gcalKR)                                  = gcals
        v                                                 <- Resource.eval(tcsObjects(gcdb, site))
        (tcsGN, tcsGS, tcsKR, altairCtr, altairKR, condsR) = v
        w                                                 <- Resource.eval(gemsObjects)
        (gemsCtr, gemsKR, gsaoiCtr, gsaoiKR)               = w
        //        (gnirsCtr, gnirsKR)                        <- Resource.eval(gnirs)
        //        f2Controller                               <- Resource.eval(flamingos2)
        //        (niriCtr, niriKR)                          <- Resource.eval(niri)
        //        (nifsCtr, nifsKR)                          <- Resource.eval(nifs)
        gms                                               <- Resource.eval(gmosObjects(site))
        (gmosSouthCtr, gmosNorthCtr, gmosKR)               = gms
        //        gpiController                              <- gpi[IO](httpClient)
        //        ghostController                            <- ghost[IO](httpClient)
        gwsKR                                             <- Resource.eval(gws)
      } yield Systems[IO](
        odbProxy,
        dhsClient,
        tcsGS,
        tcsGN,
        gcalCtr,
        //        f2Controller,
        gmosSouthCtr,
        gmosNorthCtr,
        //        gnirsCtr,
        //        gsaoiCtr,
        //        gpiController,
        //        ghostController,
        //        niriCtr,
        //        nifsCtr,
        altairCtr,
        gemsCtr,
        gcdb,
        tcsKR,
        condsR,
        gcalKR,
        gmosKR,
        //        gnirsKR,
        //        niriKR,
        //        nifsKR,
        //        gsaoiKR,
        altairKR,
        gemsKR,
        gwsKR
      )
  }

  private def decodeTops(s: String): Map[String, String] =
    s.split("[=,]")
      .grouped(2)
      .collect { case Array(k, v) =>
        k.trim -> v.trim
      }
      .toMap

  def build(
    site:       Site,
    httpClient: Client[IO],
    settings:   ObserveEngineConfiguration,
    sso:        LucumaSSOConfiguration,
    service:    CaService
  )(using T: Temporal[IO], L: Logger[IO]): Resource[IO, Systems[IO]] =
    Builder(settings, sso, service, decodeTops(settings.tops)).build(site, httpClient)

  def dummy[F[_]: Async: Logger]: F[Systems[F]] =
    GuideConfigDb
      .newDb[F]
      .map(guideDb =>
        new Systems(
          new DummyOdbProxy[F],
          DhsClientProvider.dummy[F],
          TcsSouthControllerSim[F],
          TcsNorthControllerSim[F],
          GcalControllerSim[F],
          GmosControllerDisabled[F, GmosController.GmosSite.South.type]("south"),
          GmosControllerDisabled[F, GmosController.GmosSite.North.type]("north"),
          AltairControllerSim[F],
          GemsControllerSim[F],
          guideDb,
          DummyTcsKeywordsReader[F],
          DummyConditionSetReader.apply[F](Site.GN),
          DummyGcalKeywordsReader[F],
          GmosKeywordReaderDummy[F],
          AltairKeywordReaderDummy[F],
          GemsKeywordReaderDummy[F],
          GwsKeywordsReaderDummy[F]
        )
      )
}
