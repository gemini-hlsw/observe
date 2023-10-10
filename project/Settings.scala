import sbt.*
import java.lang.{Runtime => JRuntime}
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*

/**
 * Application settings and dependencies
 */
object Settings {

  /** Library versions */
  object LibraryVersions {
    // ScalaJS libraries
    val crystal      = "0.34.5"
    val javaTimeJS   = "2.5.0"
    val lucumaReact  = "0.44.1"
    val scalaDom     = "2.3.0"
    val scalajsReact = "2.1.1"

    // Scala libraries
    val catsEffectVersion = "3.4.10"
    val catsVersion       = "2.10.0"
    val kittens           = "3.0.0"
    val mouseVersion      = "1.2.1"
    val fs2Version        = "3.9.2"
    val scalaXml          = "1.2.0"
    val catsTime          = "0.5.1"

    val http4sVersion              = "0.23.23"
    val http4sDomVersion           = "0.2.10"
    val http4sJdkHttpClientVersion = "0.9.1"
    val http4sScalaXmlVersion      = "0.23.13"

    val squants          = "1.8.3"
    val unboundId        = "3.2.1"
    val jwt              = "9.4.4"
    val slf4j            = "2.0.9"
    val log4cats         = "2.6.0"
    val log4catsLogLevel = "0.3.1"
    val logback          = "1.4.11"
    val janino           = "3.1.10"
    val pureConfig       = "0.17.4"
    val monocleVersion   = "3.2.0"
    val circeVersion     = "0.14.6"

    // test libraries
    val jUnitInterface         = "0.13.2"
    val munitVersion           = "0.7.29"
    val munitDisciplineVersion = "1.0.9"
    val munitCatsEffectVersion = "1.0.7"

    val acm           = "0.1.1"
    val giapiScala    = "0.1.0"
    val geminiLocales = "0.7.0"
    val pprint        = "0.8.1"

    // Gemini Libraries
    val lucumaCore    = "0.86.1"
    val lucumaUI      = "0.86.0"
    val lucumaSchemas = "0.62.0"
    val lucumaSSO     = "0.6.7"

    // Clue
    val clue = "0.33.0"

    val atto = "0.9.5"
  }

