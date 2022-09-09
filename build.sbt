import Settings.Libraries._
import Settings.LibraryVersions
import Common._
import AppsCommon._
import sbt.Keys._
import NativePackagerHelper._
import com.typesafe.sbt.packager.docker._
import org.scalajs.linker.interface.ModuleSplitStyle

name := "observe"

Global / onChangedBuildSource := ReloadOnSourceChanges

Global / semanticdbEnabled := true

ThisBuild / Compile / packageDoc / publishArtifact := false
ThisBuild / Test / bspEnabled                      := false

ThisBuild / githubWorkflowSbtCommand := "sbt -v -J-Xmx6g"

inThisBuild(
  Seq(
    scalacOptions += "-Ymacro-annotations",
    Global / onChangedBuildSource                            := ReloadOnSourceChanges,
    scalafixDependencies += LucumaSchemas,
    scalafixScalaBinaryVersion                               := "2.13",
    ScalafixConfig / bspEnabled.withRank(KeyRanks.Invisible) := false
  ) ++ lucumaPublishSettings
)

// Gemini repository
ThisBuild / resolvers += "Gemini Repository".at(
  "https://github.com/gemini-hlsw/maven-repo/raw/master/releases"
)

Global / resolvers ++= Resolver.sonatypeOssRepos("public")

// This key is used to find the JRE dir. It could/should be overridden on a user basis
// Add e.g. a `jres.sbt` file with your particular configuration
ThisBuild / ocsJreDir := Path.userHome / ".jres11"

ThisBuild / evictionErrorLevel := Level.Info

Global / cancelable := true

// Should make CI builds more robust
Global / concurrentRestrictions += Tags.limit(ScalaJSTags.Link, 2)

// Uncomment for local gmp testing
// ThisBuild / resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

enablePlugins(GitBranchPrompt)

lazy val esModule = Seq(
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  Compile / fastLinkJS / scalaJSLinkerConfig ~= { _.withSourceMap(false) },
  Compile / fullLinkJS / scalaJSLinkerConfig ~= { _.withSourceMap(false) },
  Compile / fastLinkJS / scalaJSLinkerConfig ~= (_.withModuleSplitStyle(
    // If the browser is too slow for the SmallModulesFor switch to ModuleSplitStyle.FewestModules
    ModuleSplitStyle.SmallModulesFor(List("explore"))
  )),
  Compile / fullLinkJS / scalaJSLinkerConfig ~= (_.withModuleSplitStyle(
    ModuleSplitStyle.FewestModules
  ))
)

// Custom commands to facilitate web development
val startObserveAllCommands   = List(
  "observe_web_server/reStart",
  "observe_web_client/Compile/fastOptJS/startWebpackDevServer",
  "~observe_web_client/fastOptJS"
)
val restartObserveWDSCommands = List(
  "observe_web_client/Compile/fastOptJS/stopWebpackDevServer",
  "observe_web_client/Compile/fastOptJS/startWebpackDevServer",
  "~observe_web_client/fastOptJS"
)
val stopObserveAllCommands    = List(
  "observe_web_server/reStop",
  "observe_web_client/Compile/fastOptJS/stopWebpackDevServer"
)

addCommandAlias("startObserveAll", startObserveAllCommands.mkString(";", ";", ""))
addCommandAlias("restartObserveWDS", restartObserveWDSCommands.mkString(";", ";", ""))
addCommandAlias("stopObserveAll", stopObserveAllCommands.mkString(";", ";", ""))

ThisBuild / resolvers ++=
  Resolver.sonatypeOssRepos("snapshots")

ThisBuild / updateOptions := updateOptions.value.withLatestSnapshots(false)

//////////////
// Projects
//////////////

lazy val root = tlCrossRootProject.aggregate(
  giapi,
  ocs2_api,
  observe_web_server,
//  observe_web_client,
  observe_server,
  observe_model,
  observe_engine
)

lazy val giapi = project
  .in(file("modules/giapi"))
  .enablePlugins(GitBranchPrompt)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(Cats.value,
                                Mouse.value,
                                Shapeless.value,
                                CatsEffect.value,
                                Fs2,
                                GiapiJmsUtil,
                                GiapiJmsProvider,
                                GiapiStatusService,
                                Giapi,
                                GiapiCommandsClient
    ) ++ Logging.value ++ Monocle.value,
    libraryDependencies ++= Seq(GmpStatusGateway  % "test",
                                GmpStatusDatabase % "test",
                                GmpCmdJmsBridge   % "test",
                                NopSlf4j          % "test"
    ) ++ MUnit.value
  )

