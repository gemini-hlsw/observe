val sbtLucumaVersion = "0.12.6"
addSbtPlugin("edu.gemini" % "sbt-lucuma-app" % sbtLucumaVersion)
addSbtPlugin("edu.gemini" % "sbt-lucuma-css" % sbtLucumaVersion)

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.18.2")

// sbt revolver lets launching applications from the sbt console
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")

// Extract metadata from sbt and make it available to the code
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")

// Support making distributions
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.0")

// Generate code for GraphQL queries
addSbtPlugin("edu.gemini" % "sbt-clue" % "0.43.0")
