package playterminal.core

import akka.actor._
import akka.actor.Actor._
import akka.routing._
import com.typesafe.config._
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.iteratee._
import play.api.http.HeaderNames._
import play.utils._
import play.core.ApplicationProvider

/**
 * holds Play's internal invokers
 */
class Invoker2(applicationProvider: Option[ApplicationProvider], classLoader: Option[ClassLoader]) extends {
  override val system: ActorSystem = {
    classLoader match {
      case Some(cl) => ActorSystem("play", ConfigFactory.defaultReference(cl).getConfig("play"), cl)
      case None => ActorSystem("play", ConfigFactory.load().getConfig("play"))
    }

  }
} with _root_.play.core.Invoker(applicationProvider)
