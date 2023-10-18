import Settings.Libraries._
import Settings.LibraryVersions
import Common._
import AppsCommon._
import sbt.Keys._
import NativePackagerHelper._
import com.typesafe.sbt.packager.docker._
import org.scalajs.linker.interface.ModuleSplitStyle

name := "observe"

ThisBuild / resolvers := List(Resolver.mavenLocal)

ThisBuild / githubWorkflowSbtCommand := "sbt -v -J-Xmx6g"

ThisBuild / lucumaCssExts += "svg"

ThisBuild / tlFatalWarnings := false // TODO: Remove this when we are ready to have linting checks

Global / onChangedBuildSource                   := ReloadOnSourceChanges
ThisBuild / scalafixDependencies += "edu.gemini" % "lucuma-schemas_3" % LibraryVersions.lucumaSchemas
ThisBuild / scalafixScalaBinaryVersion          := "2.13"
ThisBuild / scalaVersion                        := "3.3.1"
ThisBuild / crossScalaVersions                  := Seq("3.3.1")
ThisBuild / scalacOptions ++= Seq("-language:implicitConversions")
ThisBuild / scalafixResolvers += coursierapi.MavenRepository.of(
  "https://s01.oss.sonatype.org/content/repositories/snapshots/"
)

// Gemini repository
ThisBuild / resolvers += "Gemini Repository".at(
  "https://github.com/gemini-hlsw/maven-repo/raw/master/releases"
)

// This key is used to find the JRE dir. It could/should be overridden on a user basis
// Add e.g. a `jres.sbt` file with your particular configuration
ThisBuild / ocsJreDir := Path.userHome / ".jres11"

ThisBuild / evictionErrorLevel := Level.Info

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

//////////////
// Projects
//////////////

lazy val root = tlCrossRootProject.aggregate(
  observe_web_server,
  observe_web_client,
  observe_server,
  observe_model,
  observe_ui_model,
  observe_engine
)

lazy val stateengine = project
  .in(file("modules/stateengine"))
  .settings(
    name := "stateengine",
    libraryDependencies ++= Seq(
      CatsEffect.value,
      Mouse.value,
      Fs2.value
    ) ++ MUnit.value ++ Cats.value
  )

lazy val observe_web_server = project
  .in(file("modules/web/server"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(GitBranchPrompt)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(UnboundId,
                                LucumaSSO.value,
                                JwtCore,
                                JwtCirce,
                                Http4sServer,
                                Log4CatsNoop.value
    ) ++
      Http4sClient ++ Http4s ++ PureConfig ++ Logging.value,
    // Supports launching the server in the background
    reStart / mainClass := Some("observe.web.server.http4s.WebServerLauncher")
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

lazy val observe_ui_model = project
  .in(file("modules/web/client-model"))
  .settings(lucumaGlobalSettings: _*)
  .enablePlugins(ScalaJSPlugin)
  .settings(
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      LucumaSchemas.value
    ) ++ LucumaCore.value ++ Circe.value
  )

lazy val observe_web_client = project
  .in(file("modules/web/client"))
  .settings(lucumaGlobalSettings: _*)
  .settings(esModule: _*)
  .enablePlugins(ScalaJSPlugin, LucumaCssPlugin, CluePlugin, BuildInfoPlugin)
  .settings(
    Test / test      := {},
    coverageEnabled  := false,
    libraryDependencies ++= Seq(
      Kittens.value,
      CatsEffect.value,
      Clue.value,
      ClueJs.value,
      Crystal.value,
      Fs2.value,
      Http4sDom.value,
      LucumaUI.value
    ) ++ ScalaJSReactIO.value ++ Cats.value ++ LucumaReact.value ++ Monocle.value ++ LucumaCore.value ++ Log4CatsLogLevel.value,
    scalacOptions ~= (_.filterNot(Set("-Vtype-diffs"))),
    buildInfoKeys    := Seq[BuildInfoKey](
      scalaVersion,
      sbtVersion,
      git.gitHeadCommit,
      "buildDateTime" -> System.currentTimeMillis()
    ),
    buildInfoPackage := "observe.ui"
  )
  .dependsOn(observe_model.js, observe_ui_model)

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
        Http4sXml,
        Log4Cats.value,
        Log4CatsNoop.value,
        PPrint.value,
        Clue.value,
        ClueHttp4s,
        LucumaSchemas.value,
        Atto,
        ACM,
        GiapiScala
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
      PPrint.value,
      Mouse.value,
      CatsTime.value
    ) ++ MUnit.value ++ Monocle.value ++ LucumaCore.value ++ Circe.value
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
