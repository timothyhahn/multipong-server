package multipongserver

import akka.actor.ActorSystem
import java.net.InetSocketAddress
import com.typesafe.config.ConfigFactory
import scala.collection.mutable.{ArrayBuffer, SynchronizedBuffer}
import com.typesafe.scalalogging.slf4j.Logging 

/** Application that is the server and kicks everything off **/
object ServerApp extends App with Logging {

  logger.info("Reading configuration")
  val conf = ConfigFactory.load
  val serverConf =  conf.getConfig("server")
  lazy val address: String = serverConf.getString("address")
  lazy val port: Int = serverConf.getInt("port")

  logger.info("Creating actors and server")
  val system = ActorSystem("echo-service-system")
  val endpoint = new InetSocketAddress(address, port)
  var lobbies: ArrayBuffer[Lobby] = new ArrayBuffer[Lobby] with SynchronizedBuffer[Lobby]

  system.actorOf(ReceivedMessageService.props(endpoint, lobbies), "receive-service")

  while(true) {
    lobbies.foreach(lobby => lobby.process)
    lobbies.filter(lobby => lobby.gameOver).foreach(lobby => lobbies -= lobby)
    Thread.sleep(1000 / 100) // 10 times per second
  }
}
