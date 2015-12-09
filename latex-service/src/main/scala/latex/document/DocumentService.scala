package latex.document

import scala.concurrent.{ Future }
import latex.workspace.Workspace

trait DocumentService {
  def getDocument(documentId: String): Future[Option[Document]]
}

import scala.concurrent.duration._
import scala.util.{ Try, Success, Failure }
import scala.util.control.NonFatal
import akka.actor.{ Actor, ActorSystem, Props, ActorRef, ActorLogging }
import akka.pattern.ask
import akka.util.Timeout

class DocumentServiceActorStage(workspace: Workspace, actorSystem: ActorSystem, timeout: FiniteDuration) extends DocumentService {

  implicit val askTimeout = Timeout(timeout)
  val router = actorSystem.actorOf(Props(classOf[DocumentActorRouter], workspace))

  def getDocument(documentId: String): Future[Option[Document]] = (router ? GetDocument(documentId)).asInstanceOf[Future[Option[Document]]]

}