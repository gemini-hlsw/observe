// Code graciously lifted from https://github.com/pureconfig/pureconfig to temporarily remove the dependency on a
// https4s version that does not supports cats-effect 3.
// TODO: Remove the file and restore the dependency on pureconfig-http4s after Pure Config fully supports cats-effects 3

package pureconfig.module

import org.http4s.Uri

import pureconfig.error.CannotConvert
import pureconfig.{ConfigReader, ConfigWriter}

package object http4s {

  implicit val uriReader: ConfigReader[Uri] =
    ConfigReader.fromString(str =>
      Uri
        .fromString(str)
        .fold(err => Left(CannotConvert(str, "Uri", err.sanitized)), uri => Right(uri))
    )

  implicit val uriWriter: ConfigWriter[Uri] = ConfigWriter[String].contramap(_.renderString)
}
