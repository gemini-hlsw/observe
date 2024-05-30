// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import eu.timepit.refined.cats.given
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.Instrument

/** Metadata about the sequence required on the exit point */
case class SequenceMetadata(
  instrument: Instrument,
  observer:   Option[Observer],
  name:       NonEmptyString
) derives Eq
