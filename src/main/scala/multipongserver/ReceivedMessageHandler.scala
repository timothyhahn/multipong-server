package multipongserver

/** MultiPong Imports **/
import net.timothyhahn.multipong.systems.CollisionSystem;
import net.timothyhahn.multipong.systems.MovementSystem;
import net.timothyhahn.multipong.systems.PointsSystem;

/** Artemis Imports **/
import com.artemis.{Entity, World}
import com.artemis.managers.{GroupManager, TagManager}

/** Java Imports **/
import java.net.InetSocketAddress
import java.rmi.server.UID

/** Akka Imports **/
import akka.io.{IO, Tcp}
import akka.io.TcpPipelineHandler.{Init, WithinActorContext}
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.util.ByteString

/** Scala Imports **/
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/** Json Imports **/
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

/** Factory for [[multipongserver.ReceivedMessageHandler]] instances */
object ReceivedMessageHandler {
  def props(init: Init[WithinActorContext, String, String], connection: ActorRef, lobbies: ArrayBuffer[Lobby]): Props =
    Props(new ReceivedMessageHandler(init, connection, lobbies))
}

/** Handles messages received over a TCP connection.
 * 
 * @constructor create a ReceivedMessageHandler
 * @param init Init with which to create this handler
 * @param connection Actor that holds the tcp connection the message came from
 * @param lobbies an ArrayBuffer of Lobbys that are used to handle different instances of the game
 */
class ReceivedMessageHandler(init: Init[WithinActorContext, String, String], connection: ActorRef, lobbies: ArrayBuffer[Lobby]) extends Actor with ActorLogging { 
  context.watch(connection)

  val player = new Player((new UID()).toString, connection)
  
  /** Creates an Artemis World */
  def createWorld: World = {
    var world: World = new World
    world.setSystem(new CollisionSystem)
    world.setSystem(new MovementSystem)
    world.setSystem(new PointsSystem)
    world.setManager(new GroupManager)
    world.setManager(new TagManager)

    world.initialize
    world.setDelta(1)
    return world
  }

  /** Creates a lobby with a random name */
  def createLobby: Lobby = {
    println(System.getProperty("user.dir"))
    val file = scala.io.Source.fromFile("src/main/resources/words.txt")
    val words: List[String] = file.getLines.toList
    file.close
    val random = new Random

    val name: String = words(random.nextInt(words.length)) + " " + words(random.nextInt(words.length))
    return new Lobby(name, createWorld, ArrayBuffer.empty[Player])
  }

  /** Handles new messages */
  def receive: Receive = {
    case init.Event(data) =>
      val text = data
      log.info ("Received '{}' from remote address {}", text, connection)

      var tempType = "\"fail\""

      try {
        val rootJSON = parse(text)

        // Get the type of message
        try {
          tempType = compact(render(rootJSON \ "type"))
        } catch {
          case e: Exception => 
            e.printStackTrace
        } 

        val msgType: String = tempType.substring(1, tempType.length - 1)

        msgType match {
          case "close" => context.stop(self)

          case "whoami" => // Sends back player's name
            connection ! Tcp.Write(ByteString.fromString("{\"type\": \"ack\", \"data\": \"" + player.name + "\"}\n"))

          case "create" => // Creates a lobby and sends back an acknowledgement
            lobbies += createLobby             
            connection ! Tcp.Write(ByteString.fromString("{\"type\": \"ack\"}\n"))

          case "list" => // Sends a list of all Lobbys
            case class JLobby(name: String, count: Int)
 
            var aLobbies: ArrayBuffer[JLobby] = ArrayBuffer()

            for (lobby <- lobbies) {
              aLobbies += JLobby(lobby.name, lobby.players.length)
            }

            val json = (
              ("lobbies" -> aLobbies.toList.map{ l =>
                (("name" -> l.name) ~
                 ("count" -> l.count))}))

            connection ! Tcp.Write(ByteString.fromString(compact(render(json)) + "\n"))

          case "join" => // Lets a player join a game and sends back whether they are on the left or right
            val tempName: String = compact(render(rootJSON \ "name"))
            val msgName: String = tempName.substring(1, tempName.length - 1)

            var lobbyToJoin = None: Option[Lobby]

            for(lobby <- lobbies) {
              if(lobby.name == msgName)   
                lobbyToJoin = Some(lobby)
            }

            lobbyToJoin match { 
              case None =>
                connection ! Tcp.Write(ByteString.fromString("{\"type\": \"fail\", \"reason\": \"n\"}\n"))
              case Some(lobby) =>
                if(lobby.players.length == 0) {
                  player.isLeft = true
                  player.lobby = Some(lobby)
                  lobby.addPlayer(player)
                  connection ! Tcp.Write(ByteString.fromString("{\"type\": \"ack\", \"data\": \"l\"}\n"))
                } else if (lobby.players.length == 1) {
                  player.isLeft = false
                  player.lobby = Some(lobby)
                  lobby.addPlayer(player)
                  connection ! Tcp.Write(ByteString.fromString("{\"type\": \"ack\", \"data\": \"r\"}\n"))
                } else {
                  connection ! Tcp.Write(ByteString.fromString("{\"type\": \"fail\", \"reason\": \"f\"}\n"))
                }
            }

          case "move" => // Processes movements that come in from players
            val msgPos: String = compact(render(rootJSON \ "pos"))
            val moveTo: Int = msgPos.toInt

            player.lobby match{
              case None =>
                log.warning("Failure to find lobby for player {}", player)
              case Some(lobby) =>
                lobby.move(moveTo, player.isLeft)
            }

          case _      =>
            log.warning("Received nonsensical message")
        
        }
      } catch {
        case e: Exception => 
          e.printStackTrace
      }

    case _: Tcp.ConnectionClosed =>
      log.info("Stopping, because connection closed")
      context.stop(self)

    case Terminated(`connection`) =>
      log.info("Stopping, because connection closed")
      context.stop(self)
  }
}
