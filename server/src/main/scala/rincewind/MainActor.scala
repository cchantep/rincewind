package rincewind 

import scala.util.Random

import akka.actor.ActorRef

case object StartServer

case class ReaderRequest(reader: ActorRef, uuid: String, seq: List[Int])
case class CommunicationError(writer: ActorRef, req: ReaderRequest)
case class ReleaseWriter(writer: ActorRef)

class MainActor extends akka.actor.Actor {
  import akka.actor.Props

  /**
    * @param writers Available writers
    * @param waiting Request from readers waiting for an available writer
    */
  private def active(writers: List[ActorRef])(waiting: List[ReaderRequest]): Receive = {
    case StartServer => println(s"Started server: ${self.path}")
    case 0 => {
      val writer = sender()
      println(s"Writer is greeting us: $writer")

      waiting match {
        case req :: rs => {
          pair(writer, req)
          context become active(writers)(rs)
        }

        case _/* Nil */ => {
          println("No waiting request, will use writer later")
          context become active(writer :: writers)(Nil)
        }
      }
    }
    case (uuid: String, 0) => {
      val req = ReaderRequest(sender(), uuid, Nil)

      println(s"Reader is asking for a new sequence: $req")

      writers match {
        case writer :: ws => {
          pair(writer, req)
          context become active(ws)(waiting)
        }
        case _/* Nil */ => {
          println("No available writer, will wait for")
          context become active(Nil)(req :: waiting)
        }
      }
    }
    case ReleaseWriter(w) => 
      println(s"Released writer: $w")
      context become active(w :: writers)(waiting)

    case CommunicationError(w, req) => {
      println(s"Communication error: $w, $req")

      // TODO: Check whether `w` writer is still available
      // (for now let it aside)

      // TODO: Check reader is still available

      Random.shuffle(/*w :: */writers) match {
        case nw :: ws => // randomly pickup a new writer
          pair(nw, req) // new pairing
          context become active(ws)(waiting)

        case _ => context become active(writers)(req :: waiting)
      }
    }
    case msg => println(s"Unsupported message = $msg")
  }

  val receive = active(Nil)(Nil)

  /** Pairs a request from a reader with an available writer. */
  private def pair(writer: ActorRef, req: ReaderRequest): Unit = {
    println(s"Pairing writer & reader: $writer / $req")
    context.system.actorOf(Props(classOf[SequenceActor],
      writer, req)) ! StartSequence

  }
}
