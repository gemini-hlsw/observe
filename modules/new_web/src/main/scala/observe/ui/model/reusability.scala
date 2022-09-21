// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import japgolly.scalajs.react.Reusability
import observe.model.ClientStatus
import japgolly.scalajs.react.ReactCats.*
import observe.model.enums.SequenceState
import lucuma.core.util.NewType
import observe.ui.model.enums.ObsClass

object reusability:
  given Reusability[ClientStatus]  = Reusability.byEq
  given Reusability[SequenceState] = Reusability.byEq
  given Reusability[TabOperations] = Reusability.byEq
  given Reusability[ObsClass]      = Reusability.byEq

  // TODO Move to lucuma-ui (and unify with explore)
  given reusabilityNewType[W, T <: NewType[W]#Type](using
    reusability: Reusability[W]
  ): Reusability[T] =
    reusability.asInstanceOf[Reusability[T]]
