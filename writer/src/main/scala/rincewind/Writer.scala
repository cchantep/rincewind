package rincewind 

import scala.util.Try

import com.typesafe.config.ConfigFactory
import akka.actor.{ ActorSystem, Props }

case object StartWriter
case object Retry

object Writer {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("application")

    (for {
      hostname <- Option(config getString "akka.rincewind.server.hostname")
      p <- Option(config getString "akka.rincewind.server.port")
      port <- Try(p.toInt).toOption
    } yield hostname -> port).fold(println(
      "Missing server configuration: akka.rincewind.server.{ hostname, port }")) {
      case (hostname, port) =>
        println("Will start writer...")

        ActorSystem("WriterSystem", config).
          actorOf(Props(classOf[WriterActor], hostname, port)) ! StartWriter
        
    }
  }
}

final class WriterActor(serverHost: String, serverPort: Int)
    extends akka.actor.Actor {

  import scala.util.Random
  import scala.concurrent.duration._
  import akka.actor.{
    ActorIdentity, ActorRef, Identify, ReceiveTimeout, Terminated
  }

  /** Path to the remote server actor */
  val path = s"akka.tcp://Rincewind@$serverHost:$serverPort/user/server"

  /** Last instance of server has terminated. */
  private val serverTerminated: Receive = {
    case Retry => {
      println(s"Server has terminated: $path")

      // TODO: Try to reconnect, for now just stop writer properly
      context.system.shutdown()
    }
    case msg => println(s"Unsupported message = $msg")
  }

  /** Writer has greeted the server, now it's active. */
  private def active(data: List[Int]): Receive = {
    case (offset: Int, length: Int) => 
      sendData(sender())(offset, data.drop(offset).take(length))
      
    case Terminated(_) => {
      context become serverTerminated
      self ! Retry
    }
    case msg => println(s"Unsupported message = $msg")
  }

  /** Writer is started, waiting for server location. */
  private val locating: Receive = {
    case ActorIdentity(`path`, None) =>
      println(s"Remote actor not available: $path")

    case ActorIdentity(`path`, Some(serverActor)) => {
      context.setReceiveTimeout(Duration.Undefined)

      println(s"Located server at $serverActor")

      context watch serverActor
      context become active(captureData)
      serverActor ! 0
    }

    case ReceiveTimeout =>
      println("Fails to locate the server")
      context.actorSelection(path) ! Identify(path)

    case msg => println(s"Unsupported message = $msg")
  }
    
  /** Local actor system is up, will try to locate server. */
  private val starting: Receive = {
    case StartWriter =>
      context become locating
      context.setReceiveTimeout(3.seconds)
      
      context.actorSelection(path) ! Identify(path)

      println(s"Started writer, waiting to locate server...")

    case msg => println(s"Unsupported message = $msg")
  }

  val receive = starting

  /** Fixture data */
  private def captureData: List[Int] = (1 to 10).toList

  /** Sends some data to the server. */
  @annotation.tailrec
  private def sendData(server: ActorRef)(offset: Int, data: List[Int]): Unit = data match {
    case next :: then => {
      server ! (offset, next)
      sendData(server)(offset+1, then)
    }
    case _ => ()
  }
}
