// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats.data.NonEmptyList
import cats.implicits.*
import diode.data.*
import lucuma.core.util.arb.ArbEnumerated.*
import observe.model.Observation
import lucuma.core.enums.Site
import lucuma.core.data.Zipper
import lucuma.core.data.arb.ArbZipper.*
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*
import scala.collection.immutable.SortedMap
import observe.model.enums.Instrument
import observe.model.enums.Resource
import observe.model.enums.BatchExecState
import observe.model.*
import observe.model.events.ServerLogMessage
import observe.model.arb.all.{*, given}
import observe.model.ObserveModelArbitraries.{*, given}
import observe.model.SequenceEventsArbitraries.slmArb
import observe.model.SequenceEventsArbitraries.slmCogen
import observe.model.SequenceEventsArbitraries.qmArb
import observe.common.FixedLengthBuffer
import observe.common.ArbitrariesCommon.*
import observe.web.client.model.SectionVisibilityState.*
import observe.web.client.circuit.*
import observe.web.client.model.Pages.*
import observe.web.client.model.Formatting.OffsetsDisplay
import observe.web.client.components.sequence.steps.StepConfigTable
import observe.web.client.components.sequence.steps.StepsTable
import observe.web.client.components.queue.CalQueueTable
import observe.web.client.components.SessionQueueTable
import observe.web.client.model.arb.ArbTabOperations
import shapeless.tag
import org.scalacheck.Arbitrary.*
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen.*
import org.scalacheck.*
import org.scalajs.dom.WebSocket
import web.client.table.TableArbitraries
import web.client.table.TableState

trait ArbitrariesWebClient extends TableArbitraries with ArbTabOperations {

  given Arbitrary[QueueOperations] =
    Arbitrary {
      for {
        r <- arbitrary[AddDayCalOperation]
        c <- arbitrary[ClearAllCalOperation]
        u <- arbitrary[RunCalOperation]
        s <- arbitrary[StopCalOperation]
      } yield QueueOperations(r, c, u, s)
    }

  given Cogen[QueueOperations] =
    Cogen[AddDayCalOperation].contramap(x => x.addDayCalRequested)

  given Arbitrary[CalibrationQueueTab] =
    Arbitrary {
      for {
        st <- arbitrary[BatchExecState]
        o  <- arbitrary[Option[Observer]]
      } yield CalibrationQueueTab(st, o)
    }

  given Cogen[CalibrationQueueTab] =
    Cogen[(BatchExecState, Option[Observer])]
      .contramap { x =>
        (x.state, x.observer)
      }

  given Arbitrary[QueueSeqOperations] =
    Arbitrary {
      for {
        r <- arbitrary[RemoveSeqQueue]
        m <- arbitrary[MoveSeqQueue]
      } yield QueueSeqOperations(r, m)
    }

  given Cogen[QueueSeqOperations] =
    Cogen[(RemoveSeqQueue, MoveSeqQueue)]
      .contramap(x => (x.removeSeqQueue, x.moveSeqQueue))

  given Arbitrary[InstrumentSequenceTab] =
    Arbitrary {
      for {
        i   <- arbitrary[Instrument]
        idx <- arbitrary[Option[StepId]]
        sv  <- arbitrary[Either[SequenceView, SequenceView]]
        to  <- arbitrary[TabOperations]
        so  <- arbitrary[SystemOverrides]
        se  <- arbitrary[Option[StepId]]
        ov  <- arbitrary[SectionVisibilityState]
      } yield InstrumentSequenceTab(i,
                                    sv.bimap(tag[InstrumentSequenceTab.CompletedSV][SequenceView],
                                             tag[InstrumentSequenceTab.LoadedSV][SequenceView]
                                    ),
                                    idx,
                                    se,
                                    to,
                                    so,
                                    ov
      )
    }

