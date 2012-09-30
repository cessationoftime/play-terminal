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

import scala.collection.JavaConversions._
import java.io.IOException
import java.io.OutputStream
import models.ChatRoom
import models.HtmlText
import models.Text
object HtmlAnsiOutputStream extends AnsiOutputStreamIdentifiers {

  private[console] val ANSI_COLOR_MAP = Array("black", "red",
    "green", "yellow", "blue", "magenta", "cyan", "white");

  private[console] val BYTES_QUOT = "&quot;" //.getBytes();
  private[console] val BYTES_AMP = "&amp;" //.getBytes();
  private[console] val BYTES_LT = "&lt;" //.getBytes();
  private[console] val BYTES_GT = "&gt;" //.getBytes();

}

case class HtmlTag(name: String, attribute: String = "") {
  def startTag = "<" + name + " " + attribute + ">"
  def endTag = "</" + name + ">"
}
/**
 * @author <a href="http://code.dblock.org">Daniel Doubrovkine</a>
 */
class HtmlAnsiOutputStream(os: OutputStream) extends AnsiOutputStream(os) {
  import HtmlAnsiOutputStream._
  private var concealOn = false;

  @throws(classOf[IOException])
  override def close() = {
    closeTags;
    super.close();
  }

  private val closingAttributes = collection.mutable.Stack.empty[HtmlTag];

  def apply(html: String) {
    ChatRoom.default ! HtmlText("PlayOutputStream", html)
  }

  def apply(data: Char) {
    ChatRoom.default ! Text("PlayOutputStream", data)
  }
  def apply(data: Int) {
    apply(data.toChar)
  }

  @throws(classOf[IOException])
  private def write(s: String): Unit = {
    apply(s);
    out.write(s.getBytes());
  }

  @throws(classOf[IOException])
  private def writeTag(s: HtmlTag): Unit = {
    apply(s.startTag);
    closingAttributes.push(s);
  }

  @throws(classOf[IOException])
  private def closeTags: Unit =
    while (!closingAttributes.isEmpty) {
      apply(closingAttributes.pop.endTag);
    }

  @throws(classOf[IOException])
  override def write(data: Int): Unit = {
    data match {
      case 34 => apply(BYTES_QUOT); // "
      case 38 => apply(BYTES_AMP); // &			
      case 60 => apply(BYTES_LT); // <
      case 62 => apply(BYTES_GT); // >
      case _ => super.write(data); apply(data);
    }
    out.write(data)
  }

  @throws(classOf[IOException])
  def writeLine(buf: Array[Byte], offset: Int, len: Int): Unit = {
    write(buf, offset, len);
    closeTags;
  }

  @throws(classOf[IOException])
  protected override def processSetAttribute(attribute: Int): Unit = attribute match {
    case ATTRIBUTE_CONCEAL_ON =>
      // write("\u001B[8m");
      concealOn = true;
    case ATTRIBUTE_INTENSITY_BOLD => writeTag(HtmlTag("b"));
    case ATTRIBUTE_INTENSITY_NORMAL => closeTags;
    case ATTRIBUTE_UNDERLINE => writeTag(HtmlTag("u"));
    case ATTRIBUTE_UNDERLINE_OFF => closeTags;
    case ATTRIBUTE_NEGATIVE_ON =>
    case ATTRIBUTE_NEGATIVE_Off =>
  }

  @throws(classOf[IOException])
  protected override def processAttributeRest(): Unit = {
    if (concealOn) {
      // write("\u001B[0m");
      concealOn = false;
    }
    closeTags;
  }

  @throws(classOf[IOException])
  protected override def processSetForegroundColor(color: Int): Unit =
    writeTag(HtmlTag("span", """style="color: """ + ANSI_COLOR_MAP(color) + ";\""));

  @throws(classOf[IOException])
  protected override def processSetBackgroundColor(color: Int): Unit =
    writeTag(HtmlTag("span", """style="background-color: """ + ANSI_COLOR_MAP(color) + ";\""))

}
