// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import lucuma.core.enums.Instrument

object operations:
  enum OperationLevel:
    case Observation, NsCycle, NsNod

  import OperationLevel.*

  enum Operations(val level: OperationLevel):
    // Operations possible at the observation level
    case PauseObservation  extends Operations(Observation)
    case StopObservation   extends Operations(Observation)
    case AbortObservation  extends Operations(Observation)
    case ResumeObservation extends Operations(Observation)

    // Operations possible for N&S Cycle
    case PauseGracefullyObservation extends Operations(NsCycle)
    case StopGracefullyObservation  extends Operations(NsCycle)

    // Operations possible for N&S Nod
    case PauseImmediatelyObservation extends Operations(NsNod)
    case StopImmediatelyObservation  extends Operations(NsNod)

  sealed trait SupportedOperations:
    def apply(
      level:           OperationLevel,
      isObservePaused: Boolean,
      isMultiLevel:    Boolean
    ): List[Operations]

  // private object F2SupportedOperations extends SupportedOperations:
  //   def apply(
  //     level:           OperationLevel,
  //     isObservePaused: Boolean,
  //     isMultiLevel:    Boolean
  //   ): List[Operations] =
  //     Nil

  private object GmosSupportedOperations extends SupportedOperations:
    def apply(
      level:           OperationLevel,
      isObservePaused: Boolean,
      isMultiLevel:    Boolean
    ): List[Operations] =
      println(s"QUERYING $level, $isObservePaused, $isMultiLevel")
      level match
        case Observation =>
          if (isMultiLevel)
            if (isObservePaused)
              List(Operations.ResumeObservation, Operations.AbortObservation)
            else
              List(Operations.AbortObservation)
          else if (isObservePaused)
            List(
              Operations.ResumeObservation,
              Operations.StopObservation,
              Operations.AbortObservation
            )
          else
            List(
              Operations.PauseObservation,
              Operations.StopObservation,
              Operations.AbortObservation
            )
        case NsCycle     =>
          List(Operations.PauseGracefullyObservation, Operations.StopGracefullyObservation)
        case NsNod       =>
          List(Operations.PauseImmediatelyObservation, Operations.StopImmediatelyObservation)

  // private object GnirsSupportedOperations extends SupportedOperations:
  //   def apply(
  //     level:           OperationLevel,
  //     isObservePaused: Boolean,
  //     isMultiLevel:    Boolean
  //   ): List[Operations] =
  //     level match
  //       case Observation => List(Operations.StopObservation, Operations.AbortObservation)
  //       case _           => Nil

  // private object NiriSupportedOperations extends SupportedOperations:
  //   def apply(
  //     level:           OperationLevel,
  //     isObservePaused: Boolean,
  //     isMultiLevel:    Boolean
  //   ): List[Operations] =
  //     level match
  //       case Observation => List(Operations.StopObservation, Operations.AbortObservation)
  //       case _           => Nil

  // private object NifsSupportedOperations extends SupportedOperations:
  //   def apply(
  //     level:           OperationLevel,
  //     isObservePaused: Boolean,
  //     isMultiLevel:    Boolean
  //   ): List[Operations] =
  //     level match
  //       case Observation => List(Operations.StopObservation, Operations.AbortObservation)
  //       case _           => Nil

  // private object GsaoiSupportedOperations extends SupportedOperations:
  //   def apply(
  //     level:           OperationLevel,
  //     isObservePaused: Boolean,
  //     isMultiLevel:    Boolean
  //   ): List[Operations] =
  //     level match
  //       case Observation => List(Operations.StopObservation, Operations.AbortObservation)
  //       case _           => Nil

  private object NilSupportedOperations extends SupportedOperations:
    def apply(
      level:           OperationLevel,
      isObservePaused: Boolean,
      isMultiLevel:    Boolean
    ): List[Operations] =
      Nil

  private val instrumentOperations: Map[Instrument, SupportedOperations] = Map(
    // Instrument.F2     -> F2SupportedOperations,
    Instrument.GmosSouth -> GmosSupportedOperations,
    Instrument.GmosNorth -> GmosSupportedOperations
    // Instrument.Gnirs -> GnirsSupportedOperations,
    // Instrument.Niri   -> NiriSupportedOperations,
    // Instrument.Nifs   -> NifsSupportedOperations,
    // Instrument.Gsaoi  -> GsaoiSupportedOperations
  )

  extension (i: Instrument)
    def operations(
      level:           OperationLevel,
      isObservePaused: Boolean,
      isMultiLevel:    Boolean = false
    ): List[Operations] =
      instrumentOperations
        .getOrElse(i, NilSupportedOperations)(
          level,
          isObservePaused,
          isMultiLevel
        )
