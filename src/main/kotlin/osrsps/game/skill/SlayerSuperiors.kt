package osrsps.game.skill

import osrsps.game.GameRandom
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.Drop
import osrsps.game.content.message
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn

/** The superiors' messages. */
object SlayerSuperiorLines : Lines("slayer_superior", "Slayer superiors") {
    val APPEARED = line("appeared", "a superior spawning for the player, in red; words on the OSRS Wiki's Superior slayer monster page")
    val NOT_YOURS = line("not_yours", "attacking another player's superior; words on the OSRS Wiki's Superior slayer monster page")
}

/**
 * One superior: the cache's type by gameval name, its normal counterpart's (whose drop table it rolls), the Slayer XP its kill gives, the Slayer level its normal counterpart
 * needs (which sets the unique drop rate) and its flat armour.
 */
class Superior(val internalName: String, val base: String, val xp: Int, val slayerLevel: Int, val flatArmour: Int = 0)

/**
 * Superior Slayer monsters (Slayer step 6), from the OSRS Wiki's Superior slayer monster page (read 2026-11-15). With
 * Bigger and Badder bought and on, a player on a task who kills one of its monsters (finishing it or having dealt the
 * most damage) has a 1 in 200 chance that its superior appears where it died, for them alone, with a message
 * ([SlayerSuperiorLines.APPEARED]). Only one is out for a player at a time; another player's attack on it is refused
 * ([SlayerSuperiorLines.NOT_YOURS]). Its kill gives its own Slayer XP, task or not, and counts as one of the task (guessed: the Wiki
 * doesn't say), and drops its own drops plus three rolls on its normal counterpart's table, one of which may instead be
 * a superior unique: each of the two unique tables is hit at 1 / (200 - (Slayer level + 55)² / 125), the first giving
 * the imbued heart (1/8) or a dust or mist battlestaff (7/16 each), the second an eternal gem (1/8) or nothing.
 *
 * It goes away 2 minutes after it appeared if its player never attacked it, and at the next 30-second check once they
 * have died, logged out or left (Mod Ash). A hit on either unique table takes the place of one of the three rolls (the
 * Wiki's superiors' drop pages: "only 1 roll may be from the superior unique table"), the two tables equally likely. In
 * single-way combat its player may attack it while another monster fights them, which then gives up (the Wiki).
 * Guessed: "left" is being on another level or more than [AREA] tiles away; it stands still.
 *
 * Built: the chasm crawler (cave crawlers) and giant rockslug (rockslugs, with 2 flat armour), the superiors of the
 * Slayer monsters in so far. Left out: the Combat Achievements' and the Wilderness's better chances, the clue scroll
 * chance (no clue scrolls yet), spawning elsewhere when the dying monster's tile can't fit it.
 */
class SlayerSuperiors(private val world: World) {
    companion object {
        /** Bigger and Badder's bit in slayer_unlock (the 242 cache's row 6428). */
        const val BIGGER_AND_BADDER = 35

        /** 1 in this many on-task kills. */
        const val CHANCE = 200

        /** The rolls on the normal counterpart's drops a superior's kill gives, one maybe taken by a unique. */
        const val ROLLS = 3

        /** Superiors by the task their normal counterpart is, with the Wiki's Slayer XP. */
        val BY_TASK = mapOf(
            37 to Superior("superior_cave_crawler", "slayer_cave_crawler_1", xp = 600, slayerLevel = 10),
            51 to Superior("superior_rockslug", "slayer_rockslug", xp = 770, slayerLevel = 20, flatArmour = 2),
        )

        const val IMBUED_HEART = 20724
        const val MIST_BATTLESTAFF = 20730
        const val DUST_BATTLESTAFF = 20736
        const val ETERNAL_GEM = 21270

        /** How long an ignored superior stays: 2 minutes. */
        const val IGNORED_TICKS = 200

        /** How often it checks for its player: every 30 seconds. */
        const val CHECK_TICKS = 50

        /** How far its player may be and still be about. Guessed. */
        const val AREA = 32

        /** The unique tables' rate, 1 in (25000 - (level + 55)²) / 125: as a chance out of the first. */
        fun uniqueOdds(slayerLevel: Int): Int = 25_000 - (slayerLevel + 55) * (slayerLevel + 55)

        /** Which unique table a draw out of [uniqueOdds] hits: 1 or 2, 125 draws each, or 0 for neither. */
        fun uniqueTable(draw: Int): Int = when {
            draw < 125 -> 1
            draw < 250 -> 2
            else -> 0
        }
    }

    private class Out(val owner: String, val since: Long) {
        var attacked = false
    }

    private val out = LinkedHashMap<Npc, Out>()

    /** Superiors that died, between their body going (World.removeNpc) and their drops going down. */
    private val dropping = HashMap<Npc, Superior>()

    private val slayer get() = world.slayer

    /** Its superior's details when [npc] is a superior that is out, else null. */
    private fun superiorOf(npc: Npc): Superior? = if (npc in out) superiorType(npc) else null

    /** The Slayer XP in tenths a kill of [npc] gives, if it is a superior. */
    fun xpTenths(npc: Npc): Int? = superiorOf(npc)?.let { it.xp * 10 }

