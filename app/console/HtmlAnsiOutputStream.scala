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
import org.fusesource.jansi.AnsiOutputStream
import models.ChatRoom
import models.HtmlText
import models.Text
object HtmlAnsiOutputStream {

  private[console] val ANSI_COLOR_MAP = Array("black", "red",
    "green", "yellow", "blue", "magenta", "cyan", "white");

  private[console] val BYTES_QUOT = "&quot;" //.getBytes();
  private[console] val BYTES_AMP = "&amp;" //.getBytes();
  private[console] val BYTES_LT = "&lt;" //.getBytes();
  private[console] val BYTES_GT = "&gt;" //.getBytes();

  private[console] val ATTRIBUTE_INTENSITY_BOLD = 1; // 	Intensity: Bold 	
  private[console] val ATTRIBUTE_INTENSITY_FAINT = 2; // 	Intensity; Faint 	not widely supported
  private[console] val ATTRIBUTE_ITALIC = 3; // 	Italic; on 	not widely supported. Sometimes treated as inverse.
  private[console] val ATTRIBUTE_UNDERLINE = 4; // 	Underline; Single 	
  private[console] val ATTRIBUTE_BLINK_SLOW = 5; // 	Blink; Slow 	less than 150 per minute
  private[console] val ATTRIBUTE_BLINK_FAST = 6; // 	Blink; Rapid 	MS-DOS ANSI.SYS; 150 per minute or more
  private[console] val ATTRIBUTE_NEGATIVE_ON = 7; // 	Image; Negative 	inverse or reverse; swap foreground and background
  private[console] val ATTRIBUTE_CONCEAL_ON = 8; // 	Conceal on
  private[console] val ATTRIBUTE_UNDERLINE_DOUBLE = 21; // 	Underline; Double 	not widely supported
  private[console] val ATTRIBUTE_INTENSITY_NORMAL = 22; // 	Intensity; Normal 	not bold and not faint
  private[console] val ATTRIBUTE_UNDERLINE_OFF = 24; // 	Underline; None 	
  private[console] val ATTRIBUTE_BLINK_OFF = 25; // 	Blink; off 	
  private[console] val ATTRIBUTE_NEGATIVE_Off = 27; // 	Image; Positive 	
  private[console] val ATTRIBUTE_CONCEAL_OFF = 28; // 	Reveal 	conceal off

  private[console] val BLACK = 0;
  private[console] val RED = 1;
  private[console] val GREEN = 2;
  private[console] val YELLOW = 3;
  private[console] val BLUE = 4;
  private[console] val MAGENTA = 5;
  private[console] val CYAN = 6;
  private[console] val WHITE = 7;

  private[console] val ERASE_SCREEN_TO_END = 0;
  private[console] val ERASE_SCREEN_TO_BEGINING = 1;
  private[console] val ERASE_SCREEN = 2;

  private[console] val ERASE_LINE_TO_END = 0;
  private[console] val ERASE_LINE_TO_BEGINING = 1;
  private[console] val ERASE_LINE = 2;
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
  override def write(data: Int): Unit = data match {
    case 34 => write(BYTES_QUOT); // "
    case 38 => write(BYTES_AMP); // &			
    case 60 => write(BYTES_LT); // <
    case 62 => write(BYTES_GT); // >
    case _ =>
      apply(data)
      super.write(data);
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
