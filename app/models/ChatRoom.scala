package models

import akka.actor._
import akka.util.duration._
import play.api._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import akka.util.Timeout
import akka.pattern.ask
import play.api.Play.current
import play.api.libs.concurrent.execution.defaultContext
import java.io.PipedInputStream
import java.io.PipedOutputStream

//object Robot {
//
//  def apply(chatRoom: ActorRef) {
//
//    // Create an Iteratee that log all messages to the console.
//    val loggerIteratee = Iteratee.foreach[JsValue](event => Logger("robot").info(event.toString))
//
//    implicit val timeout = Timeout(1 second)
//    // Make the robot join the room
//    chatRoom ? (Join("Robot")) map {
//      case Connected(robotChannel) =>
//        // Apply this Enumerator on the logger.
//        robotChannel |>> loggerIteratee
//    }
//
//        // Make the robot talk every 30 seconds
//        Akka.system.scheduler.schedule(
//          2 seconds,
//          3 seconds,
//          chatRoom,
//          Talk("Robot", "I'm still alive"))
//  }
//
//}

object ChatRoom {

  implicit val timeout = Timeout(1 second)

  lazy val default = Akka.system.actorOf(Props[ChatRoom])

  def join(username: String): Promise[(Iteratee[JsValue, _], Enumerator[JsValue])] = {
    (default ? Join(username)).asPromise.map {

      case Connected(enumerator) =>

        // Create an Iteratee to consume the feed
        val iteratee = Iteratee.foreach[JsValue] { event =>
          default ! SendSbtCommand(username, (event \ "text").as[String])
        }.mapDone { _ =>
          default ! Quit(username)
        }

        (iteratee, enumerator)

      case CannotConnect(error) =>

        // Connection error

        // A finished Iteratee sending EOF
        val iteratee = Done[JsValue, Unit]((), Input.EOF)

        // Send an error and close the socket
        val enumerator = Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))

        (iteratee, enumerator)

    }

  }

}

class ChatRoom extends Actor {

  var members = Map.empty[String, PushEnumerator[JsValue]]
  var sbtInput: Option[ActorRef] = None
  def receive = {
    case NeedInput =>
      sbtInput = Some(sender)
    case SendSbtCommand(username, text) => // terminalWriter.write(text.getBytes("UTF-8"))
      sbtInput foreach { _ ! text }
    case Text(username, text) => {
      if (text == '\n') {
        notifyAll("text", username, "<br />")
      } else {
        notifyAll("text", username, text.toString)
      }
    }

    case HtmlText(username, text) => {
      notifyAll("htmlText", username, text)
    }

    case Join(username) => {
      // Create an Enumerator to write to this socket
      val channel = Enumerator.imperative[JsValue](onStart = () => self ! NotifyJoin(username))
      if (members.contains(username)) {
        sender ! CannotConnect("This username is already used")
      } else {
        members = members + (username -> channel)

        sender ! Connected(channel)
      }
    }

    case NotifyJoin(username) => {
      notifyAll("join", username, "has entered the room")
    }

    case Talk(username, text) => {
      notifyAll("talk", username, text)
    }

    case Quit(username) => {
      members = members - username
      notifyAll("quit", username, "has leaved the room")
    }

  }

  def notifyAll(kind: String, user: String, text: String) {
    val msg = JsObject(
      Seq(
        "kind" -> JsString(kind),
        "user" -> JsString(user),
        "message" -> JsString(text),
        "members" -> JsArray(
          members.keySet.toList.map(JsString))))
    members.foreach {
      case (_, channel) => channel.push(msg)
    }
  }

}

case class Text(username: String, text: Char)
case class HtmlText(username: String, text: String)
case class SendSbtCommand(username: String, sbtCommand: String)
case object NeedInput
case class Join(username: String)
case class Quit(username: String)
case class Talk(username: String, text: String)
case class NotifyJoin(username: String)

case class Connected(enumerator: Enumerator[JsValue])
case class CannotConnect(msg: String)
