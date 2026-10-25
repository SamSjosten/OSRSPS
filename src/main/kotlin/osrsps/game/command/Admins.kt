package osrsps.game.command

import osrsps.game.Player
import osrsps.game.SaveStore

/**
 * Who may run the commands that change the world for everyone (`::pause`, `::npc`, `::addloc` and the like). Names
 * match the way saves and accounts do, so an admin's name is safe once they have logged in and claimed it.
 */
class Admins private constructor(
    /** Save keys of the admins, or null when everyone is one. */
    private val keys: Set<String>?,
) {
    val everyone: Boolean get() = keys == null

    fun isAdmin(player: Player): Boolean = keys == null || player.saveKey in keys

    override fun toString(): String = keys?.sorted()?.joinToString().orEmpty().ifEmpty { if (keys == null) "everyone" else "nobody" }

    companion object {
        /** Every player is an admin: the server as it was before admins, for tests and a server nobody else joins. */
        val EVERYONE = Admins(null)

        /** The admins named in [list], separated by commas; a blank or null list makes [EVERYONE] an admin. */
        fun parse(list: String?): Admins {
            if (list.isNullOrBlank()) return EVERYONE
            return Admins(list.split(',').mapNotNull { SaveStore.saveKey(it) }.toSet())
        }
    }
}
