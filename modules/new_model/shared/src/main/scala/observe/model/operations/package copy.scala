// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// package observe.model.operations

// import lucuma.core.enums.Instrument
// import lucuma.core.enums.Instrument.*

// // Phantom tag types
// type OperationLevel
// object OperationLevel:
//   type Observation <: OperationLevel
//   type NsCycle <: OperationLevel
//   type NsNod <: OperationLevel

// import OperationLevel.*

// enum Operations[A <: OperationLevel]:
//   // Operations possible at the observation level
//   case PauseObservation  extends Operations[Observation]
//   case StopObservation   extends Operations[Observation]
//   case AbortObservation  extends Operations[Observation]
//   case ResumeObservation extends Operations[Observation]

//   // Operations possible for N&S Cycle
//   case PauseGracefullyObservation extends Operations[NsCycle]
//   case StopGracefullyObservation  extends Operations[NsCycle]

//   // Operations possible for N&S Nod
//   case PauseImmediatelyObservation extends Operations[NsNod]
//   case StopImmediatelyObservation  extends Operations[NsNod]

// sealed trait OperationLevelType[L <: OperationLevel]
// implicit object ObservationLevel extends OperationLevelType[Observation]
// implicit object NsCycleLevel     extends OperationLevelType[NsCycle]
// implicit object NsNodLevel       extends OperationLevelType[NsNod]

// sealed trait SupportedOperations {
//   def apply[L <: OperationLevel](isObservePaused: Boolean, isMultiLevel: Boolean)(implicit
//     level:                                        OperationLevelType[L]
//   ): List[Operations[L]]
// }

// private val F2SupportedOperations = new SupportedOperations {
//   def apply[L <: OperationLevel](isObservePaused: Boolean, isMultiLevel: Boolean)(implicit
//     level:                                        OperationLevelType[L]
//   ): List[Operations[L]] =
//     Nil
// }

// private val GmosSupportedOperations = new SupportedOperations {
//   def apply[L <: OperationLevel](isObservePaused: Boolean, isMultiLevel: Boolean)(implicit
//     level:                                        OperationLevelType[L]
//   ): List[Operations[L]] =
//     level match {
//       case ObservationLevel =>
//         if (isMultiLevel) {
//           if (isObservePaused) {
//             List(Operations.ResumeObservation, Operations.AbortObservation)
//           } else {
//             List(Operations.AbortObservation)
//           }
//         } else {
//           if (isObservePaused) {
//             List(Operations.ResumeObservation,
//                  Operations.StopObservation,
//                  Operations.AbortObservation
//             )
//           } else {
//             List(Operations.PauseObservation,
//                  Operations.StopObservation,
//                  Operations.AbortObservation
//             )
//           }
//         }
//       case NsCycleLevel     =>
//         List(Operations.PauseGracefullyObservation, Operations.StopGracefullyObservation)
//       case NsNodLevel       =>
//         List(Operations.PauseImmediatelyObservation, Operations.StopImmediatelyObservation)
//       case _                => Nil
//     }
// }

// private val GnirsSupportedOperations = new SupportedOperations {
//   def apply[L <: OperationLevel](isObservePaused: Boolean, isMultiLevel: Boolean)(implicit
//     level:                                        OperationLevelType[L]
//   ): List[Operations[L]] =
//     level match {
//       case ObservationLevel =>
//         if (isObservePaused) {
//           List(Operations.StopObservation, Operations.AbortObservation)
//         } else {
//           List(Operations.StopObservation, Operations.AbortObservation)
//         }
//       case _                => Nil
//     }
// }

// private val NiriSupportedOperations = new SupportedOperations {
//   def apply[L <: OperationLevel](isObservePaused: Boolean, isMultiLevel: Boolean)(implicit
//     level:                                        OperationLevelType[L]
//   ): List[Operations[L]] =
//     level match {
//       case ObservationLevel =>
//         if (isObservePaused) {
//           List(Operations.StopObservation, Operations.AbortObservation)
//         } else {
//           List(Operations.StopObservation, Operations.AbortObservation)
//         }
//       case _                => Nil
//     }
// }

// private val NifsSupportedOperations = new SupportedOperations {
//   def apply[L <: OperationLevel](isObservePaused: Boolean, isMultiLevel: Boolean)(implicit
//     level:                                        OperationLevelType[L]
//   ): List[Operations[L]] =
//     level match {
//       case ObservationLevel =>
//         if (isObservePaused) {
//           List(Operations.StopObservation, Operations.AbortObservation)
//         } else {
//           List(Operations.StopObservation, Operations.AbortObservation)
//         }
//       case _                => Nil
//     }
// }

// private val GsaoiSupportedOperations = new SupportedOperations {
//   def apply[L <: OperationLevel](isObservePaused: Boolean, isMultiLevel: Boolean)(implicit
//     level:                                        OperationLevelType[L]
//   ): List[Operations[L]] =
//     level match {
//       case ObservationLevel =>
//         List(Operations.StopObservation, Operations.AbortObservation)
//       case _                => Nil
//     }
// }

// private val NilSupportedOperations = new SupportedOperations {
//   def apply[L <: OperationLevel](isObservePaused: Boolean, isMultiLevel: Boolean)(implicit
//     level:                                        OperationLevelType[L]
//   ): List[Operations[L]] =
//     Nil
// }

// private val instrumentOperations: Map[Instrument, SupportedOperations] = Map(
//   Flamingos2 -> F2SupportedOperations,
//   GmosSouth  -> GmosSupportedOperations,
//   GmosNorth  -> GmosSupportedOperations,
//   Gnirs      -> GnirsSupportedOperations,
//   Niri       -> NiriSupportedOperations,
//   Nifs       -> NifsSupportedOperations,
//   Gsaoi      -> GsaoiSupportedOperations
// )

// final implicit class SupportedOperationsOps(val i: Instrument) extends AnyVal {
//   def operations[L <: OperationLevel](isObservePaused: Boolean, isMultiLevel: Boolean = false)(
//     implicit level:                                    OperationLevelType[L]
//   ): List[Operations[L]] =
//     instrumentOperations
//       .getOrElse(i, NilSupportedOperations)(isObservePaused, isMultiLevel)
// }
