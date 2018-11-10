// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client

import cats.data.NonEmptyList
import cats.implicits._
import diode.data._
import gem.arb.ArbObservation
import gem.arb.ArbEnumerated._
import gem.Observation
import gem.enum.Site
import scala.collection.immutable.SortedMap
import seqexec.model.enum.Instrument
import seqexec.model.enum.BatchExecState
import seqexec.model.enum.QueueManipulationOp
import seqexec.model.ClientId
import seqexec.model.QueueId
import seqexec.model.Observer
import seqexec.model.TargetName
import seqexec.model.SequenceState
import seqexec.model.SequenceView
import seqexec.model.SequencesQueue
import seqexec.model.Notification
import seqexec.model.Step
import seqexec.model.StepId
import seqexec.model.UserDetails
import seqexec.model.ObservationProgress
import seqexec.model.events.ServerLogMessage
import seqexec.model.SeqexecModelArbitraries._
import seqexec.model.SequenceEventsArbitraries.slmArb
import seqexec.model.SequenceEventsArbitraries.slmCogen
import seqexec.model.SequenceEventsArbitraries.qmArb
import seqexec.web.common.FixedLengthBuffer
import seqexec.web.common.Zipper
import seqexec.web.common.ArbitrariesWebCommon._
import seqexec.web.client.model._
import seqexec.web.client.model.RunOperation
import seqexec.web.client.circuit._
import seqexec.web.client.model.Pages._
import seqexec.web.client.components.sequence.steps.OffsetFns.OffsetsDisplay
import seqexec.web.client.components.sequence.steps.StepConfigTable
import seqexec.web.client.components.sequence.steps.StepsTable
import seqexec.web.client.components.queue.CalQueueTable
import seqexec.web.client.components.SessionQueueTableBody
import org.scalacheck.Arbitrary._
import org.scalacheck.Arbitrary
import org.scalacheck._
import org.scalajs.dom.WebSocket
import web.client.table.TableArbitraries
import web.client.table.TableState

trait ArbitrariesWebClient extends ArbObservation with TableArbitraries {

  implicit val arbTabSelected: Arbitrary[TabSelected] =
    Arbitrary(Gen.oneOf(TabSelected.Selected, TabSelected.Background))

  implicit val tsCogen: Cogen[TabSelected] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbRunOperation: Arbitrary[RunOperation] =
    Arbitrary(Gen.oneOf(RunOperation.RunIdle, RunOperation.RunInFlight))

  implicit val roCogen: Cogen[RunOperation] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbSyncOperation: Arbitrary[SyncOperation] =
    Arbitrary(Gen.oneOf(SyncOperation.SyncIdle, SyncOperation.SyncInFlight))

  implicit val soCogen: Cogen[SyncOperation] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbPauseOperation: Arbitrary[PauseOperation] =
    Arbitrary(Gen.oneOf(PauseOperation.PauseIdle, PauseOperation.PauseInFlight))

  implicit val poCogen: Cogen[PauseOperation] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbTabOperations: Arbitrary[TabOperations] =
    Arbitrary {
      for {
        r <- arbitrary[RunOperation]
        s <- arbitrary[SyncOperation]
        p <- arbitrary[PauseOperation]
      } yield TabOperations(r, s, p)
    }

  implicit val toCogen: Cogen[TabOperations] =
    Cogen[(RunOperation, SyncOperation, PauseOperation)].contramap(x =>
      (x.runRequested, x.syncRequested, x.pauseRequested))

  implicit val arbAddDayCalOperation: Arbitrary[AddDayCalOperation] =
    Arbitrary(
      Gen.oneOf(AddDayCalOperation.AddDayCalIdle,
                AddDayCalOperation.AddDayCalInFlight))

  implicit val adCogen: Cogen[AddDayCalOperation] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbClearAllCalOperation: Arbitrary[ClearAllCalOperation] =
    Arbitrary(
      Gen.oneOf(ClearAllCalOperation.ClearAllCalIdle,
                ClearAllCalOperation.ClearAllCalInFlight))

