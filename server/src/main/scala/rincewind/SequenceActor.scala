package rincewind

import akka.actor.ActorRef

case object StartSequence

class SequenceActor(writer: ActorRef, req: ReaderRequest)
    extends akka.actor.Actor {

  import scala.concurrent.duration._  
  import akka.actor.{ ReceiveTimeout, Terminated }

  def processing(pending: ReaderRequest): Receive = {
    case ReceiveTimeout => // TODO: Fallback strategy
      println(s"Too much time to get next data: $req") 

    case Terminated(_) => {
      println(s"Failure before sequence generation: $writer, $req")
      context.actorSelection("server") ! CommunicationError(writer, req)
    }
    case msg => println(s"Unsupported message: $msg")      
  }

  val starting: Receive = {
    case StartSequence => {
      println(s"Will provide sequence for $req using $writer")

      context.setReceiveTimeout(5.seconds) // TODO: Get from config

      context watch writer
      context watch req.reader

      context become processing(req)
      writer ! (0, 10) // TODO: Get sequence size from config (there 10)
    }
    case Terminated(_) => {
      println(s"Failure before sequence generation: $writer, $req")
      context.actorSelection("server") ! CommunicationError(writer, req)
    }
    case msg => println(s"Unsupported message: $msg")
  }

  val receive = starting
}
