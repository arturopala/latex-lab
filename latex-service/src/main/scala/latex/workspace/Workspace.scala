package latex.workspace

import java.io.File
import akka.http.scaladsl._
import server.Route
import server.Directives._
import model._
import StatusCodes._
import latex.http.HttpService.contentTypeResolver

trait Workspace {
  def location: String
  def /(segment: String): Workspace
  def exists(filename: String): Boolean
  def list: Seq[String]
  def urlOf(filename: String): Option[String]
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

}