  implicit val caqCogen: Cogen[ClearAllCalOperation] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbRunCalOperation: Arbitrary[RunCalOperation] =
    Arbitrary(
      Gen.oneOf(RunCalOperation.RunCalIdle, RunCalOperation.RunCalInFlight))

  implicit val rcCogen: Cogen[RunCalOperation] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbStopCalOperation: Arbitrary[StopCalOperation] =
    Arbitrary(
      Gen.oneOf(StopCalOperation.StopCalIdle, StopCalOperation.StopCalInFlight))

  implicit val scCogen: Cogen[StopCalOperation] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbQueueOperations: Arbitrary[QueueOperations] =
    Arbitrary {
      for {
        r <- arbitrary[AddDayCalOperation]
        c <- arbitrary[ClearAllCalOperation]
        u <- arbitrary[RunCalOperation]
        s <- arbitrary[StopCalOperation]
      } yield QueueOperations(r, c, u, s)
    }

  implicit val qoCogen: Cogen[QueueOperations] =
    Cogen[AddDayCalOperation].contramap(x => x.addDayCalRequested)

  implicit val arbRemoveSeqQueue: Arbitrary[RemoveSeqQueue] =
    Arbitrary {
      Gen.oneOf(RemoveSeqQueue.RemoveSeqQueueIdle,
                RemoveSeqQueue.RemoveSeqQueueInFlight)
    }

  implicit val rsqCogen: Cogen[RemoveSeqQueue] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbMoveSeqQueue: Arbitrary[MoveSeqQueue] =
    Arbitrary {
      Gen.oneOf(MoveSeqQueue.MoveSeqQueueIdle,
                MoveSeqQueue.MoveSeqQueueInFlight)
    }

  implicit val msqCogen: Cogen[MoveSeqQueue] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbCalibrationQueueTab: Arbitrary[CalibrationQueueTab] =
    Arbitrary {
      for {
        ts <- arbitrary[TableState[StepsTable.TableColumn]]
        st <- arbitrary[BatchExecState]
        o  <- arbitrary[Option[Observer]]
      } yield CalibrationQueueTab(ts, st, o)
    }

  implicit val cqtCogen: Cogen[CalibrationQueueTab] =
    Cogen[
      (TableState[StepsTable.TableColumn], BatchExecState, Option[Observer])]
      .contramap { x =>
        (x.tableState, x.state, x.observer)
      }

  implicit val arbQueueSeqOperations: Arbitrary[QueueSeqOperations] =
    Arbitrary {
      for {
        r <- arbitrary[RemoveSeqQueue]
        m <- arbitrary[MoveSeqQueue]
      } yield QueueSeqOperations(r, m)
    }

  implicit val sopCogen: Cogen[QueueSeqOperations] =
    Cogen[(RemoveSeqQueue, MoveSeqQueue)]
      .contramap(x => (x.removeSeqQueue, x.moveSeqQueue))

  implicit val arbInstrumentSequenceTab: Arbitrary[InstrumentSequenceTab] =
    Arbitrary {
      for {
        i   <- arbitrary[Instrument]
        idx <- arbitrary[Option[Int]]
        sv  <- arbitrary[Option[SequenceView]]
        pr  <- arbitrary[Option[SequenceView]]
        ts  <- arbitrary[TableState[StepsTable.TableColumn]]
        to  <- arbitrary[TabOperations]
      } yield
        InstrumentSequenceTab(
          i,
          sv.map(k => k.copy(metadata = k.metadata.copy(instrument = i))),
          pr,
          idx,
          ts,
          to)
    }

  implicit val istCogen: Cogen[InstrumentSequenceTab] =
    Cogen[(Instrument,
           Option[SequenceView],
           Option[SequenceView],
           Option[Int],
           TableState[StepsTable.TableColumn],
           TabOperations)].contramap { x =>
      (x.inst,
       x.currentSequence,
       x.completedSequence,
       x.stepConfig,
       x.tableState,
       x.tabOperations)
    }

