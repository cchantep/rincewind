package rincewind

import akka.actor.{ ActorRef, PoisonPill }

case object StartSequence

class SequenceActor(writer: ActorRef, req: ReaderRequest)
    extends akka.actor.Actor {

  import scala.concurrent.duration._  
  import akka.actor.{ ReceiveTimeout, Terminated }

  def processing(pending: ReaderRequest)(offset: Int, count: Int): Receive = {
    case (`offset`, data: Int) => {
      println(s"Data from writer for ${req.uuid}: $offset -> $data")

      count match {
        case 1 /* last */ => {
          self ! PoisonPill // TODO: Wait everything is successfully sent back
          sendData(pending.seq) 
        }
        case _ => context become processing(
          pending.copy(seq = pending.seq :+ data))(offset + 1, count - 1)
      }
    }

    case (i: Int, _: Int) => 
      println(s"Issue with data order: $i != $offset")
      sender() ! (offset, count)

    case ReceiveTimeout => // TODO: Fallback strategy
      println(s"Too much time to get next data: $req") 

    case Terminated(_) => 
      println(s"Failure while processing sequence: $writer, $req")
      communicationError(pending)

    case msg => println(s"Unsupported sequence message: $msg")
  }

  val starting: Receive = {
    case StartSequence => {
      println(s"Will provide sequence for $req using $writer")

      context.setReceiveTimeout(5.seconds) // TODO: Get from config

      context watch writer
      context watch req.reader

      val st = 0 -> 10

      context become processing(req)(st._1, st._2)
      writer ! st // TODO: Get sequence size from config (there 10)
    }

    case Terminated(_) => 
      println(s"Failure before sequence generation: $writer, $req")
      communicationError(req)

    case msg => println(s"Unsupported message: $msg")
  }

  val receive = starting

  @inline private def communicationError(pending: ReaderRequest): Unit = {
    serverSelection ! CommunicationError(writer, pending)
    self ! PoisonPill
  }

  @annotation.tailrec
  private def sendData(data: List[Int]): Unit = data match {
    case d :: ds =>
      req.reader ! (req.uuid, d)
      sendData(ds)

    case _ =>
      req.reader ! (req.uuid, -1)
      serverSelection ! ReleaseWriter(writer)
  }

  @inline private def serverSelection = context.actorSelection("../server")
}
