import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "play-terminal"
    val appVersion      = "1.0"

    val appDependencies = Seq(
              // Add your project dependencies here,
        "org.fusesource.jansi" % "jansi" % "1.9"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      // Add your own project settings here      
    )

}
