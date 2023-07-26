// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import cats.syntax.all.*
import lucuma.core.math.Angle
import lucuma.core.math.Offset
import lucuma.core.optics.Format
import lucuma.core.syntax.all.*
import monocle.*
import monocle.function.At.atMap
import monocle.function.FilterIndex
import monocle.macros.GenLens
import monocle.macros.GenPrism
import monocle.std.option.some
import observe.model.enums.*
import observe.model.events.*

trait ModelLenses {
  // Some useful Monocle lenses
  val obsNameL: Lens[SequenceView, String]                                          =
    GenLens[SequenceView](_.metadata.name)
  val eachStepT: Traversal[List[Step], Step]                                        =
    Traversal.fromTraverse[List, Step]
  val obsStepsL: Lens[SequenceView, List[Step]]                                     = GenLens[SequenceView](_.steps)
  val eachViewT: Traversal[List[SequenceView], SequenceView]                        =
    Traversal.fromTraverse[List, SequenceView]
  val sessionQueueL: Lens[SequencesQueue[SequenceView], List[SequenceView]]         =
    GenLens[SequencesQueue[SequenceView]](_.sessionQueue)
  // Prism to focus on only the ObserveEvents that have a queue
  val sequenceEventsP: Prism[ObserveEvent, ObserveModelUpdate]                      =
    GenPrism[ObserveEvent, ObserveModelUpdate]
  // Required for type correctness
  val stepConfigRoot: Iso[Map[SystemName, Parameters], Map[SystemName, Parameters]] =
    Iso.id[Map[SystemName, Parameters]]
  val parametersRoot: Iso[Map[ParamName, ParamValue], Map[ParamName, ParamValue]]   =
    Iso.id[Map[ParamName, ParamValue]]

  val sequenceStepT: Traversal[SequenceView, Step] =
    obsStepsL.andThen( // sequence steps
      eachStepT
    )                  // each step

  // Focus on a param value
  def paramValueL(param: ParamName): Lens[Parameters, Option[String]] =
    parametersRoot.andThen( // map of parameters
      atMap[ParamName, ParamValue].at(param)
    )                       // parameter containing the name

  // Focus on params with a prefix
  def paramValuesWithPrefixT(param: ParamName): Traversal[Parameters, String] =
    // parametersRoot andThen // map of parameters
    FilterIndex.mapFilterIndex[ParamName, ParamValue].filterIndex { (n: ParamName) =>
      n.startsWith(param)
    } // parameter containing the name

  // Possible set of observe parameters
  def systemConfigL(system: SystemName): Lens[StepConfig, Option[Parameters]] =
    stepConfigRoot.andThen( // map of systems
      atMap[SystemName, Parameters].at(system)
    )                       // subsystem name

  // Param name of a StepConfig
  def configParamValueO(
    system: SystemName,
    param:  String
  ): Optional[StepConfig, String] =
    systemConfigL(system)
      .andThen( // observe parameters
        some[Parameters]
      )
      .andThen( // focus on the option
        paramValueL(system.withParam(param))
      )
      .andThen(      // find the target name
        some[String] // focus on the option
      )

  // Focus on the sequence view
  val sequenceQueueViewL: Lens[ObserveModelUpdate, SequencesQueue[SequenceView]] =
    Lens[ObserveModelUpdate, SequencesQueue[SequenceView]](_.view)(q => {
      case e @ SequenceStart(_, _, _)          => e.copy(view = q)
      case e @ StepExecuted(_, _)              => e.copy(view = q)
      case e @ FileIdStepExecuted(_, _)        => e.copy(view = q)
      case e @ SequenceCompleted(_)            => e.copy(view = q)
      case e @ SequenceLoaded(_, _)            => e.copy(view = q)
      case e @ SequenceUnloaded(_, _)          => e.copy(view = q)
      case e @ StepBreakpointChanged(_)        => e.copy(view = q)
      case e @ OperatorUpdated(_)              => e.copy(view = q)
      case e @ ObserverUpdated(_)              => e.copy(view = q)
      case e @ ConditionsUpdated(_)            => e.copy(view = q)
      case e @ StepSkipMarkChanged(_)          => e.copy(view = q)
      case e @ SequencePauseRequested(_)       => e.copy(view = q)
      case e @ SequencePauseCanceled(_, _)     => e.copy(view = q)
      case e @ SequenceRefreshed(_, _)         => e.copy(view = q)
      case e @ ActionStopRequested(_)          => e.copy(view = q)
      case e @ SequenceError(_, _)             => e.copy(view = q)
      case e @ SequencePaused(_, _)            => e.copy(view = q)
      case e @ ExposurePaused(_, _)            => e.copy(view = q)
      case e @ SequenceUpdated(_)              => e.copy(view = q)
      case e @ LoadSequenceUpdated(_, _, _, _) => e.copy(view = q)
      case e @ ClearLoadedSequencesUpdated(_)  => e.copy(view = q)
      case e @ QueueUpdated(_, _)              => e.copy(view = q)
      case e                                   => e
    })

  val sequenceViewT: Traversal[ObserveModelUpdate, SequenceView] =
    sequenceQueueViewL
      .andThen( // Find the sequence view
        sessionQueueL
      )
      .andThen( // Find the queue
        eachViewT
      )         // each sequence on the queue

  // Composite lens to change the sequence name of an event
  val sequenceNameT: Traversal[ObserveEvent, ObservationName] =
    sequenceEventsP
      .andThen( // Events with model updates
        sequenceQueueViewL
      )
      .andThen( // Find the sequence view
        sessionQueueL
      )
      .andThen( // Find the queue
        eachViewT
      )
      .andThen( // each sequence on the queue
        obsNameL
      )         // sequence's observation name

  def filterEntry[K, V](predicate: (K, V) => Boolean): Traversal[Map[K, V], V] =
    new PTraversal[Map[K, V], Map[K, V], V, V] {
      override def modifyA[F[_]: Applicative](f: V => F[V])(s: Map[K, V]): F[Map[K, V]] =
        s.toList
          .traverse { case (k, v) =>
            (if (predicate(k, v)) f(v) else v.pure[F]).tupleLeft(k)
          }
          .map(kvs => kvs.toMap)
    }

  // Find the Parameters of the steps containing science steps
  val scienceStepT: Traversal[StepConfig, Parameters] =
    filterEntry[SystemName, Parameters] { case (s, p) =>
      s === SystemName.Observe && p.exists { case (k, v) =>
        k === SystemName.Observe.withParam("observeType") && v === "OBJECT"
      }
    }

  val scienceTargetNameO: Optional[Parameters, TargetName] =
    paramValueL(SystemName.Observe.withParam("object")).andThen( // find the target name
      some[String]
    )                                                            // focus on the option

  val signedArcsecFormat: Format[String, Angle]                     =
    Format[String, BigDecimal](_.parseBigDecimalOption, _.toString)
      .andThen(Angle.signedDecimalArcseconds.reverse.asFormat)
  def signedComponentFormat[A]: Format[String, Offset.Component[A]] =
    signedArcsecFormat.andThen(Offset.Component.angle[A].reverse)

  val stringToDoubleP: Prism[String, Double] =
    Prism((x: String) => x.parseDoubleOption)(_.show)

  val stringToString = Iso.id[String].asPrism

  val stringToStepTypeP: Prism[String, StepType] =
    Prism(StepType.fromString)(_.label)

  val stringToGuidingP: Prism[String, Guiding] =
    Prism(Guiding.fromString)(_.configValue)

}

object ModelLenses extends ModelLenses
