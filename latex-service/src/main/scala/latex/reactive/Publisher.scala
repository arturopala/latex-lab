package latex.reactive

import scala.annotation.tailrec

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import akka.actor.ActorPath
import akka.actor.Terminated
import akka.actor.ActorLogging
import akka.actor.Props
import akka.stream.actor._
import org.reactivestreams.{ Subscriber, Publisher }

final class PublisherActor[T](implicit system: ActorSystem) extends Publisher[T] {

  private[this] val worker = system.actorOf(Props(new PublisherActorWorker))

  override def subscribe(subscriber: Subscriber[_ >: T]): Unit = {
    Option(subscriber).map(s => new Subscription(subscriber, worker, all))
      .getOrElse(throw new NullPointerException)
  }

  def publish(element: T): Unit = worker ! Publish(element)

  case class Cancel(s: Subscription)
  case class Subscribe(s: Subscription, predicate: T => Boolean)
  case class Demand(s: Subscription, n: Long)
  case class Publish(element: T)

  final def all: T => Boolean = (e: T) => true

  final case class Subscription(subscriber: Subscriber[_ >: T], worker: ActorRef, predicate: T => Boolean, var cancelled: Boolean = false) extends org.reactivestreams.Subscription {
    override def cancel(): Unit = worker ! Cancel(this)
    override def request(n: Long): Unit = worker ! Demand(this, n)

    private var demand: Long = 0
    private var buffer: Vector[T] = Vector()

    private[reactive] def addDemand(n: Long) = {
      if (n > 0) {
        demand = demand + n
        if (demand < 0) demand = 0
        if (buffer.size > 0) {
          val min = Math.min(buffer.size, demand).toInt
          val (send, stay) = buffer.splitAt(min)
          buffer = stay
          send foreach push
        }
      }
    }

    private[reactive] def push(element: T): Unit = {
      if (demand > 0) {
        subscriber.onNext(element)
        demand = demand - 1
      }
      else {
        buffer = buffer :+ element
      }
    }
    worker ! Subscribe(this, predicate)
  }

  private[this] final class PublisherActorWorker extends Actor {

    var subscriptions: Vector[Subscription] = Vector()

    def receive: Receive = {
      case Subscribe(s, _) =>
        subscriptions.find(_.subscriber == s.subscriber).getOrElse {
          subscriptions = subscriptions :+ s
          s.subscriber.onSubscribe(s)
        }
      case Cancel(s) if !s.cancelled =>
        subscriptions = subscriptions.filterNot(_ == s)
        s.cancelled = true
      case Cancel(s) if s.cancelled =>
      case Demand(s, n) if !s.cancelled && n > 0 =>
        s.addDemand(n)
      case Demand(s, n) if s.cancelled =>
      case Publish(element) =>
        subscriptions.foreach(s => if (s.predicate(element)) s.push(element))
      case _ =>
    }

  }

  def withPredicate(predicate: T => Boolean): Publisher[T] = new Publisher[T] {

    override def subscribe(subscriber: Subscriber[_ >: T]): Unit = {
      Option(subscriber).map(s => new Subscription(subscriber, worker, predicate))
        .getOrElse(throw new NullPointerException)
    }
  }

}
