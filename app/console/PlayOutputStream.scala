package console

import java.io.OutputStream
import java.io.IOException
import models.ChatRoom
import models.Talk
import models.Text
import models.HtmlText
import java.io.FilterOutputStream

class PlayOutputStream(os: OutputStream) extends HtmlAnsiOutputStream(os) {
  import HtmlAnsiOutputStream._

  def apply(html: String) {
    ChatRoom.default ! HtmlText("PlayOutputStream", html)
  }

  @throws(classOf[IOException])
  override def write(data: Int): Unit = {
    ChatRoom.default ! Text("PlayOutputStream", data.toChar)
    super.write(data);
  }
}