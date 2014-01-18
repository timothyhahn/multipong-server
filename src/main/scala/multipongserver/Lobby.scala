package multipongserver

/** MultiPong Imports **/
import net.timothyhahn.multipong.actions.MoveAction
import net.timothyhahn.multipong.systems.PointsSystem
import net.timothyhahn.multipong.components.{Bounds, Points, Position, Velocity}

/** Artemis Imports **/
import com.artemis.{World, Entity}
import com.artemis.managers.{GroupManager, TagManager}

/** Akka Imports **/
import akka.io.Tcp
import akka.util.ByteString

/** Json Imports **/
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

/** Scala Imports **/
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConversions._


/** A lobby created for each "instance" of a game
 *
 * @constructor create a lobby with a name, an (Artemis) World, and a list of players
 * @param namec the name of the lobby
 * @param worldc the world this game plays in
 * @param playersc the list of players
 */
class Lobby(namec: String, worldc: World, playersc: ArrayBuffer[Player]) {
  val name: String = namec
  val world: World = worldc
  var players: ArrayBuffer[Player] = playersc
  var gameStarted: Boolean = false
  val worldWidth: Int = 480
  val worldHeight:Int = 320
  val ballSize: Int = 8
  val paddleHeight: Int = 128
  val paddleWidth: Int = 16
  var gameOver: Boolean = false
  var counter: Int = 0
  var timestep: Int = 0
  var pointsSystem: PointsSystem = null

  /** Get a specific entity based on position
   *
   * @param isLeft whether or not the entity you want is the left one or not
   */
  def getEntity(isLeft: Boolean): Entity =  {
    val tagManager = world.getManager(classOf[TagManager])
    if(isLeft)
      return tagManager.getEntity("LEFT")
    else
      return tagManager.getEntity("RIGHT")
  }

  /** Create a move that will be processed and sent to both players
   *
   * @param moveTo y position in world that you want to move to
   * @param isLeft whether or not the move is for the left or the right
   */
  def move(moveTo: Int, isLeft: Boolean) {
    val ma = new MoveAction(moveTo, getEntity(isLeft))
    ma.process

    val toSend: String = mJSON(world)
    players.foreach(player => player.connection ! Tcp.Write(ByteString.fromString(toSend + "\n")))
  }

  /** Add a player to the list of players
   *
   * @param player the Player to be added
   */
  def addPlayer(player: Player) {
    players += player
  }

  /** Create a JSON representation of the y position and y velocities of paddles
   *
   * @param world the world you want to create a JSON representation of
   */
  def mJSON(world: World): String = {
    case class JPlayer(id: String, y: Int, yv: Int)
 
    var aPlayers: ArrayBuffer[JPlayer] = ArrayBuffer()

    val tagManager = world.getManager(classOf[TagManager])
    val playerTags: List[String] = tagManager.getRegisteredTags.toList

    for (playerID <- playerTags) {
      if(playerID != "BALL") {
        val tempEntity: Entity = tagManager.getEntity(playerID)
        val tempPos: Position = tempEntity.getComponent(classOf[Position])
        val tempVel: Velocity = tempEntity.getComponent(classOf[Velocity])
        aPlayers += JPlayer(playerID, tempPos.getY(), tempVel.getY())
      }
    }

    val json = (
      ("type" -> "move") ~
      ("entities" -> aPlayers.toList.map{ p =>
      (("id" -> p.id) ~
       ("y" -> p.y) ~
       ("yv" -> p.yv))}))

    return compact(render(json)) + "\n"
  }

  /** Create a JSON representaiton of the entire world and its entities
   *
   * @param world the world you want to create a JSON representation of
   */
  def wJSON(world: World): String = {
      case class JPlayer(id: String, x: Int, y: Int, xv: Int, yv: Int)
 
      var aPlayers: ArrayBuffer[JPlayer] = ArrayBuffer()

      val tagManager = world.getManager(classOf[TagManager])
      val playerTags: List[String] = tagManager.getRegisteredTags.toList

      for (playerID <- playerTags) {
          val tempEntity: Entity = tagManager.getEntity(playerID)
          val tempPos: Position = tempEntity.getComponent(classOf[Position])
          val tempVel: Velocity = tempEntity.getComponent(classOf[Velocity])
          aPlayers += JPlayer(playerID, tempPos.getX(), tempPos.getY(), tempVel.getX(), tempVel.getY())
      }

      val lPoints : Points = tagManager.getEntity("LEFT").getComponent(classOf[Points])
      val rPoints : Points = tagManager.getEntity("RIGHT").getComponent(classOf[Points])

      val json = (
        ("type" -> "fullsync") ~
        ("lpoints" -> lPoints.getPoints()) ~
        ("rpoints" -> rPoints.getPoints()) ~
        ("entities" -> aPlayers.toList.map{ p =>
        (("id" -> p.id) ~
         ("x" -> p.x) ~
         ("y" -> p.y) ~
         ("xv" -> p.xv) ~
         ("yv" -> p.yv))}))

    return compact(render(json)) + "\n"
  }

  /** Handles a "tick" according to the lobby. If the game has started it will process the world, otherwise it will get ready for more
   *  players.
   */
  def process {
    if(gameStarted){
      world.process
      gameOver = pointsSystem.isGameOver
    
      val toSend: String = wJSON(world)
      if((timestep % 5) == 0) {
        players.foreach(player => player.connection ! Tcp.Write(ByteString.fromString(toSend + "\n")))
      }
      timestep += 1
    } else {
      if(players.length == 2) {
        gameStarted = true
        val leftPaddle: Entity = world.createEntity
        leftPaddle.addComponent(new Position(0, 100))
        leftPaddle.addComponent(new Velocity(0,0))
        leftPaddle.addComponent(new Bounds(paddleWidth, paddleHeight + 4))
        leftPaddle.addComponent(new Points())
        world.getManager(classOf[GroupManager]).add(leftPaddle, "PADDLES")
        world.getManager(classOf[TagManager]).register("LEFT", leftPaddle)

        val rightPaddle: Entity = world.createEntity
        rightPaddle.addComponent(new Position(worldWidth - paddleWidth, 100))
        rightPaddle.addComponent(new Velocity(0,0))
        rightPaddle.addComponent(new Bounds(paddleWidth, paddleHeight))
        rightPaddle.addComponent(new Points())
        world.getManager(classOf[GroupManager]).add(rightPaddle, "PADDLES")
        world.getManager(classOf[TagManager]).register("RIGHT", rightPaddle)

        val ball: Entity = world.createEntity
        ball.addComponent(new Position(worldWidth / 2 - ballSize / 2, worldHeight / 2 - ballSize / 2))
        ball.addComponent(new Velocity(-2,0))
        ball.addComponent(new Bounds(ballSize))
        world.getManager(classOf[GroupManager]).add(ball, "BALLS")
        world.getManager(classOf[TagManager]).register("BALL", ball)

        val toSend: String = wJSON(world)
        players.foreach(player => player.connection ! Tcp.Write(ByteString.fromString(toSend + "\n")))
        pointsSystem = world.getSystem(classOf[PointsSystem])

        Thread.sleep(3000)
      }
    }    
  }
}
