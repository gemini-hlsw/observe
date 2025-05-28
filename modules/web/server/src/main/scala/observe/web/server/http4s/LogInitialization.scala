// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.effect.Sync
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.util.StatusPrinter
import org.slf4j.LoggerFactory
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
    Logger.getGlobal.setLevel(Level.ALL)
    StatusPrinter.print(LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext])
    Slf4jLogger.getLoggerFromName[F]("observe")
