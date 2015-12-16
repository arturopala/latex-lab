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
import document.{ DocumentHttpService, DocumentService, DocumentServiceActorStage }
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import latex.workspace.Workspace

class Module() {

  implicit lazy val system = ActorSystem("app")
  implicit lazy val materializer = akka.stream.ActorMaterializer()
  lazy val config = ConfigFactory.load()
  lazy val root: Workspace = Workspace(config.getString("app.workspace"))
  lazy val timeout: FiniteDuration = FiniteDuration(config.getDuration("app.http.timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
  lazy val httpService: HttpService = wire[HttpService]
  lazy val documentHttpService: DocumentHttpService = wire[DocumentHttpService]
  lazy val documentService: DocumentService = wire[DocumentServiceActorStage]
}

object ActorOf {
  def apply[T](name: String, args: Any*)(implicit factory: ActorRefFactory, ct: ClassTag[T]): ActorRef = factory.actorOf(Props(ct.runtimeClass, args: _*), name)
}
