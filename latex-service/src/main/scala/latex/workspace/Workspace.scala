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
  def copyFrom(source: File, charset: Charset, filename: String): Future[Either[Exception, String]]
  def route: Route
}

object Workspace {
  val baseUrlPrefix = "resources"
  def apply(location: String): Workspace = new FileSystemWorkspace(location, baseUrlPrefix :: Nil)

  val blacklist = Seq(".log")
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

  private def walk(file: File): Seq[String] = {
    import scala.collection.JavaConversions._
    import java.nio.file.{ Files, Path }
    import java.util.stream.Collectors
    val rootPath = root.toPath
    val paths = Files.walk(rootPath).collect(Collectors.toList())
    iterableAsScalaIterable[Path](paths)
      .map(path => rootPath.relativize(path).toString)
      .filter(f => isPublic(f) && exists(f))
      .toSeq
  }

  def list: Seq[String] = walk(root)

  private def isPublic(filename: String) = (!filename.isEmpty) && (!filename.startsWith(".")) && (Workspace.blacklist forall (!filename.endsWith(_)))

  def urlOf(filename: String): Option[String] = if (isPublic(filename) && exists(filename))
    Some(urlPrefix.reverse.mkString("/", "/", "/"+filename)) else None

  def route: Route = pathPrefix(Workspace.baseUrlPrefix) {
    pathPrefix(Segment / Rest) { (documentKey, filepath) =>
      if (isPublic(filepath))
        getFromFile(new File(root, s"$documentKey/$filepath"))
      else
        complete(NotFound)
    }
  }

  private def assertRootExists = if (!root.exists) root.mkdirs()

  def copyFrom(source: File, charset: Charset, filename: String): Future[Either[Exception, String]] = Future {
    try {
      assertRootExists
      //TODO check charset and transcode to utf-8 if needed
      Files.copy(source.toPath, root.toPath.resolve(filename), StandardCopyOption.REPLACE_EXISTING)
      Right(filename)
    }
    catch {
      case e: Exception =>
        println(e)
        Left(e)
    }

  }

}