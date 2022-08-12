// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq

object Observation {

  type Id = lucuma.core.model.Observation.Id

  type Name = String

  sealed case class IdName(id: Id, name: Name)

  object IdName {
    implicit val idNameEq: Eq[IdName] = Eq.by(x => (x.id, x.name))
  }

}