lazy val ocs2_api = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/ocs2_api"))
  .settings(commonSettings)
  .settings(
    name := "ocs2-api",
    libraryDependencies ++= Seq(CatsTime.value) ++
      LucumaCore.value
  )
  .jsSettings(coverageEnabled := false)
  .dependsOn(observe_model)

// Project for the server side application
lazy val observe_web_server = project
  .in(file("modules/web/server"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(GitBranchPrompt)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(UnboundId,
                                JwtCore,
                                JwtCirce,
                                Http4sServer,
                                Http4sPrometheus,
                                CommonsHttp,
                                ScalaMock,
                                Log4CatsNoop.value
    ) ++
      Http4sClient ++ Http4s ++ PureConfig ++ Logging.value,
    // Supports launching the server in the background
    reStart / javaOptions += s"-javaagent:${(ThisBuild / baseDirectory).value}/app/observe-server/src/universal/bin/jmx_prometheus_javaagent-0.3.1.jar=6060:${(ThisBuild / baseDirectory).value}/app/observe-server/src/universal/bin/prometheus.yaml",
    reStart / mainClass  := Some("observe.web.server.http4s.WebServerLauncher"),
    Compile / bspEnabled := false
  )
  .settings(
    buildInfoUsePackageAsPath := true,
    buildInfoKeys ++= Seq[BuildInfoKey](name, version, buildInfoBuildNumber),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoObject           := "OcsBuildInfo",
    buildInfoPackage          := "observe.web.server"
  )
  .dependsOn(observe_server)
  .dependsOn(observe_model.jvm % "compile->compile;test->test")

/*lazy val observe_web_client = project
  .in(file("modules/web/client"))
  .enablePlugins(ScalaJSPlugin)
  .enablePlugins(ScalaJSBundlerPlugin)
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(GitBranchPrompt)
  .disablePlugins(RevolverPlugin)
  .settings(
    // Needed for Monocle macros
    scalacOptions += "-Ymacro-annotations",
    scalacOptions ~= (_.filterNot(
      Set(
        // By necessity facades will have unused params
        "-Wunused:params",
        "-Wunused:explicits"
      )
    )),
    coverageEnabled                 := false,
    // Configurations for webpack
    fastOptJS / webpackBundlingMode := BundlingMode.LibraryOnly(),
    fullOptJS / webpackBundlingMode := BundlingMode.Application,
    webpackResources                := (baseDirectory.value / "src" / "webpack") * "*.js",
    webpackDevServerPort            := 9090,
    webpack / version               := "4.46.0",
    startWebpackDevServer / version := "3.11.0",
    // Use a different Webpack configuration file for production and create a single bundle without source maps
    fullOptJS / webpackConfigFile   := Some(
      baseDirectory.value / "src" / "webpack" / "prod.webpack.config.js"
    ),
    fastOptJS / webpackConfigFile   := Some(
      baseDirectory.value / "src" / "webpack" / "dev.webpack.config.js"
    ),
    Test / webpackConfigFile        := Some(
      baseDirectory.value / "src" / "webpack" / "test.webpack.config.js"
    ),
    webpackEmitSourceMaps           := false,
    Test / parallelExecution        := false,
    installJsdom / version          := "16.4.0",
    Test / requireJsDomEnv          := true,
    // Use yarn as it is faster than npm
    useYarn                         := true,
    // JS dependencies via npm
    Compile / npmDependencies ++= Seq(
      "fomantic-ui-less" -> LibraryVersions.fomanticUI,
      "prop-types"       -> "15.7.2",
      "core-js"          -> "2.6.11" // Without this, core-js 3 is used, which conflicts with @babel/runtime-corejs2
    ),
    Compile / fastOptJS / scalaJSLinkerConfig ~= { _.withSourceMap(false) },
    Compile / fullOptJS / scalaJSLinkerConfig ~= { _.withSourceMap(false) },
    // NPM libs for development, mostly to let webpack do its magic
    Compile / npmDevDependencies ++= Seq(
      "postcss"                       -> "8.1.1",
      "postcss-loader"                -> "4.0.3",
      "autoprefixer"                  -> "10.0.1",
      "url-loader"                    -> "4.1.0",
      "file-loader"                   -> "6.0.0",
      "css-loader"                    -> "3.5.3",
      "style-loader"                  -> "1.2.1",
      "less"                          -> "3.9.0",
      "less-loader"                   -> "7.0.1",
      "webpack-merge"                 -> "4.2.2",
      "mini-css-extract-plugin"       -> "0.8.0",
      "webpack-dev-server-status-bar" -> "1.1.0",
      "cssnano"                       -> "4.1.10",
      "terser-webpack-plugin"         -> "3.0.6",
      "html-webpack-plugin"           -> "4.3.0",
      "css-minimizer-webpack-plugin"  -> "1.1.5",
      "favicons-webpack-plugin"       -> "4.2.0",
      "@packtracker/webpack-plugin"   -> "2.3.0"
    ),
    libraryDependencies ++= Seq(
      Cats.value,
      Mouse.value,
      CatsEffect.value,
      ScalaJSDom.value,
      JavaTimeJS.value,
      ScalaJSReactSemanticUI.value,
      ScalaJSReactVirtualized.value,
      ScalaJSReactClipboard.value,
      ScalaJSReactSortable.value,
      ScalaJSReactDraggable.value,
      GeminiLocales.value,
      LucumaUI.value,
      PPrint.value,
      TestLibs.value
    ) ++ MUnit.value ++ ReactScalaJS.value ++ Diode.value ++ Log4CatsLogLevel.value ++ Circe.value
  )
  .settings(
    buildInfoUsePackageAsPath := true,
    buildInfoKeys ++= Seq[BuildInfoKey](name, version),
    buildInfoObject           := "OcsBuildInfo",
    buildInfoPackage          := "observe.web.client"
  )
  .dependsOn(observe_model.js % "compile->compile;test->test")*/

lazy val new_web = project
  .in(file("modules/new_web"))
  .settings(lucumaGlobalSettings: _*)
  .settings(esModule: _*)
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaVersion    := "3.2.1-RC1",
    Test / test     := {},
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      Cats.value,
      CatsEffect.value,
      Crystal.value,
      Fs2,
      LucumaUI3.value
    ) ++ ScalaJSReactIO.value ++ LucumaReact.value ++ Monocle.value ++ LucumaCore3.value ++ Log4CatsLogLevel.value,
    scalacOptions ~= (_.filterNot(Set("-Vtype-diffs")))
  )

