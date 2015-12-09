package latex.http

import org.scalatest.{ Finders, FlatSpecLike, Matchers }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._
import spray.json._
import DefaultJsonProtocol._

import latex._
import document._

class HttpServiceSpec extends FlatSpecLike with Matchers with ScalatestRouteTest with ActorSystemTestKit {

  val module = new Module

  import DocumentJsonProtocol._

  println(s"Workspace location: ${module.root.location}")

  "GET /documents/test" should "return document info in json format" in {
    Get("/documents/test") ~> module.httpService.route ~> check {
      status should be(OK)
      contentType should be(ContentType(MediaTypes.`application/json`, HttpCharsets.`UTF-8`))
      responseAs[String].parseJson.convertTo[Document] >> { document =>
        document.url shouldBe "/resources/test/test.tex"
        document.resources should have size 3
      }
    }
  }

  "GET /resources/test/test.tex" should "return test tex document" in {
    Get("/resources/test/test.tex") ~> module.httpService.route ~> check {
      status should be(OK)
      contentType should be(ContentType(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`))
      responseAs[String] should include("""\begin{figure}[h!]""")
    }
  }

  "GET /resources/test/ExampleProjectTest.bib" should "return test bib document" in {
    Get("/resources/test/ExampleProjectTest.bib") ~> module.httpService.route ~> check {
      status should be(OK)
      contentType should be(ContentType(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`))
      responseAs[String] should include("""url={http://books.google.com/books?id=W-xMPgAACAAJ},""")
    }
  }

  "GET /resources/test/ExampleProjectTest.jpg" should "return test jpeg image" in {
    Get("/resources/test/ExampleProjectTest.jpg") ~> module.httpService.route ~> check {
      status should be(OK)
      contentType should be(ContentType(MediaTypes.`image/jpeg`))
    }
  }

}
