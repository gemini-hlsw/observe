// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Step
import lucuma.core.util.NewType

object RecordedAtomId extends NewType[Atom.Id]
type RecordedAtomId = RecordedAtomId.Type

object RecordedStepId extends NewType[Step.Id]
type RecordedStepId = RecordedStepId.Type