  implicit val arbPreviewSequenceTab: Arbitrary[PreviewSequenceTab] =
    Arbitrary {
      for {
        idx <- arbitrary[Option[Int]]
        sv  <- arbitrary[SequenceView]
        lo  <- arbitrary[Boolean]
        ts  <- arbitrary[TableState[StepsTable.TableColumn]]
        to  <- arbitrary[TabOperations]
      } yield PreviewSequenceTab(sv, idx, lo, ts, to)
    }

  implicit val pstCogen: Cogen[PreviewSequenceTab] =
    Cogen[(SequenceView,
           Option[Int],
           TableState[StepsTable.TableColumn],
           TabOperations)].contramap { x =>
      (x.currentSequence, x.stepConfig, x.tableState, x.tabOperations)
    }

  implicit val arbSeqexecTab: Arbitrary[SeqexecTab] = Arbitrary {
    Gen.frequency(10 -> arbitrary[InstrumentSequenceTab],
                  1 -> arbitrary[PreviewSequenceTab],
                  1 -> arbitrary[CalibrationQueueTab])
  }

  implicit val sxCogen: Cogen[SeqexecTab] =
    Cogen[Either[CalibrationQueueTab,
                 Either[PreviewSequenceTab, InstrumentSequenceTab]]]
      .contramap {
        case a: CalibrationQueueTab   => Left(a)
        case a: PreviewSequenceTab    => Right(Left(a))
        case a: InstrumentSequenceTab => Right(Right(a))
      }

  implicit val arbSequenceTab: Arbitrary[SequenceTab] = Arbitrary {
    Gen.frequency(10 -> arbitrary[InstrumentSequenceTab],
                  1 -> arbitrary[PreviewSequenceTab])
  }

  implicit val stCogen: Cogen[SequenceTab] =
    Cogen[Either[PreviewSequenceTab, InstrumentSequenceTab]]
      .contramap {
        case a: PreviewSequenceTab    => Left(a)
        case a: InstrumentSequenceTab => Right(a)
      }

  implicit val arbSequenceOnDisplay: Arbitrary[SequencesOnDisplay] =
    Arbitrary {
      for {
        c <- arbitrary[CalibrationQueueTab]
        l <- Gen.chooseNum(0, 4)
        s <- Gen.listOfN(l, arbitrary[SeqexecTab])
      } yield {
        val sequences = NonEmptyList.of(c, s: _*)
        SequencesOnDisplay(Zipper.fromNel(sequences))
      }
    }

  implicit val sequencesOnDisplayCogen: Cogen[SequencesOnDisplay] =
    Cogen[Zipper[SeqexecTab]]
      .contramap(_.tabs)

  implicit val arbOffsetsDisplay: Arbitrary[OffsetsDisplay] =
    Arbitrary {
      for {
        s <- Gen.option(Gen.posNum[Double])
      } yield
        s.fold(OffsetsDisplay.NoDisplay: OffsetsDisplay)(
          OffsetsDisplay.DisplayOffsets.apply)
    }

  implicit val odCogen: Cogen[OffsetsDisplay] =
    Cogen[Option[Double]].contramap {
      case OffsetsDisplay.NoDisplay         => None
      case OffsetsDisplay.DisplayOffsets(i) => Some(i)
    }

  implicit val arbWebSocket: Arbitrary[WebSocket] =
    Arbitrary {
      new WebSocket("ws://localhost:9090")
    }

  implicit val wsCogen: Cogen[WebSocket] =
    Cogen[String].contramap(_.url)

  implicit def pendingStaleGen[A: Arbitrary]: Arbitrary[PendingStale[A]] =
    Arbitrary {
      for {
        a <- arbitrary[A]
        t <- Gen.posNum[Long]
      } yield PendingStale(a, t)
    }

  implicit def potArbitrary[A: Arbitrary]: Arbitrary[Pot[A]] =
    Arbitrary(
      Gen.oneOf(
        Gen.const(Empty),
        Gen.const(Unavailable),
        arbitrary[A].map(Ready.apply),
        Gen.const(Pending()),
        arbitrary[PendingStale[A]],
        arbitrary[Throwable].map(Failed(_)),
        arbitrary[(A, Throwable)].map { case (a, t) => FailedStale(a, t) }
      ))

