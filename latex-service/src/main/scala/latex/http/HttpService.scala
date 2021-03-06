package latex.http

import scala.concurrent.duration._
import scala.concurrent.{ Future }
import scala.util.{ Try, Success, Failure, Left, Right }
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
import directives._
import akka.event.Logging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import DefaultJsonProtocol._
import org.reactivestreams.Publisher
import latex.workspace.Workspace
import java.io.File

/**
 * Http service exposing resources, REST and WS APIs.
 */
class HttpService(
    documentHttpService: latex.document.DocumentHttpService,
    workspace:           Workspace
)(implicit system: ActorSystem, materializer: ActorMaterializer) extends SprayJsonSupport {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val timeout = Timeout(5.seconds)

  def bind(host: String, port: Int): Future[Http.ServerBinding] = {
    Http().bindAndHandle(route, host, port) andThen {
      case Success(_) => println(s"Server online at http://$host:$port/")
      case Failure(e) => println(s"Error starting HTTP server: $e")
    }
  }

  import HttpService._

  //////////////////////////////////////////////////////////////////////
  //                   Main routing configuration                     //
  //////////////////////////////////////////////////////////////////////
  val route =
    handleRejections(rejectionHandler) {
      handleExceptions(exceptionHandler) {
        logRequestResult("http", Logging.InfoLevel) {
          respondWithHeaders(AccessControlAllowAll) {
            documentHttpService.route ~
              workspace.route
          } ~
            websocketRoute ~
            assetsRoute
        }
      }
    }

}

object HttpService {

  def publisherAsMessageSource[A](p: Publisher[A])(f: A => String): Source[TextMessage, Unit] = Source(p).map(e => TextMessage.Strict(f(e))).named("tms")

  val pathEmpty: Directive0 = pathEnd | pathSingleSlash

  val AccessControlAllowAll = RawHeader("Access-Control-Allow-Origin", "*")

  implicit val contentTypeResolver = ContentTypeResolver {
    case PlainTextMediaType() => ContentTypes.`text/plain(UTF-8)`
    case f                    => ContentTypeResolver.Default(f)
  }

  val assetsRoute =
    path("app.js") { getFromResource("public/app.js") } ~
      path("style.css") { getFromResource("public/style.css") } ~
      pathPrefix("assets") {
        getFromResourceDirectory("public/")
      } ~
      getFromResource("public/index.html")

  val websocketRoute =
    pathPrefix("ws") {
      pathEmpty {
        handleWebsocket { websocket =>
          complete(websocket.handleMessagesWithSinkSource(Sink.ignore, Source.single(TextMessage.Strict("hello")), None))
        }
      }
    }

  val PlainTextMediaType = ".+?\\.(?:tex|bib|txt)".r

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
      .handleNotFound { complete(NotFound, "Nothing exists here!") }
      .result()
  }

  def handleWebsocket: Directive1[UpgradeToWebsocket] =
    optionalHeaderValueByType[UpgradeToWebsocket](()).flatMap {
      case Some(upgrade) ⇒ provide(upgrade)
      case None          ⇒ reject(ExpectedWebsocketRequestRejection)
    }

  def uploadRequestBodyToFile(acceptedMediaTypes: Seq[MediaType], destination: File): Directive1[(ContentType, File)] = extractRequestContext.flatMap { ctx ⇒
    import ctx.executionContext
    val entity = ctx.request.entity
    acceptedMediaTypes.find(_ == entity.contentType.mediaType) match {
      case None => complete(NotAcceptable)
      case Some(mediaType) =>
        val uploading = entity.getDataBytes.runWith(Sink.file(destination), ctx.materializer).map(_ => (entity.contentType, destination))
        onComplete(uploading) flatMap {
          case Success(uploaded) => provide(uploaded)
          case Failure(ex) =>
            destination.delete()
            failWith(ex)
        }
    }
  }

}
