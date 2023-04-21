// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import org.http4s.Uri
import observe.model.Observation
import observe.model.dhs.*

sealed trait ObserveFailure extends Exception with Product with Serializable

object ObserveFailure {

  /** Observe does not know how to deal with instrument in sequence. */
  final case class UnrecognizedInstrument(name: String) extends ObserveFailure

  /** Something went wrong while running a sequence. * */
  final case class Execution(errMsg: String) extends ObserveFailure

  /** Aborted sequence * */
  // TODO Reconsider if abort should be handled as an error
  final case class Aborted(obsIdName: Observation.IdName) extends ObserveFailure

  /** Exception thrown while running a sequence. */
  final case class ObserveException(ex: Throwable) extends ObserveFailure

  /** Exception thrown while running a sequence. */
  final case class ObserveExceptionWhile(context: String, ex: Throwable) extends ObserveFailure

  /** Invalid operation on a Sequence */
  final case class InvalidOp(errMsg: String) extends ObserveFailure

  /** Indicates an unexpected problem while performing a Observe operation. */
  final case class Unexpected(msg: String) extends ObserveFailure

  /** Indicates an attempt to enqueue an empty sequence */
  final case class EmptySequence(title: String) extends ObserveFailure

  /** Timeout */
  final case class Timeout(msg: String) extends ObserveFailure

  /** Sequence loading errors */
  final case class OdbSeqError(msg: String) extends ObserveFailure

  /** Exception thrown while communicating with the GDS */
  final case class GdsException(ex: Throwable, url: Uri) extends ObserveFailure

  /** XMLRPC error while communicating with the GDS */
  final case class GdsXmlError(msg: String, url: Uri) extends ObserveFailure

  /** Null epics read */
  final case class NullEpicsError(channel: String) extends ObserveFailure

  /** Observation command timeout on instrument */
  final case class ObsTimeout(fileId: ImageFileId) extends ObserveFailure

  /** Observation system timeout, e.g. TCS/GCAL */
  final case class ObsSystemTimeout(fileId: ImageFileId) extends ObserveFailure

  /** Observation command timeout */
  final case class ObsCommandTimeout(obsIdName: Observation.IdName) extends ObserveFailure

  /** Failed simulation */
  case object FailedSimulation extends ObserveFailure

  def explain(f: ObserveFailure): String = f match {
    case UnrecognizedInstrument(name) => s"Unrecognized instrument: $name"
    case Execution(errMsg)            => s"Sequence execution failed with error: $errMsg"
    case Aborted(obsId)               => s"Observation ${obsId.name} aborted"
    case ObserveException(ex)         =>
      s"Application exception: ${Option(ex.getMessage).getOrElse(ex.toString)}"
    case ObserveExceptionWhile(c, e)  =>
      s"Application exception while $c: ${Option(e.getMessage).getOrElse(e.toString)}"
    case InvalidOp(msg)               => s"Invalid operation: $msg"
    case Unexpected(msg)              => s"Unexpected error: $msg"
    case Timeout(msg)                 => s"Timeout while waiting for $msg"
    case EmptySequence(title)         => s"Attempt to enqueue empty sequence $title"
    case OdbSeqError(fail)            => fail
    case GdsException(ex, url)        =>
      s"Failure connecting with GDS at $url: ${ex.getMessage}"
    case GdsXmlError(msg, url)        => s"XML RPC error with GDS at $url: $msg"
    case FailedSimulation             => s"Failed to simulate"
    case NullEpicsError(channel)      => s"Failed to read epics channel: $channel"
    case ObsTimeout(fileId)           => s"Observation of $fileId timed out"
    case ObsSystemTimeout(fileId)     => s"Observation of $fileId timed out on a subsystem"
    case ObsCommandTimeout(obsId)     => s"Observation command on ${obsId.name} timed out"
  }

}
