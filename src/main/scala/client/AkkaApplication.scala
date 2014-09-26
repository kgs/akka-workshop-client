package client

import akka.actor._
import akka.routing.RoundRobinRouter
import com.virtuslab.akkaworkshop.Decrypter
import com.virtuslab.akkaworkshop.PasswordsDistributor._

import scala.util.{Failure, Success, Try}


class WorkerActor extends Actor {

  var currentToken: Token = _
  var remoteActor: ActorSelection = _

  private def requestNewPassword(): Unit = remoteActor ! SendMeEncryptedPassword(currentToken)

  private def decryptPassword(password: String): Try[String] = Try {
    val decrypter = new Decrypter
    val prepared = decrypter.prepare(password)
    val decoded = decrypter.decode(prepared)
    val decrypted = decrypter.decrypt(decoded)
    decrypted
  }

  override def receive: Receive = {

    //once actor is register ask for first password
    case (actorSelection: ActorSelection, token: String) =>
      println(s"Starting worker: $self")
      remoteActor = actorSelection
      currentToken = token
      requestNewPassword()

    //once you got a password to decrypt - do it!
    case EncryptedPassword(encryptedPassword) =>
      println(s"Encrypted password from server: $encryptedPassword")
      decryptPassword(encryptedPassword) match {
        case Success(decryptedPassword) => {
          println(s"Success: $decryptedPassword)")
          remoteActor ! ValidateDecodedPassword(currentToken, encryptedPassword, decryptedPassword)
        }
        case Failure(t) => {
          println(s"Failure: $t")
          self ! EncryptedPassword(encryptedPassword)
        }
      }

    //correct password
    case PasswordCorrect(password) =>
      println(s"Correct password: $password")
      requestNewPassword()

    //incorrect password
    case PasswordIncorrect(password) =>
      println(s"Incorrect password: $password")
      requestNewPassword()
  }
}

class MasterActor extends Actor {

  var currentToken: Token = _
  var remoteActor: ActorSelection = _
  var workerActor: ActorRef = _

  override def receive: Receive = {

    case (actorSelection: ActorSelection, worker: ActorRef) =>
      remoteActor = actorSelection
      workerActor = worker

      //register a actor
      remoteActor ! Register(s"@kgs42")

    case Registered(token) =>
      println("Registered with token: $token")
      for (n <- 1 until 4) workerActor ! (remoteActor, token)
  }
}

/**
 * Author: Krzysztof Romanowski
 */
object AkkaApplication extends App {
  println("### Starting simple application ###")

  //actor system
  val system = ActorSystem("HelloSystem")

  //distributed actor
  val remoteActor = system.actorSelection("akka.tcp://application@192.168.88.1:9552/user/PasswordsDistributor")

  val masterActor = system.actorOf(Props[MasterActor])
  val workerActor = system.actorOf(Props[WorkerActor].withRouter(RoundRobinRouter(nrOfInstances = 4)))

  masterActor ! (remoteActor, workerActor)

  system.awaitTermination()
}
