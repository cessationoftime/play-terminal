package console

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

import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.UnsupportedEncodingException

/**
 * A ANSI output stream extracts ANSI escape codes written to
 * an output stream.
 *
 * For more information about ANSI escape codes, see:
 * http://en.wikipedia.org/wiki/ANSI_escape_code
 *
 * This class just filters out the escape codes so that they are not
 * sent out to the underlying OutputStream.  Subclasses should
 * actually perform the ANSI escape behaviors.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 * @author Joris Kuipers
 * @since 1.0
 */
object AnsiOutputStream extends AnsiOutputStreamIdentifiers
class AnsiOutputStreamIdentifiers {
  case class ErrorMessage(message: String)
  val MAX_ESCAPE_SEQUENCE_LENGTH = 100;
  val LOOKING_FOR_FIRST_ESC_CHAR = 0;
  val LOOKING_FOR_SECOND_ESC_CHAR = 1;
  val LOOKING_FOR_NEXT_ARG = 2;
  val LOOKING_FOR_STR_ARG_END = 3;
  val LOOKING_FOR_INT_ARG_END = 4;
  val LOOKING_FOR_OSC_COMMAND = 5;
  val LOOKING_FOR_OSC_COMMAND_END = 6;
  val LOOKING_FOR_OSC_PARAM = 7;
  val LOOKING_FOR_ST = 8;

  val FIRST_ESC_CHAR = 27;
  val SECOND_ESC_CHAR = '[';
  val SECOND_OSC_CHAR = ']';
  val BEL = 7;
  val SECOND_ST_CHAR = '\\';

  val ERASE_SCREEN_TO_END = 0;
  val ERASE_SCREEN_TO_BEGINING = 1;
  val ERASE_SCREEN = 2;
  val ERASE_LINE_TO_END = 0;
  val ERASE_LINE_TO_BEGINING = 1;
  val ERASE_LINE = 2;
  val ATTRIBUTE_INTENSITY_BOLD = 1; // 	Intensity: Bold 	
  val ATTRIBUTE_INTENSITY_FAINT = 2; // 	Intensity; Faint 	not widely supported
  val ATTRIBUTE_ITALIC = 3; // 	Italic; on 	not widely supported. Sometimes treated as inverse.
  val ATTRIBUTE_UNDERLINE = 4; // 	Underline; Single 	
  val ATTRIBUTE_BLINK_SLOW = 5; // 	Blink; Slow 	less than 150 per minute
  val ATTRIBUTE_BLINK_FAST = 6; // 	Blink; Rapid 	MS-DOS ANSI.SYS; 150 per minute or more
  val ATTRIBUTE_NEGATIVE_ON = 7; // 	Image; Negative 	inverse or reverse; swap foreground and background
  val ATTRIBUTE_CONCEAL_ON = 8; // 	Conceal on
  val ATTRIBUTE_UNDERLINE_DOUBLE = 21; // 	Underline; Double 	not widely supported
  val ATTRIBUTE_INTENSITY_NORMAL = 22; // 	Intensity; Normal 	not bold and not faint
  val ATTRIBUTE_UNDERLINE_OFF = 24; // 	Underline; None 	
  val ATTRIBUTE_BLINK_OFF = 25; // 	Blink; off 	
  val ATTRIBUTE_NEGATIVE_Off = 27; // 	Image; Positive 	
  val ATTRIBUTE_CONCEAL_OFF = 28; // 	Reveal 	conceal off
  val BLACK = 0;
  val RED = 1;
  val GREEN = 2;
  val YELLOW = 3;
  val BLUE = 4;
  val MAGENTA = 5;
  val CYAN = 6;
  val WHITE = 7;
  val REST_CODE: Array[Byte] = try {
    new org.fusesource.jansi.Ansi().reset().toString().getBytes("UTF-8");
  } catch {
    case e: UnsupportedEncodingException => throw new RuntimeException(e);
  }

}

