// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.*
import cats.data.StateT
import cats.effect.Concurrent
import cats.effect.std.Queue
import cats.syntax.all.*
import fs2.Stream
import lucuma.core.model.sequence.Step
import monocle.Optional
import mouse.boolean.*
import observe.model.Observation
import observe.model.SequenceState
import org.typelevel.log4cats.Logger

import EventResult.Outcome
import EventResult.SystemUpdate
import EventResult.UserCommandResponse
import Result.PartialVal
import Result.RetVal
import SystemEvent.*
import UserEvent.*
import Handle.given

class Engine[F[_]: MonadThrow: Logger, S, U] private (
  stateL:      Engine.State[F, S],
  streamQueue: Queue[F, Stream[F, Event[F, S, U]]],
  inputQueue:  Queue[F, Event[F, S, U]],
  atomLoad:    (Engine[F, S, U], Observation.Id) => Handle[F, S, Event[F, S, U], U],
  atomReload:  (
    Engine[F, S, U],
    Observation.Id,
    OnAtomReloadAction
  ) => Handle[F, S, Event[F, S, U], U]
) {
  val L: Logger[F] = Logger[F]

  type EngineEvent     = Event[F, S, U]
  type EngineUserEvent = UserEvent[F, S, U]
  type EngineHandle[A] = Handle[F, S, EngineEvent, A]
  object EngineHandle {
    def pure[A](a: A): EngineHandle[A] = a.pure[EngineHandle]

    def liftF[A](f: F[A]): EngineHandle[A] = Handle.liftF(f)

    val unit: EngineHandle[Unit] = Handle.unit

    def fromEventStream(p: Stream[F, EngineEvent]): EngineHandle[Unit] = Handle.fromEventStream(p)

    def fromSingleEvent(f: F[EngineEvent]): EngineHandle[Unit] = Handle.fromSingleEvent(f)

    val getState: EngineHandle[S] = Handle.getState

    def inspectState[A](f: S => A): EngineHandle[A] = Handle.inspectState(f)

    def modifyStateF[A](f: S => F[(S, A)]): EngineHandle[A] = Handle.modifyStateF(f)

    def modifyState[A](f: S => (S, A)): EngineHandle[A] = Handle.modifyState(f)

    def modifyState_(f: S => S): EngineHandle[Unit] = Handle.modifyState_(f)

    def getSequenceState(id: Observation.Id): EngineHandle[Option[Sequence.State[F]]] =
      inspectState(stateL.sequenceStateIndex(id).getOption(_))

    def inspectSequenceState[A](id: Observation.Id)(
      f: Sequence.State[F] => A
    ): EngineHandle[Option[A]] =
      inspectState(stateL.sequenceStateIndex(id).getOption(_).map(f))

    def modifySequenceState(id: Observation.Id)(
      f: Sequence.State[F] => Sequence.State[F]
    ): EngineHandle[Unit] =
      modifyState_(stateL.sequenceStateIndex(id).modify(f))

    def replaceSequenceState(id: Observation.Id)(s: Sequence.State[F]): EngineHandle[Unit] =
      modifyState_(stateL.sequenceStateIndex(id).replace(s))

    // For debugging
    def printSequenceState(id: Observation.Id): EngineHandle[Unit] =
      inspectSequenceState(id)((qs: Sequence.State[F]) => StateT.liftF(L.debug(s"$qs"))).void

    extension [A](f: S => (S, A)) {
      def toHandle: EngineHandle[A] =
        Handle.fromStateT(StateT[F, S, A](st => f(st).pure[F]))
    }

    // extension [F[_]: Applicative, A](f: EngineState[F] => F[(EngineState[F], A)]) {
    //   def toHandleF[B >: A]: HandlerType[F, B] = // Type trick to allow unions of a supertype
    //     StateT[F, EngineState[F], B](st => f(st).map(x => x: (EngineState[F], B)))
    //       .toHandleT[EventType[F]]
    // }
  }

  /**
   * Changes the `Status` and returns the new `Queue.State`.
   */
  private def switch(id: Observation.Id)(st: SequenceState): EngineHandle[Unit] =
    EngineHandle.modifySequenceState(id)(Sequence.State.status.replace(st))

  def start(id: Observation.Id): Handle[F, S, Event[F, S, U], Unit] =
    EngineHandle.getSequenceState(id).flatMap {
      case Some(seq) =>
        {
          EngineHandle.replaceSequenceState(id)(
            Sequence.State.status.replace(
              SequenceState.Running(
                userStop = false,
                internalStop = false,
                waitingUserPrompt = false,
                waitingNextAtom = true,
                starting = true
              )
            )(
              seq.rollback
            )
          ) *> send(Event.modifyState(atomReload(this, id, OnAtomReloadAction.StartNewAtom)))
        }.whenA(seq.status.isIdle || seq.status.isError)
      case None      => Handle.unit
    }

  def pause(id: Observation.Id): EngineHandle[Unit] =
    EngineHandle.modifySequenceState(id)(Sequence.State.userStopSet(true))

  private def cancelPause(id: Observation.Id): EngineHandle[Unit] =
    EngineHandle.modifySequenceState(id)(Sequence.State.userStopSet(false))

  def startSingle(c: ActionCoords): EngineHandle[Outcome] =
    EngineHandle.getState.flatMap { st =>
      val x: Option[Stream[F, Result]] =
        for
          seq <- stateL.sequenceStateIndex(c.obsId).getOption(st)
          if (seq.status.isIdle || seq.status.isError) && !seq.getSingleState(c.actCoords).active
          act <- seq.rollback.getSingleAction(c.actCoords)
        yield act.gen

      x.map { p =>
        EngineHandle.modifySequenceState(c.obsId)(u => u.startSingle(c.actCoords)) *>
          Handle
            .fromEventStream[F, S, EngineEvent](
              p.attempt.flatMap {
                case Right(r @ Result.OK(_))    =>
                  Stream.emit(Event.singleRunCompleted(c, r))
                case Right(e @ Result.Error(_)) =>
                  Stream.emit(Event.singleRunFailed(c, e))
                case Right(r)                   =>
                  Stream.emit(
                    Event.singleRunFailed(
                      c,
                      Result.Error(s"Unhandled result for single run action: $r")
                    )
                  )
                case Left(t: Throwable)         => Stream.raiseError[F](t)
              }
            )
            .as[Outcome](Outcome.Ok)
      }.getOrElse(EngineHandle.pure[Outcome](Outcome.Failure))

    }

  private def completeSingleRun[V <: RetVal](c: ActionCoords, r: V): EngineHandle[Unit] =
    EngineHandle.modifySequenceState(c.obsId)(_.completeSingle(c.actCoords, r))

  private def failSingleRun(c: ActionCoords, e: Result.Error): EngineHandle[Unit] =
    EngineHandle.modifySequenceState(c.obsId)(_.failSingle(c.actCoords, e))

  /**
   * Tells if a sequence can be safely removed
   */
  def canUnload(id: Observation.Id)(st: S): Boolean =
    stateL.sequenceStateIndex(id).getOption(st).forall(canUnload)

  def canUnload(seq: Sequence.State[F]): Boolean = Sequence.State.canUnload(seq)

  /**
   * Refresh the steps executions of an existing sequence. Does not add nor remove steps.
   * @param id
   *   sequence identifier
   * @param steps
   *   List of new steps definitions
   * @return
   */
  def update(id: Observation.Id, steps: List[EngineStep[F]]): Endo[S] =
    stateL.sequenceStateIndex(id).modify(_.update(steps.map(_.executions)))

  def updateSteps(steps: List[EngineStep[F]]): Endo[Sequence.State[F]] =
    _.update(steps.map(_.executions))

  /**
   * Adds the current `Execution` to the completed `Queue`, makes the next pending `Execution` the
   * current one, and initiates the actual execution.
   *
   * If there are no more pending `Execution`s, it emits the `Finished` event.
   */
  private def next(id: Observation.Id): EngineHandle[Unit] =
    EngineHandle
      .getSequenceState(id)
      .flatMap(
        _.map { seq =>
          seq.status match {
            case SequenceState.Running(userStop, internalStop, _, _, _) =>
              seq.next match {
                // Empty state
                case None                                  =>
                  send(Event.finished(id))
                // Final State
                case Some(qs @ Sequence.State.Final(_, _)) =>
                  EngineHandle.replaceSequenceState(id)(qs) *> switch(id)(
                    SequenceState.Running(
                      userStop,
                      internalStop,
                      waitingUserPrompt = true,
                      waitingNextAtom = true,
                      starting = false
                    )
                  ) *> send(Event.modifyState(atomLoad(this, id)))
                // Step execution completed. Check requested stop and breakpoint here.
                case Some(qs)                              =>
                  EngineHandle.replaceSequenceState(id)(qs) *>
                    (if (
                       qs.getCurrentBreakpoint && !qs.current.execution.exists(_.uninterruptible)
                     ) {
                       switch(id)(SequenceState.Idle) *> send(Event.breakpointReached(id))
                     } else if (seq.isLastAction) {
                       // Only process stop states after the last action of the step.
                       if (userStop || internalStop) {
                         if (qs.current.execution.exists(_.uninterruptible))
                           send(Event.executing(id)) *> send(Event.stepComplete(id))
                         else
                           switch(id)(SequenceState.Idle) *> send(Event.sequencePaused(id))
                       } else {
                         // after the last action of the step, we need to reload the sequence
                         switch(id)(
                           SequenceState.Running(
                             userStop,
                             internalStop,
                             waitingUserPrompt = false,
                             waitingNextAtom = true,
                             starting = false
                           )
                         ) *> send(
                           Event.modifyState(atomReload(this, id, OnAtomReloadAction.StartNewAtom))
                         )
                           *> send(Event.stepComplete(id))
                       }
                     } else send(Event.executing(id)))
              }
            case _                                                      => EngineHandle.unit
          }
        }.getOrElse(EngineHandle.unit)
      )

  def startNewAtom(id: Observation.Id): EngineHandle[Unit] =
    EngineHandle
      .getSequenceState(id)
      .flatMap(
        _.map { seq =>
          seq.status match {
            case SequenceState.Running(userStop, internalStop, _, true, isStarting) =>
              if (!isStarting && (userStop || internalStop)) {
                seq match {
                  // Final State
                  case Sequence.State.Final[F](_, _) => send(Event.finished(id))
                  // Execution completed
                  case _                             => switch(id)(SequenceState.Idle)
                }
              } else {
                seq match {
                  // Final State
                  case Sequence.State.Final[F](_, _) => send(Event.finished(id))
                  // Execution completed. Check breakpoint here
                  case _                             =>
                    if (!isStarting && seq.getCurrentBreakpoint) {
                      switch(id)(SequenceState.Idle) *> send(Event.breakpointReached(id))
                    } else
                      switch(id)(
                        SequenceState.Running(userStop, internalStop, false, false, false)
                      ) *>
                        send(Event.executing(id))
                }
              }
            case _                                                                  => EngineHandle.unit
          }
        }.getOrElse(EngineHandle.unit)
      )

  /**
   * Executes all actions in the `Current` `Execution` in parallel. When all are done it emits the
   * `Executed` event. It also updates the `State` as needed.
   */
  // Send the expected event when the `Action` is executed
  // It doesn't catch run time exceptions. If desired, the Action has to do it itself.
  private def act(
    id:     Observation.Id,
    stepId: Step.Id,
    t:      (Stream[F, Result], Int)
  ): Stream[F, EngineEvent] = t match {
    case (gen, i) =>
      gen
        .takeThrough:
          case Result.Partial(_) => true
          case _                 => false
        .attempt
        .flatMap:
          case Right(r @ Result.OK(_))        => Stream.emit(Event.completed(id, stepId, i, r))
          case Right(r @ Result.OKStopped(_)) => Stream.emit(Event.stopCompleted(id, stepId, i, r))
          case Right(r @ Result.OKAborted(_)) => Stream.emit(Event.aborted(id, stepId, i, r))
          case Right(r @ Result.Partial(_))   => Stream.emit(Event.partial(id, stepId, i, r))
          case Right(e @ Result.Error(_))     => Stream.emit(Event.failed(id, i, e))
          case Right(r @ Result.Paused(_))    => Stream.emit(Event.paused(id, i, r))
          case Left(t: Throwable)             => Stream.raiseError[F](t)
  }

  private def execute(id: Observation.Id)(using ev: Concurrent[F]): EngineHandle[Unit] =
    EngineHandle.getState.flatMap(st =>
      stateL
        .sequenceStateIndex(id)
        .getOption(st)
        .map {
          case seq @ Sequence.State.Final(_, _)     =>
            // The sequence is marked as completed here
            EngineHandle.replaceSequenceState(id)(seq) *> send(Event.finished(id))
          case seq @ Sequence.State.Zipper(z, _, _) =>
            val stepId                          = z.focus.toStep.id
            val u: List[Stream[F, EngineEvent]] =
              seq.current.actions
                .map(_.gen)
                .zipWithIndex
                .map(act(id, stepId, _))
            val v: Stream[F, EngineEvent]       = Stream.emits(u).parJoin(u.length)
            val w: List[EngineHandle[Unit]]     =
              seq.current.actions.indices
                .map(i => EngineHandle.modifySequenceState(id)(_.start(i)))
                .toList
            w.sequence *> Handle.fromEventStream(v)
        }
        .getOrElse(EngineHandle.unit)
    )

  private def getState(f: S => Option[Stream[F, EngineEvent]]): EngineHandle[Unit] =
    EngineHandle.getState.flatMap(s =>
      Handle[F, S, EngineEvent, Unit](f(s).pure[StateT[F, S, *]].map(((), _)))
    )

  private def actionStop(
    id: Observation.Id,
    f:  S => Option[Stream[F, EngineEvent]]
  ): EngineHandle[Unit] =
    EngineHandle
      .getSequenceState(id)
      .flatMap(_.map { s =>
        (Handle(
          StateT[F, S, (Unit, Option[Stream[F, EngineEvent]])](st => ((st, ((), f(st)))).pure[F])
        ) *>
          EngineHandle.modifySequenceState(id)(Sequence.State.internalStopSet(true)))
          .whenA(Sequence.State.isRunning(s))
      }.getOrElse(EngineHandle.unit))

  /**
   * Given the index of the completed `Action` in the current `Execution`, it marks the `Action` as
   * completed and returns the new updated `State`.
   *
   * When the index doesn't exist it does nothing.
   */
  private def complete[R <: RetVal](
    id: Observation.Id,
    i:  Int,
    r:  Result.OK[R]
  ): EngineHandle[Unit] =
    EngineHandle.modifySequenceState(id)(_.mark(i)(r)) *>
      EngineHandle
        .getSequenceState(id)
        .flatMap(
          _.flatMap(
            _.current.execution
              .forall(Action.completed)
              .option(Handle.fromEventStream[F, S, EngineEvent](Stream(Event.executed(id))))
          ).getOrElse(EngineHandle.unit)
        )

  private def stopComplete[R <: RetVal](
    id: Observation.Id,
    i:  Int,
    r:  Result.OKStopped[R]
  ): EngineHandle[Unit] = EngineHandle.modifySequenceState(id)(_.mark(i)(r)) *>
    EngineHandle
      .getSequenceState(id)
      .flatMap(
        _.flatMap(
          _.current.execution
            .forall(Action.completed)
            .option(Handle.fromEventStream[F, S, EngineEvent](Stream(Event.executed(id))))
        ).getOrElse(EngineHandle.unit)
      )

  private def abort[R <: RetVal](
    id: Observation.Id,
    i:  Int,
    r:  Result.OKAborted[R]
  ): EngineHandle[Unit] =
    EngineHandle.modifySequenceState(id)(_.mark(i)(r)) *>
      switch(id)(SequenceState.Aborted)

  private def partialResult[R <: PartialVal](
    id: Observation.Id,
    i:  Int,
    p:  Result.Partial[R]
  ): EngineHandle[Unit] =
    EngineHandle.modifySequenceState(id)(_.mark(i)(p))

  def actionPause(id: Observation.Id, i: Int, p: Result.Paused): EngineHandle[Unit] =
    EngineHandle.modifySequenceState(id)(s => Sequence.State.internalStopSet(false)(s).mark(i)(p))

  private def actionResume(
    id:   Observation.Id,
    i:    Int,
    cont: Stream[F, Result]
  ): EngineHandle[Unit] =
    EngineHandle
      .getSequenceState(id)
      .flatMap(_.collect {
        case s @ Sequence.State.Zipper(z, _, _)
            if Sequence.State.isRunning(s) && s.current.execution.lift(i).exists(Action.paused) =>
          EngineHandle.modifySequenceState(id)(_.start(i)) *> Handle.fromEventStream(
            act(id, z.focus.toStep.id, (cont, i))
          )
      }.getOrElse(EngineHandle.unit))

  /**
   * For now it only changes the `Status` to `Paused` and returns the new `State`. In the future
   * this function should handle the failed action.
   */
  private def fail(id: Observation.Id)(i: Int, e: Result.Error): EngineHandle[Unit] =
    EngineHandle.modifySequenceState(id)(_.mark(i)(e)) *>
      switch(id)(SequenceState.Failed(e.msg))

  private def logError(e: Result.Error): EngineHandle[Unit] = error(e.errMsg.getOrElse(e.msg))

  /**
   * Log info lifted into Handle.
   */
  private def info(msg: => String): EngineHandle[Unit] = EngineHandle.liftF(L.info(msg))

  /**
   * Log warning lifted into Handle.
   */
  private def warning(msg: => String): EngineHandle[Unit] = EngineHandle.liftF(L.warn(msg))

  /**
   * Log debug lifted into Handle.
   */
  private def debug(msg: => String): EngineHandle[Unit] = EngineHandle.liftF(L.debug(msg))

  /**
   * Log error lifted into Handle
   */
  private def error(msg: => String): EngineHandle[Unit] = EngineHandle.liftF(L.error(msg))

  /**
   * Enqueue `Event` in the Handle.
   */
  private def send(ev: EngineEvent): EngineHandle[Unit] = Handle.fromEventStream(Stream(ev))

  private def handleUserEvent(ue: EngineUserEvent): EngineHandle[EventResult[U]] = ue match {
    case Start(id, _, _)             =>
      debug(s"Engine: Start requested for sequence $id") *> start(id) *>
        EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, None))
    case Pause(id, _)                =>
      debug(s"Engine: Pause requested for sequence $id") *> pause(id) *>
        EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, None))
    case CancelPause(id, _)          =>
      debug(s"Engine: Pause canceled for sequence $id") *> cancelPause(id) *>
        EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, None))
    case Breakpoints(id, _, step, v) =>
      debug(s"Engine: breakpoints changed for sequence $id and step $step to $v") *>
        EngineHandle.modifySequenceState(id)(_.setBreakpoints(step, v)) *>
        EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, None))
    case Poll(_)                     =>
      debug("Engine: Polling current state") *>
        EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, None))
    case GetState(f)                 =>
      getState(f) *> EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, None))
    case ModifyState(f)              =>
      summon[Monad[EngineHandle]].map(f)((r: U) =>
        UserCommandResponse[F, U](ue, Outcome.Ok, Some(r))
      )
    case ActionStop(id, f)           =>
      debug("Engine: Action stop requested") *> actionStop(id, f) *>
        EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, None))
    case ActionResume(id, i, cont)   =>
      debug("Engine: Action resume requested") *> actionResume(id, i, cont) *>
        EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, None))
    case LogDebug(msg, _)            =>
      debug(msg) *> EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, None))
    case LogInfo(msg, _)             =>
      info(msg) *> EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, None))
    case LogWarning(msg, _)          =>
      warning(msg) *> EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, None))
    case LogError(msg, _)            =>
      error(msg) *> EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, None))
    case Pure(v)                     =>
      EngineHandle.pure(UserCommandResponse(ue, Outcome.Ok, v.some))
  }

  private def handleSystemEvent(
    se: SystemEvent
  )(using ci: Concurrent[F]): EngineHandle[EventResult[U]] = se match {
    case Completed(id, _, i, r)     =>
      debug(s"Engine: From sequence $id: Action completed ($r)") *>
        complete(id, i, r) *>
        EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case StopCompleted(id, _, i, r) =>
      debug(s"Engine: From sequence $id: Action completed with stop ($r)") *>
        stopComplete(id, i, r) *>
        EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case Aborted(id, _, i, r)       =>
      debug(s"Engine: From sequence $id: Action completed with abort ($r)") *> abort(id, i, r) *>
        EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case PartialResult(id, _, i, r) =>
      debug(s"Engine: From sequence $id: Partial result ($r)") *>
        partialResult(id, i, r) *>
        EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case Paused(id, i, r)           =>
      debug("Engine: Action paused") *>
        actionPause(id, i, r) *> EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case Failed(id, i, e)           =>
      logError(e) *> fail(id)(i, e) *>
        EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case Busy(id, _)                =>
      warning(
        s"Cannot run sequence $id " +
          s"because " +
          s"required systems are in use."
      ) *> EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case BreakpointReached(obsId)   =>
      debug(s"Engine: Breakpoint reached in observation [$obsId]") *>
        EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case Executed(id)               =>
      debug(s"Engine: Execution $id completed") *>
        next(id) *> EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case Executing(id)              =>
      debug("Engine: Executing") *>
        execute(id) *> EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case StepComplete(obsId)        =>
      debug(s"Engine: Step completed for observation [$obsId]") *>
        EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case SequencePaused(obsId)      =>
      debug(s"Engine: Sequence paused for observation [$obsId]") *>
        EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case SequenceComplete(id)       =>
      debug("Engine: Finished") *>
        switch(id)(SequenceState.Completed) *> EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case SingleRunCompleted(c, r)   =>
      debug(s"Engine: single action $c completed with result $r") *>
        completeSingleRun(c, r.response) *> EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case SingleRunFailed(c, e)      =>
      debug(s"Engine: single action $c failed with error $e") *>
        failSingleRun(c, e) *> EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
    case Null                       => EngineHandle.pure(SystemUpdate(se, Outcome.Ok))
  }

  /**
   * Main logical thread to handle events and produce output.
   */
  private def run(
    onSystemEvent: PartialFunction[SystemEvent, EngineHandle[Unit]]
  )(ev: EngineEvent)(using Concurrent[F]): EngineHandle[EventResult[U]] =
    ev match
      case Event.EventUser(ue)   => handleUserEvent(ue)
      case Event.EventSystem(se) =>
        handleSystemEvent(se).flatMap: (r: EventResult[U]) =>
          onSystemEvent.applyOrElse(se, (_: SystemEvent) => EngineHandle.unit).as(r)

  /** Traverse a process with a stateful computation. */
  // input, stream of events
  // initalState: state
  // f takes an event and the current state, it produces a new state, a new value B and more actions
  def mapEvalState(
    initialState: S,
    f:            (EngineEvent, S) => F[(S, (EventResult[U], S), Option[Stream[F, EngineEvent]])]
  )(using ev: Concurrent[F]): Stream[F, (EventResult[U], S)] =
    Stream.exec(streamQueue.offer(Stream.fromQueueUnterminated(inputQueue))) ++
      Stream
        .fromQueueUnterminated(streamQueue)
        .parJoinUnbounded
        .evalMapAccumulate(initialState) { (s, a) =>
          f(a, s).flatMap {
            case (ns, b, None)     => (ns, b).pure[F]
            case (ns, b, Some(st)) => streamQueue.offer(st) >> (ns, b).pure[F]
          }
        }
        .map(_._2)

  private def runE(
    onSystemEvent: PartialFunction[SystemEvent, EngineHandle[Unit]]
  )(ev: EngineEvent, s: S)(using
    ci:            Concurrent[F]
  ): F[(S, (EventResult[U], S), Option[Stream[F, EngineEvent]])] =
    run(onSystemEvent)(ev).run.run(s).map { case (si, (r, p)) =>
      (si, (r, si), p)
    }

  // Only used for testing.
  def process(
    onSystemEvent: PartialFunction[SystemEvent, Handle[F, S, Event[F, S, U], Unit]]
  )(s0: S)(using
    ev:            Concurrent[F]
  ): Stream[F, (EventResult[U], S)] =
    mapEvalState(s0, runE(onSystemEvent)(_, _))

  def offer(in: Event[F, S, U]): F[Unit] = inputQueue.offer(in)

  def inject(f: F[Event[F, S, U]]): F[Unit] = streamQueue.offer(Stream.eval(f))

}