    /** Whether [npc] is [player]'s own superior. */
    fun owns(player: Player, npc: Npc): Boolean = out[npc]?.owner == player.saveKey

    /** Why [player] can't attack [npc]: another player's superior. */
    fun barred(player: Player, npc: Npc): Line? = out[npc]?.takeIf { it.owner != player.saveKey }?.let { SlayerSuperiorLines.NOT_YOURS }

    /** [player], on task, got credit for killing [npc]: perhaps its superior appears. */
    fun rolled(player: Player, npc: Npc) {
        if (npc in out || player.loggedOut) return
        if (!slayer.hasBit(player, BIGGER_AND_BADDER)) return
        val superior = BY_TASK[npc.type.params[Slayer.PARAM_CATEGORY] as? Int ?: return] ?: return
        if (out.values.any { it.owner == player.saveKey }) return
        if (world.random.nextInt(CHANCE) != 0) return
        spawn(player, superior, npc.x, npc.z, npc.level)
    }

    /** Puts [superior] out for [player] on [x], [z], now: where its counterpart died, or for `::slayer superior`. */
    fun spawn(player: Player, superior: Superior, x: Int, z: Int, level: Int): Npc? {
        val type = world.npcTypes.find(superior.internalName) ?: return null
        val spawn = NpcSpawn(type, x, z, level, source = "superior for ${player.saveKey}", respawns = false)
        val npc = world.spawnNpc(spawn) ?: return null
        if (superior.flatArmour > 0) npc.armour = { magic -> if (magic) 0 else superior.flatArmour }
        out[npc] = Out(player.saveKey, world.tick)
        world.message(player, SlayerSuperiorLines.APPEARED)
        return npc
    }

    /** [npc] died or left the world. */
    fun removed(npc: Npc) {
        if (out.remove(npc) != null && npc.dead) superiorType(npc)?.let { dropping[npc] = it }
    }

    private fun superiorType(npc: Npc): Superior? = BY_TASK.values.firstOrNull { it.internalName == npc.type.internalName }

    /** Each tick: the superiors whose time is up go. */
    fun process() {
        if (out.isEmpty()) return
        val tick = world.tick
        // Attacked at all, hit, missed or splashed: whoever last attacked it is on record (MeleeCombat.attacked).
        for ((npc, it) in out) if (npc.lastAttacker?.saveKey == it.owner) it.attacked = true
        val gone = out.entries.filter { (npc, it) ->
            !npc.dead && (
                !it.attacked && tick - it.since >= IGNORED_TICKS ||
                    (tick - it.since) % CHECK_TICKS == 0L && tick > it.since && !ownerAbout(npc, it.owner)
                )
        }.map { it.key }
        for (npc in gone) world.removeNpc(npc)
    }

    private fun ownerAbout(npc: Npc, owner: String): Boolean {
        val player = world.onlinePlayers.firstOrNull { it.saveKey == owner && !it.loggedOut } ?: return false
        return !player.dead && player.level == npc.level && maxOf(kotlin.math.abs(player.x - npc.x), kotlin.math.abs(player.z - npc.z)) <= AREA
    }

    /**
     * What a superior's kill drops beyond its own drops, if [npc] is one: three rolls on its normal counterpart's table,
     * one of which may be a unique. Null for anything else.
     */
    fun extraDrops(npc: Npc, random: GameRandom): List<Drop>? {
        val superior = dropping.remove(npc) ?: return null
        val base = world.npcTypes.find(superior.base)?.let { world.npcDrops[it] }
        val drops = ArrayList<Drop>()
        var rolls = ROLLS
        unique(random, superior)?.let {
            rolls--
            it.forEach(drops::add)
        }
        repeat(rolls) { base?.table?.roll(random)?.forEach { drops += Drop(it.type, it.count(random)) } }
        // The counterpart's tertiary drops come with each of the three rolls (the OSRS Wiki's Giant rockslug: mystic
        // gloves (light) at 1/512 with rolls=3). Inferred: a roll the unique table took still rolls them.
        repeat(ROLLS) { for (extra in base?.tertiary.orEmpty()) if (random.nextInt(extra.denominator) == 0) drops += Drop(extra.item.type, extra.item.count(random)) }
        return drops
    }

    /** A roll on the unique tables: a list (maybe empty, the second table's nothing) when one is hit, else null. */
    private fun unique(random: GameRandom, superior: Superior): List<Drop>? {
        // One draw for both tables, each hit at 125 in the odds: an equal chance of either (OSRS Wiki, Superior slayer
        // monster: "an equal chance of hitting either Unique Drop Table").
        val table = uniqueTable(random.nextInt(uniqueOdds(superior.slayerLevel)))
        if (table == 1) {
            val roll = random.nextInt(16)
            val id = when {
                roll < 2 -> IMBUED_HEART
                roll < 9 -> DUST_BATTLESTAFF
                else -> MIST_BATTLESTAFF
            }
            return listOfNotNull(world.objTypes[id]?.let { Drop(it, 1) })
        }
        if (table == 2) {
            if (random.nextInt(8) != 0) return emptyList()
            return listOfNotNull(world.objTypes[ETERNAL_GEM]?.let { Drop(it, 1) })
        }
        return null
    }
}
