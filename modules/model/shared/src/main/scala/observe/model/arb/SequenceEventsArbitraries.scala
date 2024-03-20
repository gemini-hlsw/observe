// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.enums.Instrument
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.model.User
import lucuma.core.model.arb.ArbTelescopeGuideConfig.given
import lucuma.core.model.arb.ArbUser.given
import lucuma.core.model.sequence.Step
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbGid.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.ObservationProgress
import observe.model.QueueManipulationOp.*
import observe.model.*
import observe.model.arb.ObserveModelArbitraries.given
import observe.model.arb.all.given
import observe.model.dhs.*
import observe.model.enums.*
import observe.model.events.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

import java.time.Instant

trait SequenceEventsArbitraries {

  given Arbitrary[GuideConfigUpdate] = Arbitrary[GuideConfigUpdate] {
    arbitrary[TelescopeGuideConfig].map(GuideConfigUpdate.apply)
  }

  given Arbitrary[ConnectionOpenEvent] = Arbitrary[ConnectionOpenEvent] {
    for {
      u  <- arbitrary[Option[User]]
      id <- arbitrary[ClientId]
      v  <- arbitrary[String]
    } yield ConnectionOpenEvent(u, id, v)
  }

  given Arbitrary[SequenceStart] = Arbitrary[SequenceStart] {
    for {
      id <- arbitrary[Observation.Id]
      si <- arbitrary[Step.Id]
      sv <- arbitrary[SequencesQueue[SequenceView]]
    } yield SequenceStart(id, si, sv)
  }

  given Arbitrary[StepExecuted]                = Arbitrary[StepExecuted] {
    for {
      i <- arbitrary[Observation.Id]
      s <- arbitrary[SequencesQueue[SequenceView]]
    } yield StepExecuted(i, s)
  }
  given Arbitrary[SequenceCompleted]           = Arbitrary[SequenceCompleted] {
    arbitrary[SequencesQueue[SequenceView]].map(SequenceCompleted.apply)
  }
  given Arbitrary[SequenceLoaded]              = Arbitrary[SequenceLoaded] {
    for {
      i <- arbitrary[Observation.Id]
      s <- arbitrary[SequencesQueue[SequenceView]]
    } yield SequenceLoaded(i, s)
  }
  given Arbitrary[SequenceUnloaded]            = Arbitrary[SequenceUnloaded] {
    for {
      i <- arbitrary[Observation.Id]
      s <- arbitrary[SequencesQueue[SequenceView]]
    } yield SequenceUnloaded(i, s)
  }
  given Arbitrary[SequenceStopped]             = Arbitrary[SequenceStopped] {
    for {
      i <- arbitrary[Observation.Id]
      s <- arbitrary[SequencesQueue[SequenceView]]
    } yield SequenceStopped(i, s)
  }
  given Arbitrary[SequenceAborted]             = Arbitrary[SequenceAborted] {
    for {
      i <- arbitrary[Observation.Id]
      s <- arbitrary[SequencesQueue[SequenceView]]
    } yield SequenceAborted(i, s)
  }
  given Arbitrary[StepBreakpointChanged]       = Arbitrary[StepBreakpointChanged] {
    arbitrary[SequencesQueue[SequenceView]].map(StepBreakpointChanged.apply)
  }
  given Arbitrary[StepSkipMarkChanged]         = Arbitrary[StepSkipMarkChanged] {
    arbitrary[SequencesQueue[SequenceView]].map(StepSkipMarkChanged.apply)
  }
  given Arbitrary[SequencePauseRequested]      = Arbitrary[SequencePauseRequested] {
    arbitrary[SequencesQueue[SequenceView]].map(SequencePauseRequested.apply)
  }
  given Arbitrary[SequencePauseCanceled]       = Arbitrary[SequencePauseCanceled] {
    for {
      i <- arbitrary[Observation.Id]
      s <- arbitrary[SequencesQueue[SequenceView]]
    } yield SequencePauseCanceled(i, s)
  }
  given Arbitrary[SequenceRefreshed]           = Arbitrary[SequenceRefreshed] {
    for {
      s <- arbitrary[SequencesQueue[SequenceView]]
      c <- arbitrary[ClientId]
    } yield SequenceRefreshed(s, c)
  }
  given Arbitrary[ActionStopRequested]         = Arbitrary[ActionStopRequested] {
    arbitrary[SequencesQueue[SequenceView]].map(ActionStopRequested.apply)
  }
  given Arbitrary[ServerLogMessage]            = Arbitrary[ServerLogMessage] {
    for {
      l <- arbitrary[ServerLogLevel]
      t <- arbitrary[Instant]
      m <- Gen.alphaStr
    } yield ServerLogMessage(l, t, m)
  }
  given Arbitrary[events.NullEvent.type]       = Arbitrary[NullEvent.type](NullEvent)
  given Arbitrary[OperatorUpdated]             = Arbitrary[OperatorUpdated] {
    arbitrary[SequencesQueue[SequenceView]].map(OperatorUpdated.apply)
  }
  given Arbitrary[ObserverUpdated]             = Arbitrary[ObserverUpdated] {
    arbitrary[SequencesQueue[SequenceView]].map(ObserverUpdated.apply)
  }
  given Arbitrary[ConditionsUpdated]           = Arbitrary[ConditionsUpdated] {
    arbitrary[SequencesQueue[SequenceView]].map(ConditionsUpdated.apply)
  }
  given Arbitrary[QueueManipulationOp]         = Arbitrary[QueueManipulationOp] {
    for {
      q <- arbitrary[QueueId]
      i <- arbitrary[List[Observation.Id]]
      r <- arbitrary[List[Int]]
      c <- arbitrary[ClientId]
      o <- arbitrary[Observation.Id]
      p <- arbitrary[Int]
      m <- Gen.oneOf(Moved(q, c, o, p),
                     Started(q),
                     Stopped(q),
                     Clear(q),
                     AddedSeqs(q, i),
                     RemovedSeqs(q, i, r)
           )
    } yield m
  }
  given Arbitrary[QueueUpdated]                = Arbitrary[QueueUpdated] {
    for {
      o <- arbitrary[QueueManipulationOp]
      s <- arbitrary[SequencesQueue[SequenceView]]
    } yield QueueUpdated(o, s)
  }
  given Arbitrary[LoadSequenceUpdated]         = Arbitrary[LoadSequenceUpdated] {
    for {
      i <- arbitrary[Instrument]
      o <- arbitrary[Observation.Id]
      s <- arbitrary[SequencesQueue[SequenceView]]
      c <- arbitrary[ClientId]
    } yield LoadSequenceUpdated(i, o, s, c)
  }
  given Arbitrary[ClearLoadedSequencesUpdated] =
    Arbitrary[ClearLoadedSequencesUpdated] {
      arbitrary[SequencesQueue[SequenceView]]
        .map(ClearLoadedSequencesUpdated.apply)
    }
  given Arbitrary[SequenceError]               = Arbitrary[SequenceError] {
    for {
      i <- arbitrary[Observation.Id]
      s <- arbitrary[SequencesQueue[SequenceView]]
    } yield SequenceError(i, s)
  }

