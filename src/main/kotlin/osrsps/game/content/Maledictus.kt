package osrsps.game.content

import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import osrsps.game.GroundObjs
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.map.LineOfSight
import osrsps.map.Tile

/** The Revenant maledictus' words (keys only; the words are in data/dialogue.txt). */
object MaledictusLines : Lines("revenant_maledictus", "Revenant maledictus") {
    val APPEARED_NORTH = line("appeared_north", "every player in the caves, as it appears in the northern area (no source yet)")
    val APPEARED_MIDDLE = line("appeared_middle", "every player in the caves, as it appears in the middle area (no source yet)")
    val APPEARED_SOUTH = line("appeared_south", "every player in the caves, as it appears in the southern area (no source yet)")
}

/**
 * The Revenant maledictus (Wilderness step W5.4; the Wiki's Revenant maledictus, read 2026-11-15): `wild_cave_superior`
 * 11246, size 5, 1,250 hitpoints, the strongest ghost of the Revenant Caves.
 *
 * - **Appearing**: "a small chance of the maledictus appearing whenever any revenant is slain ... calculated each kill
 *   with the total accumulated combat levels of revenants slain by all players on that world, divided by a flat spawn
 *   rate. Once the maledictus appears, this chance is reset and the boss is prevented from appearing for 45 minutes";
 *   at one of the Wiki's eleven places ([SPOTS]), every player in the caves told whether it is in the north, the middle
 *   or the south. "The maledictus will remain active for 10 minutes ... before disappearing, if not killed", and "will
 *   no longer despawn in the middle of a fight".
 * - **Multi-combat** in the singles-plus caves: "it can be attacked by any number of players, and will attack all players
 *   in range simultaneously" ([Npc.multiTarget]).
 * - **Its attacks**: the standard "white, spike-shaped ranged attack" at everyone in range, and "after every few standard
 *   attacks" the next of its three specials in turn: the ice spell (the players' tiles, then damage and a freeze for
 *   anyone still on theirs), the blood spell (the same, healing half the damage dealt) and the air surge ("a typeless
 *   area-of-effect at one player ... up to 25 damage to anyone in a 5x5 area, but less near the edges"). "Protection
 *   prayers will reduce damage taken by half."
 * - **Its loot**: "The top damaging player will obtain either an ancient totem or ancient emblem drop, along with two
 *   rolls on the revenant dragon's drop table ... All other players will get one blighted super restore and two pieces
 *   of blighted food drop under them regardless of their location as long as they dealt damage to the boss." Loot is
 *   given "even if the player with the most damage has left the cave".
 *
 * GUESSED: the spawn rate ([RATE]); the 10-minute life running out only once no one has hit it for [CALM_TICKS]; a
 * special every [SPECIAL_EVERY]th attack; the specials' landing after [SPELL_TICKS], their max hits ([SPELL_MAX]), the
 * freeze's [FREEZE_TICKS]; the surge's edge ring up to [SURGE_EDGE] and its ignoring prayers (typeless); the range
 * ([REACH]); the totem or emblem at even odds; the blighted food (blighted manta rays); which animation and graphic is
 * which special's (by the cache's names: attack_stun for ice, attack_heal for blood, attack_dot for the surge); the
 * spawn tiles within each place. Not built: the minimap arrow, the Forinthry Surge, the attack's telegraph glow, its
 * walking.
 */
object Maledictus {
    const val NAME = "revenant maledictus"
    const val BOSS = 11246

    /** The revenant dragon, whose table the top damage dealer rolls twice. */
    const val DRAGON = 7940
    const val DRAGON_COMBAT = 135

    const val RATE = 2_000_000
    const val COOLDOWN_TICKS = 4_500
    const val LIFE_TICKS = 1_000
    const val CALM_TICKS = 17

    const val SPEED = 5
    const val REACH = 10
    const val SPECIAL_EVERY = 4
    const val SPELL_TICKS = 3
    const val SPELL_MAX = 30
    const val FREEZE_TICKS = 5
    const val SURGE_CENTRE = 25
    const val SURGE_EDGE = 12
    const val PRAYED_PERCENT = 50

