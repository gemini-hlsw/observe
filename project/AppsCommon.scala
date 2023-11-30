import com.typesafe.sbt.packager.MappingsHelper.*
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.*
import sbt.Keys.*
import sbt.{Project, Resolver, _}

/**
 * Define tasks and settings used by application definitions
 */
object AppsCommon {
  lazy val applicationConfName   =
    settingKey[String]("Name of the application to lookup the configuration")
  lazy val applicationConfSite   =
    settingKey[DeploymentSite]("Name of the site for the application configuration")
  lazy val downloadConfiguration =
    taskKey[Seq[File]]("Download a configuration file for an application")

  sealed trait LogType
  object LogType {
    case object ConsoleAndFiles extends LogType
    case object Files           extends LogType
  }

  sealed trait DeploymentSite {
    def site: String
  }
  object DeploymentSite       {
    case object GS extends DeploymentSite {
      override def site: String = "gs"
    }
    case object GN extends DeploymentSite {
      override def site: String = "gn"
    }
  }

  lazy val embeddedJreSettings = Seq(
    // Put the jre in the tarball
    Universal / mappings ++= {
      // We look for the JRE in the project's base directory. This can be a symlink.
      val jreDir = (ThisBuild / baseDirectory).value / "jre"
      if (!jreDir.exists)
        throw new Exception("JRE directory does not exist: " + jreDir)
      directory(jreDir).map { path =>
        path._1 -> ("jre/" + path._1.relativeTo(jreDir).get.getPath)
      }
    },

    // Make the launcher use the embedded jre
    Universal / javaOptions ++= Seq(
      "-java-home ${app_home}/../jre"
    )
  )
}
