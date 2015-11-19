package latex.reactive

import java.nio.charset.Charset
import java.nio.file.{ FileSystems, Files, Path }
import java.util.function.Consumer

import org.scalatest.{ Finders, FlatSpecLike, Matchers }
import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import org.scalatest.junit.JUnitRunner
import com.typesafe.config.ConfigFactory
import akka.testkit.TestProbe
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicInteger
import akka.actor.ActorRef

import latex.ActorSystemTestKit

import org.reactivestreams.{ Publisher, Subscriber, Subscription }

class PublisherSpec extends FlatSpecLike with Matchers with ActorSystemTestKit {

  "A Publisher Actor" must "receive subscription and call onSubscribe first (1.9)" in new ActorSystemTest {
    val subscriber = new TestSubscriber[String]
    val tested = new PublisherActor[String]
    tested.subscribe(subscriber)
    val subscription = subscriber.probe.expectMsgType[Subscription]
    subscription should not be null
  }

  it must "throw NullPointerException when subscriber is null (1.9)" in new ActorSystemTest {
    val tested = new PublisherActor[String]
    an[NullPointerException] should be thrownBy tested.subscribe(null)
  }

  it must "handle multiple different subscriptions" in new ActorSystemTest {
    val tested = new PublisherActor[Int]
    val s1 = new TestSubscriber[Int]
    val s2 = new TestSubscriber[Int]
    val s3 = new TestSubscriber[Int]
    tested.subscribe(s1)
    tested.subscribe(s2)
    tested.subscribe(s3)
    val sub1 = s1.probe.expectMsgType[Subscription]
    val sub2 = s2.probe.expectMsgType[Subscription]
    val sub3 = s3.probe.expectMsgType[Subscription]
    sub1 should not be sub2
    sub2 should not be sub3
  }

  it must "not allow duplicate subscription of the same subscriber" in new ActorSystemTest {
    val subs = new TestSubscriber[String]
    val tested = new PublisherActor[String]
    tested.subscribe(subs)
    tested.subscribe(subs)
    subs.probe.expectMsgType[Subscription]
    subs.probe.expectNoMsg(200.millis)
  }

  it must "handle subscription cancelling" in new ActorSystemTest {
    val subs = new TestSubscriber[String]
    val tested = new PublisherActor[String]
    tested.subscribe(subs)
    val subscription = subs.probe.expectMsgType[Subscription]
    subscription.cancel()
    subscription.cancel()
    subscription.cancel()
  }

  it must "forget subscriber when subscription has been cancelled" in new ActorSystemTest {
    val subs = new TestSubscriber[String]
    val tested = new PublisherActor[String]
    tested.subscribe(subs)
    val subscription1 = subs.probe.expectMsgType[Subscription]
    subscription1.cancel()
    tested.subscribe(subs)
    val subscription2 = subs.probe.expectMsgType[Subscription]
    subscription1 should not be subscription2
  }

  it must "allow to request elements multiple times" in new ActorSystemTest {
    val subs = new TestSubscriber[String]
    val tested = new PublisherActor[String]
    tested.subscribe(subs)
    val subscription1 = subs.probe.expectMsgType[Subscription]
    subscription1.request(1)
    subscription1.request(0)
    subscription1.request(-1)
    subscription1.request(10)
    subscription1.request(250000)
    subscription1.request(111)
    subscription1.request(19998776)
    subscription1.request(-245678)
    subscription1.cancel()
  }

  it must "buffer elements when not requested" in new ActorSystemTest {
    val subs = new TestSubscriber[String]
    val tested = new PublisherActor[String]
    tested.subscribe(subs)
    val subscription1 = subs.probe.expectMsgType[Subscription]
    tested.publish("abc")
    subs.probe.expectNoMsg(200.millis)
    tested.publish("abcd")
    subs.probe.expectNoMsg(200.millis)
    subscription1.request(2)
    subs.probe.expectMsg("abc")
    subs.probe.expectMsg("abcd")
    subscription1.request(2)
    tested.publish("bca")
    subs.probe.expectMsg("bca")
    tested.publish("abc")
    subs.probe.expectMsg("abc")
    tested.publish("_abc1234567890")
    subs.probe.expectNoMsg(200.millis)
    subscription1.request(2)
    subs.probe.expectMsg("_abc1234567890")
    tested.publish("bccda1_!")
    subscription1.request(1)
    subs.probe.expectMsg("bccda1_!")
  }

  it must "publish elements to subscriber" in new ActorSystemTest {
    val subs = new TestSubscriber[String]
    val tested = new PublisherActor[String]
    tested.subscribe(subs)
    val subscription1 = subs.probe.expectMsgType[Subscription]
    subscription1.request(100)
    tested.publish("abc")
    subs.probe.expectMsg("abc")
    tested.publish("abcd")
    subs.probe.expectMsg("abcd")
    tested.publish("bca")
    subs.probe.expectMsg("bca")
    tested.publish("abc")
    subs.probe.expectMsg("abc")
    tested.publish("_abc1234567890")
    subs.probe.expectMsg("_abc1234567890")
  }

  it must "broadcast elements to multiple subscribers" in new ActorSystemTest {
    val subs1 = new TestSubscriber[String]
    val subs2 = new TestSubscriber[String]
    val tested = new PublisherActor[String]
    tested.subscribe(subs1)
    tested.subscribe(subs2)
    val subscription1 = subs1.probe.expectMsgType[Subscription]
    val subscription2 = subs2.probe.expectMsgType[Subscription]
    subscription1.request(100)
    subscription2.request(10)
    tested.publish("abc")
    subs1.probe.expectMsg("abc")
    subs2.probe.expectMsg("abc")
    tested.publish("abcd")
    subs1.probe.expectMsg("abcd")
    subs2.probe.expectMsg("abcd")
    tested.publish("bca")
    subs1.probe.expectMsg("bca")
    subs2.probe.expectMsg("bca")
    tested.publish("abc")
    subs1.probe.expectMsg("abc")
    subs2.probe.expectMsg("abc")
    tested.publish("_abc1234567890")
    subs1.probe.expectMsg("_abc1234567890")
    subs2.probe.expectMsg("_abc1234567890")
  }

  it must "not broadcast elements to the already cancelled subscribers" in new ActorSystemTest {
    val subs1 = new TestSubscriber[String]
    val subs2 = new TestSubscriber[String]
    val tested = new PublisherActor[String]
    tested.subscribe(subs1)
    tested.subscribe(subs2)
    val subscription1 = subs1.probe.expectMsgType[Subscription]
    val subscription2 = subs2.probe.expectMsgType[Subscription]
    subscription1.request(100)
    subscription2.request(10)
    tested.publish("abc")
    subs1.probe.expectMsg("abc")
    subs2.probe.expectMsg("abc")
    tested.publish("abcd")
    subs1.probe.expectMsg("abcd")
    subs2.probe.expectMsg("abcd")
    subscription1.cancel()
    tested.publish("bca")
    subs1.probe.expectNoMsg(100.millis)
    subs2.probe.expectMsg("bca")
    tested.publish("abc")
    subs1.probe.expectNoMsg(100.millis)
    subs2.probe.expectMsg("abc")
    subscription2.cancel()
    tested.publish("_abc1234567890")
    subs1.probe.expectNoMsg(100.millis)
    subs2.probe.expectNoMsg(100.millis)
  }

}
