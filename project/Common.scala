import Settings.Libraries.*
import sbt.Keys.*
import sbt.*
import com.timushev.sbt.updates.UpdatesPlugin.autoImport.*

/**
 * Define tasks and settings used by module definitions
 */
object Common {
  lazy val commonSettings = Seq(
    Compile / packageDoc / mappings := Seq(),
    Compile / doc / sources         := Seq.empty,
    testFrameworks += new TestFramework("munit.Framework")
  )

}
