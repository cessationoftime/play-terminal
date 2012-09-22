package akka.actor

import akka.routing.NoRouter
import akka.dispatch.MessageDispatcher
import akka.event.EventStream
import java.util.concurrent.atomic.AtomicLong
import akka.util.Switch
import akka.util.Helpers
import akka.dispatch.Promise
import akka.dispatch.Supervise
import akka.dispatch.SystemMessage
import akka.dispatch.ChildTerminated
import akka.routing.RoutedActorRef
import akka.event.Logging

/**
 * Local ActorRef provider.
 */
class TerminalActorRefProvider(
  _systemName: String,
  val settings: ActorSystem.Settings,
  val eventStream: EventStream,
  val scheduler: Scheduler,
  val deployer: Deployer) extends ActorRefProvider {

  // this is the constructor needed for reflectively instantiating the provider
  def this(_systemName: String,
    settings: ActorSystem.Settings,
    eventStream: EventStream,
    scheduler: Scheduler,
    dynamicAccess: DynamicAccess) =
    this(_systemName,
      settings,
      eventStream,
      scheduler,
      new Deployer(settings, dynamicAccess))

  val rootPath: ActorPath = RootActorPath(Address("akka", _systemName))

  val log = Logging(eventStream, "LocalActorRefProvider(" + rootPath.address + ")")

  val deadLetters = new DeadLetterActorRef(this, rootPath / "deadLetters", eventStream)

  val deathWatch = new LocalDeathWatch(1024) //TODO make configrable

  /*
   * generate name for temporary actor refs
   */
  private val tempNumber = new AtomicLong

  private def tempName() = Helpers.base64(tempNumber.getAndIncrement())

  private val tempNode = rootPath / "temp"

  def tempPath() = tempNode / tempName()

  /**
   * Top-level anchor for the supervision hierarchy of this actor system. Will
   * receive only Supervise/ChildTerminated system messages or Failure message.
   */
  private[akka] val theOneWhoWalksTheBubblesOfSpaceTime: InternalActorRef = new MinimalActorRef {
    val stopped = new Switch(false)

    @volatile
    var causeOfTermination: Option[Throwable] = None

    val path = rootPath / "bubble-walker"

    def provider: ActorRefProvider = TerminalActorRefProvider.this

    override def stop() = stopped switchOn {
      terminationFuture.complete(causeOfTermination.toLeft(()))
    }

    override def isTerminated = stopped.isOn

    override def !(message: Any)(implicit sender: ActorRef = null): Unit = stopped.ifOff(message match {
      case Failed(ex) if sender ne null ⇒ causeOfTermination = Some(ex); sender.asInstanceOf[InternalActorRef].stop()
      case _ ⇒ log.error(this + " received unexpected message [" + message + "]")
    })

    override def sendSystemMessage(message: SystemMessage): Unit = stopped ifOff {
      message match {
        case Supervise(child) ⇒ // TODO register child in some map to keep track of it and enable shutdown after all dead
        case ChildTerminated(child) ⇒ stop()
        case _ ⇒ log.error(this + " received unexpected system message [" + message + "]")
      }
    }
  }

  /**
   * Overridable supervision strategy to be used by the “/user” guardian.
   */
  protected def guardianSupervisionStrategy = {
    import akka.actor.SupervisorStrategy._
    OneForOneStrategy() {
      case _: ActorKilledException ⇒ Stop
      case _: ActorInitializationException ⇒ Stop
      case _: Exception ⇒ Restart
    }
  }

  /*
   * Guardians can be asked by ActorSystem to create children, i.e. top-level
   * actors. Therefore these need to answer to these requests, forwarding any
   * exceptions which might have occurred.
   */
  private class Guardian extends Actor {

    override val supervisorStrategy = guardianSupervisionStrategy

    def receive = {
      case Terminated(_) ⇒ context.stop(self)
      case CreateChild(child, name) ⇒ sender ! (try context.actorOf(child, name) catch { case e: Exception ⇒ e })
      case CreateRandomNameChild(child) ⇒ sender ! (try context.actorOf(child) catch { case e: Exception ⇒ e })
      case StopChild(child) ⇒ context.stop(child); sender ! "ok"
      case m ⇒ deadLetters ! DeadLetter(m, sender, self)
    }

    // guardian MUST NOT lose its children during restart
    override def preRestart(cause: Throwable, msg: Option[Any]) {}
  }

  /**
   * Overridable supervision strategy to be used by the “/system” guardian.
   */
  protected def systemGuardianSupervisionStrategy = {
    import akka.actor.SupervisorStrategy._
    OneForOneStrategy() {
      case _: ActorKilledException ⇒ Stop
      case _: ActorInitializationException ⇒ Stop
      case _: Exception ⇒ Restart
    }
  }

  /*
   * Guardians can be asked by ActorSystem to create children, i.e. top-level
   * actors. Therefore these need to answer to these requests, forwarding any
   * exceptions which might have occurred.
   */
  private class SystemGuardian extends Actor {

    override val supervisorStrategy = systemGuardianSupervisionStrategy

    def receive = {
      case Terminated(_) ⇒
        eventStream.stopDefaultLoggers()
        context.stop(self)
      case CreateChild(child, name) ⇒ sender ! (try context.actorOf(child, name) catch { case e: Exception ⇒ e })
      case CreateRandomNameChild(child) ⇒ sender ! (try context.actorOf(child) catch { case e: Exception ⇒ e })
      case StopChild(child) ⇒ context.stop(child); sender ! "ok"
      case m ⇒ deadLetters ! DeadLetter(m, sender, self)
    }

    // guardian MUST NOT lose its children during restart
    override def preRestart(cause: Throwable, msg: Option[Any]) {}
  }

  /*
   * The problem is that ActorRefs need a reference to the ActorSystem to
   * provide their service. Hence they cannot be created while the
   * constructors of ActorSystem and ActorRefProvider are still running.
   * The solution is to split out that last part into an init() method,
   * but it also requires these references to be @volatile and lazy.
   */
  @volatile
  private var system: ActorSystemImpl = _

  def dispatcher: MessageDispatcher = system.dispatcher

  lazy val terminationFuture: Promise[Unit] = Promise[Unit]()(dispatcher)

  @volatile
  private var extraNames: Map[String, InternalActorRef] = Map()

  /**
   * Higher-level providers (or extensions) might want to register new synthetic
   * top-level paths for doing special stuff. This is the way to do just that.
   * Just be careful to complete all this before ActorSystem.start() finishes,
   * or before you start your own auto-spawned actors.
   */
  def registerExtraNames(_extras: Map[String, InternalActorRef]): Unit = extraNames ++= _extras

  private val guardianProps = Props(new Guardian)

  lazy val rootGuardian: InternalActorRef =
    new LocalActorRef(system, guardianProps, theOneWhoWalksTheBubblesOfSpaceTime, rootPath, true) {
      object Extra {
        def unapply(s: String): Option[InternalActorRef] = extraNames.get(s)
      }

      override def getParent: InternalActorRef = this

      override def getSingleChild(name: String): InternalActorRef = {
        name match {
          case "temp" ⇒ tempContainer
          case Extra(e) ⇒ e
          case _ ⇒ super.getSingleChild(name)
        }
      }
    }

  lazy val guardian: InternalActorRef =
    actorOf(system, guardianProps, rootGuardian, rootPath / "user", true, None, false)

  lazy val systemGuardian: InternalActorRef =
    actorOf(system, guardianProps.withCreator(new SystemGuardian), rootGuardian, rootPath / "system", true, None, false)

  lazy val tempContainer = new VirtualPathContainer(system.provider, tempNode, rootGuardian, log)

  def registerTempActor(actorRef: InternalActorRef, path: ActorPath): Unit = {
    assert(path.parent eq tempNode, "cannot registerTempActor() with anything not obtained from tempPath()")
    tempContainer.addChild(path.name, actorRef)
  }

  def unregisterTempActor(path: ActorPath): Unit = {
    assert(path.parent eq tempNode, "cannot unregisterTempActor() with anything not obtained from tempPath()")
    tempContainer.removeChild(path.name)
  }

  def init(_system: ActorSystemImpl) {
    system = _system
    // chain death watchers so that killing guardian stops the application
    deathWatch.subscribe(systemGuardian, guardian)
    deathWatch.subscribe(rootGuardian, systemGuardian)
    eventStream.startDefaultLoggers(_system)
  }

  def actorFor(ref: InternalActorRef, path: String): InternalActorRef = path match {
    case RelativeActorPath(elems) ⇒
      if (elems.isEmpty) {
        log.debug("look-up of empty path string '{}' fails (per definition)", path)
        deadLetters
      } else if (elems.head.isEmpty) actorFor(rootGuardian, elems.tail)
      else actorFor(ref, elems)
    case ActorPathExtractor(address, elems) if address == rootPath.address ⇒ actorFor(rootGuardian, elems)
    case _ ⇒
      log.debug("look-up of unknown path '{}' failed", path)
      deadLetters
  }

  def actorFor(path: ActorPath): InternalActorRef =
    if (path.root == rootPath) actorFor(rootGuardian, path.elements)
    else {
      log.debug("look-up of foreign ActorPath '{}' failed", path)
      deadLetters
    }

  def actorFor(ref: InternalActorRef, path: Iterable[String]): InternalActorRef =
    if (path.isEmpty) {
      log.debug("look-up of empty path sequence fails (per definition)")
      deadLetters
    } else ref.getChild(path.iterator) match {
      case Nobody ⇒
        log.debug("look-up of path sequence '{}' failed", path)
        new EmptyLocalActorRef(system.provider, ref.path / path, eventStream)
      case x ⇒ x
    }

  def actorOf(system: ActorSystemImpl, props: Props, supervisor: InternalActorRef, path: ActorPath,
    systemService: Boolean, deploy: Option[Deploy], lookupDeploy: Boolean): InternalActorRef = {
    props.routerConfig match {
      case NoRouter ⇒ new LocalActorRef(system, props, supervisor, path, systemService) // create a local actor
      case router ⇒
        val lookup = if (lookupDeploy) deployer.lookup(path.elements.drop(1).mkString("/", "/", "")) else None
        val fromProps = Iterator(props.deploy.copy(routerConfig = props.deploy.routerConfig withFallback router))
        val d = fromProps ++ deploy.iterator ++ lookup.iterator reduce ((a, b) ⇒ b withFallback a)
        new RoutedActorRef(system, props.withRouter(d.routerConfig), supervisor, path)
    }
  }

  def getExternalAddressFor(addr: Address): Option[Address] = if (addr == rootPath.address) Some(addr) else None
}