  /**
   * Global libraries
   */
  object Libraries {
    // Test Libraries
    val MUnit          = Def.setting(
      Seq(
        "org.scalameta" %%% "munit"               % LibraryVersions.munitVersion           % Test,
        "org.typelevel" %%% "munit-cats-effect-3" % LibraryVersions.munitCatsEffectVersion % Test,
        "org.typelevel" %%% "discipline-munit"    % LibraryVersions.munitDisciplineVersion % Test
      )
    )
    val JUnitInterface =
      "com.github.sbt" % "junit-interface" % LibraryVersions.jUnitInterface % "test"
    // Server side libraries
    val Cats       = Def.setting(
      Seq(
        "org.typelevel" %%% "cats-core"    % LibraryVersions.catsVersion,
        "org.typelevel" %%% "cats-testkit" % LibraryVersions.catsVersion % Test
      )
    )
    val Kittens    = Def.setting("org.typelevel" %%% "kittens" % LibraryVersions.kittens)
    val CatsEffect =
      Def.setting("org.typelevel" %%% "cats-effect" % LibraryVersions.catsEffectVersion)
    val Fs2        = Def.setting("co.fs2" %%% "fs2-core" % LibraryVersions.fs2Version)
    val Fs2IO      = "co.fs2" %% "fs2-io" % LibraryVersions.fs2Version % "test"
    val Mouse      = Def.setting("org.typelevel" %%% "mouse" % LibraryVersions.mouseVersion)
    val UnboundId  =
      "com.unboundid" % "unboundid-ldapsdk-minimal-edition" % LibraryVersions.unboundId
    val JwtCore          = "com.github.jwt-scala" %% "jwt-core"         % LibraryVersions.jwt
    val JwtCirce         = "com.github.jwt-scala" %% "jwt-circe"        % LibraryVersions.jwt
    val Slf4j            = "org.slf4j"             % "slf4j-api"        % LibraryVersions.slf4j
    val JuliSlf4j        = "org.slf4j"             % "jul-to-slf4j"     % LibraryVersions.slf4j
    val NopSlf4j         = "org.slf4j"             % "slf4j-nop"        % LibraryVersions.slf4j
    val CatsTime         = Def.setting(
      "org.typelevel" %%% "cats-time-testkit" % LibraryVersions.catsTime % Test
    )
    val Log4Cats         = Def.setting("org.typelevel" %%% "log4cats-slf4j" % LibraryVersions.log4cats)
    val Log4CatsNoop     =
      Def.setting("org.typelevel" %%% "log4cats-noop" % LibraryVersions.log4cats % "test")
    val Logback          = Seq(
      "ch.qos.logback"      % "logback-core"    % LibraryVersions.logback,
      "ch.qos.logback"      % "logback-classic" % LibraryVersions.logback,
      "org.codehaus.janino" % "janino"          % LibraryVersions.janino
    )
    val Log4CatsLogLevel = Def.setting(
      Seq(
        "org.typelevel" %%% "log4cats-core"     % LibraryVersions.log4cats,
        "com.rpiaggio"  %%% "log4cats-loglevel" % LibraryVersions.log4catsLogLevel
      )
    )
    val Logging          = Def.setting(Seq(JuliSlf4j) ++ Logback)
    val PureConfig       = Seq(
      "com.github.pureconfig" %% "pureconfig-core"        % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-cats"        % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-cats-effect" % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-http4s"      % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-ip4s"        % LibraryVersions.pureConfig
    )
    val Squants          = Def.setting("org.typelevel" %%% "squants" % LibraryVersions.squants)
    val ScalaXml         =
      Def.setting("org.scala-lang.modules" %%% "scala-xml" % LibraryVersions.scalaXml)
    val Http4s           = Seq("org.http4s" %% "http4s-dsl" % LibraryVersions.http4sVersion,
                     "org.http4s" %% "http4s-ember-server" % LibraryVersions.http4sVersion
    )
    val Http4sClient     = Seq(
      "org.http4s" %% "http4s-dsl"             % LibraryVersions.http4sVersion,
      "org.http4s" %% "http4s-jdk-http-client" % LibraryVersions.http4sJdkHttpClientVersion
    )
    val Http4sCore       = "org.http4s"           %% "http4s-core"      % LibraryVersions.http4sVersion
    val Http4sServer     = "org.http4s"           %% "http4s-server"    % LibraryVersions.http4sVersion
    val Http4sCirce      = "org.http4s"           %% "http4s-circe"     % LibraryVersions.http4sVersion
    val Http4sDom        = Def.setting("org.http4s" %%% "http4s-dom" % LibraryVersions.http4sDomVersion)
    val Http4sXml        = "org.http4s"           %% "http4s-scala-xml" % LibraryVersions.http4sScalaXmlVersion
    val Monocle          = Def.setting(
      Seq(
        "dev.optics" %%% "monocle-core"   % LibraryVersions.monocleVersion,
        "dev.optics" %%% "monocle-macro"  % LibraryVersions.monocleVersion,
        "dev.optics" %%% "monocle-unsafe" % LibraryVersions.monocleVersion,
        "dev.optics" %%% "monocle-law"    % LibraryVersions.monocleVersion
      )
    )
    val Circe            = Def.setting(
      Seq(
        "io.circe" %%% "circe-core"    % LibraryVersions.circeVersion,
        "io.circe" %%% "circe-generic" % LibraryVersions.circeVersion,
        "io.circe" %%% "circe-parser"  % LibraryVersions.circeVersion,
        "io.circe" %%% "circe-refined" % LibraryVersions.circeVersion,
        "io.circe" %%% "circe-testing" % LibraryVersions.circeVersion % "test"
      )
    )

