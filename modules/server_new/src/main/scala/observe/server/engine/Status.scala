// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.engine

/**
 * Flag to indicate whether the global execution is `Running` or `Waiting`.
 */
enum Status:
  case Waiting, Completed, Running
