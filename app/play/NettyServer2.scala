package playterminal.core

import play.core.server.NettyServer
import java.io.FileOutputStream
import java.io.File
import play.core.StaticApplication
import play.core.ApplicationProvider
import play.core.Invoker
import play.api.Mode

object NettyServer2 {

  /**
   * creates a NettyServer based on the application represented by applicationPath
   * @param applicationPath path to application
   */
  def createServer(applicationPath: File): Option[NettyServer] = {
    // Manage RUNNING_PID file
    java.lang.management.ManagementFactory.getRuntimeMXBean.getName.split('@').headOption.map { pid =>
      val pidFile = Option(System.getProperty("pidfile.path")).map(new File(_)).getOrElse(new File(applicationPath.getAbsolutePath, "RUNNING_PID"))

      // The Logger is not initialized yet, we print the Process ID on STDOUT
      println("Play server process ID is " + pid)

      if (pidFile.getAbsolutePath != "/dev/null") {
        if (pidFile.exists) {
          println("This application is already running (Or delete " + pidFile.getAbsolutePath + " file).")
          System.exit(-1)
        }

        new FileOutputStream(pidFile).write(pid.getBytes)
        Runtime.getRuntime.addShutdownHook(new Thread {
          override def run {
            pidFile.delete()
          }
        })
      }
    }

    try {
      val appProvider = new StaticApplication(applicationPath);
      val server = new {
        override val invoker = new Invoker2(Some(appProvider))
      } with NettyServer(
        appProvider,
        Option(System.getProperty("http.port")).map(Integer.parseInt(_)).getOrElse(9000),
        Option(System.getProperty("https.port")).map(Integer.parseInt(_)),
        Option(System.getProperty("http.address")).getOrElse("0.0.0.0"))

      Runtime.getRuntime.addShutdownHook(new Thread {
        override def run {
          server.stop()
        }
      })

      Some(server)
    } catch {
      case e => {
        println("Oops, cannot start the server.")
        e.printStackTrace()
        None
      }
    }

  }

}