  given Cogen[InstrumentSequenceTab] =
    Cogen[
      (
        Instrument,
        SequenceView,
        Option[StepId],
        Option[StepId],
        TabOperations,
        SystemOverrides,
        SectionVisibilityState
      )
    ].contramap { x =>
      (x.inst,
       x.sequence,
       x.stepConfig,
       x.selectedStep,
       x.tabOperations,
       x.systemOverrides,
       x.subsysControls
      )
    }

  given Arbitrary[PreviewSequenceTab] =
    Arbitrary {
      for {
        idx <- arbitrary[Option[StepId]]
        sv  <- arbitrary[SequenceView]
        lo  <- arbitrary[Boolean]
        to  <- arbitrary[TabOperations]
      } yield PreviewSequenceTab(sv, idx, lo, to)
    }

  given Cogen[PreviewSequenceTab] =
    Cogen[(SequenceView, Option[StepId], TabOperations)].contramap { x =>
      (x.currentSequence, x.stepConfig, x.tabOperations)
    }

  given Arbitrary[ObserveTab] = Arbitrary {
    Gen.frequency(10 -> arbitrary[InstrumentSequenceTab],
                  1  -> arbitrary[PreviewSequenceTab],
                  1  -> arbitrary[CalibrationQueueTab]
    )
  }

  given Cogen[ObserveTab] =
    Cogen[Either[CalibrationQueueTab, Either[PreviewSequenceTab, InstrumentSequenceTab]]]
      .contramap {
        case a: CalibrationQueueTab   => Left(a)
        case a: PreviewSequenceTab    => Right(Left(a))
        case a: InstrumentSequenceTab => Right(Right(a))
      }

  given Arbitrary[SequenceTab] = Arbitrary {
    Gen.frequency(10 -> arbitrary[InstrumentSequenceTab], 1 -> arbitrary[PreviewSequenceTab])
  }

  given Cogen[SequenceTab] =
    Cogen[Either[PreviewSequenceTab, InstrumentSequenceTab]]
      .contramap {
        case a: PreviewSequenceTab    => Left(a)
        case a: InstrumentSequenceTab => Right(a)
      }

  given Arbitrary[SequencesOnDisplay] =
    Arbitrary {
      for {
        c <- arbitrary[CalibrationQueueTab]
        l <- Gen.chooseNum(0, 4)
        s <- Gen.listOfN(l, arbitrary[ObserveTab])
      } yield {
        val sequences = NonEmptyList.of(c, s: _*)
        SequencesOnDisplay(Zipper.fromNel(sequences))
      }
    }

  given Cogen[SequencesOnDisplay] =
    Cogen[Zipper[ObserveTab]]
      .contramap(_.tabs)

  private val posNumTuple3Gen: Gen[(Double, Double, Double)] =
    for {
      a1 <- Gen.posNum[Double]
      a2 <- Gen.posNum[Double]
      a3 <- Gen.posNum[Double]
    } yield (a1, a2, a3)

  given Arbitrary[OffsetsDisplay] =
    Arbitrary {
      for {
        s <- Gen.option(posNumTuple3Gen)
      } yield s.fold(OffsetsDisplay.NoDisplay: OffsetsDisplay)(
        (OffsetsDisplay.DisplayOffsets.apply _).tupled
      )
    }

  given Cogen[OffsetsDisplay] =
    Cogen[Option[(Double, Double, Double)]].contramap {
      case OffsetsDisplay.NoDisplay                  => None
      case OffsetsDisplay.DisplayOffsets(ow, aw, nw) => Some((ow, aw, nw))
    }

  given Arbitrary[WebSocket] =
    Arbitrary {
      new WebSocket("ws://localhost:9090")
    }

  given Cogen[WebSocket] =
    Cogen[String].contramap(_.url)

  given [A: Arbitrary]: Arbitrary[PendingStale[A]] =
    Arbitrary {
      for {
        a <- arbitrary[A]
        t <- Gen.posNum[Long]
      } yield PendingStale(a, t)
    }

