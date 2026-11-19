package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.PlayerEffects
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Agility
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** The ditch's warning screen (keys only; the words are in data/dialogue.txt). */
object WildernessDitchLines : Lines("wilderness_ditch", "Wilderness Ditch") {
    val WARNING = line("warning", "the warning screen's message (wilderness_warningscreen:message) on crossing into the Wilderness")
}

/**
 * Crossing the Wilderness Ditch (`ditch_wilderness_cover` 23271, Cross), the border along the Wilderness's south edge,
 * and its 13 pieces from x 3328 to 3340 (`ditch_wilderness_cover_members` 50652, the same 1 by 2 Cross in the 242
 * cache). Every world here is a members world, so those cross like the rest. Inferred from the name: live keeps them
 * from free-to-play players.
 *
 * Each piece of the ditch is 1 by 2 tiles in the 242 cache, laid along z 3521 so it covers 3521 and 3522, plus a short
 * run turned east-west at x 2996 (map read from the 242 cache, 2026-11-15). Cross jumps the player over the piece to the
 * tile straight across it, either way.
 *
 * Going north the Doomsayer's warning screen shows first (`wilderness_warningscreen` 475, the Wiki's Doomsayer page:
 * the Wilderness Ditch is one of his danger signs, toggleable after the seventh time it is seen). It is warning 7 in the
 * 242 cache's enum 688 (`cws_doomsayer:wilderness ditch`) and the client's cws scripts read its state from varbit
 * `cws_warning_7`: 0 to 5 counts the times it was seen, 6 shows the "Don't ask me this again" toggle on, 7 means the
 * player turned the warning off. "Enter Wilderness" jumps, "Don't go" doesn't.
 *
 * Guessed: the jump's animation timing (WILD_DITCH_JUMP 6132, from RuneLite's gameval names, over 2 ticks with the
 * exact move starting at client cycle 33), that the count moves when the screen is answered rather than opened, and
 * that the screen shows only on the way in. Not built: the Doomsayer's Toggle warnings list, which reads the same varbit.
 */
object WildernessDitch {
    const val DITCH = 23271
    const val DITCH_MEMBERS = 50652
    const val CROSS = 1

    /** WILD_DITCH_JUMP. */
    const val ANIM_JUMP = 6132
    const val JUMP_TICKS = 2

    // Guessed: the jump leaves the ground a little after the animation starts.
    const val JUMP_START_CYCLES = 33

    const val WARNING_SCREEN = 475
    const val WARNING_UNIVERSE = 1
    const val WARNING_MESSAGE = 10
    const val WARNING_TOGGLE = 13

    /** The universe's two buttons send their own sub: 1 "Enter Wilderness", 0 "Don't go" (client script 1437). */
    const val ENTER = 1
    const val DONT_GO = 0

    /** cws_warning_id: which warning the screen is, read as it loads. */
    const val VARBIT_WARNING_ID = 14801
    const val WARNING_ID = 7

    /** cws_warning_7: the ditch warning's count and toggle. */
    const val VARBIT_WARNING = 3857
    const val TOGGLE_SHOWN = 6
    const val WARNING_OFF = 7

    /** Every id above with its gameval name in the 242 cache, for [osrsps.cache.GameFrameCheck]. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
        WARNING_SCREEN to ("wilderness_warningscreen" to mapOf(WARNING_UNIVERSE to "universe", WARNING_MESSAGE to "message", WARNING_TOGGLE to "toggle")),
    )

    /**
     * The tile across [ditch] (its footprint [width] by [length] before turning) from [from], or null when [from] is
     * beside its short side: a piece is jumped along its 2-tile side only.
     */
    fun landing(ditch: ZoneLoc, width: Int, length: Int, from: Tile): Tile? {
        val turned = ditch.angle and 1 == 1
        val sizeX = if (turned) length else width
        val sizeZ = if (turned) width else length
        val acrossZ = sizeZ >= sizeX
        val xs = ditch.x until ditch.x + sizeX
        val zs = ditch.z until ditch.z + sizeZ
        return if (acrossZ) {
            if (from.x !in xs) return null
            when {
                from.z < ditch.z -> Tile(from.x, zs.last + 1)
                from.z > zs.last -> Tile(from.x, ditch.z - 1)
                else -> null
            }
        } else {
            if (from.z !in zs) return null
            when {
                from.x < ditch.x -> Tile(xs.last + 1, from.z)
                from.x > xs.last -> Tile(ditch.x - 1, from.z)
                else -> null
            }
        }
    }

