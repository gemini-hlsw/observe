val sbtLucumaVersion = "0.11.6"
addSbtPlugin("edu.gemini" % "sbt-lucuma-app"         % sbtLucumaVersion)
addSbtPlugin("edu.gemini" % "sbt-lucuma-sjs-bundler" % sbtLucumaVersion)
addSbtPlugin("edu.gemini" % "sbt-lucuma-css"         % sbtLucumaVersion)

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.2")

// sbt revolver lets launching applications from the sbt console
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")

// Extract metadata from sbt and make it available to the code
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")

// Support making distributions
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")

// Use NPM modules rather than webjars
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.21.1")

// Generate code for GraphQL queries
addSbtPlugin("edu.gemini" % "sbt-clue" % "0.32.0")

// Used to find dependencies
addDependencyTreePlugin

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")

Global / onLoad := { s => "dependencyUpdates" :: s }
