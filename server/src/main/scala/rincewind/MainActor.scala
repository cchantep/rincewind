package rincewind 

import scala.util.Random

import akka.actor.ActorRef

case object StartServer

case class ReaderRequest(reader: ActorRef, uuid: String, seq: List[Int])
case class CommunicationError(writer: ActorRef, req: ReaderRequest)

class MainActor extends akka.actor.Actor {
  import akka.actor.Props

  /**
    * @param writers Available writers
    * @param waiting Request from readers waiting for an available writer
    */
  private def started(writers: List[ActorRef])(waiting: List[ReaderRequest]): Receive = {
    case StartServer => println(s"Started server: ${self.path}")
    case 0 => {
      val writer = sender()
      println(s"Writer is greeting us: $writer")

      waiting match {
        case req :: rs => {
          pair(writer, req)
          context become started(writers)(rs)
        }

        case _/* Nil */ => {
          println("No waiting request, will use writer later")
          context become started(writer :: writers)(Nil)
        }
      }
    }
    case (uuid: String, 0) => {
      val req = ReaderRequest(sender(), uuid, Nil)

      println(s"Reader is asking for a new sequence: $req")

      writers match {
        case writer :: ws => {
          pair(writer, req)
          context become started(ws)(waiting)
        }
        case _/* Nil */ => {
          println("No available writer, will wait for")
          context become started(Nil)(req :: waiting)
        }
      }
    }
    case CommunicationError(w, req) => {
      println(s"Communication error: $w, $req")

      Random.shuffle(w :: writers) match {
        case nw :: ws => // randomly pickup a new writer
          pair(nw, req) // new pairing
          context become started(ws)(waiting)

        case _ => println("WoW!!! This is weird")
      }
    }
    case msg => println(s"Unsupported message = $msg")
  }

  val receive = started(Nil)(Nil)

  /** Pairs a request from a reader with an available writer. */
  private def pair(writer: ActorRef, req: ReaderRequest): Unit = {
    println(s"Pairing writer & reader: $writer / $req")
    context.system.actorOf(Props(classOf[SequenceActor],
      writer, req)) ! StartSequence

  }
}
