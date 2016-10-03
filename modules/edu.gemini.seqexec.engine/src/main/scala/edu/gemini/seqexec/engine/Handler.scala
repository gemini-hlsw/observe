package edu.gemini.seqexec.engine

import Event._
import scalaz.Scalaz._
import scalaz.concurrent.Task
import scalaz.stream.Process

object Handler {

  /**
    * Main logical thread to handle events and produce output.
    */
  def handler(q: EventQueue): Process[Engine, QState] = {

    def handleUserEvent(ue: UserEvent): Engine[QState] = ue match {
      case Start => log("Output: Started") *> switch(q)(Status.Running)
      case Pause => log("Output: Paused") *> switch(q)(Status.Waiting)
      case AddExecution(pend) => log("Output: Adding Pending Execution") // TODO: Implement handler
      case Poll => log("Output: Polling current state")
      case Exit => log("Bye") *> close(q)
    }

    def handleSystemEvent(se: SystemEvent): Engine[QState] = se match {
      case (Completed(i, r)) => log("Output: Action completed") *> complete(i, r)
      case (Failed(i, e)) => log("Output: Action failed") *> fail(q)(i, e)
      case Executed => log("Output: Execution completed, launching next execution") *> next(q)
      // TODO: Closing to facilitate testing, in reality it shouldn't close
      case Finished => log("Output: Finished") *> close(q)
    }

    receive(q) >>= (
      ev => Process.eval(
        ev match {
          case EventUser(ue) => handleUserEvent(ue)
          case EventSystem(se) => handleSystemEvent(se)
        }
      )
    )
  }

  def run(q: EventQueue)(qs: QState): Task[QState] =
    handler(q).run.exec(qs)
}
