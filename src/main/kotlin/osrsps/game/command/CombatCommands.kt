package osrsps.game.command

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.AmmoKind
import osrsps.game.combat.AttackStance
import osrsps.game.combat.AttackType
import osrsps.game.combat.Autocast
import osrsps.game.combat.Blowpipe
import osrsps.game.combat.CombatMath
import osrsps.game.combat.DropItem
import osrsps.game.combat.MagicInteraction
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.MeleeInteraction
import osrsps.game.combat.RangedInteraction
import osrsps.game.combat.Spellbook
import osrsps.game.npc.Npc
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Combat testing tools: `::combat`, `::fight`, `::hp` and `::drops` only read, so anyone may run them; `::hit`
 * (an NPC, or yourself with `me`), `::kill`, `::god`, `::heal`, `::multi` and `::blowpipe` change the world, so admins
 * only.
 */
fun CommandRegistry.registerCombatCommands(world: World) {
    register("combat", "::combat [index|name]", admin = false) {
        val combat = world.combat
        val target = target(world, player, args, "::combat")
        val type = target.type
        val style = combat.style(player)
        combat.npcCombat[type] ?: fail("${type.name} #${target.index} can't be fought")
        val ranged = combat.rangedMatchup(player, target)
        if (ranged != null) {
            val weight = style.weight?.name?.lowercase() ?: "standard"
            val it = combat.npcAttack(target, player)!!
            val speed = combat.attackSpeed(player) - if (style.stance == AttackStance.RAPID) 1 else 0
            reply("${style.name} (${style.stance.name.lowercase()}, ranged $weight), speed $speed: attack roll ${ranged.attackRoll}, max hit ${ranged.maxHit}")
            reply("vs ${type.name} #${target.index} (level ${type.combatLevel}, ${type.stats.hitpoints} hp): defence roll ${ranged.defenceRoll}, you hit ${percent(ranged.hitChance)}")
            reply("It: attack roll ${it.attackRoll} vs your ${it.defenceRoll}, hits ${percent(CombatMath.hitChance(it.attackRoll, it.defenceRoll))}, max hit ${it.maxHit}")
            return@register
        }
        if (style.type == AttackType.MAGIC) {
            val spell = world.spells.autocast(Autocast.key(player)) ?: fail("You autocast a spell that isn't in yet")
            val magic = combat.magicMatchup(player, target, spell)!!
            val it = combat.npcAttack(target, player)!!
            reply("${style.name} (${spell.name}), speed ${combat.attackSpeed(player)}: attack roll ${magic.attackRoll}, max hit ${magic.maxHit}")
            val weakness = combat.npcCombat[type]?.weakness?.let { ", weak to ${it.element.name.lowercase()} (${it.percent}%)" }.orEmpty()
            reply("vs ${type.name} #${target.index} (level ${type.combatLevel}, ${type.stats.hitpoints} hp$weakness): defence roll ${magic.defenceRoll}, you hit ${percent(magic.hitChance)}")
            reply("It: attack roll ${it.attackRoll} vs your ${it.defenceRoll}, hits ${percent(CombatMath.hitChance(it.attackRoll, it.defenceRoll))}, max hit ${it.maxHit}")
            return@register
        }
        if (combat.meleeType(style) == null) fail("${style.name} is a ${style.type.name.lowercase()} style; only melee and ranged are in so far")
        val m = combat.matchup(player, target) ?: fail("${type.name} #${target.index} can't be fought")
        val stance = style.stance.name.lowercase()
        val attackType = style.type.name.lowercase()
        reply("${style.name} ($stance, $attackType), speed ${combat.attackSpeed(player)}: attack roll ${m.attackRoll}, max hit ${m.maxHit}")
        reply("vs ${type.name} #${target.index} (level ${type.combatLevel}, ${type.stats.hitpoints} hp): defence roll ${m.defenceRoll}, you hit ${percent(m.hitChance)}")
        reply("It: attack roll ${m.npcAttackRoll} vs your ${m.playerDefenceRoll}, hits ${percent(m.npcHitChance)}, max hit ${m.npcMaxHit}")
    }

    register("fight", "::fight", admin = false) {
        val npc = when (val fight = player.interaction) {
            is MeleeInteraction -> fight.npc
            is RangedInteraction -> fight.npc
            is MagicInteraction -> fight.npc
            else -> fail("You are not fighting anything.")
        }
        val wait = (player.nextAttackTick - world.tick).coerceAtLeast(0)
        reply("You are fighting ${npc.type.name} #${npc.index} (${npc.hitpoints}/${npc.type.stats.hitpoints} hp); next swing in $wait tick${if (wait == 1L) "" else "s"}.")
    }

    register("hp", "::hp", admin = false) {
        val max = player.skills.baseLevel(Skill.HITPOINTS)
        val attacker = player.attackedBy
        val ago = world.tick - player.attackedTick
        val attacked = if (attacker != null && attacker.target === player && ago <= MeleeCombat.IN_COMBAT_TICKS) {
            "; under attack by ${attacker.type.name} #${attacker.index}, hit $ago tick${if (ago == 1L) "" else "s"} ago"
        } else {
            ""
        }
        reply("You have ${player.hitpoints}/$max hitpoints$attacked" + (if (player.god) "; ::god is on" else "") + ".")
    }

    register("blowpipe", "::blowpipe [dart darts scales]", admin = true) {
        val blowpipe = world.rangedCombat.blowpipe
        if (args.isNotEmpty()) {
            // Charges it as the bank's Configure-Charges would, which isn't in: ::blowpipe dragon dart 500 16383.
            val numbers = args.takeLast(2).map { parseCount(it) }
            val darts = numbers.getOrNull(0)?.takeIf { args.size >= 3 && it in 0..Blowpipe.MAX_DARTS }
            val scales = numbers.getOrNull(1)?.takeIf { it in 0..Blowpipe.MAX_SCALES }
            if (darts == null || scales == null) fail("Usage: ::blowpipe dart darts scales (each 0 to ${Blowpipe.MAX_DARTS})")
            val text = args.dropLast(2).joinToString(" ")
            val dart = findItem(world, text) ?: fail("No item '$text'")
            if (dart.id !in world.rangedWeapons.ammoOfKind(AmmoKind.DARTS)) fail("${dart.name} isn't a dart the blowpipe fires")
            player.charges[Blowpipe.DART_TYPE] = if (darts > 0) dart.id else 0
            player.charges[Blowpipe.DARTS] = darts
            player.charges[Blowpipe.SCALES] = scales
        }
        reply(blowpipe.check(player))
    }

    register("multi", "::multi [on|off]", admin = true) {
        val multi = world.multiCombat
        val on = when (args.firstOrNull()?.lowercase()) {
            null -> !multi.at(player.level, player.x, player.z)
            "on" -> true
            "off" -> false
            else -> fail("Usage: ::multi [on|off]")
        }
        multi.set(player.level, player.x, player.z, on)
        val zone = "the 8x8 zone from ${player.x and 7.inv()}, ${player.z and 7.inv()}"
        reply(if (on) "Combat is multi-way in $zone until the server restarts." else "Combat is single-way in $zone again.")
    }

    // In place of the altars that switch books, which come with their areas.
    register("spellbook", "::spellbook [${Spellbook.NAMES.joinToString("|")}]") {
        val usage = "Usage: ::spellbook [${Spellbook.NAMES.joinToString("|")}]"
        if (args.size > 1) fail(usage)
        val name = args.firstOrNull()?.lowercase()
        if (name == null) {
            reply("Your spellbook is ${Spellbook.NAMES[Spellbook.of(player)]}.")
            return@register
        }
        val book = Spellbook.NAMES.indexOf(name).takeIf { it >= 0 } ?: fail(usage)
        Spellbook.set(player, book)
        reply("Your spellbook is $name now.")
    }

    register("god", "::god", admin = true) {
        player.god = !player.god
        reply(if (player.god) "You can't be hurt now (::god again to turn it off)." else "You can be hurt again.")
    }

    register("sure", "::sure", admin = true) {
        player.sureHits = !player.sureHits
        reply(if (player.sureHits) "Your attacks can't miss now (::sure again to turn it off)." else "Your attacks can miss again.")
    }

    register("heal", "::heal", admin = true) {
        player.hitpoints = player.skills.baseLevel(Skill.HITPOINTS)
        reply("Healed to ${player.hitpoints} hitpoints.")
    }

    register("hit", "::hit damage [index|name|me]", admin = true) {
        val damage = args.firstOrNull()?.toIntOrNull()?.takeIf { it >= 0 } ?: fail("Usage: ::hit damage [index|name|me]")
        if (args.getOrNull(1).equals("me", ignoreCase = true)) {
            if (player.dead) fail("You are already dead")
            val dealt = world.meleeCombat.hitPlayer(player, null, damage, world.tick)
            reply("Hit yourself for $dealt: ${player.hitpoints}/${player.skills.baseLevel(Skill.HITPOINTS)} hp" + if (player.dead) ", dead" else "")
            return@register
        }
        val target = target(world, player, args.drop(1), "::hit $damage")
        if (target.dead) fail("${target.type.name} #${target.index} is already dead")
        if (world.combat.npcCombat[target.type] == null) fail("${target.type.name} #${target.index} can't be fought")
        val dealt = world.meleeCombat.hit(target, player, damage, world.tick)
        reply("Hit ${target.type.name} #${target.index} for $dealt: ${target.hitpoints}/${target.type.stats.hitpoints} hp" + if (target.dead) ", dead" else "")
    }

    register("drops", "::drops [index|name]", admin = false) {
        val target = target(world, player, args, "::drops")
        val type = target.type
        val def = world.npcDrops[type]?.takeIf { !it.isEmpty } ?: fail("${type.name} drops nothing")
        fun item(item: DropItem) = item.type.name + (if (item.max > 1) " x${if (item.min == item.max) item.min else "${item.min}-${item.max}"}" else "")
        if (def.always.isNotEmpty()) reply("${type.name} always drops: ${def.always.joinToString { item(it) }}")
        val table = def.table
        if (table != null) {
            val lines = table.lines.sortedByDescending { it.weight }.joinToString { line ->
                val what = line.item?.let(::item) ?: if (line.table != null) "a herb" else "nothing"
                "$what ${line.weight}/${table.slots}"
            }
            reply("One of: $lines")
        }
        if (def.tertiary.isNotEmpty()) reply("Also: ${def.tertiary.joinToString { "${item(it.item)} 1/${it.denominator}" }}")
    }

    register("kill", "::kill [index|name]", admin = true) {
        val target = target(world, player, args, "::kill")
        if (target.dead) fail("${target.type.name} #${target.index} is already dead")
        if (world.combat.npcCombat[target.type] == null) fail("${target.type.name} #${target.index} can't be fought")
        world.meleeCombat.hit(target, player, target.hitpoints, world.tick)
        reply("Killed ${target.type.name} #${target.index}.")
    }
}

