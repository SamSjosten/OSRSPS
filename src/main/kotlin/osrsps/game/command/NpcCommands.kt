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
        val spawn = NpcSpawn(type, player.x, player.z, player.level, wander = wander ?: 0, source = "::npc by ${player.username}", respawns = false)
        val npc = world.spawnNpc(spawn) ?: fail("No free NPC slot")
        reply("Spawned ${type.name} (${type.id}) as #${npc.index} at ${npc.x}, ${npc.z}" + if (spawn.wander > 0) ", wander ${spawn.wander}" else "")
    }

    register("npcs", "::npcs [radius]") {
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
            reply("#${npc.index} ${npc.type.name} (${npc.type.id}) at ${npc.x}, ${npc.z}; spawn ${spawn.x}, ${spawn.z}, wander ${spawn.wander}")
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

    register("delnpc", "::delnpc index", admin = true) {
        val index = args.singleOrNull()?.toIntOrNull() ?: fail("Usage: ::delnpc index (from ::npcs)")
        val npc = world.npcs[index] ?: fail("No NPC #$index")
        world.removeNpc(npc)
        reply("Removed ${npc.type.name} #$index.")
    }
}

private const val MAX_WANDER = 64
private const val DEFAULT_LIST_RADIUS = 15
private const val MAX_LIST_RADIUS = 104
/** Each line is a chatbox message; more than this scrolls the rest away. */
private const val MAX_LISTED = 20
