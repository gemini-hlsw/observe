val sbtLucumaVersion = "0.14.3"
addSbtPlugin("edu.gemini" % "sbt-lucuma-app"    % sbtLucumaVersion)
addSbtPlugin("edu.gemini" % "sbt-lucuma-css"    % sbtLucumaVersion)
addSbtPlugin("edu.gemini" % "sbt-lucuma-docker" % sbtLucumaVersion)

// sbt revolver lets launching applications from the sbt console
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")

// Extract metadata from sbt and make it available to the code
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")

// Generate code for GraphQL queries
addSbtPlugin("edu.gemini" % "sbt-clue" % "0.48.0")