    internal suspend fun ScriptContext.cross(world: World, ditch: ZoneLoc) {
        arriveDelay()
        val type = world.locs.types[ditch.id] ?: return
        val from = Tile(player.x, player.z)
        val to = landing(ditch, type.width, type.length, from) ?: return world.message(player, GameLines.CANT_REACH)
        // Rock or a wall on the far side (the 242 map's x 3042 run lands on rock at 3042,3520): no jump there (exploit
        // review round 34). Inferred: live can't land a player inside scenery either.
        if (!world.canStand(to.x, to.z, ditch.level)) return world.message(player, GameLines.CANT_REACH)
        if (!Wilderness.inside(from.x, from.z) && Wilderness.inside(to.x, to.z)) {
            if (!warned(world)) return
            // Answered a tick or more later: the jump starts from the tile the screen was opened on, or not at all.
            if (player.x != from.x || player.z != from.z || player.level != ditch.level) return
        }
        val angle = Agility.angle(to.x - from.x, to.z - from.z)
        player.effects.exactMove(PlayerEffects.ExactMove(from.x, from.z, to.x, to.z, JUMP_START_CYCLES, JUMP_TICKS * 30, angle))
        player.anim(ANIM_JUMP)
        delay(JUMP_TICKS)
        world.teleport(player, to.x, to.z, ditch.level)
    }

    /**
     * Shows the warning screen unless the player turned it off, and returns whether they chose to go on. Closing the
     * screen any other way (walking, Esc) drops the script, as a dialogue's does.
     */
    private suspend fun ScriptContext.warned(world: World): Boolean {
        val vars = player.vars
        // Without the cache's varbits (some tests) there is nothing to count with, so the screen always shows.
        val known = vars.types.varbit(VARBIT_WARNING) != null
        val seen = if (known) vars.getVarbit(VARBIT_WARNING) else 0
        if (seen >= WARNING_OFF) return true
        world.interfaces.setVarbitNow(player, VARBIT_WARNING_ID, WARNING_ID)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, WARNING_SCREEN)
        world.interfaces.setText(player, WARNING_SCREEN, WARNING_MESSAGE, world.textFor(player)[WildernessDitchLines.WARNING])
        world.interfaces.setEvents(player, WARNING_SCREEN, WARNING_UNIVERSE, DONT_GO, ENTER, Chatbox.EVENT_PAUSE_BUTTON)
        world.interfaces.setEvents(player, WARNING_SCREEN, WARNING_TOGGLE, 0, 0, Chatbox.EVENT_PAUSE_BUTTON)
        val answer = pauseButton(WARNING_SCREEN, WARNING_UNIVERSE, DONT_GO..ENTER)
        world.interfaces.closeModals(player)
        // Guessed: counted when answered, so the seventh showing is the first with the toggle (0 to 6 seen before it).
        if (known && seen < TOGGLE_SHOWN) vars.setVarbit(VARBIT_WARNING, seen + 1)
        return answer == ENTER
    }

    /**
     * A click on the warning screen's "Don't ask me this again" toggle (resume_pausebutton on its component). The
     * client has already flipped its picture (cws_response_toggle); the server keeps the varbit, flipping between shown
     * and off, while the screen stays up. Returns false when the click isn't the toggle's.
     */
    fun toggle(player: Player, interfaceId: Int, componentId: Int): Boolean {
        if (interfaceId != WARNING_SCREEN || componentId != WARNING_TOGGLE) return false
        if (player.modals[FrameSlot.MAINMODAL] != WARNING_SCREEN) return true
        val vars = player.vars
        if (vars.types.varbit(VARBIT_WARNING) == null) return true
        when (vars.getVarbit(VARBIT_WARNING)) {
            TOGGLE_SHOWN -> vars.setVarbit(VARBIT_WARNING, WARNING_OFF)
            WARNING_OFF -> vars.setVarbit(VARBIT_WARNING, TOGGLE_SHOWN)
        }
        return true
    }
}

fun registerWildernessDitch(world: World) {
    for (ditch in listOf(WildernessDitch.DITCH, WildernessDitch.DITCH_MEMBERS)) {
        world.handlers.onLoc(ditch, WildernessDitch.CROSS) { op -> with(WildernessDitch) { cross(world, op.loc!!) } }
    }
}
