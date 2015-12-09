package latex.document

import akka.http.scaladsl._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import StatusCodes._
import Directives._
import spray.json._
import DefaultJsonProtocol._
import DocumentJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

object DocumentHttpService {
  val baseUrlPrefix = "documents"
}

class DocumentHttpService(documentService: DocumentService) extends SprayJsonSupport {

  val route = pathPrefix(DocumentHttpService.baseUrlPrefix) {
    path(Segment) { documentKey =>
      pathEndOrSingleSlash {
        get {
          onSuccess(documentService.getDocument(documentKey)) {
            case Some(document) => complete(OK, document)
            case None           => complete(NotFound)
          }
        } ~
          post {
            entity(as[String]) { text =>
              complete { OK }
            }
          }
      }
    }
  }
}