package osrsps.game.skill

import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** Light sources' words, in the dialogue file. */
object LightSourceLines : Lines("light_source", "Light sources") {
    val LIT = line("lit", "a tinderbox used on an unlit light source, or an unlit torch on a fire or range: it lights", "item")
    val LEVEL = line("level", "lighting a light source without the Firemaking level", "level", "item")
    val EXTINGUISHED = line("extinguished", "Extinguish on a lit light source", "item")
    val LANTERN_FILLED = line("lantern_filled", "a candle (lit or not) used on an empty candle lantern: it goes in", "item")
}

/**
 * One light source that can be lit and put out: [unlit] lights into [lit] with a tinderbox at [level] Firemaking.
 * [brightness] (1 to 3) only changes how much a dark area shows on live, so it isn't sent; [nakedFlame] is whether it
 * sets off swamp gas; [extinguishable] whether a gas explosion, a fall into water or a monster can put it out.
 */
class LightSource(
    val name: String,
    val unlit: Int,
    val lit: Int,
    val level: Int,
    val brightness: Int,
    val nakedFlame: Boolean,
    val extinguishable: Boolean = true,
)

/**
 * Light sources: what lights a dark area, lighting them with a tinderbox (either way round) and putting them out with
 * their Extinguish option. Usable anywhere: dark areas ask [hasLight], swamp gas asks [nakedFlames] and puts them out
 * with [extinguish].
 *
 * Sources: the OSRS Wiki's Light sources page (read 2026-11-09) for the list, the Firemaking level to light each, its
 * brightness, which ignite gas and which effects can put out; Candle lantern for filling an empty lantern with a candle
 * (Firemaking 1 unlit, 4 lit, no XP); Torch for lighting an unlit torch on a fire or range. Item ids, names and the
 * Extinguish op from the 242 cache. Lighting gives no XP (the Wiki's Firemaking info for each has none). Guessed: no
 * animation or delay on lighting or putting out, as none is documented; the messages' words (no Wiki quote; keys only).
 * Out: filling oil lamps and lanterns with lamp oil (the lamp oil still is in Rimmington, outside Misthalin), the
 * mining helmet's Extinguish while worn (its worn op isn't in the cache), dry sticks, spicy stews, the abyssal
 * lantern's lighting with logs (Guardians of the Rift).
 */
object LightSources {
    const val TINDERBOX = 590
    const val EXTINGUISH = "Extinguish"

    const val CANDLE = 36
    const val LIT_CANDLE = 33
    const val BLACK_CANDLE = 38
    const val LIT_BLACK_CANDLE = 32
    const val UNLIT_TORCH = 596
    const val LIT_TORCH = 594
    const val CANDLE_LANTERN_EMPTY = 4527
    const val CANDLE_LANTERN = 4529
    const val CANDLE_LANTERN_LIT = 4531
    const val BLACK_CANDLE_LANTERN = 4532
    const val BLACK_CANDLE_LANTERN_LIT = 4534
    const val OIL_LAMP = 4522
    const val OIL_LAMP_LIT = 4524
    const val OIL_LANTERN = 4537
    const val OIL_LANTERN_LIT = 4539
    const val BULLSEYE_LANTERN = 4548
    const val BULLSEYE_LANTERN_LIT = 4550
    const val SAPPHIRE_LANTERN = 4701
    const val SAPPHIRE_LANTERN_LIT = 4702
    const val EMERALD_LANTERN = 9064
    const val EMERALD_LANTERN_LIT = 9065
    const val MINING_HELMET = 5014
    const val MINING_HELMET_LIT = 5013

    /** The lightable ones, as the Wiki's table lists them. */
    val ALL = listOf(
        LightSource("lit_candle", CANDLE, LIT_CANDLE, level = 1, brightness = 1, nakedFlame = true),
        LightSource("lit_black_candle", BLACK_CANDLE, LIT_BLACK_CANDLE, level = 1, brightness = 1, nakedFlame = true),
        LightSource("torch", UNLIT_TORCH, LIT_TORCH, level = 1, brightness = 1, nakedFlame = true),
        LightSource("candle_lantern", CANDLE_LANTERN, CANDLE_LANTERN_LIT, level = 4, brightness = 1, nakedFlame = false),
        LightSource("black_candle_lantern", BLACK_CANDLE_LANTERN, BLACK_CANDLE_LANTERN_LIT, level = 4, brightness = 1, nakedFlame = false),
        LightSource("oil_lamp", OIL_LAMP, OIL_LAMP_LIT, level = 12, brightness = 2, nakedFlame = true),
        LightSource("oil_lantern", OIL_LANTERN, OIL_LANTERN_LIT, level = 26, brightness = 2, nakedFlame = false),
        LightSource("bullseye_lantern", BULLSEYE_LANTERN, BULLSEYE_LANTERN_LIT, level = 49, brightness = 3, nakedFlame = false, extinguishable = false),
        LightSource("sapphire_lantern", SAPPHIRE_LANTERN, SAPPHIRE_LANTERN_LIT, level = 49, brightness = 2, nakedFlame = false),
        LightSource("emerald_lantern", EMERALD_LANTERN, EMERALD_LANTERN_LIT, level = 49, brightness = 3, nakedFlame = false),
        LightSource("mining_helmet", MINING_HELMET, MINING_HELMET_LIT, level = 65, brightness = 2, nakedFlame = false),
    )

    /**
     * Light sources that are never unlit, carried or worn, by brightness: the Firemaking cape (and trimmed), the max
     * cape (skillcape_max), the Bruma torch and its off-hand, and the abyssal lantern lit with each kind of log.
     */
    val ALWAYS_LIT: Map<Int, Int> = mapOf(9804 to 3, 9805 to 3, 13342 to 3, 20720 to 2, 29777 to 2) +
        (26824..26848 step 2).associateWith { 3 }