  implicit def potCogen[A: Cogen]: Cogen[Pot[A]] =
    Cogen[Option[Option[
      Either[Long,
             Either[A, Either[(A, Long), Either[Throwable, (A, Throwable)]]]]]]]
      .contramap {
        case Empty              => None
        case Unavailable        => Some(None)
        case Pending(a)         => Some(Some(Left(a)))
        case Ready(a)           => Some(Some(Right(Left(a))))
        case PendingStale(a, l) => Some(Some(Right(Right(Left((a, l))))))
        case Failed(t)          => Some(Some(Right(Right(Right(Left(t))))))
        case FailedStale(a, t)  => Some(Some(Right(Right(Right(Right((a, t)))))))
      }

  implicit val arbWebSocketConnection: Arbitrary[WebSocketConnection] =
    Arbitrary {
      for {
        ws <- arbitrary[Pot[WebSocket]]
        a  <- arbitrary[Int]
        r  <- arbitrary[Boolean]
      } yield WebSocketConnection(ws, a, r)
    }

  implicit val wssCogen: Cogen[WebSocketConnection] =
    Cogen[(Pot[WebSocket], Int, Boolean)].contramap(x =>
      (x.ws, x.nextAttempt, x.autoReconnect))

  implicit val arbClientStatus: Arbitrary[ClientStatus] =
    Arbitrary {
      for {
        u <- arbitrary[Option[UserDetails]]
        w <- arbitrary[WebSocketConnection]
      } yield ClientStatus(u, w)
    }

  implicit val cssCogen: Cogen[ClientStatus] =
    Cogen[(Option[UserDetails], WebSocketConnection)].contramap(x => (x.u, x.w))

  implicit val arbRunningStep: Arbitrary[RunningStep] =
    Arbitrary {
      for {
        l <- arbitrary[Int]
        i <- arbitrary[Int]
      } yield RunningStep(l, i)
    }

  implicit val runningStepCogen: Cogen[RunningStep] =
    Cogen[(Int, Int)].contramap(x => (x.last, x.total))

  implicit val arbSection: Arbitrary[SectionVisibilityState] =
    Arbitrary(Gen.oneOf(SectionOpen, SectionClosed))

  implicit val sectionCogen: Cogen[SectionVisibilityState] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbStcf: Arbitrary[SequenceTabContentFocus] =
    Arbitrary {
      for {
        g <- arbitrary[Boolean]
        i <- arbitrary[Option[Instrument]]
        d <- arbitrary[Option[Observation.Id]]
        c <- arbitrary[Boolean]
        s <- arbitrary[TabSelected]
        l <- arbitrary[SectionVisibilityState]
      } yield SequenceTabContentFocus(g, i, d, c, s, l)
    }

  implicit val stcfCogen: Cogen[SequenceTabContentFocus] =
    Cogen[(Option[Instrument],
           Option[Observation.Id],
           TabSelected,
           SectionVisibilityState)]
      .contramap(x => (x.instrument, x.id, x.active, x.logDisplayed))

  implicit val arbQtcf: Arbitrary[CalQueueTabContentFocus] =
    Arbitrary {
      for {
        g <- arbitrary[Boolean]
        a <- arbitrary[TabSelected]
        s <- arbitrary[SectionVisibilityState]
      } yield CalQueueTabContentFocus(g, a, s)
    }

  implicit val qtcfCogen: Cogen[CalQueueTabContentFocus] =
    Cogen[(Boolean, TabSelected, SectionVisibilityState)]
      .contramap(x => (x.canOperate, x.active, x.logDisplayed))

  implicit val arbtcf: Arbitrary[TabContentFocus] = Arbitrary {
    Gen.frequency(10 -> arbitrary[SequenceTabContentFocus],
                  4 -> arbitrary[CalQueueTabContentFocus])
  }

  implicit val tcfCogen: Cogen[TabContentFocus] =
    Cogen[Either[CalQueueTabContentFocus, SequenceTabContentFocus]]
      .contramap {
        case t: SequenceTabContentFocus => t.asRight
        case t: CalQueueTabContentFocus => t.asLeft
      }