/**
 * The NPC [args] names: none for one that can be fought, an index from ::npcs, or a name. With none or a name, the NPC
 * the player is fighting, or else the one attacking them, comes first when it fits; then the nearest that does. So
 * `::kill man` in a fight kills that man, not another one standing closer.
 */
private fun target(world: World, player: Player, args: List<String>, usage: String): Npc {
    fun pick(filter: (Npc) -> Boolean): Npc? {
        val fought = when (val fight = player.interaction) {
            is MeleeInteraction -> fight.npc
            is RangedInteraction -> fight.npc
            else -> null
        }
        val attacker = player.attackedBy?.takeIf { it.target === player }
        return listOfNotNull(fought, attacker).firstOrNull { !it.dead && filter(it) }
            ?: nearest(world, player.x, player.z, player.level) { !it.dead && filter(it) }
    }
    return when {
        args.isEmpty() -> pick { world.combat.npcCombat[it.type] != null }
            ?: throw CommandFailure("No NPC you can fight within $SEARCH_RADIUS tiles. Try $usage index|name")
        args.size == 1 && args[0].toIntOrNull() != null -> world.npcs[args[0].toInt()] ?: throw CommandFailure("No NPC #${args[0]}")
        else -> {
            val text = args.joinToString(" ")
            pick { it.type.name.equals(text, true) || it.type.internalName.equals(text, true) }
                ?: throw CommandFailure("No NPC called '$text' within $SEARCH_RADIUS tiles")
        }
    }
}

private fun nearest(world: World, x: Int, z: Int, level: Int, filter: (Npc) -> Boolean): Npc? =
    world.npcs.near(x, z, level, SEARCH_RADIUS).filter(filter).minByOrNull { max(abs(it.x - x), abs(it.z - z)) }

private fun percent(chance: Double): String = String.format(Locale.ROOT, "%.1f%%", chance * 100)

private const val SEARCH_RADIUS = 15