  given [A: Arbitrary]: Arbitrary[Pot[A]] =
    Arbitrary(
      Gen.oneOf(
        Gen.const(Empty),
        Gen.const(Unavailable),
        arbitrary[A].map(Ready.apply),
        Gen.const(Pending()),
        arbitrary[PendingStale[A]],
        arbitrary[Throwable].map(Failed),
        arbitrary[(A, Throwable)].map { case (a, t) => FailedStale(a, t) }
      )
    )

  given [A: Cogen]: Cogen[Pot[A]] =
    Cogen[Option[
      Option[Either[Long, Either[A, Either[(A, Long), Either[Throwable, (A, Throwable)]]]]]
    ]]
      .contramap {
        case Empty              => None
        case Unavailable        => Some(None)
        case Pending(a)         => Some(Some(Left(a)))
        case Ready(a)           => Some(Some(Right(Left(a))))
        case PendingStale(a, l) => Some(Some(Right(Right(Left((a, l))))))
        case Failed(t)          => Some(Some(Right(Right(Right(Left(t))))))
        case FailedStale(a, t)  => Some(Some(Right(Right(Right(Right((a, t)))))))
      }

  given Arbitrary[WebSocketConnection] =
    Arbitrary {
      for {
        ws <- arbitrary[Pot[WebSocket]]
        a  <- arbitrary[Int]
        r  <- arbitrary[Boolean]
      } yield WebSocketConnection(ws, a, r, None)
    }

  given Cogen[WebSocketConnection] =
    Cogen[(Pot[WebSocket], Int, Boolean)].contramap(x => (x.ws, x.nextAttempt, x.autoReconnect))

  given Arbitrary[ClientStatus] =
    Arbitrary {
      for {
        u <- arbitrary[Option[UserDetails]]
        c <- arbitrary[Option[ClientId]]
        d <- arbitrary[Map[String, String]]
        w <- arbitrary[WebSocketConnection]
      } yield ClientStatus(u, c, d, w)
    }

  given Cogen[ClientStatus] =
    Cogen[(Option[UserDetails], Option[ClientId], Map[String, String], WebSocketConnection)]
      .contramap(x => (x.user, x.clientId, x.displayNames, x.w))

  given Arbitrary[StepsTableTypeSelection] =
    Arbitrary(
      Gen.oneOf(Gen.const(StepsTableTypeSelection.StepsTableSelected),
                arbitrary[StepId].map(StepsTableTypeSelection.StepConfigTableSelected.apply)
      )
    )

  given Cogen[StepsTableTypeSelection] =
    Cogen[String].contramap(_.productPrefix)

  given Arbitrary[SequenceTabContentFocus] =
    Arbitrary {
      for {
        g <- arbitrary[Boolean]
        i <- arbitrary[Instrument]
        d <- arbitrary[Observation.Id]
        s <- arbitrary[TabSelected]
        t <- arbitrary[StepsTableTypeSelection]
        l <- arbitrary[SectionVisibilityState]
        o <- arbitrary[Boolean]
        a <- arbitrary[List[StepId]]
      } yield SequenceTabContentFocus(g, i, d, s, t, l, o, a)
    }

  given Cogen[SequenceTabContentFocus] =
    Cogen[
      (
        Boolean,
        Instrument,
        Observation.Id,
        TabSelected,
        StepsTableTypeSelection,
        SectionVisibilityState,
        Boolean,
        List[StepId]
      )
    ]
      .contramap(x =>
        (x.canOperate,
         x.instrument,
         x.id,
         x.active,
         x.tableType,
         x.logDisplayed,
         x.isPreview,
         x.steps
        )
      )

  given Arbitrary[CalQueueTabContentFocus] =
    Arbitrary {
      for {
        g <- arbitrary[Boolean]
        a <- arbitrary[TabSelected]
        s <- arbitrary[SectionVisibilityState]
      } yield CalQueueTabContentFocus(g, a, s)
    }

