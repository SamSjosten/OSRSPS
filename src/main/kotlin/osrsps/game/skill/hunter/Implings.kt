package osrsps.game.skill.hunter

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.NpcDrops
import osrsps.game.combat.NpcDropsDef
import osrsps.game.content.message
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.script.ScriptContext
import osrsps.game.skill.SuccessRoll
import kotlin.math.sign

private val logger = KotlinLogging.logger {}

/**
 * An impling: its NPC types [npcs] (one picked at random when it shows, for the crystal impling's seventeen names), the
 * Hunter [level] to catch it with a net (10 more barehanded), the XP in tenths, the catch pairs for [SuccessRoll] with an
 * insect net ([netPair]) and with a magic insect net or barehanded ([magicPair]), its jarred item [jarred], and [loot],
 * its chest in [Implings.RESOURCE].
 */
class Impling(
    val loot: String,
    val npcs: List<Int>,
    val level: Int,
    val xpTenths: Int,
    val netPair: Pair<Int, Int>,
    val magicPair: Pair<Int, Int>,
    val jarred: Int,
)

/** A table of implings by weight, as the Wiki's spawn tiers are written (20/100 and so on), unseen for [unseenTicks] before each shows. */
class ImplingTier(
    val name: String,
    val lines: List<Pair<Impling, Int>>,
    val unseenTicks: Int = Implings.UNSEEN_TICKS,
    /** Whether a caught one's spawner starts over at once, or waits for the next cycle. */
    val restartsOnCatch: Boolean = true,
) {
    val total: Int = lines.sumOf { it.second }

    fun roll(world: World): Impling {
        var left = world.random.nextInt(total)
        for ((impling, weight) in lines) {
            left -= weight
            if (left < 0) return impling
        }
        error("A draw of $total on $name landed on no line")
    }
}

/**
 * Implings on Gielinor (Hunter step 6b). Catch on one, wielding an insect net or magic insect net (or barehanded at 10
 * levels over its requirement); with an impling jar in the backpack the jar becomes the jarred impling, without one its
 * loot is taken at once. Loot on a jarred impling gives a roll of its table and the empty jar back, 9 times in 10.
 *
 * Spawning, from the Wiki's Impling page: every [CYCLE_TICKS] the implings out despawn; four spawners are rolled on
 * the high tier, N (5 to 10) on the mid tier and 2N on the low tier, each put at a random one of [VARIABLE_POINTS]; each
 * of [LOW_POINTS] (the Zanaris one too) has a low-tier spawner, and Prifddinas one crystal spawner at a random one of
 * [PRIF_POINTS]. A spawner shows its impling [UNSEEN_TICKS] later ([CRYSTAL_UNSEEN_TICKS] for the crystal one, from its
 * page), picking the impling then from its tier. A visible
 * impling roams up to [ROAM] tiles from its point, and an unseen one roams the same way, so it shows where that took it
 * ([roamed]). A caught one's spawner starts over at once at the same point, but the crystal one's waits for the next
 * cycle (its page: half-hourly).
 *
 * Sources: the OSRS Wiki's Impling page (read 2026-11-15) for the tiers, the points, the cycle, the 2-minute unseen
 * phase, the 100-tile roam, the respawn on capture, barehanded at 10 levels over, a jar needed to keep one and looting at
 * once without one; each impling's page for its level, XP and catch pairs; each jar's page for its loot (in
 * [RESOURCE]), Loot as its op, and the 10% chance (Mod Ash) the impling jar breaks. Ids from the rev 242 cache:
 * ii_impling_type_1 to 10 and ii_impling_type_12_*, ii_captured_impling_1 to 10 and 12, ii_impling_jar 11260.
 *
 * Guessed: one attempt per Catch, judged the tick after the swing, as for butterflies; the unseen roam following the
 * visible one's rules; the first cycle at boot; a caught impling's spawner rolling the tier it was made from;
 * which of the crystal impling's seventeen names shows (at random); Loot needing one free slot beside the jar, and loot
 * that doesn't fit (looting at once with a full backpack) going on the ground; that the miss and catch messages are
 * their own lines. Left out: Puro-Puro and its implings, the lucky impling (its loot is the clue reward tables, not
 * built), clue scrolls in the loot tables, the impling tracker, binding spells' grace, implings flying over water, the
 * stuck teleport.
 */
class Implings(private val world: World) {
    /** A spawner: [tier] at [x], [z], showing an impling on [showTick]; [npc] while one is out. */
    class Spawner(val tier: ImplingTier, val x: Int, val z: Int, var showTick: Long) {
        var npc: Npc? = null
    }

