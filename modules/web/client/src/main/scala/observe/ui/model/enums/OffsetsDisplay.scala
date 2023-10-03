// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.enums

import cats.Eq
import cats.derived.*

// Used to decide if the offsets are displayed
enum OffsetsDisplay derives Eq:
  case NoDisplay extends OffsetsDisplay
  case DisplayOffsets(offsetsWidth: Double, axisLabelWidth: Double, nsNodLabelWidth: Double)
      extends OffsetsDisplay
