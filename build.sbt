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

ThisBuild / resolvers := List(Resolver.mavenLocal)

ThisBuild / Compile / packageDoc / publishArtifact := false
ThisBuild / Test / bspEnabled                      := false

ThisBuild / githubWorkflowSbtCommand := "sbt -v -J-Xmx6g"

ThisBuild / lucumaCssExts += "svg"

ThisBuild / tlFatalWarnings := false // TODO: Remove this when we are ready to have linting checks

inThisBuild(
  Seq(
    Global / onChangedBuildSource                            := ReloadOnSourceChanges,
    scalafixDependencies += "edu.gemini"                      % "lucuma-schemas_3" % Settings.LibraryVersions.lucumaSchemas,
    scalafixScalaBinaryVersion                               := "2.13",
    ScalafixConfig / bspEnabled.withRank(KeyRanks.Invisible) := false
  ) ++ lucumaPublishSettings
)

ThisBuild / scalaVersion       := "3.3.1"
ThisBuild / crossScalaVersions := Seq("3.3.1")
ThisBuild / scalacOptions ++= Seq("-language:implicitConversions")

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
    ModuleSplitStyle.FewestModules
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

ThisBuild / scalafixResolvers += coursierapi.MavenRepository.of(
  "https://s01.oss.sonatype.org/content/repositories/snapshots/"
)

lazy val root = tlCrossRootProject.aggregate(
  giapi,
//  ocs2_api,
  observe_web_server,
  observe_web_client,
  observe_server,
  observe_model,
  observe_engine
)

lazy val stateengine = project
  .in(file("modules/stateengine"))
  .settings(
    name := "stateengine",
    libraryDependencies ++= Seq(
      Cats.value,
      CatsEffect.value,
      Mouse.value,
      Fs2.value
    ) ++ MUnit.value
  )

lazy val giapi = project
  .in(file("modules/giapi"))
  .enablePlugins(GitBranchPrompt)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(Cats.value,
                                Mouse.value,
                                CatsEffect.value,
                                Fs2.value,
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

//lazy val ocs2_api = crossProject(JVMPlatform, JSPlatform)
//  .crossType(CrossType.Pure)
//  .in(file("modules/ocs2_api"))
//  .settings(commonSettings)
//  .settings(
//    name := "ocs2-api",
//    libraryDependencies ++= Seq(CatsTime.value) ++
//      LucumaCore.value
//  )
//  .jsSettings(coverageEnabled := false)
//  .dependsOn(observe_model)

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

lazy val new_model = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/new_model"))
  .enablePlugins(GitBranchPrompt)
  .settings(
    libraryDependencies ++= Seq(
      Cats.value,
      Kittens.value,
      CatsTime.value,
      LucumaSchemas.value
    ) ++ MUnit.value ++ Monocle.value ++ LucumaCore.value ++ Circe.value
  )
  .jvmSettings(
    commonSettings,
    libraryDependencies += Http4sCore
  )
  .jsSettings(
    // And add a custom one
    libraryDependencies += JavaTimeJS.value,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
  )

lazy val observe_web_client = project
  .in(file("modules/web"))
  .settings(lucumaGlobalSettings: _*)
  .settings(esModule: _*)
  .enablePlugins(ScalaJSPlugin, LucumaCssPlugin, CluePlugin, BuildInfoPlugin)
  .settings(
    Test / test                             := {},
    coverageEnabled                         := false,
    libraryDependencies ++= Seq(
      Cats.value,
      Kittens.value,
      CatsEffect.value,
      Clue.value,
      ClueJs.value,
      Crystal.value,
      Fs2.value,
      LucumaUI.value
    ) ++ ScalaJSReactIO.value ++ LucumaReact.value ++ Monocle.value ++ LucumaCore.value ++ Log4CatsLogLevel.value,
    // TODO Remove this, only used for prototype:
    libraryDependencies += ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0")
      .cross(CrossVersion.for3Use2_13), // Do not use this, it's insecure. Substitute with GenUUID
    scalacOptions ~= (_.filterNot(Set("-Vtype-diffs"))),
    buildInfoKeys    := Seq[BuildInfoKey](
      scalaVersion,
      sbtVersion,
      git.gitHeadCommit,
      "buildDateTime" -> System.currentTimeMillis()
    ),
    buildInfoPackage := "observe.ui"
  )
  .dependsOn(new_model.js)

// List all the modules and their inter dependencies
lazy val observe_server = project
  .in(file("modules/server_new"))
  .enablePlugins(GitBranchPrompt, BuildInfoPlugin, CluePlugin)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++=
      Seq(
        Http4sCirce,
        Squants.value,
        OpenCSV,
        Http4sXml,
        Http4sBoopickle,
        PrometheusClient,
        Log4Cats.value,
        Log4CatsNoop.value,
        TestLibs.value,
        PPrint.value,
        Clue.value,
        ClueHttp4s,
        LucumaSchemas.value,
        ACM,
        Atto
      ) ++ MUnit.value ++ Http4s ++ Http4sClient ++ PureConfig ++ Monocle.value ++
        Circe.value,
    headerSources / excludeFilter := HiddenFileFilter || (file(
      "modules/server_new"
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
//             ocs2_api.jvm,
             observe_model.jvm % "compile->compile;test->test"
  )
  .settings(
    unmanagedSources / excludeFilter := (unmanagedSources / excludeFilter).value
      || (Compile / sourceDirectory).value + "/scala/observe/server/flamingos2/*"
      || (Compile / sourceDirectory).value + "/scala/observe/server/ghost/*"
      || (Compile / sourceDirectory).value + "/scala/observe/server/gnirs/*"
      || (Compile / sourceDirectory).value + "/scala/observe/server/gpi/*"
      || (Compile / sourceDirectory).value + "/scala/observe/server/gsaoi/*"
      || (Compile / sourceDirectory).value + "/scala/observe/server/nifs/*"
      || (Compile / sourceDirectory).value + "/scala/observe/server/niri/*"
  )

// Unfortunately crossProject doesn't seem to work properly at the module/build.sbt level
// We have to define the project properties at this level
lazy val observe_model = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/model"))
  .enablePlugins(GitBranchPrompt)
  .settings(
    libraryDependencies ++= Seq(
      Squants.value,
      Mouse.value,
      BooPickle.value,
      CatsTime.value,
      Atto
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
    libraryDependencies ++= Seq(Fs2.value,
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
