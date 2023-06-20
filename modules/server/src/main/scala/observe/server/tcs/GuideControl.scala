// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import observe.server.EpicsCommand
import observe.server.tcs.TcsController._
import observe.server.tcs.TcsEpics.ProbeFollowCmd
import observe.server.tcs.TcsEpics.ProbeGuideCmd

final case class GuideControl[F[_]](
  subs:            Subsystem,
  parkCmd:         EpicsCommand[F],
  nodChopGuideCmd: ProbeGuideCmd[F],
  followCmd:       ProbeFollowCmd[F]
)
