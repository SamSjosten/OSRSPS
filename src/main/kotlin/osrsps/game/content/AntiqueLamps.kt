package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.skill.construction.House

/** The diary lamps' words: the skill screen's title, the XP message and Destroy's question. */
object AntiqueLampLines : Lines("antique_lamp", "Antique lamp (easy)") {
    val TITLE = line("title", "the skill screen's title (xpreward:title) over the skill icons")
    val GRANTED = line("granted", "after Confirm: the {amount} of XP in {skill} given", "amount", "skill")
    val CANT = line("cant", "Confirm on a skill the screen allowed but the server refuses (below the lamp's level, or Construction without a house); words guessed", "skill")
    val DESTROY_TITLE = line("destroy_title", "Destroy's box: its question above the lamp (confirmdestroy's title)")
    val DESTROY_WARNING = line("destroy_warning", "Destroy's box: the warning beside the lamp, from the Wiki's infobox (\"you cannot reclaim it\")")
}

/**
 * The four Achievement Diary lamps (`diary_easy_lamp` 13145 to `diary_elite_lamp` 13148): Rub opens the skill screen
 * (xpreward, 240) for any skill at the lamp's level or above, and Confirm gives the XP exactly, whatever the XP rate.
 * Amounts and levels are the Wiki's (Antique lamp (easy) and the diaries' reward lists): 2,500 at 30, 7,500 at 40,
 * 15,000 at 50, 50,000 at 70. The lamps can't be banked (the cache's param_59, [osrsps.game.bank.Bank]) or traded.
 *
 * The screen is the client's: it greys out a skill below varp if1's level or missing from varp if2's mask (proc
 * script3809, bits from enum_81), and Confirm sends a resume_pausebutton on the universe's child for the chosen skill
 * (its enum_681 key less one). The server checks the same again on Confirm, as a client could send any slot.
 *
 * Guessed: if1 and if2 are left as they are when the screen closes; the screen opens from a delay-free click; rubbing
 * plays no animation.
 */
object AntiqueLamps {
    enum class Lamp(val obj: Int, val xp: Int, val level: Int) {
        EASY(13145, 2_500, 30),
        MEDIUM(13146, 7_500, 40),
        HARD(13147, 15_000, 50),
        ELITE(13148, 50_000, 70),
        ;
    }

    /** The skill for each slot the screen sends (enum_681 from key 1, so slot 0 is Attack); Sailing is 23. */
    val SLOT_SKILLS: List<Skill> = listOf(
        Skill.ATTACK, Skill.STRENGTH, Skill.RANGED, Skill.MAGIC, Skill.DEFENCE, Skill.HITPOINTS, Skill.PRAYER,
        Skill.AGILITY, Skill.HERBLORE, Skill.THIEVING, Skill.CRAFTING, Skill.RUNECRAFT, Skill.MINING, Skill.SMITHING,
        Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING, Skill.FLETCHING, Skill.SLAYER, Skill.FARMING,
        Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING,
    )

    /** if2's bit for [skill] (enum_81): the slot plus one. */
    fun bit(skill: Skill): Int = SLOT_SKILLS.indexOf(skill) + 1

    /** Every skill allowed, as a diary lamp allows. */
    val ALL_SKILLS_MASK: Int = SLOT_SKILLS.fold(0) { mask, skill -> mask or (1 shl bit(skill)) }

    const val RUB = 1
    const val DESTROY = 5

    /** xpreward, its universe (whose children carry the pause buttons) and its title. */
    const val SCREEN = 240
    const val SCREEN_UNIVERSE = 0
    const val SCREEN_TITLE = 26

    /** Varps if1 (the lowest level allowed) and if2 (the skills allowed, a bit each) that the screen reads. */
    const val VARP_LEVEL = 261
    const val VARP_SKILLS = 262

