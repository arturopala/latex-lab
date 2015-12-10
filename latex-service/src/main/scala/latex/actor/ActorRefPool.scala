package latex.actor

import scala.util.{ Try, Success, Failure }
import akka.actor._

trait ActorRefPool[Key] extends Actor {

  def createNewActorRefFor(key: Key): ActorRef
  def keyHasBeenRemoved(key: Key): Unit = ()

  val pool = collection.mutable.Map[Key, ActorRef]()
  val reverse = collection.mutable.Map[ActorRef, Key]()

  def getOrCreate(key: Key): Try[ActorRef] = pool.get(key) match {
    case Some(ref) => Success(ref)
    case None =>
      try {
        val ref = createNewActorRefFor(key)
        pool(key) = ref
        reverse(ref) = key
        context.watch(ref)
        Success(ref)
      }
      catch {
        case e: Exception => Failure(e)
      }
  }

  def receive: Receive = {
    case msg: KeyHolder[Key] => getOrCreate(msg.key) match {
      case Success(ref) => ref forward msg
      case Failure(e)   => sender() ! None
    }
    case Terminated(ref) =>
      reverse.remove(ref) foreach { key =>
        pool.remove(key)
        keyHasBeenRemoved(key)
      }
  }

}