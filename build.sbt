import _root_.cats.effect.kernel.syntax.resource
import Settings.Libraries._
import Settings.LibraryVersions
import Common._
import sbt.Keys._
import NativePackagerHelper._
import org.scalajs.linker.interface.ModuleSplitStyle
import scala.sys.process._
import sbt.nio.file.FileTreeView

val herokuAppName = settingKey[String]("Heroku staging app name")

val build = taskKey[File]("Build module for deployment")

name := "observe"

ThisBuild / dockerExposedPorts ++= Seq(9090, 9091) // Must match deployed app.conf web-server.port
ThisBuild / dockerBaseImage := "eclipse-temurin:17-jre"

ThisBuild / resolvers := List(Resolver.mavenLocal)

val pushCond          = "github.event_name == 'push'"
val prCond            = "github.event_name == 'pull_request'"
val mainCond          = "github.ref == 'refs/heads/main'"
val notMainCond       = "github.ref != 'refs/heads/main'"
val geminiRepoCond    = "startsWith(github.repository, 'gemini')"
val notDependabotCond = "github.actor != 'dependabot[bot]'"
val isMergedCond      = "github.event.pull_request.merged == true"
def allConds(conds: String*) = conds.mkString("(", " && ", ")")
def anyConds(conds: String*) = conds.mkString("(", " || ", ")")

val faNpmAuthToken = "FONTAWESOME_NPM_AUTH_TOKEN" -> "${{ secrets.FONTAWESOME_NPM_AUTH_TOKEN }}"
val herokuToken    = "HEROKU_API_KEY"             -> "${{ secrets.HEROKU_API_KEY }}"

ThisBuild / githubWorkflowSbtCommand := "sbt -v -J-Xmx6g"
ThisBuild / githubWorkflowEnv += faNpmAuthToken
ThisBuild / githubWorkflowEnv += herokuToken

lazy val setupNodeNpmInstall =
  List(
    WorkflowStep.Use(
      UseRef.Public("actions", "setup-node", "v4"),
      name = Some("Setup Node.js"),
      params = Map(
        "node-version"          -> "20",
        "cache"                 -> "npm",
        "cache-dependency-path" -> "modules/web/client/package-lock.json"
      )
    ),
    WorkflowStep.Use(
      UseRef.Public("actions", "cache", "v3"),
      name = Some("Cache node_modules"),
      id = Some("cache-node_modules"),
      params = {
        val prefix = "node_modules"
        val key    = s"$prefix-$${{ hashFiles('modules/web/client/package-lock.json') }}"
        Map("path" -> "node_modules", "key" -> key, "restore-keys" -> prefix)
      }
    ),
    WorkflowStep.Run(
      List("cd modules/web/client", "npm clean-install --verbose"),
      name = Some("npm clean-install"),
      cond = Some("steps.cache-node_modules.outputs.cache-hit != 'true'")
    )
  )

lazy val herokuLogin =
  WorkflowStep.Run(
    List("heroku container:login"),
    name = Some("Login to Heroku Container")
  )

lazy val sbtDeploy =
  WorkflowStep.Sbt(
    List("deploy_observe_server_staging/docker:publish"),
    name = Some("Build and deploy Docker image to Heroku")
  )

lazy val herokuRelease =
  WorkflowStep.Run(
    List("heroku container:release web -a ${{ secrets.HEROKU_APP_NAME }}"),
    name = Some("Release app in Heroku")
  )

ThisBuild / githubWorkflowAddedJobs +=
  WorkflowJob(
    "deploy",
    "Deploy staging app to Heroku",
    WorkflowStep.Checkout ::
      WorkflowStep.SetupJava(githubWorkflowJavaVersions.value.toList.take(1)) :::
      setupNodeNpmInstall :::
      herokuLogin ::
      sbtDeploy ::
      herokuRelease ::
      Nil,
    scalas = List(scalaVersion.value),
    javas = githubWorkflowJavaVersions.value.toList.take(1),
    cond = Some(allConds(mainCond, geminiRepoCond))
  )

ThisBuild / lucumaCssExts += "svg"

Global / onChangedBuildSource                   := ReloadOnSourceChanges
ThisBuild / scalafixDependencies += "edu.gemini" % "lucuma-schemas_3" % LibraryVersions.lucumaSchemas
ThisBuild / scalafixScalaBinaryVersion          := "2.13"
ThisBuild / scalaVersion                        := "3.3.3"
ThisBuild / crossScalaVersions                  := Seq("3.3.3")
ThisBuild / scalacOptions ++= Seq("-language:implicitConversions")
ThisBuild / scalafixResolvers += coursierapi.MavenRepository.of(
  "https://s01.oss.sonatype.org/content/repositories/snapshots/"
)

// Gemini repository
ThisBuild / resolvers += "Gemini Repository".at(
  "https://github.com/gemini-hlsw/maven-repo/raw/master/releases"
)

// TODO Remove once we stop using http4s snapshot
ThisBuild / resolvers += "s01-oss-sonatype-org-snapshots".at(
  "https://s01.oss.sonatype.org/content/repositories/snapshots"
)

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
    libraryDependencies ++= Seq(
      UnboundId,
      LucumaSSO.value,
      JwtCore,
      JwtCirce,
      Http4sServer,
      Log4CatsNoop.value
    ) ++
      Http4sJDKClient.value ++ Http4s ++ PureConfig ++ Logging.value,
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
  .settings(
    Compile / packageBin / mappings ~= { _.filter(!_._1.getName.endsWith(".conf")) },
    Compile / packageBin / mappings ~= { _.filter(!_._1.getName.endsWith("logback.xml")) }
  )
  .dependsOn(observe_server)
  .dependsOn(observe_model.jvm % "compile->compile;test->test")

