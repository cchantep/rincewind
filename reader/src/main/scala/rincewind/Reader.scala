package rincewind 

import scala.util.Try

import com.typesafe.config.ConfigFactory
import akka.actor.{ ActorRef, ActorSystem, Props }

case object StartReader

case class ActiveState(
  server: ActorRef, /** Located server */

  /** Int sequences maintained on the reader, indexed by UUID. */
  sequences: Map[String, List[Int]] 
) // TODO: Use an in-memory storage for `sequences`

case class FetchSequences(count: Int)

case object Retry

object Reader {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("application")

    (for {
      hostname <- Option(config getString "akka.rincewind.server.hostname")
      p <- Option(config getString "akka.rincewind.server.port")
      port <- Try(p.toInt).toOption
    } yield hostname -> port).fold(println(
      "Missing server configuration: akka.rincewind.server.{ hostname, port }")) {
      case (hostname, port) =>
        println("Will start reader...")

        ActorSystem("ReaderSystem", config).
          actorOf(Props(classOf[ReaderActor], hostname, port)) ! StartReader
        
    }
  }
}

final class ReaderActor(serverHost: String, serverPort: Int)
    extends akka.actor.Actor {

  import java.util.UUID
  import scala.concurrent.duration._
  import akka.actor.{ ActorIdentity, Identify, ReceiveTimeout, Terminated }

  /** Path to the remote server actor */
  val path = s"akka.tcp://Rincewind@$serverHost:$serverPort/user/server"

  /** Last instance of server has terminated. */
  private val serverTerminated: Receive = {
    case Retry => {
      println(s"Server has terminated: $path")

      // TODO: Try to reconnect, for now just stop reader properly
      context.system.shutdown()
    }
    case msg => println(s"Unsupported message = $msg")
  }

  /**
   * There are some sequences to be fetched from the server. 
   * Will ask for the next one.
   */
  private def askingSequence(st: ActiveState)(remaining: Int, uuid: String, pending: List[Int]): Receive = {
    case (`uuid`, -1) => {
      println(s"Completed sequence #${st.sequences.size}: ${pending.reverse}")
      context become active(st.copy(
        sequences = st.sequences + (uuid -> pending.reverse)))
      self ! FetchSequences(remaining - 1)
    }
    case (`uuid`, data: Int) => {
      val Exp = data - 1
      pending.headOption match {
        case None | Some(Exp) =>
          println(s"Data from server for $uuid: $data")
          context become askingSequence(st)(remaining, uuid, data :: pending)

        case _ => // TODO: Retry
          println(s"Unexpected data: $data != ${pending.headOption} + 1")
      }
    }

    case ReceiveTimeout =>
      println("Too much time waiting for the pending sequence")
      // TODO: Retry

    case Terminated(_) => {
      context become serverTerminated
      self ! Retry
    }
    case msg => println(s"Unsupported message = $msg")
  }

  /** All sequences are available.  */
  private def completed(st: ActiveState): Receive = {
    // TODO: Stop the web
      
    case msg => println(s"Unsupported message = $msg")
  }

  /** Server is located, and new reader should get the sequences from there. */
  private def active(st: ActiveState): Receive = {
    case FetchSequences(0) => {
      println("All sequences are now found")
      context.setReceiveTimeout(Duration.Undefined)
      context unwatch sender() // Stop watching the server
      context become completed(st)
    }

    case FetchSequences(rem) =>
      val uuid = UUID.randomUUID.toString
      context become askingSequence(st)(rem, uuid, Nil)
      st.server ! (uuid -> 0) 

    case ReceiveTimeout =>
      println("Too much time waiting for sequences")
      // TODO: Retry

    case Terminated(_) => {
      context become serverTerminated
      self ! Retry
    }
      
    case msg => println(s"Unsupported message = $msg")
  }

  /** Reader is started, waiting for server location. */
  private val locating: Receive = {
    case ActorIdentity(`path`, None) =>
      println(s"Remote actor not available: $path")

    case ActorIdentity(`path`, Some(serverActor)) => {
      context.setReceiveTimeout(5.seconds) // TODO: Get from config

      println(s"Located server at $serverActor")

      context watch serverActor
      context become active(ActiveState(
        serverActor, Map.empty[String, List[Int]]))
      WebServer(self).run()

      self ! FetchSequences(1000) // TODO: Get from config
    }

    case ReceiveTimeout =>
      println("Fails to locate the server")
      context.actorSelection(path) ! Identify(path)

    case msg => println(s"Unsupported message = $msg")
  }
    
  /** Local actor system is up, will try to locate server. */
  private val starting: Receive = {
    case StartReader =>
      context become locating
      context.setReceiveTimeout(3.seconds)
      
      context.actorSelection(path) ! Identify(path)

      println(s"Started reader, waiting to locate server ($path) ...")

    case msg => println(s"Unsupported message = $msg")
  }

  val receive = starting  
}

