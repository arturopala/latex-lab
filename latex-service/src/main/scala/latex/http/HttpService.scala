package latex.http

import scala.concurrent.duration._
import scala.concurrent.{ Future }
import scala.util.{ Try, Success, Failure }
import scala.util.control.NonFatal

import com.typesafe.config.Config

import akka.actor.{ Actor, ActorSystem, Props, ActorRef, ActorLogging }
import akka.pattern.ask
import akka.util.Timeout

import akka.stream._
import akka.stream.scaladsl._
import akka.http.scaladsl._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server._
import StatusCodes._
import Directives._

import akka.event.Logging

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import DefaultJsonProtocol._

import org.reactivestreams.Publisher

/**
 * Http service exposing REST and WS API.
 */
class HttpService(config: Config)(implicit system: ActorSystem, materializer: ActorMaterializer) extends SprayJsonSupport {

  import scala.concurrent.ExecutionContext.Implicits.global

  import JsonProtocol._

  val workspace = config.getString("app.workspace")

  implicit val timeout = Timeout(5.seconds)

  def bind(host: String, port: Int): Future[Http.ServerBinding] = {
    Http().bindAndHandle(route, host, port) andThen {
      case Success(_) => println(s"Server online at http://$host:$port/")
      case Failure(e) => println(s"Error starting HTTP server: $e")
    }
  }

  val exceptionHandler = {
    ExceptionHandler {
      case e: Throwable =>
        complete(StatusCodes.InternalServerError)
    }
  }

  val rejectionHandler = {
    RejectionHandler.newBuilder()
      .handle {
        case MissingQueryParamRejection(param) =>
          complete(BadRequest, s"Request is missing required query parameter '$param'")
      }
      .handleAll[MethodRejection] { methodRejections ⇒
        val names = methodRejections.map(_.supported.name)
        complete(MethodNotAllowed, s"Can't do that! Supported: ${names mkString " or "}!")
      }
      .handleNotFound { complete(NotFound, "Not here!") }
      .result()
  }

  def handleWebsocket: Directive1[UpgradeToWebsocket] =
    optionalHeaderValueByType[UpgradeToWebsocket](()).flatMap {
      case Some(upgrade) ⇒ provide(upgrade)
      case None          ⇒ reject(ExpectedWebsocketRequestRejection)
    }

  def publisherAsMessageSource[A](p: Publisher[A])(f: A => String): Source[TextMessage, Unit] = Source(p).map(e => TextMessage.Strict(f(e))).named("tms")

  val pathEmpty: Directive0 = pathEnd | pathSingleSlash

  val AccessControlAllowAll = RawHeader("Access-Control-Allow-Origin", "*")

  //////////////////////////////////////////////////////////////////////
  //                   Main routing configuration                     //
  //////////////////////////////////////////////////////////////////////
  val route = handleRejections(rejectionHandler) {
    handleExceptions(exceptionHandler) {
      logRequestResult("http", Logging.InfoLevel) {
        get {
          path("app.js") { getFromResource("public/app.js") } ~
            path("style.css") { getFromResource("public/style.css") } ~
            pathPrefix("api") {
              complete(OK)
            } ~
            pathPrefix("ws") {
              pathEmpty {
                handleWebsocket { websocket =>
                  complete(websocket.handleMessagesWithSinkSource(Sink.ignore, Source.single(TextMessage.Strict("hello")), None))
                }
              }
            } ~
            pathPrefix("assets") {
              getFromResourceDirectory("public/")
            } ~
            pathPrefix("workspace") {
              getFromResourceDirectory(workspace)
            } ~
            getFromResource("public/index.html")
        }
      }
    }
  }

}
