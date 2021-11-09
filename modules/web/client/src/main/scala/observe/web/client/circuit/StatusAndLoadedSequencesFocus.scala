// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import cats.Eq
import cats.syntax.all._
import monocle.Getter
import observe.web.client.model.{ ClientStatus, ObserveAppRootModel }
import observe.model.Observation
import observe.model._
import observe.model.enum._
import observe.web.client.components.SessionQueueTable
import observe.web.client.model.ModelOps._
import observe.web.client.model._
import observe.web.client.model.lenses.firstScienceStepTargetNameT
import observe.web.client.model.lenses.obsClassT
import web.client.table._

final case class SequenceInSessionQueue(
  idName:        Observation.IdName,
  status:        SequenceState,
  instrument:    Instrument,
  active:        Boolean,
  loaded:        Boolean,
  name:          String,
  obsClass:      ObsClass,
  targetName:    Option[TargetName],
  observer:      Option[Observer],
  runningStep:   Option[RunningStep],
  nextStepToRun: Option[StepId],
  inDayCalQueue: Boolean
)

object SequenceInSessionQueue {
  implicit val eq: Eq[SequenceInSessionQueue] =
    Eq.by(x =>
      (x.idName,
       x.status,
       x.instrument,
       x.active,
       x.loaded,
       x.name,
       x.obsClass,
       x.targetName,
       x.observer,
       x.runningStep,
       x.nextStepToRun,
       x.inDayCalQueue
      )
    )

  def toSequenceInSessionQueue(
    sod:    SequencesOnDisplay,
    queue:  List[SequenceView],
    dayCal: List[Observation.Id]
  ): List[SequenceInSessionQueue] =
    queue.map { s =>
      val active     = sod.idDisplayed(s.idName.id)
      val loaded     = sod.loadedIds.contains(s.idName)
      val targetName = firstScienceStepTargetNameT.headOption(s)
      val obsClass   = obsClassT
        .headOption(s)
        .map(ObsClass.fromString)
        .getOrElse(ObsClass.Nighttime)
      SequenceInSessionQueue(
        s.idName,
        s.status,
        s.metadata.instrument,
        active,
        loaded,
        s.metadata.name,
        obsClass,
        targetName,
        s.runningStep,
        s.nextStepToRun,
        dayCal.contains(s.idName.id)
      )
    }

}

final case class StatusAndLoadedSequencesFocus(
  status:      ClientStatus,
  sequences:   List[SequenceInSessionQueue],
  tableState:  TableState[SessionQueueTable.TableColumn],
  queueFilter: SessionQueueFilter
)

object StatusAndLoadedSequencesFocus {
  implicit val eq: Eq[StatusAndLoadedSequencesFocus] =
    Eq.by(x => (x.status, x.sequences, x.tableState, x.queueFilter))

  private val sessionQueueG       = ObserveAppRootModel.sessionQueueL.asGetter
  private val sessionQueueFilterG =
    ObserveAppRootModel.sessionQueueFilterL.asGetter
  private val sodG                = ObserveAppRootModel.sequencesOnDisplayL.asGetter

  val statusAndLoadedSequencesG: Getter[ObserveAppRootModel, StatusAndLoadedSequencesFocus] =
    ClientStatus.clientStatusFocusL.asGetter.zip(
      sessionQueueG.zip(
        sodG.zip(
          ObserveAppRootModel.sessionQueueTableStateL.asGetter
            .zip(sessionQueueFilterG.zip(ObserveAppRootModel.dayCalG))
        )
      )
    ) >>> { case (s, (queue, (sod, (queueTable, (filter, dayCal))))) =>
      StatusAndLoadedSequencesFocus(s,
                                    SequenceInSessionQueue
                                      .toSequenceInSessionQueue(sod, queue, dayCal.foldMap(_.queue))
                                      .sortBy(_.idName.name),
                                    queueTable,
                                    filter
      )
    }

  val filteredSequencesG: Getter[ObserveAppRootModel, List[SequenceInSessionQueue]] = {
    sessionQueueFilterG.zip(sessionQueueG.zip(sodG.zip(ObserveAppRootModel.dayCalG))) >>> {
      case (f, (s, (sod, dayCal))) =>
        f.filter(
          SequenceInSessionQueue
            .toSequenceInSessionQueue(sod, s, dayCal.foldMap(_.queue))
        )
    }
  }

}
