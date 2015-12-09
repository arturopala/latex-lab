package latex

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import scala.concurrent.duration._

object Boot extends App {

  import system.dispatcher
  implicit val futureTimeout = Timeout(10.seconds)

  implicit val system = ActorSystem("app")
  implicit val materializer = akka.stream.ActorMaterializer()
  implicit val module = new Module

  val port = module.config.getInt("app.http.port")
  val httpBinding = module.httpService.bind("0.0.0.0", port)

  println(s"Workspace location: ${module.root.location}")

  system.whenTerminated.onComplete(_ =>
    httpBinding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ ⇒ system.shutdown()) // and shutdown when done
      )

}
