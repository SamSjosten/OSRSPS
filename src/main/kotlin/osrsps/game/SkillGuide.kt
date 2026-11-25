package osrsps.game

/**
 * The skill guide (`skill_guide_v2`, interface 860), which the client lays out by itself from the cache's
 * `skill_guide_subsections` and entry tables: the server opens it as the main modal and runs `skill_guide_v2_init`
 * (client script 1902) with the guide's skill number (enum 108: Farming is 21) and the tab to show; switching tabs is
 * the client's own (`skill_guide_v2_tab_select`). Script ids and arguments from the cache's client scripts (osrs-dumps,
 * read 2026-11-19). Guessed: the order, the sub opened before the script runs, and the script's last two arguments 0,
 * which only the level-up unlocks tab (31) reads.
 */
object SkillGuide {
    const val INTERFACE = 860
    const val INIT_SCRIPT = 1902

    /** The guide's skill numbers (enum 108). */
    const val FARMING = 21

    fun open(world: World, player: Player, skill: Int, tab: Int) {
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        world.interfaces.runClientScript(player, INIT_SCRIPT, listOf(skill, tab, 0, 0))
    }
}