case object StreamStateStorage {
  import AnsiOutputStream._
  private var mode: Int = LOOKING_FOR_FIRST_ESC_CHAR
  private var pos: Int = 0
  private var startOfValue: Int = 0
  private var buffer = new Array[Byte](MAX_ESCAPE_SEQUENCE_LENGTH);
  private var options: Vector[Option[Any]] = Vector.empty
  /**
   * save the stream state
   */
  def apply(state: StreamState): Unit = {
    mode = state.mode
    pos = state.pos
    startOfValue = state.startOfValue
    buffer = state.buffer
    options = state.options

  }
  def apply(): StreamState = StreamState(mode, pos, startOfValue, buffer, options)

}
object StreamState {
  import AnsiOutputStream._
  /**
   * the unadultered state
   */
  def virginState = StreamState(LOOKING_FOR_FIRST_ESC_CHAR, 0, 0, new Array[Byte](MAX_ESCAPE_SEQUENCE_LENGTH), Vector.empty)
}
case class StreamState(mode: Int, pos: Int, startOfValue: Int, buffer: Array[Byte], options: Vector[Option[Any]]) {
  def copy(mode: Int = this.mode, pos: Int = this.pos, startOfValue: Int = this.startOfValue, buffer: Array[Byte] = this.buffer, options: Vector[Option[Any]] = this.options) =
    StreamState(mode, pos, startOfValue, buffer, options)
  def setMode(newMode: Int) = copy(mode = newMode)
  def setOptions(newOptions: Vector[Option[Any]]) = copy(options = newOptions)
  def appendOption(opt: Option[Any]) = copy(options = options :+ opt)
  def setStartOfValue(newStartOfValue: Int) = copy(startOfValue = newStartOfValue)

  /**
   * returns the current Buffer as well as a fresh StreamState
   */
  def makeVirgin: (Array[Byte], StreamState) = (buffer, StreamState.virginState)

  def addOption(beforeAdd: String => Any = x => x, offsetSize: Int = 1): StreamState = {

    val strValue = new String(buffer, startOfValue, (pos - offsetSize) - startOfValue, "UTF-8");
    val x = beforeAdd(strValue) match {
      case i: Int => Left(i)
      case s: String => Right(s)
    }
    this.appendOption(Some(x));
  }

}
class AnsiOutputStream(os: OutputStream) extends FilterOutputStream(os) {
  import AnsiOutputStream._
  import StreamState._

  def writeOut(data: AnyRef) = data match {
    case Some(i: Int) => //out.write(i)
    case buffer: Array[Byte] => //out.write(buffer)
    case None =>
  }

  @throws(classOf[IOException])
  override def write(data: Int): Unit = {
    //read the streamState into the writeProcessor
    val (transformedData, newStreamState) = writeProcessor(data, StreamStateStorage())

    writeOut(transformedData)

    // make sure position has not passed the length of the buffer
    val finalState = if (newStreamState.pos >= newStreamState.buffer.length) {
      //  newStreamState.makeVirgin
      StreamState.virginState
    } else newStreamState

    //save the streamState for next time
    StreamStateStorage(finalState)

  }

  def writeProcessor(data: Int, oldState: StreamState): (AnyRef, StreamState) = {

    (oldState.mode, data) match {
      case (LOOKING_FOR_FIRST_ESC_CHAR, d) if d != FIRST_ESC_CHAR => (Some(data), oldState);
      case (m, d) =>
        val state = oldState.copy(pos = oldState.pos + 1)
        state.buffer(state.pos) = data.toByte;

        (m, d) match {
          case (LOOKING_FOR_FIRST_ESC_CHAR, FIRST_ESC_CHAR) => (None, state setMode LOOKING_FOR_SECOND_ESC_CHAR)
          case (LOOKING_FOR_SECOND_ESC_CHAR, SECOND_ESC_CHAR) => (None, state setMode LOOKING_FOR_NEXT_ARG)
          case (LOOKING_FOR_SECOND_ESC_CHAR, SECOND_OSC_CHAR) => (None, state setMode LOOKING_FOR_OSC_COMMAND)
          case (LOOKING_FOR_SECOND_ESC_CHAR, _) => state.makeVirgin
          case (LOOKING_FOR_NEXT_ARG, '"') => (None, state.copy(startOfValue = state.pos - 1, mode = LOOKING_FOR_STR_ARG_END))
          case (LOOKING_FOR_NEXT_ARG, d) if ('0' <= d && d <= '9') => (None, state.copy(startOfValue = state.pos - 1, mode = LOOKING_FOR_INT_ARG_END))
          case (LOOKING_FOR_NEXT_ARG, ';') => (None, state.appendOption(Some(null)))
          case (LOOKING_FOR_NEXT_ARG, '?') => (None, state.appendOption(Some('?')))
          case (LOOKING_FOR_NEXT_ARG, '=') => (None, state.appendOption(Some('=')))
          case (LOOKING_FOR_NEXT_ARG, _) => reset(processEscapeCommand(state.options, data), state)

          case (LOOKING_FOR_INT_ARG_END, d) if (!('0' <= d && d <= '9')) =>
            val s = state.addOption(_.toInt)
            if (data == ';') (None, s setMode LOOKING_FOR_NEXT_ARG)
            else reset(processEscapeCommand(s.options, data), s);

          case (LOOKING_FOR_STR_ARG_END, d) if (d != '"') =>
            val s = state.addOption()
            if (data == ';') (None, s setMode LOOKING_FOR_NEXT_ARG)
            else reset(processEscapeCommand(s.options, data), s);

          case (LOOKING_FOR_OSC_COMMAND, d) if ('0' <= data && data <= '9') =>
            (None, state.copy(startOfValue = state.pos - 1, mode = LOOKING_FOR_OSC_COMMAND_END))
          case (LOOKING_FOR_OSC_COMMAND, _) => state.makeVirgin

          case (LOOKING_FOR_OSC_COMMAND_END, ';') =>
            val s = state.addOption(_.toInt)
            (None, s.copy(startOfValue = s.pos, mode = LOOKING_FOR_OSC_PARAM))
          case (LOOKING_FOR_OSC_COMMAND_END, d) if ('0' <= data && data <= '9') => (None, state)
          // already pushed digit to buffer, just keep looking
          case (LOOKING_FOR_OSC_COMMAND_END, _) =>
            (None, state)
            // oops, did not expect this
            state.makeVirgin

          case (LOOKING_FOR_OSC_PARAM, BEL) =>
            val s = state.addOption()
            reset(processOperatingSystemCommand(s.options), s);
          case (LOOKING_FOR_OSC_PARAM, FIRST_ESC_CHAR) =>
            (None, state setMode LOOKING_FOR_ST)
          case (LOOKING_FOR_OSC_PARAM, _) => (None, state)
          // just keep looking while adding text

          case (LOOKING_FOR_ST, SECOND_ST_CHAR) =>
            val s = state.addOption(offsetSize = 2)
            reset(processOperatingSystemCommand(s.options), s);
          case (LOOKING_FOR_ST, _) =>
            (None, state setMode LOOKING_FOR_OSC_PARAM)
        }

    }

  }