    /** Rub: the screen, then the XP once a skill is confirmed. */
    suspend fun ScriptContext.rub(world: World, slot: Int, lamp: Lamp) {
        player.vars[VARP_LEVEL] = lamp.level
        player.vars[VARP_SKILLS] = ALL_SKILLS_MASK
        world.interfaces.sendVarpNow(player, VARP_LEVEL)
        world.interfaces.sendVarpNow(player, VARP_SKILLS)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, SCREEN)
        world.interfaces.setText(player, SCREEN, SCREEN_TITLE, world.textFor(player)[AntiqueLampLines.TITLE])
        world.interfaces.setEvents(player, SCREEN, SCREEN_UNIVERSE, 0, SLOT_SKILLS.size - 1, 0, Chatbox.EVENT_PAUSE_BUTTON)
        val chosen = pauseButton(SCREEN, SCREEN_UNIVERSE, 0 until SLOT_SKILLS.size)
        world.interfaces.closeModals(player)
        val skill = SLOT_SKILLS[chosen]
        if (!allowed(lamp, skill)) {
            player.message(world.textFor(player)[AntiqueLampLines.CANT, "skill" to skill.displayName])
            return
        }
        // The lamp may have moved slot meanwhile (the screen doesn't stop dragging); any copy of it will do.
        val at = if (player.inventory[slot]?.id == lamp.obj) slot else player.inventory.contents().firstOrNull { it.second.id == lamp.obj }?.first ?: return
        player.inventory.remove(at, 1)
        player.skills.addExactTenths(skill, lamp.xp * 10)
        player.message(world.textFor(player)[AntiqueLampLines.GRANTED, "amount" to "%,d".format(lamp.xp), "skill" to skill.displayName])
    }

    /** What script3809 checks, bar the members' world and the quests (every quest is done here). */
    private fun ScriptContext.allowed(lamp: Lamp, skill: Skill): Boolean {
        if (player.skills.baseLevel(skill) < lamp.level) return false
        if (skill == Skill.CONSTRUCTION) {
            val house = player.vars.types.varbit(House.LOCATION_VARBIT)?.let { player.vars.getVarbit(House.LOCATION_VARBIT) } ?: 0
            if (house == 0) return false
        }
        return true
    }
}

/**
 * Destroy's confirmation for an item that has no Drop: the chatbox's confirmdestroy (584), drawn by client script
 * confirmdestroy_init (814) from the item and two lines, whose Yes and No send a resume_pausebutton on the universe's
 * children 1 and 0. Returns true when the player chose Yes and the item is still in [slot].
 */
object DestroyItem {
    const val BOX = 584
    const val BOX_UNIVERSE = 0
    const val NO = 0
    const val YES = 1
    const val INIT_SCRIPT = 814

    suspend fun ScriptContext.confirm(world: World, slot: Int, obj: Int, title: Line, warning: Line): Boolean {
        val text = world.textFor(player)
        world.interfaces.openChatModal(player, BOX, Chatbox.UNCLAMP_INFINITE)
        world.interfaces.runClientScript(player, INIT_SCRIPT, listOf(obj, 1, text[title], text[warning]))
        world.interfaces.setEvents(player, BOX, BOX_UNIVERSE, NO, YES, 0, Chatbox.EVENT_PAUSE_BUTTON)
        val answer = pauseButton(BOX, BOX_UNIVERSE, NO..YES)
        return answer == YES && player.inventory[slot]?.id == obj
    }
}

fun registerAntiqueLamps(world: World) {
    for (lamp in AntiqueLamps.Lamp.entries) {
        world.handlers.onObj(lamp.obj, AntiqueLamps.RUB) { op -> with(AntiqueLamps) { rub(world, op.sub, lamp) } }
        world.handlers.onObj(lamp.obj, AntiqueLamps.DESTROY) { op ->
            val yes = with(DestroyItem) { confirm(world, op.sub, lamp.obj, AntiqueLampLines.DESTROY_TITLE, AntiqueLampLines.DESTROY_WARNING) }
            if (yes) player.inventory.remove(op.sub, 1)
        }
    }
}
