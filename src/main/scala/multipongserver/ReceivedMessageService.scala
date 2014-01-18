package multipongserver

/** Java Imports **/
import java.net.InetSocketAddress

/** Akka Imports **/
import akka.io.{IO, Tcp, TcpReadWriteAdapter, BackpressureBuffer, DelimiterFraming, StringByteStringAdapter, TcpPipelineHandler}
import akka.actor.{Actor, ActorLogging, Deploy, Props}
import akka.util.ByteString

/** Scala Imports **/
import scala.collection.mutable.ArrayBuffer

/** Factory for [[multipongserver.ReceivedMessageService]] instances */
object ReceivedMessageService {
  def props(endpoint: InetSocketAddress, lobbies: ArrayBuffer[Lobby]): Props =
    Props(new ReceivedMessageService(endpoint, lobbies: ArrayBuffer[Lobby]))
}

/** Creates a ReceivedMessageHandler for a TCP connection
 * 
 * @constructor create a ReceivedMessageService
 * @param endpoint the socket that the connection is coming through
 * @param lobbies an ArrayBuffer of Lobbys that are used to handle different instances of the game
 */
class ReceivedMessageService(endpoint: InetSocketAddress, lobbies: ArrayBuffer[Lobby]) extends Actor with ActorLogging {
  import context.system

  IO(Tcp) ! Tcp.Bind(self, endpoint)
  override def receive: Receive = {
    case Tcp.Connected(remote, _) =>
      log.debug("Remote address {} connected", remote)

      val init = TcpPipelineHandler.withLogger(log,
        new StringByteStringAdapter("utf-8") >> 
        new DelimiterFraming(maxSize=2048, delimiter = ByteString('\n'), includeDelimiter = true) >>
        new TcpReadWriteAdapter >>
        new BackpressureBuffer(lowBytes = 100, highBytes = 1000, maxBytes = 1000000))

      val handler = context.actorOf(ReceivedMessageHandler.props(init, sender, lobbies).withDeploy(Deploy.local))
      val pipeline = context.actorOf(TcpPipelineHandler.props(init, sender, handler).withDeploy(Deploy.local))
      sender ! Tcp.Register(pipeline)
  }
}
