package osrsps.game.content

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import osrsps.game.FrameSlot
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** The lamp's and the experience reward window's words; not on the Wiki yet, so they wait for a recording. */
object XpLampLines : Lines("xp_lamp", "Lamp") {
    val TITLE = line("title", "the experience reward window's title (xpreward:title) when a lamp is rubbed; no source yet")
    val GRANTED = line("granted", "the message after Confirm; no source yet", "xp", "skill")
}

/**
 * The random events' lamp (obj 2528, macro_genilamp; Rub, Destroy) and the experience reward window it opens
 * (interface 240, xpreward). From the OSRS Wiki's Lamp page (read 2026-11-19): rubbing it lets the player pick any
 * skill, which gains ten times its level as experience. The window is the cache's (osrs-dumps xpreward.if3 and client
 * scripts 3804 to 3809): the client draws a button for each skill on the 24 children of xpreward:universe and, on
 * Confirm, resumes the pause button on child (n - 1), n being the skill's index in enum_681. Varp if1 is the level a
 * skill needs (0: none) and varp if2 a mask of the skills on offer, bit n for index n (enum_81); the client greys out
 * the rest, and Construction without a house and Sailing before Pandemonium (client script 3809).
 *
 * The XP is exact, whatever the server's XP rate, as a lamp gives a fixed amount. Inferred: "level" is the real
 * (unboosted) level, which is what the client checks a skill against. Guessed: the server accepts any of the 24
 * skills it is sent (every account here has every quest, and is on a members world), and the window's title and the
 * message after are words we haven't seen yet. Left out: Destroy (no item here can be destroyed yet).
 */
object XpLamp {
    const val LAMP = 2528
    const val RUB = 1

    const val INTERFACE = 240
    const val UNIVERSE = 0
    const val TITLE = 26

    /** Varps if1 and if2, which the window's scripts read. */
    const val VARP_IF1 = 261
    const val VARP_IF2 = 262

    /** The lamp's experience: this many times the chosen skill's level. */
    const val LAMP_TIMES = 10

    /** The skills by their index in enum_681, from 1. */
    val BY_INDEX: List<Skill> = listOf(
        Skill.ATTACK, Skill.STRENGTH, Skill.RANGED, Skill.MAGIC, Skill.DEFENCE, Skill.HITPOINTS, Skill.PRAYER,
        Skill.AGILITY, Skill.HERBLORE, Skill.THIEVING, Skill.CRAFTING, Skill.RUNECRAFT, Skill.MINING, Skill.SMITHING,
        Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING, Skill.FLETCHING, Skill.SLAYER, Skill.FARMING,
        Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING,
    )

    /** Every skill on offer: bits 1 to 24 of if2. */
    val ALL_SKILLS: Int = (1..BY_INDEX.size).fold(0) { mask, n -> mask or (1 shl n) }

    /** The skill whose button is child [sub] of xpreward:universe, or null for none. */
    fun skillAt(sub: Int): Skill? = BY_INDEX.getOrNull(sub)

    /** The experience [times] times [skill]'s real level gives, in tenths. */
    fun tenths(player: osrsps.game.Player, skill: Skill, times: Int): Int = player.skills.baseLevel(skill) * times * 10

    /**
     * Opens the experience reward window under [title] and waits for the player's Confirm; returns the skill picked.
     * Closing the window (walking off, Esc) drops the script, as every pause button's does.
     */
    suspend fun ScriptContext.pick(world: World, title: String): Skill {
        val player = player
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        player.vars[VARP_IF1] = 0
        player.vars[VARP_IF2] = ALL_SKILLS
        player.session.queue(IfSetText(INTERFACE, TITLE, title))
        player.session.queue(IfSetEventsV2(INTERFACE, UNIVERSE, 0, BY_INDEX.size - 1, Chatbox.EVENT_PAUSE_BUTTON, 0))
        while (true) {
            val (_, sub) = pauseButtons(INTERFACE, listOf(UNIVERSE), 0 until BY_INDEX.size)
            skillAt(sub)?.let { return it }
        }
    }

    /** Rub on the lamp in [slot]: the window, then the experience, if the lamp is still there. */
    suspend fun ScriptContext.rub(world: World, slot: Int) {
        val skill = pick(world, world.textFor(player)[XpLampLines.TITLE])
        world.interfaces.closeModals(player)
        // The window was open a while: the lamp may have moved, so take one wherever it is now.
        val at = if (player.inventory[slot]?.id == LAMP) slot else (0 until player.inventory.capacity).firstOrNull { player.inventory[it]?.id == LAMP } ?: return
        player.inventory.remove(at, 1)
        val tenths = tenths(player, skill, LAMP_TIMES)
        player.skills.addExactTenths(skill, tenths)
        world.message(player, XpLampLines.GRANTED, "xp" to tenths / 10, "skill" to skill.displayName)
    }

    /** Interface and component names in the rev 242 cache, for [osrsps.cache.GameFrameCheck]. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
        get() = mapOf(INTERFACE to ("xpreward" to mapOf(UNIVERSE to "universe", TITLE to "title")))
}

fun registerXpLamp(world: World) {
    world.handlers.onObj(XpLamp.LAMP, XpLamp.RUB) { op -> with(XpLamp) { rub(world, op.sub) } }
}
