package latex.actor

import org.scalatest.{ FlatSpecLike, Matchers }
import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import com.typesafe.config.ConfigFactory
import akka.testkit.TestProbe
import scala.concurrent.duration._
import akka.actor.{ Actor, ActorRef, Props }
import org.scalatest.prop.PropertyChecks

import latex._

class ActorRefPoolSpec extends FlatSpecLike with Matchers with PropertyChecks with ActorSystemTestKit {

  def createTestActorRefPool(): TestActorRef[TestActorRefPool] = TestActorRef(Props(classOf[TestActorRefPool]))(actorSystem)

  "ActorRefPool" should "crete new actors ref for each distinct key and forward messages to them" in new ActorSystemTest {
    val tested = createTestActorRefPool()
    val testedActor = tested.underlyingActor
    info("create new actors for new keys")
    testedActor.pool should have size 0
    tested ! TestMessage("A")
    val probe1 = testedActor.probes.head
    probe1.expectMsg(TestMessage("A"))
    testedActor.pool should have size 1
    tested ! TestMessage("AA")
    val probe2 = testedActor.probes.head
    probe2.expectMsg(TestMessage("AA"))
    testedActor.pool should have size 2
    tested ! TestMessage("AA")
    testedActor.probes.head.expectMsg(TestMessage("AA"))
    testedActor.pool should have size 2
    tested ! TestMessage("AAA")
    testedActor.probes.head.expectMsg(TestMessage("AAA"))
    testedActor.pool should have size 3
    tested ! TestMessage("B")
    testedActor.probes.head.expectMsg(TestMessage("B"))
    testedActor.pool should have size 4
    tested ! TestMessage("C")
    testedActor.probes.head.expectMsg(TestMessage("C"))
    testedActor.pool should have size 5
    info("send random messages to one actor")
    forAll(minSuccessful(500)) { value: String =>
      tested ! TestMessage("AA", value)
      probe2.expectMsg(TestMessage("AA", value))
    }
    testedActor.pool should have size 5
    info("force stop an existing actor")
    actorSystem.stop(testedActor.probes.head.ref)
    Thread.sleep(200)
    testedActor.pool should have size 4
    info("recreate actor again sending message")
    tested ! TestMessage("C")
    testedActor.probes.head.expectMsg(TestMessage("C"))
    testedActor.pool should have size 5
  }

}

class TestActorRefPool() extends ActorRefPool[String] {

  var probes: List[TestProbe] = Nil

  def createNewActorRefFor(key: String): ActorRef = {
    val probe = TestProbe()(context.system)
    probes = probe :: probes
    probe.ref
  }
}

case class TestMessage(key: String, value: String = "") extends KeyHolder[String]