package edu.gemini.seqexec.server

import edu.gemini.pot.sp.SPObservationID
import edu.gemini.spModel.`type`.{DisplayableSpType, LoggableSpType, SequenceableSpType}
import edu.gemini.spModel.config2.{ConfigSequence, ItemKey}
import edu.gemini.spModel.core.Peer

import scalaz.Scalaz._
import scalaz._

sealed trait CommandError {
  val msg: String
}
case class BadParameter(msg: String) extends CommandError
case class ObsIdNotFound(msg: String) extends CommandError
case class SeqexecFailureError(e: SeqexecFailure) extends CommandError {
  val msg = SeqexecFailure.explain(e)
}

case class CommandResponse(msg: String, keys: List[(String, String)], steps: List[String])

object CommandResponse {
  def apply(msg: String): CommandResponse = CommandResponse(msg, Nil, Nil)
}

sealed trait Commands {
  import Commands.CommandResult

  def host(): CommandResult
  def host(s: String): CommandResult
  def showCount(obsId: String): CommandResult
  def showStatic(obsId: String): CommandResult
  def showStatic(obsId: String, system: String): CommandResult
  def showDynamic(obsId: String, step: String): CommandResult
  def showDynamic(obsId: String, step: String, system: String): CommandResult
  def run(obsId: String): CommandResult
  def stop(obsId: String): CommandResult
  def continue(obsId: String): CommandResult
  def state(obsId: String): CommandResult

}

object Commands {
  type CommandResult = CommandError \/ CommandResponse

  val Usage =
    "Usage: seq host [host:port] | show obsId count|static|dynamic | run obsId"
  val ShowUsage =
    """Usage:
      |  seq show obsId count
      |  seq show obsId static [calibration|instrument|telescope ...]
      |  seq show obsId dynamic step# [calibration|instrument|telescope ...]
      |  seq run obsId
      |  seq stop obsId
      |  seq continue obsId
      |  seq status obsId
      |      |
      |  Example
      |    seq show GS-2014B-Q-2-355 static
      |    -> shows all static values for observation 355
      |
      |    seq show GS-2014B-Q-2-355 static instrument
      |    -> shows all static instrument values for observation 355
      |
      |    seq show GS-2014B-Q-2-355 dynamic 4 instrument
      |    -> shows dynamic instrument values for dataset 4 of obs 355
    """.stripMargin

  def apply(): Commands = new Commands {

    override def host(): CommandResult =
      \/.right(CommandResponse(s"Default seq host set to ${ODBProxy.host().host} ${ODBProxy.host().port}"))

    override def host(peer: String): CommandResult =
      parseLoc(peer).flatMap { h =>
        ODBProxy.host(h)
        host()
      }

    override def showCount(obsId: String): CommandResult =
      for {
        oid <- parseId(obsId)
        seq <- ODBProxy.read(oid).leftMap(SeqexecFailureError.apply)
      } yield CommandResponse(s"$oid sequence has ${seq.size()} steps.")

    override def showStatic(obsId: String): CommandResult =
      for {
        oid <- parseId(obsId)
        seq <- ODBProxy.read(oid).leftMap(SeqexecFailureError.apply)
      } yield CommandResponse(s"$oid Static Values", keys(seq, 0, seq.getStaticKeys), Nil)

    override def showStatic(obsId: String, system: String): CommandResult =
      for {
        oid <- parseId(obsId)
        seq <- ODBProxy.read(oid).leftMap(SeqexecFailureError.apply)
        ks  = seq.getStaticKeys.filter(sysFilter(system))
      } yield CommandResponse(s"$oid Static Values ($system only)", keys(seq, 0, ks), Nil)

    override def showDynamic(obsId: String, step: String): CommandResult =
      for {
        oid <- parseId(obsId)
        seq <- ODBProxy.read(oid).leftMap(SeqexecFailureError.apply)
        s   <- ifStepValid(oid, seq, step)
      } yield CommandResponse(s"$oid Dynamic Values (Step ${s + 1})", keys(seq, s, seq.getIteratedKeys), Nil)

    override def showDynamic(obsId: String, step: String, system: String): CommandResult =
      for {
        oid <- parseId(obsId)
        seq <- ODBProxy.read(oid).leftMap(SeqexecFailureError.apply)
        s   <- ifStepValid(oid, seq, step)
        ks  = seq.getStaticKeys.filter(sysFilter(system))
      } yield CommandResponse(s"$oid Dynamic Values (Step ${s + 1}, $system only)", keys(seq, s, ks), Nil)

    override def run(obsId: String): CommandResult = ???

    override def stop(obsId: String): CommandResult = ???

    override def continue(obsId: String): CommandResult = ???

    override def state(obsId: String): CommandResult = ???

    def seqValue(o: Object): String = o match {
      case s: SequenceableSpType => s.sequenceValue()
      case d: DisplayableSpType  => d.displayValue()
      case l: LoggableSpType     => l.logValue()
      case _                     => o.toString
    }

    def keys(cs: ConfigSequence, step: Int, ks: Array[ItemKey]): List[(String, String)] = {
      ks.sortWith((u, v) => u.compareTo(v) < 0).map { k =>
        k.getPath -> seqValue(cs.getItemValue(step, k))
      }.toList
    }

    def sysFilter(system: String): ItemKey => Boolean =
      _.splitPath().get(0) == system


    def ifStepValid(oid: SPObservationID, cs: ConfigSequence, step: String): CommandError \/ Int =
      \/.fromTryCatchNonFatal(step.toInt - 1).fold(
      _ => \/.left(BadParameter(s"Specify an integer step, not '$step'.")), {
        case i if i < 0          => \/.left(BadParameter("Specify a positive step number."))
        case i if i >= cs.size() => \/.left(BadParameter(s"$oid only has ${cs.size} steps."))
        case i                   => \/.right(i)
      })
  }

  def parseId(s: String): CommandError \/ SPObservationID =
    \/.fromTryCatchNonFatal {
      new SPObservationID(s)
    }.leftMap(_ => BadParameter(s"Sorry, '$s' isn't a valid observation id."))

  def parseLoc(s: String): CommandError \/ Peer =
    Option(Peer.tryParse(s)) \/> BadParameter(s"Sorry, expecting host:port not '$s'.")

}
