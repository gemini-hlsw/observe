import sbt.*
import java.lang.{Runtime => JRuntime}
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*

/**
 * Application settings and dependencies
 */
object Settings {

  /** Library versions */
  object LibraryVersions {
    // Scala libraries
    val catsEffectVersion = "3.5.4"
    val catsVersion       = "2.12.0"
    val kittens           = "3.3.0"
    val mouseVersion      = "1.3.0"
    val fs2Version        = "3.10.2"
    val scalaXml          = "1.2.0"
    val catsTime          = "0.5.1"
    val catsParseVersion  = "1.0.0"

    val http4sVersion              = "0.23.24-25-e71f1b1-SNAPSHOT"
    val http4sDomVersion           = "0.2.11"
    val http4sJdkHttpClientVersion = "0.9.1"
    val http4sScalaXmlVersion      = "0.23.14"

    val coulomb          = "0.8.0"
    val commonsHttp      = "3.1"
    val unboundId        = "3.2.1"
    val jwt              = "10.0.1"
    val slf4j            = "2.0.13"
    val log4cats         = "2.7.0"
    val log4catsLogLevel = "0.3.1"
    val logback          = "1.5.6"
    val janino           = "3.1.12"
    val pureConfig       = "0.17.7"
    val monocleVersion   = "3.2.0"
    val circeVersion     = "0.14.7"

    // test libraries
    val jUnitInterface         = "0.13.2"
    val munitVersion           = "1.0.0"
    val munitDisciplineVersion = "2.0.0"
    val munitCatsEffectVersion = "2.0.0"

    val acm           = "0.1.1"
    val giapiScala    = "0.2.3"
    val geminiLocales = "0.7.0"
    val pprint        = "0.9.0"

    // Gemini Libraries
    val lucumaCore      = "0.99.0"
    val lucumaUI        = "0.108.0"
    val lucumaSchemas   = "0.88.0"
    val lucumaSSO       = "0.6.18"
    val lucumaODBSchema = "0.11.8"

    // Clue
    val clue = "0.35.4"

    // ScalaJS libraries
    val crystal      = "0.39.2"
    val javaTimeJS   = "2.6.0"
    val lucumaReact  = "0.65.1"
    val scalaDom     = "2.3.0"
    val scalajsReact = "3.0.0-beta3"
  }

