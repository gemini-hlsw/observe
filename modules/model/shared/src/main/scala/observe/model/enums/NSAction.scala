// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

enum NsAction(val tag: String) derives Enumerated:
  case Start                extends NsAction("Start")
  case NodStart             extends NsAction("NodStart")
  case NodComplete          extends NsAction("NodComplete")
  case StageObserveStart    extends NsAction("StageObserveStart")
  case StageObserveComplete extends NsAction("StageObserveComplete")
  case Done                 extends NsAction("Done")
