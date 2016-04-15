package edu.gemini.seqexec.server

import scala.collection.immutable.{ListMap, Map}

/**
  * Created by jluhrs on 4/5/16.
  */

sealed trait ActionStatus {
  def getPhase: ActionStatus.Phase
  def setPhase(p: ActionStatus.Phase): ActionStatus
}

case class SimpleActionStatus(phase: ActionStatus.Phase) extends ActionStatus {
  override def getPhase: ActionStatus.Phase = phase
  override def setPhase(p: ActionStatus.Phase): ActionStatus = copy(phase = p)
}

sealed trait ComposedActionStatus extends ActionStatus {
  def getSubstatus(k: String): Option[ActionStatus]
  def setSubstatus(k: String, v: ActionStatus): ComposedActionStatus
}

case class ParallelActionStatus(phase: ActionStatus.Phase, substatuss: Map[String, ActionStatus]) extends ComposedActionStatus {
  override def getSubstatus(k: String): Option[ActionStatus] = substatuss.get(k)

  override def setSubstatus(k: String, v: ActionStatus): ActionStatus = copy(substatuss = substatuss.updated(k, v))

  override def getPhase: ActionStatus.Phase = phase

  override def setPhase(p: ActionStatus.Phase): ActionStatus = copy(phase = p)
}

case class SequentialActionStatus(phase: ActionStatus.Phase, substatuss: ListMap[String, ActionStatus]) extends ComposedActionStatus {
  override def getSubstatus(k: String): Option[ActionStatus] = substatuss.get(k)

  override def setSubstatus(k: String, v: ActionStatus): ActionStatus = copy(substatuss = substatuss.updated(k, v))

  override def getPhase: ActionStatus.Phase = phase

  override def setPhase(p: ActionStatus.Phase): ActionStatus = copy(phase = p)
}

object ActionStatus {

  sealed trait Phase

  case object Idle extends Phase

  case class Running(completion: Float) extends Phase

  case object Stopped extends Phase

  case class Failed(e: SeqexecFailure) extends Phase

  case class Completed[T](r: T) extends Phase

  val default = Idle

  trait Lens[S, A] {
    def get(s:S): A
    def set(s:S)(a:A): S = mod(s)(_ => a)
    def mod(s:S)(f: (A)=>A): S
  }

  object ActionStatusLens extends Lens[ActionStatus, ActionStatus.Phase] {
    override def get(s: ActionStatus): Phase = s.getPhase
    override def set(s: ActionStatus)(p: Phase): ActionStatus = s.setPhase(p)
    override def mod(s: ActionStatus)(f: (Phase) => Phase): ActionStatus = s.setPhase(f(s.getPhase))
  }

  trait MapLens[S, A] {
    def get(s:S): Option[A]
    def set(s:S)(a:A): S 
    def mod(s:S)(f: (A)=>A): S = get(s).map(x => set(s)(f(x))).getOrElse(s) 
  }
  
  case class ActionSubstatusLens(key: String) extends MapLens[ComposedActionStatus, ActionStatus]{
    override def get(s: ComposedActionStatus): Option[ActionStatus] = s.getSubstatus(key)
    override def set(s: ComposedActionStatus)(a: ActionStatus): ComposedActionStatus = s.setSubstatus(key, a)
    override def mod(s: ComposedActionStatus)(f: (ActionStatus) => ActionStatus): ComposedActionStatus =
      s.getSubstatus(key) match {
        case Some(v) => s.setSubstatus(key, f(v))
        case _ => s
      }
  }

  def composeSubstatusStatus(r: ActionSubstatusLens) : MapLens[ComposedActionStatus, ActionStatus.Phase] = new MapLens[ComposedActionStatus, ActionStatus.Phase] {
    override def get(s: ComposedActionStatus): Option[Phase] = r.get(s).map(ActionStatusLens.get(_))

    override def set(s: ComposedActionStatus)(a: Phase): ComposedActionStatus =
      r.get(s).map(x => r.set(s)(ActionStatusLens.set(x)(a))).getOrElse(s)
  }  

}