  implicit val arbAvailableTab: Arbitrary[AvailableTab] =
    Arbitrary {
      for {
        d <- arbitrary[Option[Observation.Id]]
        s <- arbitrary[Option[SequenceState]]
        i <- arbitrary[Option[Instrument]]
        n <- arbitrary[Option[Int]]
        r <- arbitrary[Option[RunningStep]]
        p <- arbitrary[Boolean]
        a <- arbitrary[TabSelected]
        l <- arbitrary[Boolean]
      } yield AvailableTab(d, s, i, r, n, p, a, l)
    }

  implicit val availableTabCogen: Cogen[AvailableTab] =
    Cogen[(Option[Observation.Id],
           Option[SequenceState],
           Option[Instrument],
           Option[Int],
           Option[RunningStep],
           Boolean,
           TabSelected)]
      .contramap(
        x =>
          (x.id,
           x.status,
           x.instrument,
           x.nextStepToRun,
           x.runningStep,
           x.isPreview,
           x.active))

  implicit val arbSeqexecTabActive: Arbitrary[SeqexecTabActive] =
    Arbitrary {
      for {
        d <- arbitrary[SequenceTab]
        a <- arbitrary[TabSelected]
      } yield SeqexecTabActive(d, a)
    }

  implicit val sequenceTabActiveCogen: Cogen[SeqexecTabActive] =
    Cogen[(SeqexecTab, TabSelected)]
      .contramap(x => (x.tab, x.active))

  implicit val arbStepIdDisplayed: Arbitrary[StepIdDisplayed] =
    Arbitrary(arbitrary[Int].map(StepIdDisplayed.apply))

  implicit val stepDisplayedCogen: Cogen[StepIdDisplayed] =
    Cogen[Int].contramap(_.step)

  implicit val arbStepsTableFocus: Arbitrary[StepsTableFocus] =
    Arbitrary {
      for {
        id <- arbitrary[Observation.Id]
        i  <- arbitrary[Instrument]
        ss <- arbitrary[SequenceState]
        s  <- arbitrary[List[Step]]
        n  <- arbitrary[Option[Int]]
        e  <- arbitrary[Option[Int]]
        p  <- arbitrary[Boolean]
        ts <- arbitrary[TableState[StepsTable.TableColumn]]
      } yield StepsTableFocus(id, i, ss, s, n, e, p, ts)
    }

  implicit val sstCogen: Cogen[StepsTableFocus] =
    Cogen[(Observation.Id,
           Instrument,
           SequenceState,
           List[Step],
           Option[Int],
           Option[Int],
           TableState[StepsTable.TableColumn])].contramap { x =>
      (x.id,
       x.instrument,
       x.state,
       x.steps,
       x.stepConfigDisplayed,
       x.nextStepToRun,
       x.tableState)
    }

  implicit val arbSequencesFocus: Arbitrary[SequencesFocus] =
    Arbitrary {
      for {
        s <- arbitrary[SequencesQueue[SequenceView]]
        d <- arbitrary[SequencesOnDisplay]
      } yield SequencesFocus(s, d)
    }

  implicit val sfCogen: Cogen[SequencesFocus] =
    Cogen[(SequencesQueue[SequenceView], SequencesOnDisplay)].contramap { x =>
      (x.sequences, x.sod)
    }

  implicit val arbSequenceInfoFocus: Arbitrary[SequenceInfoFocus] =
    Arbitrary {
      for {
        l <- arbitrary[Boolean]
        n <- arbitrary[Option[String]]
        s <- arbitrary[Option[SequenceState]]
        t <- arbitrary[Option[TargetName]]
      } yield SequenceInfoFocus(l, n, s, t)
    }

  implicit val sifCogen: Cogen[SequenceInfoFocus] =
    Cogen[(Boolean, Option[String], Option[SequenceState], Option[TargetName])]
      .contramap { x =>
        (x.canOperate, x.obsName, x.status, x.targetName)
      }

  implicit val arbPreviewPage: Arbitrary[PreviewPage] =
    Arbitrary {
      for {
        i  <- arbitrary[Instrument]
        oi <- arbitrary[Observation.Id]
        sd <- arbitrary[StepIdDisplayed]
      } yield PreviewPage(i, oi, sd)
    }