  given Cogen[CalQueueTabContentFocus] =
    Cogen[(Boolean, TabSelected, SectionVisibilityState)]
      .contramap(x => (x.canOperate, x.active, x.logDisplayed))

  given Arbitrary[TabContentFocus] = Arbitrary {
    Gen.frequency(10 -> arbitrary[SequenceTabContentFocus], 4 -> arbitrary[CalQueueTabContentFocus])
  }

  given Cogen[TabContentFocus] =
    Cogen[Either[CalQueueTabContentFocus, SequenceTabContentFocus]]
      .contramap {
        case t: SequenceTabContentFocus => t.asRight
        case t: CalQueueTabContentFocus => t.asLeft
      }

  given Arbitrary[AvailableTab] =
    Arbitrary {
      for {
        d <- arbitrary[Observation.IdName]
        s <- arbitrary[SequenceState]
        i <- arbitrary[Instrument]
        n <- arbitrary[Option[StepId]]
        r <- arbitrary[Option[RunningStep]]
        p <- arbitrary[Boolean]
        a <- arbitrary[TabSelected]
        l <- arbitrary[Boolean]
        o <- arbitrary[SortedMap[Resource, ResourceRunOperation]]
        u <- arbitrary[SystemOverrides]
        m <- arbitrary[SectionVisibilityState]
      } yield AvailableTab(d, s, i, r, n, p, a, l, u, m, o)
    }

  given Cogen[AvailableTab] =
    Cogen[
      (
        Observation.IdName,
        SequenceState,
        Instrument,
        Option[StepId],
        Option[RunningStep],
        Boolean,
        TabSelected,
        SystemOverrides,
        SectionVisibilityState
      )
    ]
      .contramap(x =>
        (x.idName,
         x.status,
         x.instrument,
         x.nextStepToRun,
         x.runningStep,
         x.isPreview,
         x.active,
         x.systemOverrides,
         x.overrideControls
        )
      )

  given Arbitrary[ObserveTabActive] =
    Arbitrary {
      for {
        d <- arbitrary[SequenceTab]
        a <- arbitrary[TabSelected]
      } yield ObserveTabActive(d, a)
    }

  given Cogen[ObserveTabActive] =
    Cogen[(ObserveTab, TabSelected)]
      .contramap(x => (x.tab, x.active))

  given Arbitrary[StepIdDisplayed] =
    Arbitrary(arbitrary[Option[StepId]].map(StepIdDisplayed.apply))

  given Cogen[StepIdDisplayed] =
    Cogen[Option[StepId]].contramap(_.step)

  given Arbitrary[StepsTableFocus] =
    Arbitrary {
      for {
        id <- arbitrary[Observation.IdName]
        i  <- arbitrary[Instrument]
        ss <- arbitrary[SequenceState]
        s  <- arbitrary[List[Step]]
        n  <- arbitrary[Option[StepId]]
        e  <- arbitrary[Option[StepId]]
        se <- arbitrary[Option[StepId]]
        rs <- arbitrary[Option[RunningStep]]
        p  <- arbitrary[Boolean]
        ts <- arbitrary[TableState[StepsTable.TableColumn]]
        to <- arbitrary[TabOperations]
      } yield StepsTableFocus(id, i, ss, s, n, e, se, rs, p, ts, to)
    }

  given Cogen[StepsTableFocus] =
    Cogen[
      (
        Observation.Id,
        Instrument,
        SequenceState,
        List[Step],
        Option[StepId],
        Option[StepId],
        Option[StepId],
        TableState[StepsTable.TableColumn]
      )
    ].contramap { x =>
      (x.idName.id,
       x.instrument,
       x.state,
       x.steps,
       x.stepConfigDisplayed,
       x.nextStepToRun,
       x.selectedStep,
       x.tableState
      )
    }

