// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.Eq
import cats.derived.*
import cats.syntax.eq.*
import observe.model.ParamName
import lucuma.core.util.Enumerated

enum SystemName(val tag: String) derives Enumerated:
  case Ocs            extends SystemName("ocs")
  case Observe        extends SystemName("observe")
  case Instrument     extends SystemName("instrument")
  case Telescope      extends SystemName("telescope")
  case Gcal           extends SystemName("gcal")
  case Calibration    extends SystemName("calibration")
  case Meta           extends SystemName("meta")
  case AdaptiveOptics extends SystemName("adaptive optics")

  def withParam(p: ParamName): ParamName =
    ParamName(s"$tag:${p.value}")
