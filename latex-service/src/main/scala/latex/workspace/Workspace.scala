package latex.workspace

import java.io.File
import java.nio.file.{ Files, StandardCopyOption, Path }
import java.nio.charset.{ Charset, StandardCharsets }
import akka.http.scaladsl._
import server.Route
import server.Directives._
import model._
import StatusCodes._
import latex.http.HttpService.contentTypeResolver
import concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait Workspace {
  def location: String
  def /(segment: String): Workspace
  def exists(filename: String): Boolean
  def list: Seq[String]
  def urlOf(filename: String): Option[String]
  def copyFrom(source: File, charset: Charset, destination: String): Future[Either[Exception, String]]
  def route: Route
}

object Workspace {
  val baseUrlPrefix = "resources"
  def apply(location: String): Workspace = new FileSystemWorkspace(location, baseUrlPrefix :: Nil)
}

class FileSystemWorkspace(val location: String, urlPrefix: List[String]) extends Workspace {

  val root = new File(location)

  def /(segment: String): Workspace = {
    val file = new File(root, segment)
    new FileSystemWorkspace(file.getPath, segment :: urlPrefix)
  }

  def exists(filename: String): Boolean = {
    val file = new File(root, filename)
    file.exists && file.isFile
  }

  def list: Seq[String] = root.list filter isPublic

  private def isPublic(filename: String) = true

  def urlOf(filename: String): Option[String] = if (isPublic(filename) && exists(filename))
    Some(urlPrefix.reverse.mkString("/", "/", "/"+filename)) else None

  def route: Route = pathPrefix(Workspace.baseUrlPrefix) {
    pathPrefix(Segment / Segment) { (documentKey, filename) =>
      if (isPublic(filename))
        getFromFile(new File(root, s"$documentKey/$filename"))
      else
        complete(NotFound)
    }
  }

  private def assertRootExists = if (!root.exists) root.mkdirs()

  def copyFrom(source: File, charset: Charset, destination: String): Future[Either[Exception, String]] = Future {
    try {
      assertRootExists
      Files.copy(source.toPath, root.toPath.resolve(destination), StandardCopyOption.REPLACE_EXISTING)
      Right(destination)
    }
    catch {
      case e: Exception =>
        println(e)
        Left(e)
    }

  }

}