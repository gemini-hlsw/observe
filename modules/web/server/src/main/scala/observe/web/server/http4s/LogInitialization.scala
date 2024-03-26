// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package web.server.common

import cats.effect.Sync
import org.slf4j.bridge.SLF4JBridgeHandler
import org.typelevel.log4cats.Logger as TLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

trait LogInitialization:
  // Send logs from JULI (e.g. ocs) to SLF4J
  def setupLogger[F[_]: Sync]: F[TLogger[F]] = Sync[F].delay:
    LogManager.getLogManager.reset()
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()
    // Required to include debugging info, may affect performance though
    Logger.getGlobal.setLevel(Level.FINE)
    Slf4jLogger.getLoggerFromName[F]("observe")
