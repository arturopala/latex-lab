package latex.document

import akka.http.scaladsl._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import StatusCodes._
import Directives._
import headers.Location
import spray.json._
import DefaultJsonProtocol._
import DocumentJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import java.io.File
import latex.http.HttpService._

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
            uploadRequestBodyToFile(Seq(MediaTypes.`text/plain`), File.createTempFile(documentKey, ".tmp")) {
              case (contentType, file) =>
                onSuccess(documentService.setDocumentFile(documentKey, file, contentType.charset.nioCharset)) {
                  case Right(url) =>
                    respondWithHeader(Location(Uri(url))) {
                      complete(OK)
                    }
                  case Left(exception) =>
                    failWith(exception)
                }
            }
          }
      }
    }
  }

}