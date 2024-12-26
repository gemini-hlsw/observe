// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe

import _root_.cats.Endo
import _root_.cats.Monoid
import _root_.cats.MonoidK

object cats:
  given [A]: Monoid[Endo[A]] = MonoidK[Endo].algebra[A]
