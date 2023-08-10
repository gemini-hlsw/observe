// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client

import scala.collection.immutable.SortedMap

import diode.data.PotState
import japgolly.scalajs.react.ReactCats.*
import japgolly.scalajs.react.Reusability
import lucuma.core.util.Enumerated
import lucuma.react.common.*
import lucuma.react.semanticui.SemanticColor
import lucuma.react.semanticui.SemanticSize
import observe.model.Observation
import observe.model.*
import observe.model.dhs.*
import observe.model.enums.Resource
import observe.model.enums.ServerLogLevel
import observe.web.client.circuit.*
import observe.web.client.model.AvailableTab
import observe.web.client.model.ClientStatus
import observe.web.client.model.GlobalLog
import observe.web.client.model.QueueOperations
import observe.web.client.model.ResourceRunOperation
import observe.web.client.model.StepItems.StepStateSummary
import observe.web.client.model.TabOperations
import observe.web.client.model.TabSelected
import observe.web.client.model.UserNotificationState
import observe.web.client.model.UserPromptState
import observe.web.client.model.WebSocketConnection
import shapeless.tag.@@
import squants.Time

package object reusability {
  given [A <: AnyRef: Enumerated]: Reusability[A]              =
    Reusability.byRef
  given [A]: Reusability[Int @@ A]                             =
    Reusability.by(x => x: Int)
  given Reusability[Time]                                      = Reusability.by(_.toMilliseconds.toLong)
  given Reusability[ImageFileId]                               = Reusability.byEq
  given Reusability[StepState]                                 = Reusability.byEq
  given Reusability[Observation.Id]                            = Reusability.byEq
  given Reusability[Observation.Id]                            = Reusability.byEq
  given Reusability[Observer]                                  = Reusability.byEq
  given Reusability[Operator]                                  = Reusability.byEq
  given Reusability[SemanticColor]                             = Reusability.by(_.toJs)
  given Reusability[Css]                                       = Reusability.by(_.htmlClass)
  given Reusability[StepId]                                    = Reusability.byEq
  given Reusability[StepConfig]                                = Reusability.byEq
  val stdStepReuse: Reusability[StandardStep]                  =
    Reusability.caseClassExcept("config")
  given Reusability[NSSubexposure]                             =
    Reusability.derive[NSSubexposure]
  given Reusability[SystemOverrides]                           = Reusability.byEq
  given Reusability[NSRunningState]                            =
    Reusability.derive[NSRunningState]
  given Reusability[NodAndShuffleStatus]                       =
    Reusability.derive[NodAndShuffleStatus]
  val nsStepReuse: Reusability[NodAndShuffleStep]              =
    Reusability.caseClassExcept("config")
  given Reusability[Step]                                      =
    Reusability {
      case (a: StandardStep, b: StandardStep)           => stdStepReuse.test(a, b)
      case (a: NodAndShuffleStep, b: NodAndShuffleStep) => nsStepReuse.test(a, b)
      case _                                            => false
    }
  given Reusability[StepStateSummary]                          =
    Reusability.byEq
  given Reusability[SequenceState]                             = Reusability.byEq
  given Reusability[ClientStatus]                              = Reusability.byEq
  given Reusability[StepsTableTypeSelection]                   =
    Reusability.byEq
  given Reusability[StepsTableFocus]                           = Reusability.byEq
  given Reusability[StatusAndStepFocus]                        =
    Reusability.byEq
  given Reusability[SequenceControlFocus]                      =
    Reusability.byEq
  given Reusability[TabSelected]                               = Reusability.byRef
  given Reusability[PotState]                                  = Reusability.byRef
  given Reusability[WebSocketConnection]                       =
    Reusability.by(_.ws.state)
  given Reusability[ResourceRunOperation]                      =
    Reusability.derive
  given Reusability[AvailableTab]                              = Reusability.byEq
  given Reusability[UserDetails]                               = Reusability.byEq
  given Reusability[UserPromptState]                           = Reusability.byEq
  given Reusability[UserNotificationState]                     = Reusability.byEq
  given Reusability[QueueOperations]                           = Reusability.byEq
  given Reusability[CalQueueControlFocus]                      = Reusability.byEq
  given Reusability[CalQueueFocus]                             = Reusability.byEq
  given Reusability[QueueId]                                   = Reusability.byEq
  given Reusability[GlobalLog]                                 = Reusability.byEq
  given Reusability[Map[Resource, ResourceRunOperation]]       =
    Reusability.map
  given Reusability[Map[ServerLogLevel, Boolean]]              =
    Reusability.map
  given Reusability[SortedMap[Resource, ResourceRunOperation]] =
    Reusability.by(_.toMap)
  given Reusability[TabOperations]                             =
    Reusability.byEq
  given Reusability[M1GuideConfig]                             =
    Reusability.derive[M1GuideConfig]
  given Reusability[M2GuideConfig]                             =
    Reusability.derive[M2GuideConfig]
  given Reusability[TelescopeGuideConfig]                      =
    Reusability.derive[TelescopeGuideConfig]
  given Reusability[SemanticSize]                              = Reusability.byRef[SemanticSize]
}