  given Arbitrary[SequencesFocus] =
    Arbitrary {
      for {
        s <- arbitrary[SequencesQueue[SequenceView]]
        d <- arbitrary[SequencesOnDisplay]
      } yield SequencesFocus(s, d)
    }

  given Cogen[SequencesFocus] =
    Cogen[(SequencesQueue[SequenceView], SequencesOnDisplay)].contramap { x =>
      (x.sequences, x.sod)
    }

  given Arbitrary[SequenceInfoFocus] =
    Arbitrary {
      for {
        l <- arbitrary[Boolean]
        n <- arbitrary[String]
        s <- arbitrary[SequenceState]
        t <- arbitrary[Option[TargetName]]
      } yield SequenceInfoFocus(l, n, s, t)
    }

  given Cogen[SequenceInfoFocus] =
    Cogen[(Boolean, String, SequenceState, Option[TargetName])]
      .contramap { x =>
        (x.canOperate, x.obsName, x.status, x.targetName)
      }

  given Arbitrary[PreviewPage] =
    Arbitrary {
      for {
        i  <- arbitrary[Instrument]
        oi <- arbitrary[Observation.Id]
        sd <- arbitrary[StepIdDisplayed]
      } yield PreviewPage(i, oi, sd)
    }

  given Cogen[PreviewPage] =
    Cogen[(Instrument, Observation.Id, StepIdDisplayed)]
      .contramap(x => (x.instrument, x.obsId, x.stepId))

  given Arbitrary[SequencePage] =
    Arbitrary {
      for {
        i  <- arbitrary[Instrument]
        oi <- arbitrary[Observation.Id]
        sd <- arbitrary[StepIdDisplayed]
      } yield SequencePage(i, oi, sd)
    }

  given Cogen[SequencePage] =
    Cogen[(Instrument, Observation.Id, StepIdDisplayed)]
      .contramap(x => (x.instrument, x.obsId, x.stepId))

  given Arbitrary[PreviewConfigPage] =
    Arbitrary {
      for {
        i  <- arbitrary[Instrument]
        oi <- arbitrary[Observation.Id]
        st <- arbitrary[StepId]
      } yield PreviewConfigPage(i, oi, st)
    }

  given Cogen[PreviewConfigPage] =
    Cogen[(Instrument, Observation.Id, StepId)]
      .contramap(x => (x.instrument, x.obsId, x.stepId))

  given Arbitrary[SequenceConfigPage] =
    Arbitrary {
      for {
        i  <- arbitrary[Instrument]
        oi <- arbitrary[Observation.Id]
        st <- arbitrary[StepId]
      } yield SequenceConfigPage(i, oi, st)
    }

  given Cogen[SequenceConfigPage] =
    Cogen[(Instrument, Observation.Id, StepId)]
      .contramap(x => (x.instrument, x.obsId, x.stepId))

  given Arbitrary[ObservePages] =
    Arbitrary {
      for {
        r  <- Gen.const(Root)
        st <- Gen.const(SoundTest)
        ep <- Gen.const(CalibrationQueuePage)
        pp <- arbitrary[PreviewPage]
        sp <- arbitrary[SequencePage]
        pc <- arbitrary[PreviewConfigPage]
        sc <- arbitrary[SequenceConfigPage]
        p  <- Gen.oneOf(r, st, ep, pp, sp, pc, sc)
      } yield p
    }

  given Cogen[ObservePages] =
    Cogen[Option[Option[Option[Either[
      (Instrument, Observation.Id, StepIdDisplayed),
      Either[
        (Instrument, Observation.Id, StepIdDisplayed),
        Either[(Instrument, Observation.Id, StepId), (Instrument, Observation.Id, StepId)]
      ]
    ]]]]]
      .contramap {
        case Root                        => None
        case CalibrationQueuePage        => Some(None)
        case SoundTest                   => Some(Some(None))
        case PreviewPage(i, o, s)        => Some(Some(Some(Left((i, o, s)))))
        case SequencePage(i, o, s)       =>
          Some(Some(Some(Right(Left((i, o, s))))))
        case SequenceConfigPage(i, o, s) =>
          Some(Some(Some(Right(Right(Left((i, o, s)))))))
        case PreviewConfigPage(i, o, s)  =>
          Some(Some(Some(Right(Right(Right((i, o, s)))))))
      }

