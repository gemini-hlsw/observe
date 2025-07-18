import _root_.cats.effect.kernel.syntax.resource
import Settings.Libraries._
import Settings.LibraryVersions
import Common._
import sbt.Keys._
import NativePackagerHelper._
import org.scalajs.linker.interface.ModuleSplitStyle
import scala.sys.process._
import sbt.nio.file.FileTreeView

val build = taskKey[File]("Build module for deployment")

name := "observe"

ThisBuild / dockerExposedPorts ++= Seq(9090, 9091) // Must match deployed app.conf web-server.port
ThisBuild / dockerBaseImage := "eclipse-temurin:17-jre"

// TODO REMOVE ONCE THIS WORKS AGAIN
ThisBuild / tlCiScalafmtCheck := false
ThisBuild / tlCiScalafixCheck := false

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

lazy val dockerHubLogin =
  WorkflowStep.Run(
    List(
      "echo ${{ secrets.DOCKER_HUB_TOKEN }} | docker login --username nlsoftware --password-stdin"
    ),
    name = Some("Login to Docker Hub")
  )

lazy val sbtDockerPublish =
  WorkflowStep.Sbt(
    List("clean", "deploy/docker:publish"),
    name = Some("Build and Publish Docker image")
  )

lazy val herokuRelease =
  WorkflowStep.Run(
    List(
      "npm install -g heroku",
      "heroku container:login",
      "docker tag noirlab/gpp-obs registry.heroku.com/${{ vars.HEROKU_APP_NAME_GN || 'observe-dev-gn' }}/web",
      "docker push registry.heroku.com/${{ vars.HEROKU_APP_NAME_GN || 'observe-dev-gn' }}/web",
      "heroku container:release web -a ${{ vars.HEROKU_APP_NAME_GN || 'observe-dev-gn' }} -v",
      "docker tag noirlab/gpp-obs registry.heroku.com/${{ vars.HEROKU_APP_NAME_GS || 'observe-dev-gs' }}/web",
      "docker push registry.heroku.com/${{ vars.HEROKU_APP_NAME_GS || 'observe-dev-gs' }}/web",
      "heroku container:release web -a ${{ vars.HEROKU_APP_NAME_GS || 'observe-dev-gs' }} -v"
    ),
    name = Some("Deploy and release app in Heroku")
  )

ThisBuild / githubWorkflowAddedJobs +=
  WorkflowJob(
    "deploy",
    "Build and publish Docker image / Deploy to Heroku",
    githubWorkflowJobSetup.value.toList :::
      setupNodeNpmInstall :::
      dockerHubLogin ::
      sbtDockerPublish ::
      herokuRelease ::
      Nil,
    scalas = List(scalaVersion.value),
    javas = githubWorkflowJavaVersions.value.toList.take(1),
    cond = Some(allConds(mainCond, geminiRepoCond))
  )

ThisBuild / lucumaCssExts += "svg"

Global / onChangedBuildSource                   := ReloadOnSourceChanges
ThisBuild / scalafixDependencies += "edu.gemini" % "lucuma-schemas_3" % LibraryVersions.lucumaSchemas
ThisBuild / scalaVersion                        := "3.7.1"
ThisBuild / crossScalaVersions                  := Seq("3.7.1")
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
  observe_ui_model
)

lazy val stateengine = project
  .in(file("modules/stateengine"))
  .settings(
    name := "stateengine",
    libraryDependencies ++= Seq(
      Mouse.value,
      Fs2.value
    ) ++ MUnit.value ++ Cats.value ++ CatsEffect.value
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
  .dependsOn(observe_server)
  .dependsOn(observe_model.jvm % "compile->compile;test->test")

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
      Clue.value,
      ClueJs.value,
      Fs2.value,
      Http4sClient.value,
      Http4sDom.value
    ) ++ Crystal.value ++ LucumaUI.value ++ LucumaSchemas.value ++ ScalaJSReactIO.value ++ Cats.value ++ CatsEffect.value ++ LucumaReact.value ++ Monocle.value ++ LucumaCore.value ++ Log4CatsLogLevel.value,
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
        Http4sXml,
        Log4Cats.value,
        Log4CatsNoop.value,
        PPrint.value,
        Clue.value,
        ClueHttp4s,
        ClueNatchez,
        CatsParse.value,
        ACM,
        GiapiScala
      ) ++ Coulomb.value ++ LucumaSchemas.value ++ MUnit.value ++ Http4s ++ Http4sJDKClient.value ++ PureConfig ++ Monocle.value ++
        Circe.value ++ Natchez ++ CatsEffect.value,
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
  .dependsOn(observe_model.jvm % "compile->compile;test->test")
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
      Mouse.value,
      CatsTime.value,
      Http4sCore.value,
      Http4sCirce.value,
      Http4sLaws.value,
      LucumaODBSchema.value
    ) ++ Coulomb.value ++ MUnit.value ++ Monocle.value ++ LucumaCore.value ++ Circe.value
  )
  .jvmSettings(commonSettings)
  .jsSettings(
    // And add a custom one
    libraryDependencies += JavaTimeJS.value,
    coverageEnabled := false
  )

/**
 * Mappings common to applications, including configurations and web application.
 */
lazy val deployedAppMappings = Seq(
  Universal / mappings ++= {
    val clientDir: File                         = (observe_web_client / build).value
    val clientMappings: Seq[(File, String)]     =
      directory(clientDir).flatMap(path =>
        // Don't include environment confs, if present.
        if (path._2.endsWith(".conf.json")) None
        else Some(path._1 -> ("app/" + path._1.relativeTo(clientDir).get.getPath))
      )
    val siteConfigDir: File                     = (ThisProject / baseDirectory).value / "conf"
    val siteConfigMappings: Seq[(File, String)] = directory(siteConfigDir).map(path =>
      path._1 -> ("conf/" + path._1.relativeTo(siteConfigDir).get.getPath)
    )
    clientMappings ++ siteConfigMappings
  }
)

/**
 * Common settings for the Observe instances
 */
lazy val deployedAppSettings = Seq(
  // Main class for launching
  Compile / mainClass             := Some("observe.web.server.http4s.WebServerLauncher"),
  // Name of the launch script
  executableScriptName            := "observe-server",
  // No javadocs
  Compile / packageDoc / mappings := Seq(),
  // Don't create launchers for Windows
  makeBatScripts                  := Seq.empty,
  // Specify a different name for the config file
  bashScriptConfigLocation        := Some("${app_home}/../conf/launcher.args"),
  bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=${app_home}/../conf/$SITE/logback.xml"""",
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
    // Ensure the locale is correctly set
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
) ++ deployedAppMappings ++ commonSettings

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
  .settings(deployedAppSettings: _*)
  .settings(
    description            := "Observe Server",
    Docker / packageName   := "gpp-obs",
    Docker / daemonUserUid := Some("3624"),
    Docker / daemonUser    := "software",
    dockerBuildOptions ++= Seq("--platform", "linux/amd64"),
    dockerUpdateLatest     := true,
    dockerUsername         := Some("noirlab")
  )
