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

object InvokerConfig {

  val play = ConfigFactory.parseString("""
        # Reference configuration for Play 2.0 
  
  # Root logger
  logger.root=ERROR
  
  # Logger used by the framework:
  logger.play=INFO
  
  # Logger provided to your application:
  logger.application=DEBUG
  
  #default timeout for promises
  promise.akka.actor.typed.timeout=5s
  
  play {
      
      akka {
          event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
          loglevel = WARNING
          
          actor {
              
              deployment {
  
                  /actions {
                      router = round-robin
                      nr-of-instances = 24
                  }
  
              }
              
              retrieveBodyParserTimeout = 1 second
              
              actions-dispatcher = {
                  fork-join-executor {
                      parallelism-factor = 1.0
                      parallelism-max = 24
                  }
              }
  
              websockets-dispatcher = {
                  fork-join-executor {
                      parallelism-factor = 1.0
                      parallelism-max = 24
                  }
              }
  
              default-dispatcher = {
                  fork-join-executor {
                      parallelism-factor = 1.0
                      parallelism-max = 24
                  }
              }
              
          }
          
      }
      
  }       
        
        """)
}

/**
 * holds Play's internal invokers
 */
class Invoker(applicationProvider: Option[ApplicationProvider], classLoader: Option[ClassLoader]) {

  //   override val system: ActorSystem = {
  //    classLoader match {
  //      case Some(cl) => ActorSystem("play", ConfigFactory.defaultReference(cl).getConfig("play"), cl)
  //      case None => ActorSystem("play", ConfigFactory.load().getConfig("play"))
  //    }
  //
  //  }

  val system: ActorSystem = {
    classLoader match {
      case Some(cl) => ActorSystem("play", InvokerConfig.play.getConfig("play"), cl)
      case None => ActorSystem("play", InvokerConfig.play.getConfig("play"))
    }

  }

  /**
   * kills actor system
   */
  def stop(): Unit = {
    system.shutdown()
    system.awaitTermination()
  }

}
