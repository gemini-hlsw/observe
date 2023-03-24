// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.effect.Sync
import cats.syntax.all._
import io.prometheus.client._
import lucuma.core.enums.Site
import observe.model.enum.Instrument

final case class ObserveMetrics private (
  site:           Site,
  private val qs: Gauge,  // Amount of items on the list of queues
  private val ss: Counter // Sequences started
)

object ObserveMetrics {
  private val prefix = "observe"

  def build[F[_]: Sync](site: Site, c: CollectorRegistry): F[ObserveMetrics] =
    Sync[F].delay(
      ObserveMetrics(
        site,
        qs = Gauge
          .build()
          .name(s"${prefix}_queue_size")
          .help("Queue Size.")
          .labelNames("site")
          .register(c),
        ss = Counter
          .build()
          .name(s"${prefix}_sequence_start")
          .help("Sequence started.")
          .labelNames("site", "instrument")
          .register(c)
      )
    )

  implicit class ObserveMetricsOps(val m: ObserveMetrics) extends AnyVal {

    def queueSize[F[_]: Sync](i: Int): F[ObserveMetrics] =
      Sync[F].delay {
        m.qs
          .labels(m.site.shortName)
          .set(i.toDouble)
        m
      }

    def startRunning[F[_]: Sync](i: Instrument): F[ObserveMetrics] =
      Sync[F].delay {
        m.ss
          .labels(m.site.shortName, i.show)
          .inc()
        m
      }

  }
}
