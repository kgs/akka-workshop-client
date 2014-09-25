package client

import akka.actor.{ActorRef, Actor}
import akka.actor.Actor.Receive
import com.virtuslab.akkaworkshop.{PasswordDecoded, PasswordPrepared, Decrypter}
import scala.util.Try
import java.sql.PreparedStatement


case class DecryptedPassword(encryptedPassword: String, decryptedPassword: String)

/**
 * Author: Krzysztof Romanowski
 */
class WorkerActor extends Actor {

  val decrypter = new Decrypter
  var currentPassword: String = _
  var currentClient: ActorRef = _

  def errorHandling[T](op: => Unit): Unit =
    try {
      op
    } catch {
      case exception: IllegalStateException =>
        println(exception.getMessage)
        self ! currentPassword
    }

  override def receive: Receive = {
    case password: String =>
      currentPassword = password
      if (sender() != self)
        currentClient = sender()

      println(s"prepare for $currentPassword")

      errorHandling(self ! decrypter.prepare(password))

    case prepared: PasswordPrepared =>
      println(s"decode for $currentPassword")

      errorHandling(self ! decrypter.decode(prepared))

    case decoded: PasswordDecoded =>
      println(s"decrpty for $currentPassword")

      errorHandling {
        currentClient ! DecryptedPassword(currentPassword, decrypter.decrypt(decoded))
      }
  }
}