  given Arbitrary[UserNotificationState] =
    Arbitrary {
      for {
        v <- arbitrary[SectionVisibilityState]
        n <- arbitrary[Option[Notification]]
      } yield UserNotificationState(v, n)
    }

  given Cogen[UserNotificationState] =
    Cogen[(SectionVisibilityState, Option[Notification])].contramap(x =>
      (x.visibility, x.notification)
    )

  given Arbitrary[UserPromptState] =
    Arbitrary {
      for {
        n <- arbitrary[Option[UserPrompt]]
      } yield UserPromptState(n)
    }

  given Cogen[UserPromptState] =
    Cogen[Option[UserPrompt]].contramap(_.notification)

  given Arbitrary[GlobalLog] =
    Arbitrary {
      for {
        b <- arbitrary[FixedLengthBuffer[ServerLogMessage]]
        v <- arbitrary[SectionVisibilityState]
      } yield GlobalLog(b, v)
    }

  given Cogen[GlobalLog] =
    Cogen[(FixedLengthBuffer[ServerLogMessage], SectionVisibilityState)]
      .contramap(x => (x.log, x.display))

  given Arbitrary[StepConfigTable.TableColumn] =
    Arbitrary(Gen.oneOf(StepConfigTable.NameColumn, StepConfigTable.ValueColumn))

  given Cogen[StepConfigTable.TableColumn] =
    Cogen[String].contramap(_.productPrefix)

  given Arbitrary[SessionQueueTable.TableColumn] =
    Arbitrary(Gen.oneOf(SessionQueueTable.all.map(_.column).toList))

  given Cogen[SessionQueueTable.TableColumn] =
    Cogen[String].contramap(_.productPrefix)

  given Arbitrary[StepsTable.TableColumn] =
    Arbitrary(Gen.oneOf(StepsTable.all.map(_.column).toList))

  given Cogen[StepsTable.TableColumn] =
    Cogen[String].contramap(_.productPrefix)

  given Arbitrary[CalQueueTable.TableColumn] =
    Arbitrary(Gen.oneOf(CalQueueTable.ObsIdColumn, CalQueueTable.InstrumentColumn))

  given Cogen[CalQueueTable.TableColumn] =
    Cogen[String].contramap(_.productPrefix)

  given Arbitrary[CalQueueState] =
    Arbitrary {
      for {
        ops <- arbitrary[QueueOperations]
        ts  <- arbitrary[TableState[CalQueueTable.TableColumn]]
        sop <- arbitrary[SortedMap[Observation.Id, QueueSeqOperations]]
        lOp <- arbitrary[Option[QueueManipulationOp]]
      } yield CalQueueState(ops, ts, sop, lOp)
    }

  given Cogen[CalQueueState] =
    Cogen[(QueueOperations, TableState[CalQueueTable.TableColumn])]
      .contramap(x => (x.ops, x.tableState))

  given Arbitrary[CalibrationQueues] =
    Arbitrary {
      for {
        ops <- arbitrary[SortedMap[QueueId, CalQueueState]]
      } yield CalibrationQueues(ops)
    }

  given Cogen[CalibrationQueues] =
    Cogen[List[(QueueId, CalQueueState)]].contramap(_.queues.toList)

  given Arbitrary[AllObservationsProgressState] =
    Arbitrary {
      for {
        ops <- arbitrary[SortedMap[(Observation.Id, StepId), Progress]]
      } yield AllObservationsProgressState(ops)
    }

  given Cogen[AllObservationsProgressState] =
    Cogen[List[((Observation.Id, StepId), Progress)]]
      .contramap(_.obsProgress.toList)

