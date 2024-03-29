// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.security

import cats.*
import cats.effect.*
import cats.syntax.all.*
import com.unboundid.ldap.sdk.LDAPURL
import org.typelevel.log4cats.Logger
import io.circe.*
import io.circe.generic.semiauto.deriveCodec
import io.circe.jawn.decode
import io.circe.syntax.*
import pdi.jwt.Jwt
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtCirce
import pdi.jwt.JwtClaim
import observe.model.UserDetails
import observe.model.config.*
import AuthenticationService.AuthResult

sealed trait AuthenticationFailure      extends Product with Serializable
case class UserNotFound(user: String)   extends AuthenticationFailure
case class BadCredentials(user: String) extends AuthenticationFailure
case object NoAuthenticator             extends AuthenticationFailure
case class GenericFailure(msg: String)  extends AuthenticationFailure
case class DecodingFailure(msg: String) extends AuthenticationFailure
case object MissingCookie               extends AuthenticationFailure

/**
 * Interface for implementations that can authenticate users from a username/pwd pair
 */
trait AuthService[F[_]] {
  def authenticateUser(username: String, password: String): F[AuthResult]
}

// Intermediate class to decode the claim stored in the JWT token
case class JwtUserClaim(exp: Int, iat: Int, username: String, displayName: String) {
  def toUserDetails: UserDetails = UserDetails(username, displayName)
}

case class AuthenticationService[F[_]: Sync: Logger](
  mode:   Mode,
  config: AuthenticationConfig
) extends AuthService[F] {
  import AuthenticationService._
  given clock: java.time.Clock = java.time.Clock.systemUTC()

  private val hosts =
    config.ldapUrls.map(u => new LDAPURL(u.renderString)).map(u => (u.getHost, u.getPort))

  given Codec[UserDetails] = deriveCodec

  private val authServices =
    if (mode === Mode.Development) List(new TestAuthenticationService[F], ldapService)
    else List(ldapService)

  /**
   * From the user details it creates a JSON Web Token
   */
  def buildToken(u: UserDetails): F[String] = Sync[F].delay {
    // Given that only this server will need the key we can just use HMAC. 512-bit is the max key size allowed
    Jwt.encode(
      JwtClaim(u.asJson.noSpaces).issuedNow.expiresIn(config.sessionLifeHrs.toSeconds.toLong),
      config.secretKey,
      JwtAlgorithm.HS512
    )
  }

  /**
   * Decodes a token out of JSON Web Token
   */
  def decodeToken(t: String): AuthResult =
    for {
      claim       <- JwtCirce
                       .decode(t, config.secretKey, Seq(JwtAlgorithm.HS512))
                       .toEither
                       .leftMap(t => DecodingFailure(t.getMessage))
      userDetails <- decode[UserDetails](claim.content).leftMap(e => DecodingFailure(e.getMessage))
    } yield userDetails

  val sessionTimeout: Long = config.sessionLifeHrs.toSeconds

  override def authenticateUser(username: String, password: String): F[AuthResult] =
    authServices.authenticateUser(username, password)
}

object AuthenticationService {
  type AuthResult                   = Either[AuthenticationFailure, UserDetails]
  type AuthenticationServices[F[_]] = List[AuthService[F]]

  // Allows calling authenticate on a list of authenticator, stopping at the first
  // that succeeds
  implicit class ComposedAuth[F[_]: MonadThrow: Logger](
    val s: AuthenticationServices[F]
  ) {

    def authenticateUser(username: String, password: String): F[AuthResult] = {
      def go(l: List[AuthService[F]]): F[AuthResult] = l match {
        case Nil     => NoAuthenticator.asLeft[UserDetails].pure[F].widen[AuthResult]
        case x :: xs =>
          x.authenticateUser(username, password).attempt.flatMap {
            case Right(Right(u)) => u.asRight.pure[F]
            case Right(Left(e))  => Logger[F].warn(s"Auth method error $x with $e") *> go(xs)
            case _               => go(xs)
          }
      }
      // Discard empty values right away
      if (username.isEmpty || password.isEmpty) {
        BadCredentials(username).asLeft[UserDetails].pure[F].widen[AuthResult]
      } else {
        go(s).flatTap(a => Logger[F].info(a.toString))
      }
    }
  }
}
