// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Eq
import edu.gemini.observe.server.gems.ApdState
import edu.gemini.observe.server.gems.BinaryOnOff
import edu.gemini.observe.server.gems.ReadyState

package object gems {

  given Eq[ReadyState] = Eq.by(_.ordinal)

  given Eq[ApdState] = Eq.by(_.ordinal)

  given Eq[BinaryOnOff] = Eq.by(_.ordinal)

}