  /**
   * Global libraries
   */
  object Libraries {
    // Test Libraries
    val MUnit          = Def.setting(
      Seq(
        "org.scalameta" %%% "munit"             % LibraryVersions.munitVersion           % Test,
        "org.typelevel" %%% "munit-cats-effect" % LibraryVersions.munitCatsEffectVersion % Test,
        "org.typelevel" %%% "discipline-munit"  % LibraryVersions.munitDisciplineVersion % Test
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
    val JwtCore   = "com.github.jwt-scala" %% "jwt-core"     % LibraryVersions.jwt
    val JwtCirce  = "com.github.jwt-scala" %% "jwt-circe"    % LibraryVersions.jwt
    val Slf4j     = "org.slf4j"             % "slf4j-api"    % LibraryVersions.slf4j
    val JuliSlf4j = "org.slf4j"             % "jul-to-slf4j" % LibraryVersions.slf4j
    val NopSlf4j  = "org.slf4j"             % "slf4j-nop"    % LibraryVersions.slf4j
    val CatsTime  = Def.setting(
      "org.typelevel" %%% "cats-time-testkit" % LibraryVersions.catsTime % Test
    )

    val CatsParse        = Def.setting(
      "org.typelevel" %%% "cats-parse" % LibraryVersions.catsParseVersion
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
    val Coulomb          = Def.setting(
      Seq(
        "com.manyangled" %%% "coulomb-core"    % LibraryVersions.coulomb,
        "com.manyangled" %%% "coulomb-units"   % LibraryVersions.coulomb,
        "com.manyangled" %%% "coulomb-testkit" % LibraryVersions.coulomb % Test
      )
    )
    val ScalaXml         =
      Def.setting("org.scala-lang.modules" %%% "scala-xml" % LibraryVersions.scalaXml)
    val Http4s           = Seq(
      "org.http4s" %% "http4s-dsl"          % LibraryVersions.http4sVersion,
      "org.http4s" %% "http4s-ember-server" % LibraryVersions.http4sVersion
    )
    val Http4sClient     = Def.setting(
      "org.http4s" %%% "http4s-client" % LibraryVersions.http4sVersion
    )
    val Http4sJDKClient  =
      Def.setting(
        Seq(
          "org.http4s" %% "http4s-dsl"             % LibraryVersions.http4sVersion,
          "org.http4s" %% "http4s-jdk-http-client" % LibraryVersions.http4sJdkHttpClientVersion
        )
      )
    val Http4sServer     = "org.http4s" %% "http4s-server"    % LibraryVersions.http4sVersion
    val Http4sCore       = Def.setting(
      "org.http4s" %%% "http4s-core" % LibraryVersions.http4sVersion
    )
    val Http4sCirce      = Def.setting(
      "org.http4s" %%% "http4s-circe" % LibraryVersions.http4sVersion
    )
    val Http4sLaws       = Def.setting(
      "org.http4s" %%% "http4s-laws" % LibraryVersions.http4sVersion
    )
    val Http4sDom        = Def.setting("org.http4s" %%% "http4s-dom" % LibraryVersions.http4sDomVersion)
    val Http4sXml        = "org.http4s" %% "http4s-scala-xml" % LibraryVersions.http4sScalaXmlVersion
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
    val Crystal = Def.setting(
      Seq(
        "edu.gemini" %%% "crystal"         % LibraryVersions.crystal,
        "edu.gemini" %%% "crystal-testkit" % LibraryVersions.crystal % Test
      )
    )

    val LucumaReact    = Def.setting(
      Seq(
        "edu.gemini" %%% "lucuma-react-common"         % LibraryVersions.lucumaReact,
        "edu.gemini" %%% "lucuma-react-font-awesome"   % LibraryVersions.lucumaReact,
        "edu.gemini" %%% "lucuma-react-tanstack-table" % LibraryVersions.lucumaReact,
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
    val LucumaCore = Def.setting(
      Seq(
        "edu.gemini" %%% "lucuma-core"         % LibraryVersions.lucumaCore,
        "edu.gemini" %%% "lucuma-core-testkit" % LibraryVersions.lucumaCore
      )
    )

    val LucumaUI = Def.setting(
      Seq(
        "edu.gemini" %%% "lucuma-ui"         % LibraryVersions.lucumaUI,
        "edu.gemini" %%% "lucuma-ui-testkit" % LibraryVersions.lucumaUI % Test
      )
    )

    val LucumaSSO =
      Def.setting("edu.gemini" %%% "lucuma-sso-backend-client" % LibraryVersions.lucumaSSO)

    val LucumaSchemas   =
      Def.setting(
        Seq(
          "edu.gemini" %%% "lucuma-schemas"         % LibraryVersions.lucumaSchemas,
          "edu.gemini" %%% "lucuma-schemas-testkit" % LibraryVersions.lucumaSchemas % Test
        )
      )
    val LucumaODBSchema =
      Def.setting(
        "edu.gemini" %%% "lucuma-odb-schema" % LibraryVersions.lucumaODBSchema
      )

    val Clue          = Def.setting("edu.gemini" %%% "clue-core" % LibraryVersions.clue)
    val ClueGenerator = "edu.gemini" %% "clue-generator" % LibraryVersions.clue
    val ClueHttp4s    = "edu.gemini" %% "clue-http4s"    % LibraryVersions.clue
    val ClueJs        = Def.setting("edu.gemini" %%% "clue-scalajs" % LibraryVersions.clue)

  }

}
