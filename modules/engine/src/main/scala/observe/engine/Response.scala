// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import lucuma.core.enums.Instrument
import observe.engine.Result.RetVal
import observe.model.dhs.ImageFileId
import observe.model.enums.Resource

sealed trait Response extends RetVal with Product with Serializable

object Response:

  case class Configured(resource: Resource | Instrument) extends Response

  case class Observed(fileId: ImageFileId) extends Response

  case class Aborted(fileId: ImageFileId) extends Response

  case object Ignored extends Response