  implicit val previewPageCogen: Cogen[PreviewPage] =
    Cogen[(Instrument, Observation.Id, StepIdDisplayed)]
      .contramap(x => (x.instrument, x.obsId, x.step))

  implicit val arbSequencePage: Arbitrary[SequencePage] =
    Arbitrary {
      for {
        i  <- arbitrary[Instrument]
        oi <- arbitrary[Observation.Id]
        sd <- arbitrary[StepIdDisplayed]
      } yield SequencePage(i, oi, sd)
    }

  implicit val sequencePageCogen: Cogen[SequencePage] =
    Cogen[(Instrument, Observation.Id, StepIdDisplayed)]
      .contramap(x => (x.instrument, x.obsId, x.step))

  implicit val arbPreviewConfigPage: Arbitrary[PreviewConfigPage] =
    Arbitrary {
      for {
        i  <- arbitrary[Instrument]
        oi <- arbitrary[Observation.Id]
        st <- Gen.posNum[Int]
      } yield PreviewConfigPage(i, oi, st)
    }

  implicit val previewConfigPageCogen: Cogen[PreviewConfigPage] =
    Cogen[(Instrument, Observation.Id, Int)]
      .contramap(x => (x.instrument, x.obsId, x.step))

  implicit val arbSequenceConfigPage: Arbitrary[SequenceConfigPage] =
    Arbitrary {
      for {
        i  <- arbitrary[Instrument]
        oi <- arbitrary[Observation.Id]
        st <- Gen.posNum[Int]
      } yield SequenceConfigPage(i, oi, st)
    }

  implicit val sequenceConfigPageCogen: Cogen[SequenceConfigPage] =
    Cogen[(Instrument, Observation.Id, Int)]
      .contramap(x => (x.instrument, x.obsId, x.step))

  implicit val arbSeqexecPages: Arbitrary[SeqexecPages] =
    Arbitrary {
      for {
        r  <- Gen.const(Root)
        sc <- Gen.const(SoundTest)
        ep <- Gen.const(CalibrationQueuePage)
        pp <- arbitrary[PreviewPage]
        sp <- arbitrary[SequencePage]
        pc <- arbitrary[PreviewConfigPage]
        sc <- arbitrary[SequenceConfigPage]
        p  <- Gen.oneOf(r, sc, ep, pp, sp, pc, sc)
      } yield p
    }

  implicit val seqexecPageCogen: Cogen[SeqexecPages] =
    Cogen[Option[Option[
      Option[Either[(Instrument, Observation.Id, StepIdDisplayed),
                    Either[(Instrument, Observation.Id, StepIdDisplayed),
                           Either[(Instrument, Observation.Id, Int),
                                  (Instrument, Observation.Id, Int)]]]]]]]
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

  implicit val arbUserNotificationState: Arbitrary[UserNotificationState] =
    Arbitrary {
      for {
        v <- arbitrary[SectionVisibilityState]
        n <- arbitrary[Option[Notification]]
      } yield UserNotificationState(v, n)
    }

  implicit val userNotificationCogen: Cogen[UserNotificationState] =
    Cogen[(SectionVisibilityState, Option[Notification])].contramap(x =>
      (x.visibility, x.notification))

  implicit val arbGlobalLog: Arbitrary[GlobalLog] =
    Arbitrary {
      for {
        b <- arbitrary[FixedLengthBuffer[ServerLogMessage]]
        v <- arbitrary[SectionVisibilityState]
      } yield GlobalLog(b, v)
    }

  implicit val globalLogCogen: Cogen[GlobalLog] =
    Cogen[(FixedLengthBuffer[ServerLogMessage], SectionVisibilityState)]
      .contramap(x => (x.log, x.display))

  implicit val arbStepConfigTableTableColumn: Arbitrary[StepConfigTable.TableColumn] =
    Arbitrary(
      Gen.oneOf(StepConfigTable.NameColumn, StepConfigTable.ValueColumn))

