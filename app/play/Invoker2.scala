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
class Invoker2(applicationProvider: Option[ApplicationProvider] = None) extends _root_.play.core.Invoker {

  val conf2 = ConfigFactory.parseString("""
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

  override val system: ActorSystem = applicationProvider.map { a =>
    appProviderActorSystem(a)
  }.getOrElse(ActorSystem("play"))

  private def appProviderActorSystem(applicationProvider: ApplicationProvider) = {
    val conf = play.api.Play.maybeApplication.filter(_.mode == Mode.Prod).map(app =>
      ConfigFactory.load()).get

    ActorSystem("play", conf.withFallback(conf2).getConfig("play"))
  }

}

