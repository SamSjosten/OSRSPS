package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.WearPos
import osrsps.game.combat.Drop
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc

/** The Evil Chicken's overheads; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object EvilChickenLines : Lines("evil_chicken", "Transcript:Evil Chicken") {
    val ATTACKED = listOf(
        line("bwaaaaaauk", "When under attack: dialogue 1"),
        line("bwuk", "When under attack: dialogue 2"),
        line("bwuk_bwuk_bwuk", "When under attack: dialogue 3"),
        line("flee", "When under attack: dialogue 4", "name"),
        line("begone", "When under attack: dialogue 5", "name"),
        line("muahaha", "When under attack: dialogue 6"),
    )
}

/**
 * The Evil Chicken (OSRS Wiki, Evil Chicken page and transcript, read 2026-11-19): once a random event that attacked its
 * player, and since the 2014 update a level 159 monster standing in the Champions' Guild chicken coop (npc-spawns
 * misthalin-farms-roads.json; its fight in npc-combat.json, its bones and raw chicken in npc-drops.json). It isn't
 * aggressive; attacked, it fights back with Magic and says one of its six lines. Its feathers depend on the killer:
 * their combat level plus 0 to 86, plus 30 with a ring of wealth worn (Mod Fuzz, in the Wiki's drop note).
 *
 * Guessed: a line each time it is hurt, but no more than one every [SAY_TICKS] (the transcript gives no rate).
 */
object EvilChicken {
    const val NPC = 6739
    const val FEATHER = 314
    const val FEATHER_RANDOM = 86
    const val WEALTH_BONUS = 30

    /** Guessed: the least time between two of its lines. */
    const val SAY_TICKS = 4

    private val lastSaid = java.util.WeakHashMap<Npc, Long>()

    /** [player] hurt [npc]: if it is the Evil Chicken, it may say a line. */
    fun damaged(world: World, npc: Npc, player: Player) {
        if (npc.type.id != NPC) return
        val last = lastSaid[npc]
        if (last != null && world.tick - last < SAY_TICKS) return
        lastSaid[npc] = world.tick
        val line = EvilChickenLines.ATTACKED[world.random.nextInt(EvilChickenLines.ATTACKED.size)]
        val words = world.textFor(player)
        npc.effects.say(if ("name" in line.args) words.get(line, "name" to player.username) else words[line])
    }

    /** The feathers [npc] drops for [killer], if it is the Evil Chicken. */
    fun extraDrops(world: World, npc: Npc, killer: Player?): List<Drop> {
        if (npc.type.id != NPC || killer == null) return emptyList()
        val type = world.objTypes[FEATHER] ?: return emptyList()
        val ring = killer.equipment[WearPos.RING]?.let { world.objTypes[it.id]?.name }
        val wealth = if (ring != null && ring.startsWith("Ring of wealth")) WEALTH_BONUS else 0
        return listOf(Drop(type, killer.skills.combatLevel + world.random.nextInt(FEATHER_RANDOM + 1) + wealth))
    }
}