// List all the modules and their inter dependencies
lazy val observe_server = project
  .in(file("modules/server"))
  .enablePlugins(GitBranchPrompt, BuildInfoPlugin, CluePlugin)
  .settings(commonSettings: _*)
  .settings(
    scalacOptions += "-Ymacro-annotations",
    libraryDependencies ++=
      Seq(
        Http4sCirce,
        Squants.value,
        // OCS bundles
        SpModelCore,
        POT,
        OpenCSV,
        Http4sXml,
        Http4sBoopickle,
        PrometheusClient,
        Log4Cats.value,
        Log4CatsNoop.value,
        TestLibs.value,
        PPrint.value,
        Clue,
        ClueHttp4s,
        LucumaSchemas,
        ClueGenerator,
        ACM
      ) ++ MUnit.value ++ Http4s ++ Http4sClient ++ PureConfig ++ SeqexecOdb ++ Monocle.value ++ WDBAClient ++
        Circe.value,
    headerSources / excludeFilter := HiddenFileFilter || (file(
      "modules/server"
    ) / "src/main/scala/pureconfig/module/http4s/package.scala").getName
  )
  .settings(
    buildInfoUsePackageAsPath := true,
    buildInfoKeys ++= Seq[BuildInfoKey](name, version),
    buildInfoObject           := "OcsBuildInfo",
    buildInfoPackage          := "observe.server"
  )
  .dependsOn(observe_engine    % "compile->compile;test->test",
             giapi,
             ocs2_api.jvm,
             observe_model.jvm % "compile->compile;test->test"
  )

// Unfortunately crossProject doesn't seem to work properly at the module/build.sbt level
// We have to define the project properties at this level
lazy val observe_model = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/model"))
  .enablePlugins(GitBranchPrompt)
  .settings(
    scalacOptions += "-Ymacro-annotations",
    libraryDependencies ++= Seq(
      Squants.value,
      Mouse.value,
      BooPickle.value,
      CatsTime.value
    ) ++ MUnit.value ++ Monocle.value ++ LucumaCore.value ++ Sttp.value ++ Circe.value
  )
  .jvmSettings(
    commonSettings,
    libraryDependencies += Http4sCore
  )
  .jsSettings(
    // And add a custom one
    libraryDependencies += JavaTimeJS.value,
    coverageEnabled := false
  )

