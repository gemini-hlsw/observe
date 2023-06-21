// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.Eq
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

  final case class OK[R <: RetVal](response: R)          extends Result
  final case class OKStopped[R <: RetVal](response: R)   extends Result
  final case class Partial[R <: PartialVal](response: R) extends Result
  final case class Paused(ctx: PauseContext)             extends Result
  final case class OKAborted[R <: RetVal](response: R)   extends Result
  // TODO: Replace the message by a richer Error type like `ObserveFailure`
  final case class Error(msg: String)                    extends Result {
    override val errMsg: Option[String] = msg.some
  }
  object Error {
    given Eq[Error] = Eq.by(_.msg)
  }

}
