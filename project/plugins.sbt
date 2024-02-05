val sbtLucumaVersion = "0.11.12"
addSbtPlugin("edu.gemini" % "sbt-lucuma-app" % sbtLucumaVersion)
addSbtPlugin("edu.gemini" % "sbt-lucuma-css" % sbtLucumaVersion)

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.15.0")

// sbt revolver lets launching applications from the sbt console
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")

// Extract metadata from sbt and make it available to the code
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")

// Support making distributions
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")

// Generate code for GraphQL queries
addSbtPlugin("edu.gemini" % "sbt-clue" % "0.35.0")