  implicit val stepConfigTableColumnCogen: Cogen[StepConfigTable.TableColumn] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbQueueTableBodyTableColumn: Arbitrary[SessionQueueTableBody.TableColumn] =
    Arbitrary(Gen.oneOf(SessionQueueTableBody.all.map(_.column).toList))

  implicit val queueTableBodyTableColumnCogen: Cogen[SessionQueueTableBody.TableColumn] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbStepsTableTableColumn: Arbitrary[StepsTable.TableColumn] =
    Arbitrary(Gen.const(StepsTable.IconColumn))

  implicit val stepsTableColumnCogen: Cogen[StepsTable.TableColumn] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbCalQueueTableTableColumn
    : Arbitrary[CalQueueTable.TableColumn] =
    Arbitrary(
      Gen.oneOf(CalQueueTable.ObsIdColumn, CalQueueTable.InstrumentColumn))

  implicit val calTableColumnCogen: Cogen[CalQueueTable.TableColumn] =
    Cogen[String].contramap(_.productPrefix)

  implicit val arbCalQueuesState: Arbitrary[CalQueueState] =
    Arbitrary {
      for {
        ops <- arbitrary[QueueOperations]
        ts  <- arbitrary[TableState[CalQueueTable.TableColumn]]
        sop <- arbitrary[SortedMap[Observation.Id, QueueSeqOperations]]
        lOp <- arbitrary[Option[QueueManipulationOp]]
      } yield CalQueueState(ops, ts, sop, lOp)
    }

  implicit val calQueuesStateCogen: Cogen[CalQueueState] =
    Cogen[(QueueOperations, TableState[CalQueueTable.TableColumn])]
      .contramap(x => (x.ops, x.tableState))

  implicit val arbCalQueues: Arbitrary[CalibrationQueues] =
    Arbitrary {
      for {
        ops <- arbitrary[SortedMap[QueueId, CalQueueState]]
      } yield CalibrationQueues(ops)
    }

  implicit val calQueuesCogen: Cogen[CalibrationQueues] =
    Cogen[List[(QueueId, CalQueueState)]].contramap(_.queues.toList)

  implicit val arbAllObservationsProgressState
    : Arbitrary[AllObservationsProgressState] =
    Arbitrary {
      for {
        ops <- arbitrary[SortedMap[(Observation.Id, StepId), ObservationProgress]]
      } yield AllObservationsProgressState(ops)
    }

  implicit val obsProgressCogen: Cogen[AllObservationsProgressState] =
    Cogen[List[((Observation.Id, StepId), ObservationProgress)]]
      .contramap(_.obsProgress.toList)

  implicit val arbSeqexecUIModel: Arbitrary[SeqexecUIModel] =
    Arbitrary {
      for {
        navLocation        <- arbitrary[Pages.SeqexecPages]
        user               <- arbitrary[Option[UserDetails]]
        loginBox           <- arbitrary[SectionVisibilityState]
        globalLog          <- arbitrary[GlobalLog]
        sequencesOnDisplay <- arbitrary[SequencesOnDisplay]
        syncInProgress     <- arbitrary[Boolean]
        configTableState   <- arbitrary[TableState[StepConfigTable.TableColumn]]
        queueTableState    <- arbitrary[TableState[SessionQueueTableBody.TableColumn]]
        defaultObserver    <- arbitrary[Observer]
        notification       <- arbitrary[UserNotificationState]
        queues             <- arbitrary[CalibrationQueues]
        progress           <- arbitrary[AllObservationsProgressState]
        firstLoad          <- arbitrary[Boolean]
      } yield
        SeqexecUIModel(
          navLocation,
          user,
          loginBox,
          globalLog,
          sequencesOnDisplay,
          configTableState,
          queueTableState,
          defaultObserver,
          notification,
          queues,
          progress,
          firstLoad
        )
    }

  implicit val seqUIModelCogen: Cogen[SeqexecUIModel] =
    Cogen[
      (Pages.SeqexecPages,
       Option[UserDetails],
       SectionVisibilityState,
       GlobalLog,
       SequencesOnDisplay,
       TableState[StepConfigTable.TableColumn],
       TableState[SessionQueueTableBody.TableColumn],
       Observer,
       UserNotificationState,
       CalibrationQueues,
       AllObservationsProgressState,
       Boolean)]
      .contramap(
        x =>
          (x.navLocation,
           x.user,
           x.loginBox,
           x.globalLog,
           x.sequencesOnDisplay,
           x.configTableState,
           x.queueTableState,
           x.defaultObserver,
           x.notification,
           x.queues,
           x.obsProgress,
           x.firstLoad))

