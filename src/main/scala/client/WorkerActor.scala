package client

import akka.actor._
import akka.actor.SupervisorStrategy._
import com.virtuslab.akkaworkshop.{PasswordDecoded, PasswordPrepared, Decrypter}
import akka.routing.ActorRefRoutee
import akka.routing.Router
import akka.routing.RoundRobinRoutingLogic

case class DecryptedPassword(encryptedPassword: String, decryptedPassword: String)

object ResetWorker

object PasswordRequest

class WorkerSupervisor extends Actor {


  var router = {
    val routees = (1 to 4).map { nr =>
      val r = context.actorOf(Props[WorkerActor], s"worker-$nr")
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  var client: ActorRef = _

  var messagesInSystem = 0

  def assusreFlow: Unit = {
    (messagesInSystem to 12).foreach {
      _ => client ! PasswordRequest
    }
  }

  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 0) {
      case exception: IllegalStateException =>
        context.children.foreach(_ ! ResetWorker)
        Resume
    }

  override def receive(): Receive = {
    case password: String =>
      client = sender
      router.route(password, self)
      messagesInSystem += 1
      assusreFlow
    case decryptedPassword: DecryptedPassword =>
      client ! decryptedPassword
      messagesInSystem -= 1
      assusreFlow
  }
}

/**
 * Author: Krzysztof Romanowski
 */
class WorkerActor extends Actor {

  def log(msg: String) = println(s"[${self}] [$currentPassword] $msg")

  var decrypter = new Decrypter
  var currentPassword: String = _
  var currentClient: ActorRef = _


  def cleanOldState: Receive = {
    case freshPassword: String =>
      self ! freshPassword
    case _ =>
      self ! currentPassword
      context.become(acceptPassword)

  }

  def supervising: Receive = {
    case ResetWorker =>
      log(s"Reseting")
      decrypter = new Decrypter
      context.become(cleanOldState)
  }

  def stashMessage: Receive = {
    case other => self ! other
  }

  def phase(phase: Receive) =
    supervising orElse phase orElse stashMessage

  def acceptPassword: Receive = phase {
    case password: String =>

      if (sender() != self)
        currentClient = sender()

      currentPassword = password

      log("prepare")

      self ! decrypter.prepare(password)

      context.become(decodePassword)
  }

  def decodePassword: Receive = phase {
    case prepared: PasswordPrepared =>
      log(s"decode")

      self ! decrypter.decode(prepared)

      context.become(decryptPassword)
  }

  def decryptPassword = phase {
    case decoded: PasswordDecoded =>
      log("Decrypt")

      val decrypted = decrypter.decrypt(decoded)

      self ! DecryptedPassword(currentPassword, decrypted)

      context.become(sendPassword)
  }

  def sendPassword = phase {
    case decodedPassword: DecryptedPassword =>
      currentClient ! decodedPassword
      context.become(acceptPassword)
  }

  override def receive: Receive = acceptPassword
}
