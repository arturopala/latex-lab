package latex

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import scala.concurrent.duration._

object Boot extends App {

  implicit val futureTimeout = Timeout(10.seconds)

  val module = new Module
  import module.system.dispatcher

  val port = module.config.getInt("app.http.port")
  val httpBinding = module.httpService.bind("0.0.0.0", port)

  println(s"Workspace location: ${module.root.location}")

  module.system.whenTerminated.onComplete(_ =>
    httpBinding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ ⇒ module.system.shutdown()) // and shutdown when done
      )

}
