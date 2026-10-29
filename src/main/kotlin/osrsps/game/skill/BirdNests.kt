package osrsps.game.skill

import osrsps.game.GameRandom
import osrsps.game.GroundObjs
import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.WearPos
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap

/** Bird nests' words, in the dialogue file. */
object BirdNestLines : Lines("birdnest", "Bird nests") {
    val FALLS = line("falls", "the message, red on live (a <col> tag in the words), when a nest falls out of a tree")
    val SEARCH = line("search", "the message on taking the item out of a nest; guessed, the Wiki doesn't give one", "item")
    val INVENTORY_FULL = line("inventory_full", "the message on searching a nest with no room for what's in it")
}

/** One entry of a weighted table: [weight] out of the table's total. */
class NestEntry(val obj: Int, val weight: Int)

/**
 * Bird nests from woodcutting: a 1/256 chance on each log (members only, and every account counts as members here),
 * 10% better with a Woodcutting cape worn. The nest is a seed nest 65 times in 100, a ring nest 32 and each egg nest 1,
 * and lands on a tile next to the player for them alone at first. Search (an inventory option) leaves an empty nest
 * in its slot and puts what was in it in the backpack.
 *
 * Sources: the OSRS Wiki's Bird nest, Bird nest (seed) and Bird nest (ring) pages (read 2026-10-29) for the chances and
 * contents; obj ids by gameval name from the 241 cache. Guessed: which tile (the Wiki quotes Mod Ash, "biased west",
 * here the west tile counts twice among the free ones), the timers (a drop's 100 ticks private, then four times a
 * drop's 300 ticks on the ground, from the Wiki's note that nests were made to last four times longer in June 2022),
 * and searching needing a free slot, whatever is in the nest. No sound: the 241 synth ids have no names.
 * Out: clue nests (clues are out of scope), Forestry, the strung rabbit foot and twitcher's gloves.
 */
class BirdNests(private val world: World, private val collision: CollisionMap) {
    fun register() {
        for (nest in CONTENTS.keys) {
            val type = world.objTypes[nest] ?: continue
            val option = (1..5).firstOrNull { type.iop(it) == "Search" } ?: continue
            world.handlers.onObj(nest, option) { op -> search(op.sub, nest) }
        }
    }

    /** Rolls for a nest after [player] got a log, and drops one if it comes up. */
    fun roll(player: Player) {
        val cape = player.equipment[WearPos.CAPE]?.id in WOODCUTTING_CAPES
        if (world.random.chance(if (cape) CAPE_CHANCE else CHANCE, CHANCE_DENOMINATOR)) drop(player)
    }

    /** Drops a nest ([nest], or one picked as a tree picks it) next to [player] as a tree does. Returns its obj id. */
    fun drop(player: Player, nest: Int = pick(world.random, NEST_TYPES)): Int {
        require(nest in CONTENTS) { "$nest is not a nest a tree drops" }
        val type = world.objTypes[nest] ?: error("No obj type $nest for a bird nest")
        val (x, z) = tile(player)
        world.groundObjs.addPrivate(type, 1, x, z, player.level, player.saveKey, GroundObjs.DROP_PRIVATE_TICKS, DESPAWN_TICKS)
        player.message(world.textFor(player)[BirdNestLines.FALLS])
        return nest
    }

    /** A free tile next to [player], the west one twice as likely; their own tile if none is free. */
    private fun tile(player: Player): Pair<Int, Int> {
        val free = ArrayList<Pair<Int, Int>>(9)
        for ((dx, dz) in AROUND) {
            val x = player.x + dx
            val z = player.z + dz
            if (collision[x, z, player.level] and CollisionFlag.BLOCK_WALK != 0) continue
            free += x to z
            if (dx == -1 && dz == 0) free += x to z
        }
        return if (free.isEmpty()) player.x to player.z else world.random.pick(free)
    }

