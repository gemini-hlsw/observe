// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.Monad
import cats.effect.Clock
import cats.effect.Ref
import cats.syntax.all.*
import observe.model.ClientId
import org.http4s.headers.`User-Agent`
import org.typelevel.log4cats.Logger

import java.time.Instant

trait ClientsSetDb[F[_]] {
  def newClient(
    id:   ClientId,
    addr: ClientsSetDb.ClientAddr,
    ua:   Option[ClientsSetDb.UserAgent]
  ): F[Unit]
  def removeClient(id: ClientId): F[Unit]
  def report: F[Unit]
}

object ClientsSetDb {
  type ClientAddr = String
  type UserAgent  = `User-Agent`
  type ClientsSet = Map[ClientId, (Instant, ClientAddr, Option[UserAgent])]

  def apply[F[_]: Clock: Monad: Logger](ref: Ref[F, ClientsSet]): ClientsSetDb[F] = {
    val L = Logger[F]
    new ClientsSetDb[F] {
      def newClient(id: ClientId, addr: ClientsSetDb.ClientAddr, ua: Option[UserAgent]): F[Unit] =
        Clock[F].realTimeInstant.flatMap(i => ref.update(_ + (id -> ((i, addr, ua)))))
      def removeClient(id: ClientId): F[Unit]                                                    =
        ref.update(_ - id)
      def report: F[Unit]                                                                        =
        ref.get
          .flatMap { m =>
            (L.debug("Clients Summary:") *>
              L.debug("----------------") *>
              m.map { case (id, (i, addr, ua)) =>
                s"  Client: $id, arrived on $i from addr: $addr ${ua.foldMap(u => s"UserAgent: $u")}"
              }.toList
                .traverse(L.debug(_)) *>
              L.debug("----------------")).whenA(m.nonEmpty)
          }

    }
  }
}
