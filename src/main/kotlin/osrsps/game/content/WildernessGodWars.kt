package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.Drop
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.skill.AgilityLines
import osrsps.map.Tile

/** The Wilderness God Wars Dungeon's words, in the dialogue file. */
object WildernessGodWarsLines : Lines("wilderness_gwd", "Wilderness God Wars Dungeon") {
    val BOULDER_STRENGTH = line("boulder_strength", "Move on the boulder below Strength 60", "level")
    val ENOUGH_KEYS = line("enough_keys", "Take on an ecumenical key with as many as the player may have (the Wiki's Ecumenical key)")
}

/**
 * The Wilderness God Wars Dungeon, level 28 (Wilderness step W6.3; the Wiki's Wilderness God Wars Dungeon, God Wars Dungeon
 * (its god-related protection), Boulder and Jutting wall (Wilderness God Wars Dungeon), Ecumenical key and each
 * follower's page, read 2026-11-15). Members only.
 *
 * - **The way in**: Enter on the cave east of the Forgotten Cemetery (`wilderness_gwd_entrance` [ENTRANCE] at 3016,3738)
 *   down to the empty cavern on level 3, single-way; Use on the crevice there ([EXIT_CREVICE] at 3065,10160) back out.
 * - **The two paths**: north-west, Move on [the boulder][BOULDER] (an NPC) at Strength 60, "which can be boosted", for
 *   5.5 Strength XP "only going into the hallway"; south-east, Pass on [the jutting wall][JUTTING_WALL] at Agility 60
 *   the same way. Behind each a crevice ([CREVICE]) leads down to the main cavern: the boulder's by Armadyl's camp, the
 *   wall's by Bandos's; the main cavern's crevices ([CREVICE_BACK]) lead back up.
 * - **The followers** (npc-spawns/wilderness-god-wars-dungeon.json, multi-way: multi-combat.tsv) "are aggressive to any
 *   player unless they have equipped at least one item that is devoted to their god" ([FOLLOWERS], [spares]); the
 *   items are the Wiki's God equipment table ([ITEMS]).
 * - **The ecumenical key** ([KEY]): "all monsters in the dungeon can drop", base rate 1/[KEY_RATE] ([keyDrop]). "Players
 *   are limited to having three (3) keys at a time; completing the Medium and Hard Wilderness Diary increases this limit
 *   to four (4) and five (5) keys": the diaries aren't built, and diary-gated content is open (Sam, 9 Nov), so the
 *   limit is [KEY_LIMIT]. "If players receive additional key drops, they will be unable to pick them up as they are
 *   deleted; a message appears ... 'You already have enough of these'" ([takeKey]); keys in the bank count (INFERRED).
 *
 * INFERRED from the 242 map: where each way puts the player (beside the loc it is across from). GUESSED: the
 * animations and the 2 ticks each crossing takes; that a key's limit counts the backpack and bank. Not built: the
 * followers fighting each other, the looting bag, the Combat Achievements' better key rates, the Lesser Fanatic.
 */
object WildernessGodWars {
    const val ENTRANCE = 26766
    const val CREVICE = 26767
    const val JUTTING_WALL = 26768
    const val CREVICE_BACK = 26769
    const val BOULDER = 6621
    const val KEY = 11942
    const val KEY_RATE = 60
    const val KEY_LIMIT = 5
    const val LEVEL = 60
    const val XP_TENTHS = 55
    const val CROSS_TICKS = 2

    const val ANIM_BOULDER = 6983
    const val ANIM_WALL = 2583
    const val ANIM_CREVICE = 3835

    val ENTRANCE_AT = Tile(3016, 3738)
    val ARRIVAL = Tile(3065, 10159)
    val EXIT_CREVICE = Tile(3065, 10160)
    val SURFACE = Tile(3017, 3740)
    val BOULDER_EAST = Tile(3055, 10165)
    val BOULDER_WEST = Tile(3052, 10165)
    val WALL_AT = Tile(3066, 10148)
    val WALL_NORTH = Tile(3066, 10149)
    val WALL_SOUTH = Tile(3066, 10147)