  // TODO: implement to get perf boost: def write(byte[] b, int off, int len)

  /**
   * Resets all state to continue with regular parsing
   * @param skipBuffer if current buffer should be skipped or written to out
   * @throws IOException
   */
  private def reset(skipBuffer: Option[AnyRef], state: StreamState): (Array[Byte], StreamState) =
    if (skipBuffer.isEmpty) {
      state.makeVirgin
    } else {
      (Array.empty[Byte], virginState)
    }

  /**
   *
   * @param options
   * @param command
   * @return true if the escape command was processed.
   */
  private def processEscapeCommand(options: Vector[Option[Any]], command: Int): Option[AnyRef] = {
    try {
      command match {
        case 'A' =>
          processCursorUp(optionInt(options, 0, 1));

        case 'B' =>
          processCursorDown(optionInt(options, 0, 1));

        case 'C' =>
          processCursorRight(optionInt(options, 0, 1));

        case 'D' =>
          processCursorLeft(optionInt(options, 0, 1));

        case 'E' =>
          processCursorDownLine(optionInt(options, 0, 1));

        case 'F' =>
          processCursorUpLine(optionInt(options, 0, 1));

        case 'G' =>
          processCursorToColumn(optionInt(options, 0));

        case 'H' | 'f' =>
          processCursorTo(optionInt(options, 0, 1), optionInt(options, 1, 1));

        case 'J' =>
          processEraseScreen(optionInt(options, 0, 0));
        case 'K' =>
          processEraseLine(optionInt(options, 0, 0));
        case 'S' =>
          processScrollUp(optionInt(options, 0, 1));
        case 'T' =>
          processScrollDown(optionInt(options, 0, 1));
        case 'm' =>
          // Validate all options are ints...
          for (next <- options) {
            next match {
              case Some(i: Int) => //ignore
              case None => throw new IllegalArgumentException();
            }
          }

          var count = 0;
          for (next <- options) {
            //   next needs to be handled as Option   lfgndfjgdjndjg
            if (next != None) {
              count += 1;
              val value = next.get.asInstanceOf[Int];
              if (30 <= value && value <= 37) {
                processSetForegroundColor(value - 30);
              } else if (40 <= value && value <= 47) {
                processSetBackgroundColor(value - 40);
              } else {
                value match {
                  case 39 => processDefaultTextColor();

                  case 49 => processDefaultBackgroundColor();

                  case 0 => processAttributeRest();

                  case _ => processSetAttribute(value);
                }
              }
            }
          }
          if (count == 0) {
            processAttributeRest();
          }

        case 's' =>
          processSaveCursorPosition();

        case 'u' =>
          processRestoreCursorPosition();

        case _ =>
          if ('a' <= command && 'z' <= command) {
            processUnknownExtension(options, command);

          }
          if ('A' <= command && 'Z' <= command) {
            processUnknownExtension(options, command);

          }
          return None;
      }
    } catch {
      case ignore: IllegalArgumentException =>
    }
    return false;
  }

