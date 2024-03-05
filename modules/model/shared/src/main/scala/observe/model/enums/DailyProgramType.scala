// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.instances.string.*
import lucuma.core.enums.ProgramType
import lucuma.core.util.Enumerated

/**
 * Enumerated type for the subset of ProgramType allowed for daily science programs.
 * @group Enumerations
 */
enum DailyProgramType(toProgramType: ProgramType) derives Enumerated:
  export toProgramType.{abbreviation, tag}

  case CAL extends DailyProgramType(ProgramType.Calibration)
  case ENG extends DailyProgramType(ProgramType.Engineering)
