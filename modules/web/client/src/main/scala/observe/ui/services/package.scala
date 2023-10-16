// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.MonadThrow

protected def NotAuthorized[F[_]: MonadThrow, A]: F[A] =
  MonadThrow[F].raiseError(new RuntimeException("Not authorized"))