  given Arbitrary[ObsClass] =
    Arbitrary(Gen.oneOf(ObsClass.All, ObsClass.Daytime, ObsClass.Nighttime))

  given Cogen[ObsClass] =
    Cogen[String].contramap(_.productPrefix)

  given Arbitrary[SessionQueueFilter] =
    Arbitrary {
      for {
        ce <- arbitrary[ObsClass]
      } yield SessionQueueFilter(ce)
    }

  given Cogen[SessionQueueFilter] =
    Cogen[ObsClass]
      .contramap(_.obsClass)

  given Arbitrary[ObserveUIModel] =
    Arbitrary {
      for {
        navLocation        <- arbitrary[Pages.ObservePages]
        user               <- arbitrary[Option[UserDetails]]
        displayNames       <- arbitrary[Map[String, String]]
        loginBox           <- arbitrary[SectionVisibilityState]
        globalLog          <- arbitrary[GlobalLog]
        sequencesOnDisplay <- arbitrary[SequencesOnDisplay]
        appTableStates     <- arbitrary[AppTableStates]
        notification       <- arbitrary[UserNotificationState]
        prompt             <- arbitrary[UserPromptState]
        queues             <- arbitrary[CalibrationQueues]
        progress           <- arbitrary[AllObservationsProgressState]
        filter             <- arbitrary[SessionQueueFilter]
        sound              <- arbitrary[SoundSelection]
        firstLoad          <- arbitrary[Boolean]
      } yield ObserveUIModel(
        navLocation,
        user,
        displayNames,
        loginBox,
        globalLog,
        sequencesOnDisplay,
        appTableStates,
        notification,
        prompt,
        queues,
        progress,
        filter,
        sound,
        firstLoad
      )
    }

  given Cogen[ObserveUIModel] =
    Cogen[
      (
        Pages.ObservePages,
        Option[UserDetails],
        Map[String, String],
        SectionVisibilityState,
        GlobalLog,
        SequencesOnDisplay,
        AppTableStates,
        UserNotificationState,
        CalibrationQueues,
        AllObservationsProgressState,
        SessionQueueFilter,
        SoundSelection,
        Boolean
      )
    ]
      .contramap(x =>
        (x.navLocation,
         x.user,
         x.displayNames,
         x.loginBox,
         x.globalLog,
         x.sequencesOnDisplay,
         x.appTableStates,
         x.notification,
         x.queues,
         x.obsProgress,
         x.sessionQueueFilter,
         x.sound,
         x.firstLoad
        )
      )

  given Arbitrary[SODLocationFocus] =
    Arbitrary {
      for {
        navLocation        <- arbitrary[Pages.ObservePages]
        sequencesOnDisplay <- arbitrary[SequencesOnDisplay]
        clientId           <- arbitrary[Option[ClientId]]
      } yield SODLocationFocus(navLocation, sequencesOnDisplay, clientId)
    }

  given Cogen[SODLocationFocus] =
    Cogen[(Pages.ObservePages, SequencesOnDisplay, Option[ClientId])]
      .contramap(x => (x.location, x.sod, x.clientId))

  given Arbitrary[WebSocketsFocus] =
    Arbitrary {
      for {
        navLocation          <- arbitrary[Pages.ObservePages]
        sequences            <- arbitrary[SequencesQueue[SequenceView]]
        resourceRunRequested <-
          arbitrary[Map[Observation.Id, SortedMap[Resource, ResourceRunOperation]]]
        user                 <- arbitrary[Option[UserDetails]]
        displayNames         <- arbitrary[Map[String, String]]
        clientId             <- arbitrary[Option[ClientId]]
        site                 <- arbitrary[Option[Site]]
        sound                <- arbitrary[SoundSelection]
        serverVer            <- arbitrary[Option[String]]
        guideConf            <- arbitrary[TelescopeGuideConfig]
        acStep               <- arbitrary[AlignAndCalibStep]
      } yield WebSocketsFocus(navLocation,
                              sequences,
                              resourceRunRequested,
                              user,
                              displayNames,
                              clientId,
                              site,
                              sound,
                              serverVer,
                              guideConf,
                              acStep
      )
    }