  implicit val arbSODLocationFocus: Arbitrary[SODLocationFocus] =
    Arbitrary {
      for {
        navLocation        <- arbitrary[Pages.SeqexecPages]
        sequencesOnDisplay <- arbitrary[SequencesOnDisplay]
        clientId           <- arbitrary[Option[ClientId]]
      } yield SODLocationFocus(navLocation, sequencesOnDisplay, clientId)
    }

  implicit val sodLocationFocusogen: Cogen[SODLocationFocus] =
    Cogen[(Pages.SeqexecPages, SequencesOnDisplay, Option[ClientId])]
      .contramap(x => (x.location, x.sod, x.clientId))

  implicit val arbWebSocketsFocus: Arbitrary[WebSocketsFocus] =
    Arbitrary {
      for {
        navLocation <- arbitrary[Pages.SeqexecPages]
        sequences   <- arbitrary[SequencesQueue[SequenceView]]
        user        <- arbitrary[Option[UserDetails]]
        observer    <- arbitrary[Observer]
        clientId    <- arbitrary[Option[ClientId]]
        site        <- arbitrary[Option[Site]]
      } yield
        WebSocketsFocus(navLocation, sequences, user, observer, clientId, site)
    }

  implicit val wsfCogen: Cogen[WebSocketsFocus] =
    Cogen[(Pages.SeqexecPages,
           SequencesQueue[SequenceView],
           Option[UserDetails],
           Observer,
           Option[ClientId],
           Option[Site])]
      .contramap(
        x =>
          (x.location,
           x.sequences,
           x.user,
           x.defaultObserver,
           x.clientId,
           x.site))

  implicit val arbInitialSyncFocus: Arbitrary[InitialSyncFocus] =
    Arbitrary {
      for {
        navLocation        <- arbitrary[Pages.SeqexecPages]
        sequencesOnDisplay <- arbitrary[SequencesOnDisplay]
        firstLoad          <- arbitrary[Boolean]
      } yield InitialSyncFocus(navLocation, sequencesOnDisplay, firstLoad)
    }

  implicit val initialSyncFocusCogen: Cogen[InitialSyncFocus] =
    Cogen[(Pages.SeqexecPages, SequencesOnDisplay, Boolean)].contramap(x =>
      (x.location, x.sod, x.firstLoad))

  implicit val arbSeqexecAppRootModel: Arbitrary[SeqexecAppRootModel] =
    Arbitrary {
      for {
        sequences <- arbitrary[SequencesQueue[SequenceView]]
        ws        <- arbitrary[WebSocketConnection]
        site      <- arbitrary[Option[Site]]
        clientId  <- arbitrary[Option[ClientId]]
        uiModel   <- arbitrary[SeqexecUIModel]
      } yield SeqexecAppRootModel(sequences, ws, site, clientId, uiModel)
    }

  implicit val arbAppTableStates: Arbitrary[AppTableStates] =
    Arbitrary {
      for {
        qt <- arbitrary[TableState[SessionQueueTableBody.TableColumn]]
        ct <- arbitrary[TableState[StepConfigTable.TableColumn]]
        st <- arbitrary[Map[Observation.Id, TableState[StepsTable.TableColumn]]]
        kt <- arbitrary[Map[QueueId, TableState[CalQueueTable.TableColumn]]]
      } yield AppTableStates(qt, ct, st, kt)
    }

  implicit val appTableStatesCogen: Cogen[AppTableStates] =
    Cogen[(TableState[SessionQueueTableBody.TableColumn],
           TableState[StepConfigTable.TableColumn],
           List[(Observation.Id, TableState[StepsTable.TableColumn])])]
      .contramap { x =>
        (x.queueTable, x.stepConfigTable, x.stepsTables.toList)
      }

}
