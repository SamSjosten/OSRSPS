package osrsps.game

/**
 * The resizable (classic layout) top-level interface and the overlays opened into it on login.
 *
 * Interface and component ids are taken from RSMod's revision 233 symbol files and have not yet been
 * checked against the 241 cache. A wrong component shows up as a missing panel in the client.
 */
object GameFrame {
    const val TOPLEVEL_RESIZABLE = 161

    /** Overlay interface id to the component of [TOPLEVEL_RESIZABLE] it opens into. */
    val overlays: List<Pair<Int, Int>> = listOf(
        162 to 96, // chatbox
        651 to 6, // buff bar
        708 to 5, // stat boosts hud
        163 to 93, // private chat
        303 to 2, // hp bar hud
        90 to 3, // pvp icons
        160 to 33, // minimap orbs
        122 to 9, // xp drops
        728 to 35, // popout
        896 to 36, // world hop
        593 to 76, // combat options
        320 to 77, // skills
        629 to 78, // quest journal
        149 to 79, // inventory
        387 to 80, // worn equipment
        541 to 81, // prayer
        218 to 82, // spellbook
        707 to 83, // chat channels
        109 to 84, // account
        429 to 85, // friends
        182 to 86, // logout
        116 to 87, // settings
        216 to 88, // emotes
        239 to 89, // music
    )
}
