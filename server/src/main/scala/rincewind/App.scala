package rincewind

import com.typesafe.config.ConfigFactory
import akka.actor.{ ActorSystem, Props }

object App {
  def main(args: Array[String]): Unit = {
    println("Will start server...")
    
    ActorSystem("Rincewind", ConfigFactory.load("application")).
      actorOf(Props[MainActor], "server") ! StartServer
  }
}
