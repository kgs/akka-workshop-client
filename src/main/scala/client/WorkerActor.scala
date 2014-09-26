package client

import akka.actor._
import akka.actor.SupervisorStrategy._
import com.virtuslab.akkaworkshop.{PasswordDecoded, PasswordPrepared, Decrypter}


case class DecryptedPassword(encryptedPassword: String, decryptedPassword: String)

object ResetWorker

class WorkerSupervisor extends Actor {


  val actor = context.actorOf(Props[WorkerActor], "worker")

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 0) {
      case exception: IllegalStateException =>
        sender() ! ResetWorker
        Resume
    }

  override def receive(): Receive = {
    case msg => actor forward msg
  }
}

/**
 * Author: Krzysztof Romanowski
 */
class WorkerActor extends Actor {

  var decrypter = new Decrypter
  var currentPassword: String = _
  var currentClient: ActorRef = _


  override def receive: Receive = {

    case ResetWorker =>
      decrypter = new Decrypter
      self ! currentPassword

    case password: String =>
      currentPassword = password
      if (sender() != self)
        currentClient = sender()

      println(s"prepare for $currentPassword")

      self ! decrypter.prepare(password)

    case prepared: PasswordPrepared =>
      println(s"decode for $currentPassword")

      self ! decrypter.decode(prepared)

    case decoded: PasswordDecoded =>
      println(s"decrpty for $currentPassword")

      currentClient ! DecryptedPassword(currentPassword, decrypter.decrypt(decoded))
  }
}
