package osrsps.game.command

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.MeleeInteraction
import osrsps.game.npc.Npc
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Combat testing tools: `::combat` and `::fight` only read, so anyone may run them; `::hit` and `::kill` change the
 * world, so admins only.
 */
fun CommandRegistry.registerCombatCommands(world: World) {
    register("combat", "::combat [index|name]") {
        val combat = world.combat
        val target = target(world, player, args, "::combat")
        val type = target.type
        val style = combat.style(player)
        if (combat.meleeType(style) == null) fail("${style.name} is a ${style.type.name.lowercase()} style; only melee is in so far")
        val m = combat.matchup(player, target) ?: fail("${type.name} #${target.index} can't be fought")
        val stance = style.stance.name.lowercase()
        val attackType = style.type.name.lowercase()
        reply("${style.name} ($stance, $attackType), speed ${combat.attackSpeed(player)}: attack roll ${m.attackRoll}, max hit ${m.maxHit}")
        reply("vs ${type.name} #${target.index} (level ${type.combatLevel}, ${type.stats.hitpoints} hp): defence roll ${m.defenceRoll}, you hit ${percent(m.hitChance)}")
        reply("It: attack roll ${m.npcAttackRoll} vs your ${m.playerDefenceRoll}, hits ${percent(m.npcHitChance)}, max hit ${m.npcMaxHit}")
    }

    register("fight", "::fight") {
        val fight = player.interaction as? MeleeInteraction ?: fail("You are not fighting anything.")
        val npc = fight.npc
        val wait = (player.nextAttackTick - world.tick).coerceAtLeast(0)
        reply("You are fighting ${npc.type.name} #${npc.index} (${npc.hitpoints}/${npc.type.stats.hitpoints} hp); next swing in $wait tick${if (wait == 1L) "" else "s"}.")
    }

    register("hit", "::hit damage [index|name]", admin = true) {
        val damage = args.firstOrNull()?.toIntOrNull()?.takeIf { it >= 0 } ?: fail("Usage: ::hit damage [index|name]")
        val target = target(world, player, args.drop(1), "::hit $damage")
        if (target.dead) fail("${target.type.name} #${target.index} is already dead")
        if (world.combat.npcCombat[target.type] == null) fail("${target.type.name} #${target.index} can't be fought")
        val dealt = world.meleeCombat.hit(target, player, damage, world.tick)
        reply("Hit ${target.type.name} #${target.index} for $dealt: ${target.hitpoints}/${target.type.stats.hitpoints} hp" + if (target.dead) ", dead" else "")
    }

    register("kill", "::kill [index|name]", admin = true) {
        val target = target(world, player, args, "::kill")
        if (target.dead) fail("${target.type.name} #${target.index} is already dead")
        if (world.combat.npcCombat[target.type] == null) fail("${target.type.name} #${target.index} can't be fought")
        world.meleeCombat.hit(target, player, target.hitpoints, world.tick)
        reply("Killed ${target.type.name} #${target.index}.")
    }
}

/** The NPC [args] names: none for the nearest that can be fought, an index from ::npcs, or a name. */
private fun target(world: World, player: Player, args: List<String>, usage: String): Npc = when {
    args.isEmpty() -> nearest(world, player.x, player.z, player.level) { world.combat.npcCombat[it.type] != null && !it.dead }
        ?: throw CommandFailure("No NPC you can fight within $SEARCH_RADIUS tiles. Try $usage index|name")
    args.size == 1 && args[0].toIntOrNull() != null -> world.npcs[args[0].toInt()] ?: throw CommandFailure("No NPC #${args[0]}")
    else -> {
        val text = args.joinToString(" ")
        nearest(world, player.x, player.z, player.level) { !it.dead && (it.type.name.equals(text, true) || it.type.internalName.equals(text, true)) }
            ?: throw CommandFailure("No NPC called '$text' within $SEARCH_RADIUS tiles")
    }
}

private fun nearest(world: World, x: Int, z: Int, level: Int, filter: (Npc) -> Boolean): Npc? =
    world.npcs.near(x, z, level, SEARCH_RADIUS).filter(filter).minByOrNull { max(abs(it.x - x), abs(it.z - z)) }

private fun percent(chance: Double): String = String.format(Locale.ROOT, "%.1f%%", chance * 100)

private const val SEARCH_RADIUS = 15