    private val spawners = ArrayList<Spawner>()
    private var nextCycle = 0L
    private var loot: NpcDrops = NpcDrops.NONE

    /** The spawners there are now, for tests. */
    val all: List<Spawner> get() = spawners

    /** False in a world whose cache has no implings (most tests): then nothing is rolled, so their seeds stay put. */
    private val present: Boolean get() = ALL.any { impling -> impling.npcs.any { world.npcTypes[it] != null } }

    fun register() {
        loot = NpcDrops::class.java.getResource(RESOURCE)?.readText()?.let { NpcDrops.parse(it, world.npcTypes, world.objTypes, RESOURCE) } ?: NpcDrops.NONE
        for (impling in ALL) {
            for (npc in impling.npcs) world.handlers.onNpc(npc, CATCH) { op -> catch(impling, op.npc ?: return@onNpc) }
            world.handlers.onObj(impling.jarred, LOOT) { op -> lootJar(impling, op.sub) }
        }
    }

    /** Call once a tick; a test may pass its own [tick]. */
    fun process(tick: Long = world.tick) {
        if (tick >= nextCycle) {
            nextCycle = tick + CYCLE_TICKS
            if (present) cycle(tick)
        }
        for (spawner in spawners) if (spawner.npc == null && tick >= spawner.showTick) show(spawner, tick)
    }

    /** The 30-minute cycle: every impling out goes, and a new set of spawners is rolled. */
    private fun cycle(tick: Long) {
        for (spawner in spawners) spawner.npc?.let(world::removeNpc)
        spawners.clear()
        fun variable(tier: ImplingTier) {
            val (x, z) = VARIABLE_POINTS[world.random.nextInt(VARIABLE_POINTS.size)]
            spawners += Spawner(tier, x, z, tick + tier.unseenTicks)
        }
        repeat(HIGH_ROLLS) { variable(HIGH) }
        val n = world.random.between(MID_MIN, MID_MAX)
        repeat(n) { variable(MID) }
        repeat(n * 2) { variable(LOW) }
        for ((x, z) in LOW_POINTS) spawners += Spawner(LOW, x, z, tick + LOW.unseenTicks)
        val (px, pz) = PRIF_POINTS[world.random.nextInt(PRIF_POINTS.size)]
        spawners += Spawner(CRYSTAL_TIER, px, pz, tick + CRYSTAL_TIER.unseenTicks)
    }

    private fun show(spawner: Spawner, tick: Long) {
        val impling = spawner.tier.roll(world)
        val id = if (impling.npcs.size == 1) impling.npcs[0] else impling.npcs[world.random.nextInt(impling.npcs.size)]
        val type = world.npcTypes[id]
        if (type == null) {
            // Not in this cache: try again next cycle rather than every tick.
            spawner.showTick = nextCycle
            return
        }
        val npc = world.spawnNpc(NpcSpawn(type, spawner.x, spawner.z, wander = ROAM, source = "implings", respawns = false))
        spawner.npc = npc
        if (npc == null) {
            spawner.showTick = tick + spawner.tier.unseenTicks
            return
        }
        // It shows where its unseen roam took it, still roaming around its point.
        val (x, z) = roamed(spawner)
        if (x != npc.x || z != npc.z) world.npcs.teleport(npc, x, z)
    }

    /**
     * Where an impling unseen for its tier's [ImplingTier.unseenTicks] has got to from [spawner]'s point: the Wiki's
     * spawn mechanics have it
     * roam invisibly for those two minutes, so it walks as a visible one does ([osrsps.game.npc.Npcs]' wander, a tile in
     * the [ROAM] square around the point picked 1 in [Npcs.WANDER_CHANCE] a tick and walked naively, one tile a tick).
     * Guessed: that the unseen roam follows the visible one's rules.
     */
    private fun roamed(spawner: Spawner): Pair<Int, Int> {
        var x = spawner.x
        var z = spawner.z
        var destX = 0
        var destZ = 0
        var walking = false
        repeat(spawner.tier.unseenTicks) {
            if (!walking) {
                if (!world.random.chance(1, Npcs.WANDER_CHANCE)) return@repeat
                destX = spawner.x + world.random.between(-ROAM, ROAM)
                destZ = spawner.z + world.random.between(-ROAM, ROAM)
                walking = true
            }
            val dx = (destX - x).sign
            val dz = (destZ - z).sign
            val step = sequenceOf(dx to dz, dx to 0, 0 to dz)
                .filter { (sx, sz) -> sx != 0 || sz != 0 }
                .firstOrNull { (sx, sz) -> world.canStep(0, x, z, sx, sz) }
            if (step == null) {
                walking = false
                return@repeat
            }
            x += step.first
            z += step.second
            if (x == destX && z == destZ) walking = false
        }
        return x to z
    }

