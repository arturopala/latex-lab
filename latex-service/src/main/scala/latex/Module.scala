package latex

import scala.reflect.ClassTag
import akka.actor.Props
import scala.concurrent.duration._
import akka.actor.IndirectActorProducer
import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.ActorRefFactory
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import latex.http.HttpService
import com.typesafe.config.ConfigFactory

import com.softwaremill.macwire._

class Module(implicit system: ActorSystem, materializer: ActorMaterializer) {

  lazy val config = ConfigFactory.load()
  lazy val httpService: HttpService = wire[HttpService]
}

object ActorOf {
  def apply[T](name: String, args: Any*)(implicit factory: ActorRefFactory, ct: ClassTag[T]): ActorRef = factory.actorOf(Props(ct.runtimeClass, args: _*), name)
}
