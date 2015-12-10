package latex.document

import akka.actor._
import latex.workspace.Workspace
import latex.actor.KeyHolder
import java.io.File
import java.nio.charset.{ Charset, StandardCharsets }
import akka.pattern.pipe

case class GetDocument(key: String) extends KeyHolder[String]
case class SetDocument(key: String, file: File, charset: Charset) extends KeyHolder[String]

object DocumentActor {
  def props(documentKey: String, workspace: Workspace): Props = Props(classOf[DocumentActor], documentKey, workspace)
}

class DocumentActor(documentKey: String, workspace: Workspace) extends Actor {

  val tex = s"$documentKey.tex"

  implicit val executor = context.system.dispatcher

  def receive: Receive = {

    case GetDocument(key) if key == documentKey =>
      val documentOpt = workspace.urlOf(tex) map { url =>
        Document(url, resourceUrls)
      }
      sender() ! documentOpt

    case SetDocument(key, file, charset) if key == documentKey =>
      workspace.copyFrom(file, charset, tex) map {
        case Right(filename) =>
          println(filename)
          workspace.urlOf(filename).map(Right(_)).getOrElse(Left(new java.io.FileNotFoundException()))
        case left => left
      } pipeTo sender()

  }

  def resourceUrls: Seq[String] = workspace.list filterNot (_ == tex) map workspace.urlOf collect { case Some(e) => e }

}

import scala.util.{ Try, Success, Failure }
import latex.actor.ActorRefPool

class DocumentActorRouter(workspace: Workspace) extends ActorRefPool[String] {

  def createNewActorRefFor(key: String): ActorRef = {
    context.actorOf(DocumentActor.props(key, workspace / key))
  }

}