    private suspend fun ScriptContext.catch(impling: Impling, npc: Npc) {
        if (npc.hidden) return
        val hunter = player.skills.currentLevel(Skill.HUNTER)
        val weapon = player.equipment[WearPos.WEAPON]?.id
        val pair = when {
            weapon == Butterflies.MAGIC_INSECT_NET -> if (hunter >= impling.level) impling.magicPair else null
            weapon == Butterflies.INSECT_NET -> if (hunter >= impling.level) impling.netPair else null
            weapon == null && hunter >= impling.level + Butterflies.BAREHANDED_LEVELS -> impling.magicPair
            else -> return world.message(player, HunterLines.IMP_NEED_NET)
        } ?: return world.message(player, HunterLines.IMP_LEVEL, "level" to impling.level)
        player.anim(if (weapon == null) Butterflies.BAREHANDED_ANIM else Butterflies.NET_ANIM)
        player.actionDelay = tick + Butterflies.CATCH_TICKS
        delay(1)
        if (world.npcs[npc.index] !== npc || npc.hidden) return
        if (!SuccessRoll.roll(world.random, pair.first, pair.second, hunter)) world.message(player, HunterLines.IMP_MISS) else caught(impling, npc)
        // The player is held for the rest of the catch, hit or miss, as with butterflies.
        delay(Butterflies.CATCH_TICKS - 1)
    }

    private fun ScriptContext.caught(impling: Impling, npc: Npc) {
        world.removeNpc(npc)
        // A spawner's starts over; one put down another way (a spawn file, ::npc) is simply gone.
        spawners.firstOrNull { it.npc === npc }?.let { restart(it, world.tick) }
        player.skills.addTenths(Skill.HUNTER, impling.xpTenths)
        val jar = player.inventory.contents().firstOrNull { it.second.id == IMPLING_JAR }?.first
        if (jar != null) {
            player.inventory[jar] = InvObj(impling.jarred, 1)
            world.message(player, HunterLines.IMP_JARRED)
        } else {
            give(player, impling)
            world.message(player, HunterLines.IMP_LOOTED)
        }
    }

    /**
     * [spawner]'s impling was caught on [tick]: it starts over unseen at its point, or, for a tier that doesn't
     * ([ImplingTier.restartsOnCatch], the crystal one), stays empty until the next cycle replaces it.
     */
    internal fun restart(spawner: Spawner, tick: Long) {
        spawner.npc = null
        spawner.showTick = if (spawner.tier.restartsOnCatch) tick + spawner.tier.unseenTicks else Long.MAX_VALUE
    }

    private fun ScriptContext.lootJar(impling: Impling, slot: Int) {
        if (player.inventory[slot]?.id != impling.jarred) return
        if (player.inventory.freeSlots < 1) return world.message(player, HunterLines.LOOT_SPACE)
        val keepsJar = world.random.chance(JAR_KEPT, JAR_OF)
        player.inventory[slot] = if (keepsJar) InvObj(IMPLING_JAR, 1) else null
        give(player, impling)
    }

    /** One roll of [impling]'s loot into [player]'s backpack, what doesn't fit on their tile. */
    private fun give(player: Player, impling: Impling) {
        val def: NpcDropsDef = loot.chests[impling.loot] ?: return logger.warn { "No loot for the ${impling.loot} impling in $RESOURCE" }
        for (drop in def.roll(world.random)) world.groundObjs.give(player, drop.type, drop.count)
    }

