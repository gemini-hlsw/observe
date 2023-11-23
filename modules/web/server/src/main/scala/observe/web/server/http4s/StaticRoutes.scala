// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import scala.concurrent.duration._

import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.all.*
// import cats.instances.string._
// import cats.syntax.eq._
import org.http4s.CacheDirective._
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Response
import org.http4s.StaticFile
import org.http4s.headers.`Cache-Control`
import org.http4s.server.middleware.GZip
import org.http4s.Header
import org.http4s.Uri
import fs2.compression.Compression
import fs2.io.file.Files
import java.nio.file.Paths
import fs2.io.file.Path

class StaticRoutes[F[_]: Sync: Compression: Files]: // (builtAtMillis: Long) {
  private val AppDir: String = "app"

  private val OneYear: Int = 365 * 24 * 60 * 60 // One year in seconds

  // private val CacheHeaders: Headers = Headers(
  private val CacheHeaders: List[Header.ToRaw] = List(
    `Cache-Control`(NonEmptyList.of(`max-age`(OneYear.seconds)))
  )

  def localFile(path: String, req: Request[F]): OptionT[F, Response[F]] =
    // println(path)
    // println(fs2.io.file.Path(".").toNioPath.toAbsolutePath)
    // Get full path to JAR/class
    // https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
    val runningPath = Paths.get(getClass.getProtectionDomain.getCodeSource.getLocation.toURI)
    // Get the current directory (should be "lib") and then go to the "app" sibling
    val fullPath    = runningPath.getParent.resolveSibling(AppDir).resolve(path.stripPrefix("/"))
    // println(runningPath)
    // println(runningPath.getParent)
    // println(runningPath.getParent.resolveSibling(AppDir))
    println(fullPath)
    StaticFile.fromPath(Path.fromNioPath(fullPath), req.some) // .map(_.putHeaders())

  // // Get a resource from a local file, used in production
  // def embeddedResource(path: String, req: Request[F]): OptionT[F, Response[F]] =
  //   OptionT
  //     .fromOption(Option(getClass.getResource(path)))
  //     .flatMap(StaticFile.fromURL(_, Some(req)))

  implicit class ReqOps(req: Request[F]) {
    // private val TimestampRegex = s"(.*)\\.$builtAtMillis\\.(.*)".r

    /**
     * If a request contains the timestamp remove it to find the original file name
     */
    // def removeTimestamp(path: String): String =
    //   path match
    //     case TimestampRegex(b, e) => s"$b.$e"
    //     case xs                   => xs

    def endsWith(exts: String*): Boolean = exts.exists(req.pathInfo.toString.endsWith)

    def serve(path: String): F[Response[F]] =
      // Sync[F].delay {
      //   println(s"Serving ${req.pathInfo} from [$path]")
      //   println(s"Actual path: [${removeTimestamp(AppDir + path)}]")
      //   // println(getClass.getResource("."))
      //   println(getClass.getResource("/"))
      //   println(getClass.getResource(".."))
      //   println(getClass.getResource("/.."))
      //   println(getClass.getResource("../"))
      //   println(getClass.getResource("/../"))
      //   // println(
      //   //   s"Resource: [${Option(getClass.getResource(removeTimestamp(AppDir + path)))}]"
      //   // )
      //   // println(
      //   //   s"Resource: [${Option(getClass.getResource(removeTimestamp("../" + AppDir + path)))}]"
      //   // )
      // } >>
      localFile(path, req)
        .map(_.putHeaders(CacheHeaders: _*))
        .getOrElse(Response.notFound[F])
  }

  private val supportedExtension = List(
    ".html",
    ".js",
    ".map",
    ".css",
    ".png",
    ".eot",
    ".svg",
    ".woff",
    ".woff2",
    ".ttf",
    ".mp3",
    ".ico",
    ".webm",
    ".json"
  )

  def service: HttpRoutes[F] = GZip {
    HttpRoutes.of[F] {
      case req if req.pathInfo === Uri.Path.Root       => req.serve("/index.html")
      case req if req.endsWith(supportedExtension: _*) => req.serve(req.pathInfo.toString)
      // This maybe not desired in all cases but it helps to keep client side routing cleaner
      case req if !req.pathInfo.toString.contains(".") => req.serve("/index.html")
    }
  }
