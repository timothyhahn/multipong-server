package multipongserver

/** Akka Imports **/
import akka.actor.ActorRef

/** A Player that holds information about each person who wants to play
 *
 * @param namec the name of the player, needs to be unique
 * @param connectionc the Actor that holds the TCP connection associated with a user
 */
class Player(namec: String, connectionc: ActorRef) {
  val name = namec
  val connection = connectionc
  var isLeft: Boolean = false
  var lobby = None: Option[Lobby]
}