    companion object {
        const val RESOURCE = "/skills/hunter-implings.json"

        /** The implings' op1 Catch; the jars' iop3 Loot (the 242 cache). */
        const val CATCH = 1
        const val LOOT = 3

        const val IMPLING_JAR = 11260

        /** Mod Ash: the jar breaks 10% of the time. */
        const val JAR_KEPT = 9
        const val JAR_OF = 10

        /** The Wiki: thirty minutes, two minutes unseen, a hundred tiles. */
        const val CYCLE_TICKS = 3000
        const val UNSEEN_TICKS = 200

        /** The Crystal impling's page: three minutes unseen (it shows at :03 and :33); a caught one waits for the next half hour. */
        const val CRYSTAL_UNSEEN_TICKS = 300
        const val ROAM = 100

        const val HIGH_ROLLS = 4
        const val MID_MIN = 5
        const val MID_MAX = 10

        val BABY = Impling("baby", listOf(1635), 17, 200, 79 to 402, 99 to 422, 11238)
        val YOUNG = Impling("young", listOf(1636), 22, 220, 69 to 351, 89 to 371, 11240)
        val GOURMET = Impling("gourmet", listOf(1637), 28, 240, 61 to 325, 81 to 345, 11242)
        val EARTH = Impling("earth", listOf(1638), 36, 270, 51 to 302, 71 to 322, 11244)
        val ESSENCE = Impling("essence", listOf(1639), 42, 290, 40 to 275, 60 to 295, 11246)
        val ECLECTIC = Impling("eclectic", listOf(1640), 50, 320, 30 to 250, 50 to 270, 11248)
        val NATURE = Impling("nature", listOf(1641), 58, 360, 20 to 200, 40 to 220, 11250)
        val MAGPIE = Impling("magpie", listOf(1642), 65, 2160, 15 to 177, 35 to 197, 11252)
        val NINJA = Impling("ninja", listOf(1643), 74, 2400, 10 to 151, 30 to 171, 11254)
        val DRAGON = Impling("dragon", listOf(1644), 83, 3000, 5 to 125, 25 to 145, 11256)

        /** ii_impling_type_12_johnny to _stewie, 8741 to 8757. */
        val CRYSTAL = Impling("crystal", (8741..8757).toList(), 80, 2800, 8 to 141, 28 to 161, 23768)

        val ALL = listOf(BABY, YOUNG, GOURMET, EARTH, ESSENCE, ECLECTIC, NATURE, MAGPIE, NINJA, DRAGON, CRYSTAL)

        /** The Wiki's overworld tiers; the high tier's lucky impling (1/101) is left out, so it is out of 100. */
        val LOW = ImplingTier("low", listOf(BABY to 20, YOUNG to 20, GOURMET to 20, EARTH to 20, ESSENCE to 10, ECLECTIC to 10))
        val MID = ImplingTier("mid", listOf(GOURMET to 10, EARTH to 10, ESSENCE to 20, ECLECTIC to 37, NATURE to 20, MAGPIE to 2, NINJA to 1))
        val HIGH = ImplingTier("high", listOf(NATURE to 10, MAGPIE to 50, NINJA to 30, DRAGON to 10))
        val CRYSTAL_TIER = ImplingTier("crystal", listOf(CRYSTAL to 1), CRYSTAL_UNSEEN_TICKS, restartsOnCatch = false)

        /** The Wiki's map of Gielinor's variable-tier spawn points. */
        val VARIABLE_POINTS: List<Pair<Int, Int>> = points(
            "1254,3559 1275,3749 1705,3479 2203,3236 2331,3639 2394,3512 2459,3418 2584,2972 2592,3252 2739,3342 2841,2928 " +
                "2849,3033 3018,3523 3020,3424 3143,3231 3239,3283 3292,3265 3356,3013 3408,3125 3441,3351 3454,3486 3548,3528 " +
                "3677,3321 2982,3276 3169,3001 2904,3489 2646,3421 2654,3609 1616,3701 3135,3378 2727,3768 2814,3512 2470,3217 " +
                "2740,3535 1600,3837 2843,3161 1525,3520 3730,3016 3765,3798 2198,2963 2526,3101 2500,2875 1328,2971 1453,3130 " +
                "1721,3001 1617,3182 1288,3168 1455,3319 3160,2488",
        )

        /** The Wiki's low-tier points, the Zanaris marketplace's last. */
        val LOW_POINTS: List<Pair<Int, Int>> = points(
            "1705,3483 2354,3608 2279,3188 2455,3084 2567,3384 2784,3463 2967,3411 3093,3238 3283,3428 3278,3155 2481,4442",
        )

        /** The Wiki's crystal impling points in Prifddinas. */
        val PRIF_POINTS: List<Pair<Int, Int>> = points(
            "3290,6117 3307,6041 3210,6110 3213,6049 3237,6102 3308,6117 3240,6046 3214,6113 3290,6049 3234,6072 3240,6120 3234,6129",
        )

        private fun points(text: String) = text.split(' ').map { p -> p.split(',').let { it[0].toInt() to it[1].toInt() } }
    }
}
