package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Experience
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

private val logger = KotlinLogging.logger {}

/** The rotten potato's menus and messages, as the Wiki's page lists them. */
object RottenPotatoLines : Lines("rotten_potato", "Rotten potato") {
    val SET_ALL_STATS = line("set_all_stats", "Eat, the first option")
    val WIPE_INVENTORY = line("wipe_inventory", "Eat, the second option")
    val TELEPORT_TO_PLAYER = line("teleport_to_player", "Eat, the fourth option")
    val SPAWN_NPC = line("spawn_npc", "Eat, the fifth option")

    // Guessed: the Wiki doesn't give the prompts' words.
    val STATS_PROMPT = line("stats_prompt", "Eat, Set all stats: the prompt for the level")
    val PLAYER_PROMPT = line("player_prompt", "Eat, Teleport to player: the prompt for the name")
    val NPC_PROMPT = line("npc_prompt", "Eat, Spawn aggressive NPC: the prompt for the NPC")

    val BANK_MENU = line("bank_menu", "Peel, the first option")

    val SETSTAT = line("setstat", "Eat, Set all stats: the message for each skill", "skill", "level")
    val FINISHED = line("finished", "Eat, Set all stats: the last message")
}

/**
 * The rotten potato (obj 5733), live's J-Mod tool, as an admins' tool here: Eat (iop1) opens a menu of testing tools,
 * which run the `::` commands, so each is logged and refused to non-admins as typed ones are. The cache gives the
 * potato Eat, Slice, Peel and Mash; Eat's options are from the OSRS Wiki's page (read 2026-10-30). Live's third, Setup
 * POH, is Heal me here, as there are no houses. Peel (iop3) keeps its Bank menu; with no rares or random events, Teleport
 * to RARE! and Spawn RARE! become Teleport to a tile and Spawn an item, and AMEs for all! is left out.
 */
object RottenPotato {
    const val OBJ = 5733

    const val EAT = 1
    const val PEEL = 3

    /** Our own, in place of Setup POH; not live's words, so not in the dialogue file. */
    const val HEAL_ME = "Heal me"

    /** Our own options and prompts, in place of Peel's Teleport to RARE! and Spawn RARE!. */
    const val TELEPORT_TO_TILE = "Teleport to a tile"
    const val TILE_PROMPT = "Teleport to x z [level]:"
    const val SPAWN_ITEM = "Spawn an item"
    const val ITEM_PROMPT = "Spawn item, id|name [count]:"
}

fun registerRottenPotato(world: World) {
    val type = world.objTypes[RottenPotato.OBJ] ?: return
    if (type.iop(RottenPotato.EAT) != "Eat") return
    world.handlers.onObj(RottenPotato.OBJ, RottenPotato.EAT) { op -> if (admin(world, op.sub)) eat(world) }
    world.handlers.onObj(RottenPotato.OBJ, RottenPotato.PEEL) { op -> if (admin(world, op.sub)) peel(world) }
}

/** Whether the player is an admin; a non-admin's potato in [slot] is taken. */
private fun ScriptContext.admin(world: World, slot: Int): Boolean {
    if (world.admins.isAdmin(player)) return true
    // Guessed: what a non-J-Mod holding one sees isn't known; here it goes, so it can't be passed on.
    if (player.inventory[slot]?.id == RottenPotato.OBJ) player.inventory.remove(slot)
    logger.info { "${player.username} clicked a rotten potato, which is for admins; it was taken" }
    player.message("The rotten potato is a testing tool, so only admins can use it here.")
    return false
}

private suspend fun ScriptContext.eat(world: World) {
    val words = world.textFor(player)
    var picked = 0
    world.dialogues.start(this) {
        picked = choice(
            text(RottenPotatoLines.SET_ALL_STATS), text(RottenPotatoLines.WIPE_INVENTORY), RottenPotato.HEAL_ME,
            text(RottenPotatoLines.TELEPORT_TO_PLAYER), text(RottenPotatoLines.SPAWN_NPC),
        )
    }
    when (picked) {
        1 -> setAllStats(world, countDialog(words[RottenPotatoLines.STATS_PROMPT]))
        2 -> wipeInventory()
        3 -> command(world, "heal")
        4 -> command(world, "teleto " + stringDialog(words[RottenPotatoLines.PLAYER_PROMPT]))
        // Guessed: live's NPC is aggressive; here it is whatever the type is, as ::npc spawns it.
        5 -> command(world, "npc " + stringDialog(words[RottenPotatoLines.NPC_PROMPT]))
    }
}

private suspend fun ScriptContext.peel(world: World) {
    var picked = 0
    world.dialogues.start(this) {
        picked = choice(text(RottenPotatoLines.BANK_MENU), RottenPotato.TELEPORT_TO_TILE, RottenPotato.SPAWN_ITEM)
    }
    when (picked) {
        1 -> command(world, "bank")
        2 -> command(world, "tele " + stringDialog(RottenPotato.TILE_PROMPT))
        3 -> command(world, "item " + stringDialog(RottenPotato.ITEM_PROMPT))
    }
}

/** Runs `::`[input] as if typed, answering in the chatbox. */
private fun ScriptContext.command(world: World, input: String) = world.commands.execute(player, input, player::message)

/**
 * One skill a tick, each with its message, as the Wiki describes. Guessed: the order (the skill tab's ids), and that the
 * player is delayed meanwhile.
 */
private suspend fun ScriptContext.setAllStats(world: World, level: Int) {
    if (level !in 1..Experience.MAX_LEVEL) {
        player.message("Levels are 1 to ${Experience.MAX_LEVEL}")
        return
    }
    for (skill in Skill.entries) {
        // Each goes through ::setlevel, which logs it and checks the player is still an admin and alive.
        var done = false
        world.commands.execute(player, "setlevel ${skill.ordinal} $level") { done = it.startsWith(skill.displayName) }
        if (!done) return
        world.message(player, RottenPotatoLines.SETSTAT, "skill" to skill.displayName.lowercase(), "level" to level)
        delay(1)
    }
    world.message(player, RottenPotatoLines.FINISHED)
}

/** Every item but the potatoes goes. */
private fun ScriptContext.wipeInventory() {
    if (player.dead) return
    val inventory = player.inventory
    for (slot in 0 until inventory.capacity) {
        val obj = inventory[slot] ?: continue
        if (obj.id != RottenPotato.OBJ) inventory.remove(slot)
    }
    logger.info { "${player.username} wiped their inventory with a rotten potato" }
}