/**
 * Mappings common to applications, including configuration and web application
 */
lazy val deployedAppMappings = Seq(
  Universal / mappings ++= {
    val clientDir = (observe_web_client / build).value
    directory(clientDir).flatMap(path =>
      // Don't include environment confs, if present.
      if (path._2.endsWith(".conf.json")) None
      else Some(path._1 -> ("app/" + path._1.relativeTo(clientDir).get.getPath))
    )
  },

  // The only thing we include from the base deployment app is app.conf. We remove the "conf" path.
  Compile / packageBin / mappings ~= {
    _.filter(_._1.getName.endsWith(".conf")).map(mapping => mapping._1 -> mapping._1.getName)
  }
)

// Mappings for a particular release.
lazy val releaseAppMappings = Seq(
  // Copy the resource directory, with customized configuration files, but first remove existing mappings.
  Universal / mappings := { // maps =>
    val resourceDir         = (Compile / resourceDirectory).value
    val resourceDirMappings =
      directory(resourceDir).map(path => path._1 -> path._1.relativeTo(resourceDir).get.getPath)
    val resourceDirFiles    = resourceDirMappings.map(_._2)
    (Universal / mappings).value.filterNot(map => resourceDirFiles.contains(map._2)) ++
      resourceDirMappings
  }
)

lazy val observe_ui_model = project
  .in(file("modules/web/client-model"))
  .settings(lucumaGlobalSettings: _*)
  .enablePlugins(ScalaJSPlugin)
  .settings(
    coverageEnabled := false,
    libraryDependencies ++= Crystal.value ++ LucumaUI.value ++ LucumaSchemas.value ++ LucumaCore.value ++ Circe.value ++ MUnit.value
  )
  .dependsOn(observe_model.js)

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
      Fs2.value,
      Http4sClient.value,
      Http4sDom.value
    ) ++ Crystal.value ++ LucumaUI.value ++ LucumaSchemas.value ++ ScalaJSReactIO.value ++ Cats.value ++ LucumaReact.value ++ Monocle.value ++ LucumaCore.value ++ Log4CatsLogLevel.value,
    scalacOptions ~= (_.filterNot(Set("-Vtype-diffs"))),
    buildInfoKeys    := Seq[BuildInfoKey](
      scalaVersion,
      sbtVersion,
      git.gitHeadCommit,
      "buildDateTime" -> System.currentTimeMillis()
    ),
    buildInfoPackage := "observe.ui",
    Test / scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
  )
  .settings(
    build / fileInputs += (Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value.toGlob,
    build := {
      if ((Process("npx" :: "vite" :: "build" :: Nil, baseDirectory.value) !) != 0)
        throw new Exception("Error building web client")
      else
        baseDirectory.value / "deploy" // Must match directory declared in vite.config.js
    },
    build := build.dependsOn(Compile / fullLinkJS).value
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
        Http4sCirce.value,
        Squants.value,
        Http4sXml,
        Log4Cats.value,
        Log4CatsNoop.value,
        PPrint.value,
        Clue.value,
        ClueHttp4s,
        CatsParse.value,
        ACM,
        GiapiScala
      ) ++ LucumaSchemas.value ++ MUnit.value ++ Http4s ++ Http4sJDKClient.value ++ PureConfig ++ Monocle.value ++
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
      CatsTime.value,
      Http4sCore.value,
      Http4sCirce.value,
      Http4sLaws.value,
      LucumaODBSchema.value
    ) ++ MUnit.value ++ Monocle.value ++ LucumaCore.value ++ Circe.value
  )
  .jvmSettings(commonSettings)
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
    libraryDependencies ++= Seq(
      Fs2.value,
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
    "-J-Xrunjdwp:transport=dt_socket,address=8457,server=y,suspend=n"
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
lazy val deploy = project
  .in(file("deploy"))
  .enablePlugins(NoPublishPlugin)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(GitBranchPrompt)
  .dependsOn(observe_web_server)
  .settings(observeCommonSettings: _*)
  .settings(deployedAppMappings: _*)
  .settings(releaseAppMappings: _*)
  .settings(
    description          := "Observe staging server",
    Docker / packageName := "observe-staging",
    dockerRepository     := Some(s"registry.heroku.com/${herokuAppName.value}/web"),
    // dockerUpdateLatest   := true,
    herokuAppName        := sys.env.getOrElse("HEROKU_APP_NAME", "observe-staging"),
    dockerAliases ++= List(
      DockerAlias(
        Some("registry.heroku.com"),
        None,
        s"${herokuAppName.value}/web",
        None
      ),
      DockerAlias(
        Some("registry.heroku.com"),
        None,
        s"${herokuAppName.value}/web",
        Some(version.value)
      ),
      DockerAlias(
        None,
        Some("noirlab"),
        "gpp-obs",
        None
      ),
      DockerAlias(
        None,
        Some("noirlab"),
        "gpp-obs",
        Some(version.value)
      )
    )
  )