    const val BLIGHTED_RESTORE = 24598
    const val BLIGHTED_FOOD = 24589
    val TOP_UNIQUES = listOf(RevenantDrops.ANCIENT_TOTEM, RevenantDrops.ANCIENT_EMBLEM)

    const val ANIM_STANDARD = 9277
    const val ANIM_ICE = 9278
    const val ANIM_BLOOD = 9279
    const val ANIM_SPAWN = 9281
    const val ANIM_SURGE = 9282
    const val SPOT_STANDARD = 2033
    const val SPOT_BLOOD = 2034
    const val SPOT_ICE = 2035

    val STANDARD = NpcRangedAttack(anim = ANIM_STANDARD, projectile = SPOT_STANDARD)

    enum class Area { NORTH, MIDDLE, SOUTH }

    enum class Special { ICE, BLOOD, SURGE }

    /** A place it may appear: its south-west tile, clear for its size. */
    class Spot(val tile: Tile, val area: Area)

    val SPOTS = listOf(
        Spot(Tile(3173, 10188), Area.NORTH), // the cyclopes' and demons' chamber
        Spot(Tile(3233, 10196), Area.NORTH), // the dragons' chamber
        Spot(Tile(3214, 10188), Area.NORTH), // the northern imps'
        Spot(Tile(3241, 10165), Area.MIDDLE), // the hellhounds' ruins
        Spot(Tile(3247, 10141), Area.MIDDLE), // the eastern pyrefiends'
        Spot(Tile(3172, 10151), Area.MIDDLE), // the western pyrefiends' chamber
        Spot(Tile(3203, 10161), Area.MIDDLE), // the dark beasts' chamber
        Spot(Tile(3219, 10126), Area.MIDDLE), // the northern orks' chamber
        Spot(Tile(3209, 10091), Area.SOUTH), // the southern orks' chamber
        Spot(Tile(3156, 10109), Area.SOUTH), // the southern demons' chamber
        Spot(Tile(3167, 10119), Area.SOUTH), // outside the southern demons'
    )

    /** A world's tally toward the next appearance. */
    class Tally {
        /** The combat levels of the revenants killed since it last appeared, or since its cooldown ended. */
        var levels = 0L
        var readyTick = 0L
        var boss: Npc? = null
    }

    private val tallies = WeakHashMap<World, Tally>()

    fun tally(world: World): Tally = tallies.getOrPut(world) { Tally() }

    /** The maledictus out now, if any. */
    fun active(world: World): Npc? = tally(world).boss?.takeIf { !it.dead && world.npcs[it.index] === it }

    /** A revenant of [combat] level died: counted, then the chance it appears ([levels] in [RATE]). */
    fun revenantKilled(world: World, combat: Int) {
        val tally = tally(world)
        if (active(world) != null || world.tick < tally.readyTick) return
        tally.levels += combat
        if (world.random.nextInt(RATE) < tally.levels) spawn(world, SPOTS[world.random.nextInt(SPOTS.size)])
    }

    /** Puts it out at [spot] and tells everyone in the caves; resets the tally and starts the cooldown. */
    fun spawn(world: World, spot: Spot): Npc? {
        val type = world.npcTypes[BOSS] ?: return null
        val tally = tally(world)
        val npc = world.spawnNpc(NpcSpawn(type, spot.tile.x, spot.tile.z, 0, source = NAME, respawns = false)) ?: return null
        npc.multiTarget = true
        npc.effects.anim(ANIM_SPAWN)
        tally.levels = 0
        tally.readyTick = world.tick + COOLDOWN_TICKS
        tally.boss = npc
        born[npc] = world.tick
        val line = when (spot.area) {
            Area.NORTH -> MaledictusLines.APPEARED_NORTH
            Area.MIDDLE -> MaledictusLines.APPEARED_MIDDLE
            Area.SOUTH -> MaledictusLines.APPEARED_SOUTH
        }
        for (player in world.onlinePlayers) {
            if (player.level == 0 && RevenantCaves.inCaves(player.x, player.z)) world.message(player, line)
        }
        return npc
    }