    private suspend fun ScriptContext.search(slot: Int, nest: Int) {
        if (player.inventory[slot]?.id != nest) return
        val text = world.textFor(player)
        // The room is checked before the roll: a check on what came out would let a full backpack holding one seed
        // reroll a nest until it gave that seed.
        if (player.inventory.freeSlots == 0) {
            player.message(text[BirdNestLines.INVENTORY_FULL])
            return
        }
        val item = pick(world.random, CONTENTS.getValue(nest))
        val type = world.objTypes[item] ?: error("No obj type $item in a bird nest")
        player.inventory[slot] = InvObj(EMPTY_NEST, 1)
        // give, not add: a seed stack already at the cap can't take one more, so it goes on the floor instead of nowhere.
        world.groundObjs.give(player, type, 1)
        player.message(text[BirdNestLines.SEARCH, "item" to type.name.lowercase()])
    }

    companion object {
        /** 1/256 a log, as 10/2560 so the cape's 10% more is 11/2560. */
        const val CHANCE = 10
        const val CAPE_CHANCE = 11
        const val CHANCE_DENOMINATOR = 2560

        /** skillcape_woodcutting, skillcape_woodcutting_trimmed. Guessed: the max cape may count too on live. */
        val WOODCUTTING_CAPES = setOf(9807, 9808)

        const val DESPAWN_TICKS = GroundObjs.DROP_DESPAWN_TICKS * 4

        const val EGG_RED = 5070 // bird_nest_egg_red
        const val EGG_GREEN = 5071 // bird_nest_egg_green
        const val EGG_BLUE = 5072 // bird_nest_egg_blue
        const val RING = 5074 // bird_nest_ring
        const val EMPTY_NEST = 5075 // bird_nest_empty

        /** bird_nest_seeds_jan2019: the seed nest woodcutting gives since January 2019. */
        const val SEEDS = 22798

        /** Out of 100. */
        val NEST_TYPES = listOf(NestEntry(SEEDS, 65), NestEntry(RING, 32), NestEntry(EGG_RED, 1), NestEntry(EGG_GREEN, 1), NestEntry(EGG_BLUE, 1))

        val CONTENTS: Map<Int, List<NestEntry>> = mapOf(
            // Out of 1,011.
            SEEDS to listOf(
                NestEntry(5312, 214), // acorn
                NestEntry(5283, 170), // apple_tree_seed
                NestEntry(5284, 108), // banana_tree_seed
                NestEntry(5285, 85), // orange_tree_seed
                NestEntry(5313, 135), // willow_seed
                NestEntry(21486, 4), // teak_seed
                NestEntry(5286, 68), // curry_tree_seed
                NestEntry(5314, 54), // maple_seed
                NestEntry(5287, 42), // pineapple_tree_seed
                NestEntry(21488, 4), // mahogany_seed
                NestEntry(5288, 34), // papaya_tree_seed
                NestEntry(5289, 22), // palm_tree_seed
                NestEntry(5290, 17), // calquat_tree_seed
                NestEntry(5315, 27), // yew_seed
                NestEntry(5316, 5), // magic_tree_seed
                NestEntry(5317, 11), // spirit_tree_seed
                NestEntry(22869, 3), // celastrus_tree_seed
                NestEntry(22871, 2), // redwood_tree_seed
                NestEntry(22877, 6), // dragonfruit_tree_seed
            ),
            // Out of 100.
            RING to listOf(
                NestEntry(1635, 35), // gold_ring
                NestEntry(1637, 40), // sapphire_ring
                NestEntry(1639, 15), // emerald_ring
                NestEntry(1641, 9), // ruby_ring
                NestEntry(1643, 1), // diamond_ring
            ),
            EGG_RED to listOf(NestEntry(5076, 1)), // bird_egg_red
            EGG_GREEN to listOf(NestEntry(5078, 1)), // bird_egg_green
            EGG_BLUE to listOf(NestEntry(5077, 1)), // bird_egg_blue
        )

        private val AROUND = listOf(-1 to 0, -1 to 1, 0 to 1, 1 to 1, 1 to 0, 1 to -1, 0 to -1, -1 to -1)

        /** One obj from a weighted table. */
        fun pick(random: GameRandom, table: List<NestEntry>): Int {
            var roll = random.nextInt(table.sumOf { it.weight })
            for (entry in table) {
                if (roll < entry.weight) return entry.obj
                roll -= entry.weight
            }
            error("Unreachable")
        }
    }
}