    /** Each crevice by its loc type and tile: where it puts the player, and on which level. */
    val CREVICES: Map<Pair<Int, Tile>, Pair<Tile, Int>> = mapOf(
        (CREVICE to EXIT_CREVICE) to (SURFACE to 0),
        (CREVICE to Tile(3049, 10165)) to (Tile(3034, 10158) to 0),
        (CREVICE to Tile(3066, 10142)) to (Tile(3062, 10130) to 0),
        (CREVICE_BACK to Tile(3035, 10158)) to (Tile(3050, 10165) to 3),
        (CREVICE_BACK to Tile(3062, 10131)) to (Tile(3066, 10143) to 3),
    )

    /** The followers, by god: the Wiki's combatants list's types (the 242 cache's ids). */
    val ARMADYL = setOf(3169, 3170, 3171, 3174, 3175, 3177, 3181, 3183, 3166, 3167, 3168)
    val BANDOS = setOf(2248, 2249, 2237, 2238, 2239, 2240, 2233, 2242, 2243, 2244)
    val SARADOMIN = setOf(2213, 2214, 2210, 2211, 2212)
    val ZAMORAK = setOf(3134, 3135, 3136, 3137, 3138, 3139, 3140, 3141, 3159, 3160, 3161)

    val SARADOMIN_ITEMS = setOf(
        542, 544, 1718, 2412, 2415, 2661, 2663, 2665, 2667, 3479, 3840, 4041, 4682, 6762, 10384, 10386, 10388, 10390,
        10440, 10446, 10452, 10458, 10464, 10470, 11806, 11838, 12598, 12637, 12809, 13331, 13332, 19933, 19997, 20220,
        21776, 21778, 21791, 22296, 22326, 22327, 22328, 22954, 23191, 23332, 23607, 24721, 24723, 24725, 24727, 24731,
        24736, 25731, 25734, 25736, 27165, 27182, 29617, 33002, 33722, 34719,
    )

    val ZAMORAK_ITEMS = setOf(
        1033, 1035, 1724, 2414, 2417, 2653, 2655, 2657, 2659, 3478, 3842, 4042, 4683, 6764, 10368, 10370, 10372, 10374,
        10444, 10450, 10456, 10460, 10468, 10474, 11791, 11808, 11824, 11889, 12638, 12904, 13333, 13334, 19936, 20223,
        20517, 20520, 20595, 21780, 21782, 21795, 22296, 22545, 22555, 22978, 23194, 23334, 23605, 23613, 24144, 24288,
        24291, 24294, 24417, 24419, 24420, 24421, 27164, 27174, 27175, 27176, 27181, 27191, 27195, 27196, 27197, 27198,
        27660, 27665, 27679, 27788, 29560, 29562, 29564, 29566, 29568, 29570, 29613, 30750, 30753, 30756, 33462,
    )

    val BANDOS_ITEMS = setOf(
        11061, 11804, 11832, 11834, 11836, 12265, 12267, 12269, 12271, 12273, 12275, 12480, 12482, 12484, 12486, 12488,
        12498, 12500, 12502, 12504, 12608, 19924, 20232, 20782, 21060, 21733, 23203, 23646, 24195, 28945, 34611,
    )

    val ARMADYL_ITEMS = setOf(
        87, 11785, 11802, 11826, 11828, 11830, 12253, 12255, 12257, 12259, 12261, 12263, 12470, 12472, 12474, 12476,
        12478, 12506, 12508, 12510, 12512, 12610, 19930, 20229, 20593, 22550, 22665, 23200, 23611, 24192, 27655, 29605,
    )
    /** Each god's followers and the items that soothe them. */
    val FOLLOWERS: List<Pair<Set<Int>, Set<Int>>> = listOf(
        ARMADYL to ARMADYL_ITEMS, BANDOS to BANDOS_ITEMS, SARADOMIN to SARADOMIN_ITEMS, ZAMORAK to ZAMORAK_ITEMS,
    )

    val ALL: Set<Int> = ARMADYL + BANDOS + SARADOMIN + ZAMORAK

    /** Every item devoted to a god, the Wiki's table's. */
    val ITEMS: Set<Int> = ARMADYL_ITEMS + BANDOS_ITEMS + SARADOMIN_ITEMS + ZAMORAK_ITEMS