object Engine {

  trait State[F[_], D] {
    def sequenceStateIndex(sid: Observation.Id): Optional[D, Sequence.State[F]]
  }

  trait Types[S, E] {
    type StateType = S
    type EventData = E
  }

  def build[F[_]: Concurrent: Logger, S, U](
    stateL:         State[F, S],
    loadNextAtom:   (Engine[F, S, U], Observation.Id) => Handle[F, S, Event[F, S, U], U],
    reloadNextAtom: (
      Engine[F, S, U],
      Observation.Id,
      OnAtomReloadAction
    ) => Handle[F, S, Event[F, S, U], U]
  ): F[Engine[F, S, U]] = for {
    sq <- Queue.unbounded[F, Stream[F, Event[F, S, U]]]
    iq <- Queue.unbounded[F, Event[F, S, U]]
  } yield new Engine(stateL, sq, iq, loadNextAtom, reloadNextAtom)

  /**
   * Builds the initial state of a sequence
   */
  def load[F[_]](seq: Sequence[F]): Sequence.State[F] = Sequence.State.init(seq)

  /**
   * Redefines an existing sequence. Changes the step actions, removes steps, adds new steps.
   */
  def reload[F[_]](seq: Sequence.State[F], steps: List[EngineStep[F]]): Sequence.State[F] =
    Sequence.State.reload(steps, seq)

}
