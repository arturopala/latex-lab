package latex.document

case class Document(url: String, resources: Seq[String])

import spray.json._
import DefaultJsonProtocol._

object DocumentJsonProtocol extends DefaultJsonProtocol {

  implicit val DocumentJsonFormat = jsonFormat2(Document.apply)

}