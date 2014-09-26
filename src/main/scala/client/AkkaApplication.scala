package client


import akka.actor._
import com.typesafe.config.ConfigFactory
import com.virtuslab.akkaworkshop.{Decrypter, PasswordsDistributor}
import com.virtuslab.akkaworkshop.PasswordsDistributor._
import scala.util.Try

object Start

class WorkflowActor extends Actor {

  var currentToken: Token = _
  var remoteActor = context.actorSelection("akka.tcp://application@127.0.0.1:9552/user/PasswordsDistributor")

  val worker = context.actorOf(Props[WorkerSupervisor])

  private def requestNewPassword(): Unit = remoteActor ! SendMeEncryptedPassword(currentToken)

  override def receive: Receive = {

    //start with actor from server
    case Start=>
       remoteActor ! "ala ma kota"
      //register a actor
      remoteActor ! Register(System.getProperty("user.name"))

    //once actor is register ask for first password
    case Registered(token) =>
      currentToken = token
      requestNewPassword()

    //once you got a password to decrypt - do it!
    case EncryptedPassword(encryptedPassword) =>

      //send to worker
      worker ! encryptedPassword

    case DecryptedPassword(encryptedPassword, decryptedPassword) =>
      remoteActor ! ValidateDecodedPassword(currentToken, encryptedPassword, decryptedPassword)

    case PasswordRequest =>
      requestNewPassword()

    //correct password
    case PasswordCorrect(password) =>
      println(s"Correct password: $password")
      requestNewPassword()

    //incorrect password
    case PasswordIncorrect(password) =>
      println(s"Incorrect password: $password")

      requestNewPassword()

    case any =>
      println("Not knowing: " + any)
  }
}

/**
 * Author: Krzysztof Romanowski
 */
object AkkaApplication extends App {
  println("### Starting simple application ###")

  //actor system
  val system = ActorSystem("HelloSystem")

  //distributed actor - refactor to remote one

  //system.actorOf(Props[PasswordsDistributor], "sample-distributor")

  //to be replaced with solid code
  val listenerActor = system.actorOf(Props[WorkflowActor], "workflow")

  listenerActor ! Start

  system.awaitTermination()
}