lazy val observe_engine = project
  .in(file("modules/engine"))
  .enablePlugins(GitBranchPrompt)
  .dependsOn(observe_model.jvm % "compile->compile;test->test")
  .settings(commonSettings: _*)
  .settings(
    scalacOptions += "-Ymacro-annotations",
    libraryDependencies ++= Seq(Fs2,
                                CatsEffect.value,
                                Log4s.value,
                                Log4Cats.value
    ) ++ Monocle.value ++ MUnit.value
  )

/**
 * Common settings for the Observe instances
 */
lazy val observeCommonSettings = Seq(
  // Main class for launching
  Compile / mainClass             := Some("observe.web.server.http4s.WebServerLauncher"),
  // This is important to keep the file generation order correctly
  Universal / parallelExecution   := false,
  // Depend on webpack and add the assets created by webpack
//  Compile / packageBin / mappings ++= (observe_web_client / Compile / fullOptJS / webpack).value
//    .map(f => f.data -> f.data.getName()),
  // Name of the launch script
  executableScriptName            := "observe-server",
  // No javadocs
  Compile / packageDoc / mappings := Seq(),
  // Don't create launchers for Windows
  makeBatScripts                  := Seq.empty,
  // Specify a different name for the config file
  bashScriptConfigLocation        := Some("${app_home}/../conf/launcher.args"),
  bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml"""",
  bashScriptExtraDefines += """addJava "-javaagent:${app_home}/jmx_prometheus_javaagent-0.3.1.jar=6060:${app_home}/prometheus.yaml"""",
  // Copy logback.xml to let users customize it on site
  Universal / mappings += {
    val f = (observe_web_server / Compile / resourceDirectory).value / "logback.xml"
    f -> ("conf/" + f.getName)
  },
  // Launch options
  Universal / javaOptions ++= Seq(
    // -J params will be added as jvm parameters
    "-J-Xmx1024m",
    "-J-Xms256m",
    // Support remote JMX access
    "-J-Dcom.sun.management.jmxremote",
    "-J-Dcom.sun.management.jmxremote.authenticate=false",
    "-J-Dcom.sun.management.jmxremote.port=2407",
    "-J-Dcom.sun.management.jmxremote.ssl=false",
    // Ensure the local is correctly set
    "-J-Duser.language=en",
    "-J-Duser.country=US",
    // Support remote debugging
    "-J-Xdebug",
    "-J-Xnoagent",
    "-J-XX:+HeapDumpOnOutOfMemoryError",
    // Make sure the application exits on OOM
    "-J-XX:+ExitOnOutOfMemoryError",
    "-J-XX:+CrashOnOutOfMemoryError",
    "-J-XX:HeapDumpPath=/tmp",
    "-J-Xrunjdwp:transport=dt_socket,address=8457,server=y,suspend=n",
    "-java-home ${app_home}/../jre" // This breaks builds without jre
  )
) ++ commonSettings

/**
 * Settings for Observe in Linux
 */
lazy val observeLinux = Seq(
  // User/Group for execution
  Linux / daemonUser     := "software",
  Linux / daemonGroup    := "software",
  Universal / maintainer := "Software Group <software@gemini.edu>",
  // This lets us build RPMs from snapshot versions
  Linux / name           := "Observe Server",
  Linux / version        := {
    (ThisBuild / version).value.replace("-SNAPSHOT", "").replace("-", "_").replace(" ", "")
  }
)

/**
 * Project for the observe server app for development
 */
lazy val app_observe_server = project
  .in(file("app/observe-server"))
  .enablePlugins(NoPublishPlugin)
//  .dependsOn(observe_web_server, observe_web_client)
//  .aggregate(observe_web_server, observe_web_client)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(GitBranchPrompt)
  .settings(observeCommonSettings: _*)
  .settings(
    description          := "Observe server for local testing",
    // Put the jar files in the lib dir
    Universal / mappings += {
      val jar = (Compile / packageBin).value
      jar -> ("lib/" + jar.getName)
    },
    Universal / mappings := {
      // filter out sjs jar files. otherwise it could generate some conflicts
      val universalMappings = (Universal / mappings).value
      val filtered          = universalMappings.filter { case (_, name) =>
        !name.contains("_sjs")
      }
      filtered
    },
    Universal / mappings += {
      val f = (Compile / resourceDirectory).value / "update_smartgcal"
      f -> ("bin/" + f.getName)
    },
    Universal / mappings += {
      val f = (Compile / resourceDirectory).value / "observe-server.env"
      f -> ("systemd/" + f.getName)
    },
    Universal / mappings += {
      val f = (Compile / resourceDirectory).value / "observe-server.service"
      f -> ("systemd/" + f.getName)
    }
  )

/**
 * Project for the observe test server at GS on Linux 64
 */
lazy val app_observe_server_gs_test =
  project
    .in(file("app/observe-server-gs-test"))
    .enablePlugins(NoPublishPlugin)
//    .dependsOn(observe_web_server, observe_web_client)
//    .aggregate(observe_web_server, observe_web_client)
    .enablePlugins(LinuxPlugin)
    .enablePlugins(JavaServerAppPackaging)
    .enablePlugins(SystemdPlugin)
    .enablePlugins(GitBranchPrompt)
    .settings(observeCommonSettings: _*)
    .settings(observeLinux: _*)
    .settings(deployedAppMappings: _*)
    .settings(
      description          := "Observe GS test deployment",
      applicationConfName  := "observe",
      applicationConfSite  := DeploymentSite.GS,
      Universal / mappings := {
        // filter out sjs jar files. otherwise it could generate some conflicts
        val universalMappings = (app_observe_server / Universal / mappings).value
        val filtered          = universalMappings.filter { case (_, name) =>
          !name.contains("_sjs")
        }
        filtered
      }
    )
    .settings(embeddedJreSettingsLinux64: _*)
    .dependsOn(observe_server)

/**
 * Project for the observe test server at GN on Linux 64
 */
lazy val app_observe_server_gn_test =
  project
    .in(file("app/observe-server-gn-test"))
    .enablePlugins(NoPublishPlugin)
//    .dependsOn(observe_web_server, observe_web_client)
//    .aggregate(observe_web_server, observe_web_client)
    .enablePlugins(LinuxPlugin, RpmPlugin)
    .enablePlugins(JavaServerAppPackaging)
    .enablePlugins(GitBranchPrompt)
    .settings(observeCommonSettings: _*)
    .settings(observeLinux: _*)
    .settings(deployedAppMappings: _*)
    .settings(
      description          := "Observe GN test deployment",
      applicationConfName  := "observe",
      applicationConfSite  := DeploymentSite.GN,
      Universal / mappings := {
        // filter out sjs jar files. otherwise it could generate some conflicts
        val universalMappings = (app_observe_server / Universal / mappings).value
        val filtered          = universalMappings.filter { case (_, name) =>
          !name.contains("_sjs")
        }
        filtered
      }
    )
    .settings(embeddedJreSettingsLinux64: _*)
    .dependsOn(observe_server)

/**
 * Project for the observe server app for production on Linux 64
 */
lazy val app_observe_server_gs = project
  .in(file("app/observe-server-gs"))
  .enablePlugins(NoPublishPlugin)
//  .dependsOn(observe_web_server, observe_web_client)
//  .aggregate(observe_web_server, observe_web_client)
  .enablePlugins(LinuxPlugin, RpmPlugin)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(GitBranchPrompt)
  .settings(observeCommonSettings: _*)
  .settings(observeLinux: _*)
  .settings(deployedAppMappings: _*)
  .settings(
    description          := "Observe Gemini South server production",
    applicationConfName  := "observe",
    applicationConfSite  := DeploymentSite.GS,
    Universal / mappings := {
      // filter out sjs jar files. otherwise it could generate some conflicts
      val universalMappings = (app_observe_server / Universal / mappings).value
      val filtered          = universalMappings.filter { case (_, name) =>
        !name.contains("_sjs")
      }
      filtered
    }
  )
  .settings(embeddedJreSettingsLinux64: _*)
  .dependsOn(observe_server)

/**
 * Project for the GN observe server app for production on Linux 64
 */
lazy val app_observe_server_gn = project
  .in(file("app/observe-server-gn"))
  .enablePlugins(NoPublishPlugin)
//  .dependsOn(observe_web_server, observe_web_client)
//  .aggregate(observe_web_server, observe_web_client)
  .enablePlugins(LinuxPlugin, RpmPlugin)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(GitBranchPrompt)
  .settings(observeCommonSettings: _*)
  .settings(observeLinux: _*)
  .settings(deployedAppMappings: _*)
  .settings(
    description          := "Observe Gemini North server production",
    applicationConfName  := "observe",
    applicationConfSite  := DeploymentSite.GN,
    Universal / mappings := {
      // filter out sjs jar files. otherwise it could generate some conflicts
      val universalMappings = (app_observe_server / Universal / mappings).value
      val filtered          = universalMappings.filter { case (_, name) =>
        !name.contains("_sjs")
      }
      filtered
    }
  )
  .settings(embeddedJreSettingsLinux64: _*)
  .dependsOn(observe_server)
