package osrsps.game.command

import osrsps.game.World
import osrsps.game.npc.Npc
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Combat numbers for checking the formulas in game. Read-only, so anyone may run it. */
fun CommandRegistry.registerCombatCommands(world: World) {
    register("combat", "::combat [index|name]") {
        val combat = world.combat
        val target: Npc = when {
            args.isEmpty() -> nearest(world, player.x, player.z, player.level) { combat.npcCombat[it.type] != null }
                ?: fail("No NPC you can fight within $SEARCH_RADIUS tiles. Try ::combat index|name")
            args.size == 1 && args[0].toIntOrNull() != null -> world.npcs[args[0].toInt()] ?: fail("No NPC #${args[0]}")
            else -> {
                val text = args.joinToString(" ")
                nearest(world, player.x, player.z, player.level) { it.type.name.equals(text, true) || it.type.internalName.equals(text, true) }
                    ?: fail("No NPC called '$text' within $SEARCH_RADIUS tiles")
            }
        }
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
}

private fun nearest(world: World, x: Int, z: Int, level: Int, filter: (Npc) -> Boolean): Npc? =
    world.npcs.near(x, z, level, SEARCH_RADIUS).filter(filter).minByOrNull { max(abs(it.x - x), abs(it.z - z)) }

private fun percent(chance: Double): String = String.format(Locale.ROOT, "%.1f%%", chance * 100)

private const val SEARCH_RADIUS = 15
