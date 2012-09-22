/**
 * Copyright (C) 2009, Progress Software Corporation and/or its
 * subsidiaries or affiliates.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package console
import org.fusesource.jansi.internal.CLibrary.STDOUT_FILENO
import org.fusesource.jansi.internal.CLibrary.isatty
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.PrintStream
import scala.util.Properties
import org.fusesource.jansi.AnsiOutputStream
import org.fusesource.jansi.WindowsAnsiOutputStream
import java.io.File

object Console {
  private var installed = 0;
  private var lock: AnyRef = new Object();

  private val nettyServer = {
    import play.core.server.NettyServer._
    val app = Option(System.getProperty("user.dir")).map(new File(_)).filter(p => p.exists && p.isDirectory).flatMap { applicationPath =>
      createServer(applicationPath)
    }
    app.getOrElse(System.exit(-1).asInstanceOf[Nothing])
  }

  val system_out = System.out;
  val html_out = new PlayOutputStream(system_out)
  /**
   * If the standard out natively supports ANSI escape codes, then this just
   * returns System.out, otherwise it will provide an ANSI aware PrintStream
   * which strips out the ANSI escape sequences or which implement the escape
   * sequences.
   *
   * @return a PrintStream which is ANSI aware.
   */
  val out = new PrintStream(html_out);

  val system_err = System.err;
  val html_err = new PlayOutputStream(system_err)
  /**
   * If the standard out natively supports ANSI escape codes, then this just
   * returns System.err, otherwise it will provide an ANSI aware PrintStream
   * which strips out the ANSI escape sequences or which implement the escape
   * sequences.
   *
   * @return a PrintStream which is ANSI aware.
   */
  val err = new PrintStream(html_err);

  /**
   * Install Console.out to System.out.
   */
  def systemInstall(): Unit = lock.synchronized {
    installed += 1;
    if (installed == 1) {
      System.setOut(out);
      System.setErr(err);
    }
  }

  /**
   * undo a previous {@link #systemInstall()}.  If {@link #systemInstall()} was called
   * multiple times, it {@link #systemUninstall()} must call the same number of times before
   * it is actually uninstalled.
   */
  def systemUninstall(): Unit = lock.synchronized {
    installed -= 1;
    if (installed == 0) {
      System.setOut(system_out);
      System.setErr(system_err);
      nettyServer.stop()
    }
  }

}