// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*

case class UserDetails(username: String, displayName: String) derives Eq

object UserDetails:
  // Some useful type aliases for user elements
  type UID         = String
  type DisplayName = String
  type Groups      = List[String]
  type Thumbnail   = Array[Byte]