    /** The main cavern: map square 47,158 on the ground level. The empty cavern is the same square's level 3. */
    fun inDungeon(x: Int, z: Int, level: Int): Boolean = x shr 6 == 47 && z shr 6 == 158 && (level == 0 || level == 3)

    /** Whether [npc], a god's follower, leaves [player] alone: one of its god's items is worn. */
    fun spares(npc: Npc, player: Player): Boolean {
        val items = FOLLOWERS.firstOrNull { npc.type.id in it.first }?.second ?: return false
        return items.any { player.equipment.count(it) > 0 }
    }

    /** A kill's key: 1 in [KEY_RATE] for a follower in the dungeon. */
    fun keyDrop(world: World, npc: Npc): List<Drop> {
        if (npc.type.id !in ALL || !inDungeon(npc.x, npc.z, npc.level)) return emptyList()
        if (world.random.nextInt(KEY_RATE) != 0) return emptyList()
        val type = world.objTypes[KEY] ?: return emptyList()
        return listOf(Drop(type, 1))
    }

    /** How many keys [player] has: backpack and bank. */
    fun keys(player: Player): Long = player.inventory.count(KEY).toLong() + player.bank.count(KEY)
}

fun registerWildernessGodWars(world: World) {
    with(WildernessGodWars) {
        world.handlers.onLoc(ENTRANCE, 1) { op ->
            val loc = op.loc!!
            arriveDelay()
            if (loc.x != ENTRANCE_AT.x || loc.z != ENTRANCE_AT.z || loc.level != 0) return@onLoc world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
            world.teleport(player, ARRIVAL.x, ARRIVAL.z, 3)
        }
        for (type in listOf(CREVICE, CREVICE_BACK)) {
            world.handlers.onLoc(type, 1) { op ->
                val loc = op.loc!!
                arriveDelay()
                val (to, level) = CREVICES[type to Tile(loc.x, loc.z)] ?: return@onLoc world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
                player.anim(ANIM_CREVICE)
                delay(CROSS_TICKS)
                world.teleport(player, to.x, to.z, level)
            }
        }
        world.handlers.onLoc(JUTTING_WALL, 1) { op ->
            val loc = op.loc!!
            arriveDelay()
            if (loc.x != WALL_AT.x || loc.z != WALL_AT.z) return@onLoc world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
            if (player.skills.currentLevel(Skill.AGILITY) < LEVEL) return@onLoc world.message(player, AgilityLines.LEVEL, "level" to LEVEL)
            val inward = player.z >= WALL_NORTH.z
            val to = if (inward) WALL_SOUTH else WALL_NORTH
            player.anim(ANIM_WALL)
            delay(CROSS_TICKS)
            world.teleport(player, to.x, to.z, 3)
            if (inward) player.skills.addTenths(Skill.AGILITY, XP_TENTHS)
        }
        world.handlers.onNpc(BOULDER, 1) { op ->
            val npc = op.npc ?: return@onNpc
            if (player.skills.currentLevel(Skill.STRENGTH) < LEVEL) return@onNpc world.message(player, WildernessGodWarsLines.BOULDER_STRENGTH, "level" to LEVEL)
            val inward = player.x > npc.x
            val to = if (inward) BOULDER_WEST else BOULDER_EAST
            player.anim(ANIM_BOULDER)
            delay(CROSS_TICKS)
            world.teleport(player, to.x, to.z, 3)
            if (inward) player.skills.addTenths(Skill.STRENGTH, XP_TENTHS)
        }
        val spared = world.aggression.spares
        world.aggression.spares = { npc, player -> spares(npc, player) || spared(npc, player) }
        val extra = world.extraDrops
        world.extraDrops = { npc, killer -> extra(npc, killer) + keyDrop(world, npc) }
        world.handlers.onGroundObj(KEY, 3) { op ->
            val obj = op.groundObj!!
            if (keys(player) >= KEY_LIMIT) {
                world.groundObjs.remove(obj)
                return@onGroundObj world.message(player, WildernessGodWarsLines.ENOUGH_KEYS)
            }
            world.groundObjs.take(player, obj)
        }
    }
}
