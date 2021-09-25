// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Order
import cats.Show
import cats.Eq
import lucuma.core.math.Index

object Observation {

  type Id = lucuma.core.model.Observation.Id

  /** An observation is identified by its program and a serial index. */
  final case class Name(pid: Program.Id, index: Index) {
    def format: String =
      s"${ProgramId.fromString.reverseGet(pid)}-${Index.fromString.reverseGet(index)}"
  }
  object Name                                          {

    def fromString(s: String): Option[Observation.Name] =
      s.lastIndexOf('-') match {
        case -1 => None
        case n  =>
          val (a, b) = s.splitAt(n)
          Index.fromString.getOption(b.drop(1)).flatMap { i =>
            Program.Id.fromString.getOption(a).map(Observation.Name(_, i))
          }
      }

    def unsafeFromString(s: String): Observation.Name =
      fromString(s).getOrElse(sys.error("Malformed Observation.Name: " + s))

    /** Observations are ordered by program id and index. */
    implicit val OrderName: Order[Name]                  =
      Order.by(a => (a.pid, a.index))

    implicit val OrderingName: scala.math.Ordering[Name] =
      OrderName.toOrdering

    implicit val showId: Show[Name]                      =
      Show.fromToString

  }

  sealed case class IdName(id: Id, name: Name)

  object IdName {
    implicit val idNameEq: Eq[IdName] = Eq.by(x => (x.id, x.name))
  }

}
