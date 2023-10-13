// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.data.NonEmptyList
import cats.syntax.option.*
import lucuma.core.util.Display
import lucuma.core.util.Enumerated

/** A Observe resource represents any system that can be only used by one single agent. */
enum Resource(val label: String) derives Enumerated:
  def tag: String = label

  case P1     extends Resource("P1")
  case OI     extends Resource("OI")
  case TCS    extends Resource("TCS")
  case Gcal   extends Resource("Gcal")
  case Gems   extends Resource("Gems")
  case Altair extends Resource("Altair")

  // Mount and science fold cannot be controlled independently. Maybe in the future.
  // For now, I replaced them with TCS
  //  case Mount extends Resource
  //  case ScienceFold extends Resource

object Resource:
  val Common: List[Resource] = List(TCS, Gcal)

  given Display[Resource] = Display.byShortName(_.label)
