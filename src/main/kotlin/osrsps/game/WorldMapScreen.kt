package osrsps.game

/**
 * The world map, interface 595 (worldmap). The globe orb ([GameFrame.ORBS_WORLDMAP]) sends its op to the server: its
 * only client-side listener is the click's sound, and the M key triggers the same op (orbs_worldmap_key). The
 * client's scripts expect the map opened as an overlay in the frame's floater, where All Settings goes too:
 * keypress_permit and orbs_worldmap_setup both test if_hassub on toplevel:floater, and the orb's op reads Close
 * while something is there. Everything else is the client's: it draws the map from the cache's worldmap index,
 * sizes, zooms and searches it, and floats or docks its window, in its own scripts. The close button ([CLOSE]; Esc
 * triggers it too) and the orb again close it.
 *
 * Read from the 241 scripts' dump (Joshua-F's osrs-dumps), not seen on live: test-runner/examples/world-map.json is the
 * check in the real client.
 */
class WorldMapScreen(private val interfaces: Interfaces) {
    fun isOpen(player: Player): Boolean = player.floater == INTERFACE

    /** The orb: opens the map, or closes it while it is open. */
    fun toggle(player: Player) {
        if (isOpen(player)) close(player) else open(player)
    }

    fun open(player: Player) = interfaces.openFloater(player, INTERFACE)

    /** The map's close button. Nothing when the floater holds something else (All Settings): false then. */
    fun close(player: Player): Boolean {
        if (!isOpen(player)) return false
        interfaces.closeFloater(player)
        return true
    }

    companion object {
        const val INTERFACE = 595

        /** worldmap:close, the X at the window's top right. */
        const val CLOSE = 38

        /** The names the startup check ([osrsps.cache.GameFrameCheck]) holds these ids to. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(INTERFACE to ("worldmap" to mapOf(CLOSE to "close")))
    }
}