    private val byLit = ALL.associateBy { it.lit }
    private val byUnlit = ALL.associateBy { it.unlit }

    /** The empty candle lantern's fillings: the candle used on it, and the lantern it makes. */
    val LANTERN_FILLINGS = mapOf(
        CANDLE to CANDLE_LANTERN, BLACK_CANDLE to BLACK_CANDLE_LANTERN,
        LIT_CANDLE to CANDLE_LANTERN_LIT, LIT_BLACK_CANDLE to BLACK_CANDLE_LANTERN_LIT,
    )

    private fun carried(player: Player) = player.inventory.contents().map { it.second.id } + player.equipment.contents().map { it.second.id }

    /** How bright the player's lights are together, 0 to 3: a dark area's insects leave a player with any alone. */
    fun brightness(player: Player): Int =
        carried(player).sumOf { byLit[it]?.brightness ?: ALWAYS_LIT[it] ?: 0 }.coerceAtMost(3)

    fun hasLight(player: Player) = brightness(player) > 0

    /** The lit naked flames the player carries (candles, torches, oil lamps): what sets off swamp gas. */
    fun nakedFlames(player: Player): List<LightSource> =
        player.inventory.contents().mapNotNull { (_, obj) -> byLit[obj.id]?.takeIf { it.nakedFlame } }

    /**
     * Puts out every lit light source the player carries that [which] picks and an effect can put out, returning how
     * many went out. Worn ones too (the lit mining helmet), as the Wiki has falls into water do.
     */
    fun extinguish(player: Player, which: (LightSource) -> Boolean = { true }): Int {
        var out = 0
        for ((slot, obj) in player.inventory.contents()) {
            val source = byLit[obj.id]?.takeIf { it.extinguishable && which(it) } ?: continue
            player.inventory[slot] = InvObj(source.unlit, obj.count)
            out++
        }
        for ((pos, obj) in player.equipment.contents()) {
            val source = byLit[obj.id]?.takeIf { it.extinguishable && which(it) } ?: continue
            player.equipment[pos] = InvObj(source.unlit, obj.count)
            out++
        }
        return out
    }

    fun source(lit: Int) = byLit[lit]

    fun register(world: World) {
        for (source in ALL) {
            world.handlers.onObjOnObj(TINDERBOX, source.unlit) { op ->
                val slot = if (op.obj == source.unlit) op.objSlot else op.sub
                light(world, slot, source)
            }
            val option = (1..5).firstOrNull { world.objTypes[source.lit]?.iops?.getOrNull(it - 1) == EXTINGUISH } ?: continue
            world.handlers.onObj(source.lit, option) { op -> putOut(world, op.sub, source) }
        }
        // Unlit torches light on anything that cooks (the Wiki's Light sources page): fires and ranges.
        val torch = byUnlit.getValue(UNLIT_TORCH)
        for (loc in Cooking.FIRES + Cooking.COOK_FIRES + Cooking.RANGES) {
            world.handlers.onObjOnLoc(loc, UNLIT_TORCH) { op ->
                arriveDelay()
                light(world, op.sub, torch)
            }
        }
        for ((candle, lantern) in LANTERN_FILLINGS) {
            world.handlers.onObjOnObj(CANDLE_LANTERN_EMPTY, candle) { op ->
                val candleSlot = if (op.obj == candle) op.objSlot else op.sub
                val lanternSlot = if (candleSlot == op.sub) op.objSlot else op.sub
                fill(world, candleSlot, lanternSlot, candle, lantern)
            }
        }
    }

    private fun ScriptContext.light(world: World, slot: Int, source: LightSource) {
        val held = player.inventory[slot]?.takeIf { it.id == source.unlit } ?: return
        val name = world.objTypes[source.unlit]?.name?.lowercase() ?: source.name
        if (player.skills.currentLevel(Skill.FIREMAKING) < source.level) {
            world.message(player, LightSourceLines.LEVEL, "level" to source.level, "item" to name)
            return
        }
        player.inventory[slot] = InvObj(source.lit, held.count)
        world.message(player, LightSourceLines.LIT, "item" to name)
    }

    private fun ScriptContext.putOut(world: World, slot: Int, source: LightSource) {
        val held = player.inventory[slot]?.takeIf { it.id == source.lit } ?: return
        player.inventory[slot] = InvObj(source.unlit, held.count)
        world.message(player, LightSourceLines.EXTINGUISHED, "item" to (world.objTypes[source.unlit]?.name?.lowercase() ?: source.name))
    }

    /** A candle into an empty lantern: Firemaking 4 for a lit one, as lighting the lantern would need. */
    private fun ScriptContext.fill(world: World, candleSlot: Int, lanternSlot: Int, candle: Int, lantern: Int) {
        if (player.inventory[candleSlot]?.id != candle || player.inventory[lanternSlot]?.id != CANDLE_LANTERN_EMPTY) return
        val lit = byLit[lantern]
        val name = world.objTypes[lantern]?.name?.lowercase() ?: "candle lantern"
        if (lit != null && player.skills.currentLevel(Skill.FIREMAKING) < lit.level) {
            world.message(player, LightSourceLines.LEVEL, "level" to lit.level, "item" to name)
            return
        }
        player.inventory.remove(candleSlot, 1)
        player.inventory[lanternSlot] = InvObj(lantern, 1)
        world.message(player, LightSourceLines.LANTERN_FILLED, "item" to name)
    }
}