  /**
   *
   * @param options
   * @return true if the operating system command was processed.
   */
  private def processOperatingSystemCommand(options: Vector[Option[Any]]): Boolean = {
    val command = optionInt(options, 0);
    val label = options(1).toString;
    // for command > 2 label could be composed (i.e. contain ';'), but we'll leave
    // it to processUnknownOperatingSystemCommand implementations to handle that
    try {
      command.right.map { comm =>
        comm match {
          case 0 =>
            processChangeIconNameAndWindowTitle(label);
            return true;
          case 1 =>
            processChangeIconName(label);
            return true;
          case 2 =>
            processChangeWindowTitle(label);
            return true;
          case _ =>
            // not exactly unknown, but not supported through dedicated process methods:
            processUnknownOperatingSystemCommand(comm, label);
            return true;
        }
      }
    } catch {
      case ignore: IllegalArgumentException =>
    }
    return false;
  }

  protected def processRestoreCursorPosition(): Unit = {
  }
  protected def processSaveCursorPosition(): Unit = {
  }
  protected def processScrollDown(option: Int): Unit = {
  }
  protected def processScrollUp(option: Int): Unit = {
  }

  protected def processEraseScreen(eraseOption: Int): Unit = {
  }

  protected def processEraseLine(eraseOption: Int): Unit = {
  }

  protected def processSetAttribute(attribute: Int): Unit = {
  }

  protected def processSetForegroundColor(color: Int): Unit = {
  }

  protected def processSetBackgroundColor(color: Int): Unit = {
  }

  protected def processDefaultTextColor(): Unit = {
  }

  protected def processDefaultBackgroundColor(): Unit = {
  }

  protected def processAttributeRest(): Unit = {
  }

  protected def processCursorTo(row: Int, col: Int): Unit = {
  }

  protected def processCursorToColumn(x: Either[ErrorMessage, Int]): Unit = {
  }

  protected def processCursorUpLine(count: Int): Unit = {
  }

  protected def processCursorDownLine(count: Int): List[Char] =
    //Poor mans impl..
    List.fill(count)('\n')

  protected def processCursorLeft(count: Int): Unit = {
  }

  protected def processCursorRight(count: Int): List[Char] =
    // Poor mans impl..
    List.fill(count)(' ')

  protected def processCursorDown(count: Int): Unit = {
  }

  protected def processCursorUp(count: Int): Unit = {
  }

  protected def processUnknownExtension(options: Vector[Option[Any]], command: Int) {
  }

  protected def processChangeIconNameAndWindowTitle(label: String) {
    processChangeIconName(label);
    processChangeWindowTitle(label);
  }

  protected def processChangeIconName(label: String) {
  }

  protected def processChangeWindowTitle(label: String) {
  }

  protected def processUnknownOperatingSystemCommand(command: Int, param: String) {
  }

  def optionInt(options: Vector[Option[Any]], index: Int): Either[ErrorMessage, Int] = {
    options.lift(index).flatten.headOption match {
      case Some(x: Int) => Right(x)
      case None => Left(ErrorMessage("Int not found in Buffer"))
    }
  }
  def optionInt(options: Vector[Option[Any]], index: Int, defaultValue: => Int): Int = {
    optionInt(options, index) fold (a => defaultValue, b => b)
  }

  //  private def optionInt(options: Vector[Option[Either[Int, String]]], index: Int): Int = {
  //    if (options.size <= index)
  //      throw new IllegalArgumentException();
  //    val value = options(index);
  //    if (value == null)
  //      throw new IllegalArgumentException();
  //    if (!value.getClass().equals(classOf[Integer]))
  //      throw new IllegalArgumentException();
  //    return value.asInstanceOf[Int]
  //  }
  //
  //  private def optionInt(options: Vector[Option[Either[Int, String]]], index: Int, defaultValue: Int): Int = {
  //    if (options.size > index) {
  //      val value = options(index);
  //      if (value == null) {
  //        return defaultValue;
  //      }
  //      return value.asInstanceOf[Int]
  //    }
  //    return defaultValue;
  //  }

  override def close(): Unit = {
    write(REST_CODE);
    flush();
    super.close();
  }

}
