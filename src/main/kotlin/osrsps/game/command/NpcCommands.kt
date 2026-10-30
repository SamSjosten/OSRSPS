package osrsps.game.command

import osrsps.game.World
import osrsps.game.npc.NpcSpawn

/** Spawn, list and remove NPCs: testing tools, not live commands. */
fun CommandRegistry.registerNpcCommands(world: World) {
    register("npc", "::npc id|name [wander]", admin = true) {
        if (args.isEmpty()) fail("Usage: ::npc id|name [wander]")
        // A last word that reads as a number is the wander range when there is more than one word, so names can
        // have spaces: ::npc cow calf 3.
        val wander = args.takeIf { it.size > 1 }?.last()?.toIntOrNull()
        val words = if (wander != null) args.dropLast(1) else args
        val text = words.joinToString(" ")
        if (wander != null && wander !in 0..MAX_WANDER) fail("The wander range must be 0 to $MAX_WANDER")
        val type = world.npcTypes.lookup(text) ?: fail("No NPC '$text'. Try its id, its name, or its gameval name like cow2_calf")
        val spawn = NpcSpawn(type, player.x, player.z, player.level, wander = wander ?: 0, source = spawnSource(player.username), respawns = false)
        val npc = world.spawnNpc(spawn) ?: fail("No free NPC slot")
        reply("Spawned ${type.name} (${type.id}) as #${npc.index} at ${npc.x}, ${npc.z}" + if (spawn.wander > 0) ", wander ${spawn.wander}" else "")
    }

    register("npcs", "::npcs [radius]", admin = false) {
        val radius = when (args.size) {
            0 -> DEFAULT_LIST_RADIUS
            1 -> args[0].toIntOrNull()?.takeIf { it in 0..MAX_LIST_RADIUS } ?: fail("The radius must be 0 to $MAX_LIST_RADIUS")
            else -> fail("Usage: ::npcs [radius]")
        }
        val near = world.npcs.near(player.x, player.z, player.level, radius)
        if (near.isEmpty()) {
            reply("No NPCs within $radius tiles.")
            return@register
        }
        reply("${near.size} NPC${if (near.size == 1) "" else "s"} within $radius tiles:")
        for (npc in near.take(MAX_LISTED)) {
            val spawn = npc.spawn
            val freeze = when {
                npc.freezeTicks > 0 -> "; frozen ${npc.freezeTicks} more tick${if (npc.freezeTicks == 1) "" else "s"}"
                npc.freezeImmunityTicks > 0 -> "; immune to freezes ${npc.freezeImmunityTicks} more tick${if (npc.freezeImmunityTicks == 1) "" else "s"}"
                else -> ""
            }
            val max = npc.type.stats.hitpoints
            val hurt = if (npc.hitpoints < max) "; ${npc.hitpoints}/$max hp" else ""
            val poison = world.poison.describe(npc)?.let { "; $it" }.orEmpty()
            reply("#${npc.index} ${npc.type.name} (${npc.type.id}) at ${npc.x}, ${npc.z}; spawn ${spawn.x}, ${spawn.z}, wander ${spawn.wander}$freeze$hurt$poison")
        }
        if (near.size > MAX_LISTED) reply("...and ${near.size - MAX_LISTED} more.")
    }

    register("follow", "::follow index", admin = true) {
        val index = args.singleOrNull()?.toIntOrNull() ?: fail("Usage: ::follow index (from ::npcs); again to stop")
        val npc = world.npcs[index] ?: fail("No NPC #$index")
        if (npc.following === player) {
            world.npcs.stopFollowing(npc)
            reply("${npc.type.name} #$index stopped following you.")
            return@register
        }
        if (npc.level != player.level) fail("${npc.type.name} #$index is on another level")
        world.npcs.follow(npc, player)
        reply("${npc.type.name} #$index follows you. ::follow $index again to stop.")
    }

    register("npchp", "::npchp hitpoints index|mine", admin = true) {
        val usage = "Usage: ::npchp hitpoints index|mine (from ::npcs)"
        if (args.size != 2) fail(usage)
        val hitpoints = args[0].toIntOrNull()?.takeIf { it in 1..MAX_NPC_HITPOINTS } ?: fail("The hitpoints must be 1 to $MAX_NPC_HITPOINTS")
        // Past the NPC's own maximum too, so a test can hit one many times; its health bar shows it full until it is below.
        val chosen = if (args[1] == "mine") {
            world.npcs.all.filter { it.spawn.source == spawnSource(player.username) }.toList()
        } else {
            val index = args[1].toIntOrNull() ?: fail(usage)
            listOf(world.npcs[index] ?: fail("No NPC #$index"))
        }
        for (npc in chosen) npc.hitpoints = hitpoints
        reply("${chosen.size} NPC${if (chosen.size == 1) "" else "s"} now on $hitpoints hitpoints until they respawn.")
    }

    register("delnpc", "::delnpc index|mine", admin = true) {
        if (args.singleOrNull() == "mine") {
            // Every NPC you put in with ::npc, so a test script can clean up whatever happened to them.
            val mine = world.npcs.all.filter { it.spawn.source == spawnSource(player.username) }.toList()
            for (npc in mine) world.removeNpc(npc)
            reply("Removed ${mine.size} NPC${if (mine.size == 1) "" else "s"} you put in.")
            return@register
        }
        val index = args.singleOrNull()?.toIntOrNull() ?: fail("Usage: ::delnpc index|mine (from ::npcs)")
        val npc = world.npcs[index] ?: fail("No NPC #$index")
        world.removeNpc(npc)
        reply("Removed ${npc.type.name} #$index.")
    }
}

private fun spawnSource(username: String) = "::npc by $username"

private const val MAX_WANDER = 64
private const val DEFAULT_LIST_RADIUS = 15
private const val MAX_LIST_RADIUS = 104
/** Each line is a chatbox message; more than this scrolls the rest away. */
private const val MAX_LISTED = 20
private const val MAX_NPC_HITPOINTS = 100_000
