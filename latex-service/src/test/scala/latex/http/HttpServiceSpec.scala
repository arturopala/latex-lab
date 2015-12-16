package latex.http

import org.scalatest.{ Finders, FlatSpecLike, Matchers }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._
import spray.json._
import DefaultJsonProtocol._
import org.scalatest.prop.PropertyChecks
import org.scalacheck.Gen._
import java.util.UUID

import latex._
import document._

class HttpServiceSpec extends FlatSpecLike with Matchers with PropertyChecks with ScalatestRouteTest with ActorSystemTestKit {

  val module = new Module

  import DocumentJsonProtocol._

  println(s"Workspace location: ${module.root.location}")

  "GET /documents/test" should "return document info in json format" in {
    Get("/documents/test") ~> module.httpService.route ~> check {
      status should be(OK)
      contentType should be(ContentType(MediaTypes.`application/json`, HttpCharsets.`UTF-8`))
      responseAs[String].parseJson.convertTo[Document] >> { document =>
        document.url shouldBe "/resources/test/test.tex"
        document.resources should have size 4
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

  "GET /resources/test/images/test.jpg" should "return test jpeg image" in {
    Get("/resources/test/images/test.jpg") ~> module.httpService.route ~> check {
      status should be(OK)
      contentType should be(ContentType(MediaTypes.`image/jpeg`))
    }
  }

  "GET /resources/test/test.log" should "return 404" in {
    Get("/resources/test/test.log") ~> module.httpService.route ~> check {
      status should be(NotFound)
    }
  }

  "POST /documents/foo" should "store new content and return resource url" in {
    val prefix: String = alphaStr(Parameters.default.withSize(8)).get
    forAll((alphaStr, "text"), (uuid, "suffix"), minSize(1024), maxSize(100 * 1024), minSuccessful(50)) { (text: String, suffix: UUID) =>
      val key = prefix+"_"+suffix
      Post(s"/documents/$key", HttpEntity(ContentTypes.`text/plain(UTF-8)`, text)) ~> module.httpService.route ~> check {
        status should be(OK)
        header("Location") shouldBe Some(Location(Uri(s"/resources/$key/$key.tex")))
        module.root.exists(s"$key/$key.tex") shouldBe true
      }
    }
    new java.io.File(module.root.location).listFiles filter (_.getName.startsWith(prefix+"_")) foreach { file =>
      new java.io.File(file, file.getName+".tex").delete
      file.delete
    }
  }

}