  given Arbitrary[SequenceUpdated] = Arbitrary[SequenceUpdated] {
    arbitrary[SequencesQueue[SequenceView]].map(SequenceUpdated.apply)
  }

  given Arbitrary[SequencePaused] = Arbitrary[SequencePaused] {
    for {
      i <- arbitrary[Observation.Id]
      s <- arbitrary[SequencesQueue[SequenceView]]
    } yield SequencePaused(i, s)
  }

  given Arbitrary[ExposurePaused] = Arbitrary[ExposurePaused] {
    for {
      i <- arbitrary[Observation.Id]
      s <- arbitrary[SequencesQueue[SequenceView]]
    } yield ExposurePaused(i, s)
  }

  given Arbitrary[FileIdStepExecuted] = Arbitrary[FileIdStepExecuted] {
    for {
      i <- arbitrary[ImageFileId]
      s <- arbitrary[SequencesQueue[SequenceView]]
    } yield FileIdStepExecuted(i, s)
  }

  given Arbitrary[UserNotification] = Arbitrary[UserNotification] {
    for {
      i <- arbitrary[Notification]
      c <- arbitrary[ClientId]
    } yield UserNotification(i, c)
  }

  given Arbitrary[UserPromptNotification] = Arbitrary[UserPromptNotification] {
    for {
      i <- arbitrary[UserPrompt]
      c <- arbitrary[ClientId]
    } yield UserPromptNotification(i, c)
  }

  given Arbitrary[ObservationProgressEvent] = Arbitrary[ObservationProgressEvent] {
    for {
      p <- arbitrary[ObservationProgress]
    } yield ObservationProgressEvent(p)
  }