  given Cogen[WebSocketsFocus] =
    Cogen[
      (
        Pages.ObservePages,
        SequencesQueue[SequenceView],
        Option[UserDetails],
        Map[String, String],
        Option[ClientId],
        Option[Site],
        SoundSelection,
        Option[String],
        TelescopeGuideConfig,
        AlignAndCalibStep
      )
    ]
      .contramap(x =>
        (x.location,
         x.sequences,
         x.user,
         x.displayNames,
         x.clientId,
         x.site,
         x.sound,
         x.serverVersion,
         x.guideConfig,
         x.alignAndCalib
        )
      )

  given Arbitrary[InitialSyncFocus] =
    Arbitrary {
      for {
        navLocation        <- arbitrary[Pages.ObservePages]
        sequencesOnDisplay <- arbitrary[SequencesOnDisplay]
        firstLoad          <- arbitrary[Boolean]
        displayNames       <- arbitrary[Map[String, String]]
      } yield InitialSyncFocus(navLocation, sequencesOnDisplay, displayNames, firstLoad)
    }

  given Cogen[InitialSyncFocus] =
    Cogen[(Pages.ObservePages, SequencesOnDisplay, Map[String, String], Boolean)].contramap(x =>
      (x.location, x.sod, x.displayNames, x.firstLoad)
    )

  given Arbitrary[ObserveAppRootModel] =
    Arbitrary {
      for {
        sequences <- arbitrary[SequencesQueue[SequenceView]]
        ws        <- arbitrary[WebSocketConnection]
        site      <- arbitrary[Option[Site]]
        clientId  <- arbitrary[Option[ClientId]]
        uiModel   <- arbitrary[ObserveUIModel]
        version   <- arbitrary[Option[String]]
        guideConf <- arbitrary[TelescopeGuideConfig]
        acStep    <- arbitrary[AlignAndCalibStep]
      } yield ObserveAppRootModel(sequences,
                                  ws,
                                  site,
                                  clientId,
                                  uiModel,
                                  version,
                                  guideConf,
                                  acStep,
                                  None
      )
    }

  given Cogen[ObserveAppRootModel] =
    Cogen[
      (
        SequencesQueue[SequenceView],
        WebSocketConnection,
        Option[Site],
        Option[ClientId],
        ObserveUIModel,
        Option[String],
        TelescopeGuideConfig,
        AlignAndCalibStep
      )
    ].contramap { x =>
      (x.sequences,
       x.ws,
       x.site,
       x.clientId,
       x.uiModel,
       x.serverVersion,
       x.guideConfig,
       x.alignAndCalib
      )
    }

  given Arbitrary[AppTableStates] =
    Arbitrary {
      for {
        qt <- arbitrary[TableState[SessionQueueTable.TableColumn]]
        ct <- arbitrary[TableState[StepConfigTable.TableColumn]]
        st <- arbitrary[Map[Observation.Id, TableState[StepsTable.TableColumn]]]
        kt <- arbitrary[Map[QueueId, TableState[CalQueueTable.TableColumn]]]
      } yield AppTableStates(qt, ct, st, kt)
    }

  given Cogen[AppTableStates] =
    Cogen[
      (
        TableState[SessionQueueTable.TableColumn],
        TableState[StepConfigTable.TableColumn],
        List[(Observation.Id, TableState[StepsTable.TableColumn])],
        List[(QueueId, TableState[CalQueueTable.TableColumn])]
      )
    ]
      .contramap { x =>
        (x.sessionQueueTable, x.stepConfigTable, x.stepsTables.toList, x.queueTables.toList)
      }

}
