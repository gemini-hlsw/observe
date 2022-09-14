// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.syntax.all._
import observe.model.enums.Resource

sealed trait SingleActionOp extends Product with Serializable {
  val sidName: Observation.IdName
  val stepId: StepId
  val resource: Resource
}

object SingleActionOp {
  final case class Started(sidName: Observation.IdName, stepId: StepId, resource: Resource)
      extends SingleActionOp
  final case class Completed(sidName: Observation.IdName, stepId: StepId, resource: Resource)
      extends SingleActionOp
  final case class Error(
    sidName:  Observation.IdName,
    stepId:   StepId,
    resource: Resource,
    msg:      String
  ) extends SingleActionOp

  implicit val equal: Eq[SingleActionOp] = Eq.instance {
    case (Started(a, c, e), Started(b, d, f))     => a === b && c === d && e === f
    case (Completed(a, c, e), Completed(b, d, f)) => a === b && c === d && e === f
    case (Error(a, c, e, g), Error(b, d, f, h))   => a === b && c === d && e === f && g === h
    case _                                        => false
  }
}
