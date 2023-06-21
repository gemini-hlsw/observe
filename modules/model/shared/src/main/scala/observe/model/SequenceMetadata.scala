// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import observe.model.enums.Instrument

/** Metadata about the sequence required on the exit point */
final case class SequenceMetadata(
  instrument: Instrument,
  observer:   Option[Observer],
  name:       String
)

object SequenceMetadata {
  given Eq[SequenceMetadata] =
    Eq.by(x => (x.instrument, x.observer, x.name))
}