    // Client Side JS libraries
    val Crystal        = Def.setting("edu.gemini" %%% "crystal" % LibraryVersions.crystal)
    val LucumaReact    = Def.setting(
      Seq(
        "edu.gemini" %%% "lucuma-react-common"         % LibraryVersions.lucumaReact,
        "edu.gemini" %%% "lucuma-react-font-awesome"   % LibraryVersions.lucumaReact,
        "edu.gemini" %%% "lucuma-react-tanstack-table" % LibraryVersions.lucumaReact,
        "edu.gemini" %%% "lucuma-react-floatingui"     % LibraryVersions.lucumaReact,
        "edu.gemini" %%% "lucuma-react-prime-react"    % LibraryVersions.lucumaReact // Must be last, lest we hit a compiler snag
      )
    )
    val ScalaJSReactIO = Def.setting(
      Seq(
        "com.github.japgolly.scalajs-react" %%% "core-bundle-cb_io"        % LibraryVersions.scalajsReact,
        "com.github.japgolly.scalajs-react" %%% "extra"                    % LibraryVersions.scalajsReact,
        "com.github.japgolly.scalajs-react" %%% "extra-ext-monocle3"       % LibraryVersions.scalajsReact,
        "com.github.japgolly.scalajs-react" %%% "callback-ext-cats_effect" % LibraryVersions.scalajsReact
      )
    )
    val ReactScalaJS   = Def.setting(
      Seq(
        "com.github.japgolly.scalajs-react" %%% "core"               % LibraryVersions.scalajsReact,
        "com.github.japgolly.scalajs-react" %%% "extra"              % LibraryVersions.scalajsReact,
        "com.github.japgolly.scalajs-react" %%% "extra-ext-monocle3" % LibraryVersions.scalajsReact,
        "com.github.japgolly.scalajs-react" %%% "core-ext-cats"      % LibraryVersions.scalajsReact
      )
    )
    val ScalaJSDom     = Def.setting("org.scala-js" %%% "scalajs-dom" % LibraryVersions.scalaDom)
    val JavaTimeJS     =
      Def.setting("io.github.cquiroz" %%% "scala-java-time" % LibraryVersions.javaTimeJS)
    val GeminiLocales  =
      Def.setting("edu.gemini" %%% "gemini-locales" % LibraryVersions.geminiLocales)
    val PPrint         = Def.setting("com.lihaoyi" %%% "pprint" % LibraryVersions.pprint)

    // GIAPI Libraries
    val GiapiScala = "edu.gemini" %% "giapi"    % LibraryVersions.giapiScala
    val ACM        = "edu.gemini"  % "acm_2.13" % LibraryVersions.acm

    // Lucuma Libraries
    val LucumaCore    = Def.setting(
      Seq(
        "edu.gemini" %%% "lucuma-core"         % LibraryVersions.lucumaCore,
        "edu.gemini" %%% "lucuma-core-testkit" % LibraryVersions.lucumaCore
      )
    )
    val LucumaUI      = Def.setting("edu.gemini" %%% "lucuma-ui" % LibraryVersions.lucumaUI)
    val LucumaSSO     =
      Def.setting("edu.gemini" %%% "lucuma-sso-backend-client" % LibraryVersions.lucumaSSO)
    val LucumaSchemas =
      Def.setting("edu.gemini" %%% "lucuma-schemas" % LibraryVersions.lucumaSchemas)

    val Clue          = Def.setting("edu.gemini" %%% "clue-core" % LibraryVersions.clue)
    val ClueGenerator = "edu.gemini" %% "clue-generator" % LibraryVersions.clue
    val ClueHttp4s    = "edu.gemini" %% "clue-http4s"    % LibraryVersions.clue
    val ClueJs        = Def.setting("edu.gemini" %%% "clue-scalajs" % LibraryVersions.clue)

    val Atto = "org.tpolecat" %% "atto-core" % LibraryVersions.atto
  }

}