  given Arbitrary[ObserveModelUpdate] = Arbitrary[ObserveModelUpdate] {
    Gen.oneOf[ObserveModelUpdate](
      arbitrary[SequenceStart],
      arbitrary[StepExecuted],
      arbitrary[FileIdStepExecuted],
      arbitrary[SequenceCompleted],
      arbitrary[SequenceLoaded],
      arbitrary[SequenceUnloaded],
      arbitrary[StepBreakpointChanged],
      arbitrary[OperatorUpdated],
      arbitrary[ObserverUpdated],
      arbitrary[ConditionsUpdated],
      arbitrary[StepSkipMarkChanged],
      arbitrary[SequencePauseRequested],
      arbitrary[SequencePauseCanceled],
      arbitrary[SequenceRefreshed],
      arbitrary[ActionStopRequested],
      arbitrary[SequenceUpdated],
      arbitrary[SequencePaused],
      arbitrary[ExposurePaused],
      arbitrary[LoadSequenceUpdated],
      arbitrary[ClearLoadedSequencesUpdated],
      arbitrary[QueueUpdated],
      arbitrary[SequenceError]
    )
  }

  given Arbitrary[AlignAndCalibEvent] = Arbitrary[AlignAndCalibEvent] {
    for {
      p <- Gen.posNum[Int]
    } yield AlignAndCalibEvent(p)
  }

  given Arbitrary[ObserveEvent] = Arbitrary[ObserveEvent] {
    Gen.oneOf[ObserveEvent](
      arbitrary[ObserveModelUpdate],
      arbitrary[ConnectionOpenEvent],
      arbitrary[ServerLogMessage],
      arbitrary[UserNotification],
      arbitrary[UserPromptNotification],
      arbitrary[ObservationProgressEvent],
      arbitrary[AlignAndCalibEvent],
      arbitrary[NullEvent.type]
    )
  }

  given Cogen[ConnectionOpenEvent] =
    Cogen[(Option[User], ClientId, String)]
      .contramap(x => (x.userDetails, x.clientId, x.serverVersion))

  given Cogen[ObserveModelUpdate] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[GuideConfigUpdate] =
    Cogen[TelescopeGuideConfig].contramap(_.telescope)

  given Cogen[SequenceStart] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[StepExecuted] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[FileIdStepExecuted] =
    Cogen[(dhs.ImageFileId, SequencesQueue[SequenceView])]
      .contramap(x => (x.fileId, x.view))

  given Cogen[SequenceCompleted] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[SequenceLoaded] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[SequenceUnloaded] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[StepSkipMarkChanged] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[OperatorUpdated] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[ObserverUpdated] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[ConditionsUpdated] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[StepBreakpointChanged] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[SequencePauseRequested] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[SequencePauseCanceled] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[SequenceRefreshed] =
    Cogen[(ClientId, SequencesQueue[SequenceView])]
      .contramap(x => (x.clientId, x.view))

  given Cogen[ActionStopRequested] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[SequenceUpdated] =
    Cogen[SequencesQueue[SequenceView]].contramap(_.view)

  given Cogen[SequencePaused] =
    Cogen[(Observation.Id, SequencesQueue[SequenceView])]
      .contramap(x => (x.obsId, x.view))

  given Cogen[ExposurePaused] =
    Cogen[(Observation.Id, SequencesQueue[SequenceView])]
      .contramap(x => (x.obsId, x.view))

  given Cogen[SequenceError] =
    Cogen[(Observation.Id, SequencesQueue[SequenceView])]
      .contramap(x => (x.obsId, x.view))

  given Cogen[SequenceStopped] =
    Cogen[(Observation.Id, SequencesQueue[SequenceView])]
      .contramap(x => (x.obsId, x.view))

  given Cogen[SequenceAborted] =
    Cogen[(Observation.Id, SequencesQueue[SequenceView])]
      .contramap(x => (x.obsId, x.view))

  given Cogen[ServerLogMessage] =
    Cogen[(ServerLogLevel, Instant, String)]
      .contramap(x => (x.level, x.timestamp, x.msg))

  given Cogen[UserPromptNotification] =
    Cogen[(UserPrompt, ClientId)].contramap(x => (x.prompt, x.clientId))

  given Cogen[UserNotification] =
    Cogen[(Notification, ClientId)].contramap(x => (x.memo, x.clientId))

  given Cogen[ObservationProgressEvent] =
    Cogen[ObservationProgress].contramap(_.progress)

  given Cogen[AlignAndCalibEvent] =
    Cogen[Int].contramap(_.step)

}

object SequenceEventsArbitraries extends SequenceEventsArbitraries