    /** The tick each maledictus appeared (or, one put out by `::wboss`, was first seen). */
    private val born = WeakHashMap<Npc, Long>()

    /**
     * Its 10 minutes are up and for [CALM_TICKS] no one has hit it, nor has it attacked anyone: a player tanking it
     * without fighting back keeps it there too (Codex review on #1022).
     */
    fun expired(world: World, npc: Npc): Boolean =
        world.tick - born.getOrPut(npc) { world.tick } >= LIFE_TICKS &&
            world.tick - maxOf(npc.attackedTick, fights[npc]?.firedTick ?: -1) >= CALM_TICKS

    /** One fight's state. */
    private class Fight {
        var attacks = 0
        var specials = 0
        /** The tick of its last attack, standard or special. */
        var firedTick = -1L
    }

    private val fights = WeakHashMap<Npc, Fight>()

    /** The special its next special attack will be, for tests. */
    fun nextSpecial(npc: Npc): Special = Special.entries[(fights[npc]?.specials ?: 0) % Special.entries.size]

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[BOSS] ?: return null
        return EncounterDef(
            "revenant_maledictus", type,
            listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }))),
            scriptedAttacks = true,
        )
    }

    /**
     * The players it can strike: in the fight, within [REACH] and in sight either way. It never moves, and the line
     * isn't the same both ways, so a tile from which only the player sees it would be safe for good: their own sight
     * counts too (exploit review R66B-2). GUESSED: no source says how live traces it; this closes the safe spot.
     */
    private fun EncounterContext.inRange(): List<Player> = players.filter {
        !it.dead && it.level == boss.level &&
            LineOfSight.distance(boss.x, boss.z, boss.type.size, it.x, it.z) <= REACH && world.meleeCombat.seesEitherWay(boss, it)
    }

    private fun EncounterContext.tick() {
        val fight = fights.getOrPut(boss) { Fight() }
        val targets = inRange()
        if (targets.isEmpty() || world.tick < boss.nextAttackTick) return
        boss.nextAttackTick = world.tick + SPEED
        fight.firedTick = world.tick
        fight.attacks++
        if (fight.attacks % SPECIAL_EVERY != 0) {
            for (player in targets) world.meleeCombat.fireAt(boss, player, world.tick, STANDARD, AttackType.RANGED, PRAYED_PERCENT)
            log("fires at ${targets.joinToString(" ") { it.username }}")
            return
        }
        val special = Special.entries[fight.specials++ % Special.entries.size]
        when (special) {
            Special.ICE -> spell(targets, ANIM_ICE, SPOT_ICE) { player, _ -> freeze(player) }
            Special.BLOOD -> {
                var total = 0
                spell(targets, ANIM_BLOOD, SPOT_BLOOD, done = {
                    // Not a dead boss's: a hit that landed may have killed it (Retribution), and a dead body with
                    // hitpoints back would lose its damage tally, so its loot (Codex review on #1022).
                    if (boss.dead || boss.hitpoints <= 0) return@spell
                    boss.hitpoints = minOf(boss.maxHitpoints, boss.hitpoints + total / 2)
                    log("heals ${total / 2}")
                }) { _, dealt -> total += dealt }
            }
            Special.SURGE -> surge(targets[roll(0, targets.size - 1)])
        }
    }

    /**
     * The ice or blood spell: aimed at each target's tile, landing [SPELL_TICKS] later on whoever is still there, magic
     * damage halved by Protect from Magic; [landed] for each, then [done].
     */
    private fun EncounterContext.spell(targets: List<Player>, anim: Int, spot: Int, done: () -> Unit = {}, landed: (Player, Int) -> Unit) {
        boss.effects.anim(anim)
        val aimed = targets.associateWith { Tile(it.x, it.z) }
        log("casts ${if (anim == ANIM_ICE) "ice" else "blood"} at ${aimed.entries.joinToString(" ") { "${it.key.username}@${it.value.x},${it.value.z}" }}")
        after(SPELL_TICKS) {
            for ((player, tile) in aimed) {
                if (boss.dead) break
                world.zones.mapAnim(tile.x, tile.z, boss.level, spot)
                if (player.dead || players.none { it === player } || player.x != tile.x || player.z != tile.z) continue
                val rolled = roll(0, SPELL_MAX)
                val damage = if (world.prayers.protects(player, AttackType.MAGIC)) rolled * PRAYED_PERCENT / 100 else rolled
                landed(player, hit(player, damage))
            }
            done()
        }
    }

    /** The air surge at [target]'s tile: everyone within 2 tiles of it, up to [SURGE_CENTRE] within 1, [SURGE_EDGE] beyond. */
    private fun EncounterContext.surge(target: Player) {
        boss.effects.anim(ANIM_SURGE)
        val centre = Tile(target.x, target.z)
        log("surges at ${centre.x},${centre.z}")
        after(SPELL_TICKS) {
            for (player in players.toList()) {
                if (player.dead || player.level != boss.level) continue
                val from = max(abs(player.x - centre.x), abs(player.z - centre.z))
                if (from > 2) continue
                hit(player, roll(0, if (from <= 1) SURGE_CENTRE else SURGE_EDGE))
            }
        }
    }

    private fun freeze(player: Player) {
        if (player.dead || player.freezeTicks > 0 || player.freezeImmunityTicks > 0) return
        player.freezeTicks = FREEZE_TICKS
        player.frozenBy = null
        player.waypoints.clear()
    }

    /** Its loot from [damage] by save key: the top damage dealer's on its tile, everyone else's under them. */
    fun loot(world: World, npc: Npc, damage: Map<String, Int>) {
        val top = damage.entries.maxByOrNull { it.value }?.key ?: return
        val topPlayer = world.onlinePlayers.firstOrNull { it.saveKey == top }
        val drops = ArrayList<Pair<Int, Int>>()
        drops += TOP_UNIQUES[world.random.nextInt(TOP_UNIQUES.size)] to 1
        val skulled = (topPlayer?.skullTicks ?: 0) > 0
        // No looting bag for a top damager who owns one, nor a second from the other roll (Codex review on #1022).
        var ownsBag = topPlayer != null && RevenantDrops.ownsBag(world, topPlayer)
        repeat(2) {
            for (drop in RevenantDrops.roll(world.random, DRAGON, DRAGON_COMBAT, skulled, ownsBag) { world.objTypes[it] }) {
                drops += drop.type.id to drop.count
                if (drop.type.id == RevenantDrops.LOOTING_BAG) ownsBag = true
            }
        }
        for ((id, count) in drops) {
            val type = world.objTypes[id] ?: continue
            world.groundObjs.addPrivate(type, count, npc.x, npc.z, npc.level, top, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.LOOT_DESPAWN_TICKS)
            // The collection log's entry too, kept for a top damager who has logged out (Codex review on #1022).
            world.lootDroppedFor(top, type, count)
        }
        for (key in damage.keys) {
            if (key == top) continue
            val player = world.onlinePlayers.firstOrNull { it.saveKey == key } ?: continue
            for ((id, count) in listOf(BLIGHTED_RESTORE to 1, BLIGHTED_FOOD to 1, BLIGHTED_FOOD to 1)) {
                val type = world.objTypes[id] ?: continue
                world.groundObjs.addPrivate(type, count, player.x, player.z, player.level, key, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.LOOT_DESPAWN_TICKS)
            }
        }
    }
}

fun registerMaledictus(world: World) {
    with(Maledictus) {
        world.npcScripts.onDeath(Revenants.IDS) { npc -> revenantKilled(world, npc.type.combatLevel) }
        // Not in a cache without it (a test's).
        val def = def(world) ?: return
        world.wildernessBosses.register(WildernessBoss(NAME, setOf(BOSS), -1, customLoot = { npc, damage -> loot(world, npc, damage) }))
        world.npcScripts.onTick(listOf(BOSS)) { npc ->
            npc.multiTarget = true
            if (npc.dead || world.encounters.all.any { it.boss === npc }) return@onTick
            world.encounters.start(def, npc, RevenantCaves::inCaves, calledOff = { if (expired(world, npc)) "gone" else null })
        }
    }
}
