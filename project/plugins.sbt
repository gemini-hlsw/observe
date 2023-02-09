val sbtLucumaVersion = "0.10.9"
addSbtPlugin("edu.gemini" % "sbt-lucuma-app"         % sbtLucumaVersion)
addSbtPlugin("edu.gemini" % "sbt-lucuma-sjs-bundler" % sbtLucumaVersion)

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.0")

// sbt revolver lets launching applications from the sbt console
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

// Extract metadata from sbt and make it available to the code
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")

// Support making distributions
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.14")

// Use NPM modules rather than webjars
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0")

// Used to find dependencies
addDependencyTreePlugin

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")

Global / onLoad := { s => "dependencyUpdates" :: s }
