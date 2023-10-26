// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.Eq
import cats.derived.*
import cats.syntax.all.*

/**
 * The result of an `Action`.
 */
sealed trait Result extends Product with Serializable {
  val errMsg: Option[String] = None
}

object Result {

  // Base traits for results. They make harder to pass the wrong value.
  trait RetVal
  trait PartialVal
  trait PauseContext

  case class OK[R <: RetVal](response: R)          extends Result
  case class OKStopped[R <: RetVal](response: R)   extends Result
  case class Partial[R <: PartialVal](response: R) extends Result
  case class Paused(ctx: PauseContext)             extends Result
  case class OKAborted[R <: RetVal](response: R)   extends Result
  // TODO: Replace the message by a richer Error type like `ObserveFailure`
  case class Error(msg: String)                    extends Result derives Eq {
    override val errMsg: Option[String] = msg.some
  }

}
