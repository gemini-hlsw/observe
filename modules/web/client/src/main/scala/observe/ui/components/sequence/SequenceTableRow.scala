// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import lucuma.core.model.sequence.*
import lucuma.core.model.sequence.gmos.*
import lucuma.ui.sequence.*

protected[sequence] case class SequenceTableRow(step: SequenceRow[DynamicConfig], index: StepIndex)
