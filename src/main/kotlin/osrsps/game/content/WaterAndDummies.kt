package osrsps.game.content

import osrsps.game.InvObj
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** The messages for filling at a water source; the words are in the dialogue file, from the Wiki's item pages. */
object WaterLines : Lines("water", "Water source") {
    val FILL_BUCKET = line("fill_bucket", "Bucket of water: an empty bucket filled; {source} is the sink's, fountain's, pump's or well's name, lower case", "source")
    val FILL_JUG = line("fill_jug", "Jug of water: a jug filled", "source")
    val FILL_BOWL = line("fill_bowl", "Bowl of water: an empty bowl filled", "source")
}

/** The training dummies' message; the words are in the dialogue file, from the Wiki's Dummy page. */
object DummyLines : Lines("dummy", "Dummy") {
    val NOTHING_MORE = line("nothing_more", "attacking a dummy at Attack level 8 or more: there's nothing more to learn from it")
    val HIT = line("hit", "each hit on a dummy")
    val NO_RANGED = line("no_ranged", "attacking a dummy with a ranged style")
}

/**
 * Filling buckets, jugs and bowls at water sources: one used on a sink, fountain or water pump (or a bucket on a well)
 * fills it, and the next of the same kind a tick later, until there are none left or the player does anything else (a
 * weak queue, as milking a cow). The sources are found by the cache's names, as Herblore finds those that fill vials
 * (which Herblore does). From the Wiki's Water source page (read 2026-11-02). Guessed: one a tick and the animation, as
 * Herblore's vials; that a well takes only buckets (the page's table is unclear). Left out: cups, fishbowls, gold bowls,
 * gourd vials and watering cans.
 */
object WaterSources {
    /** The cache's names for locs that fill every vessel here (Herblore's list for vials). */
    val SOURCES = setOf("sink", "fountain", "water pump", "waterpump")

    /** The cache's name for locs that fill only buckets. */
    const val WELL = "well"

    /** An empty vessel, what it fills to, and its message; ids by gameval name. */
    class Vessel(val empty: Int, val full: Int, val line: Line)

    val BUCKET = Vessel(1925, 1929, WaterLines.FILL_BUCKET)
    val JUG = Vessel(1935, 1937, WaterLines.FILL_JUG)
    val BOWL = Vessel(1923, 1921, WaterLines.FILL_BOWL)
    val VESSELS = listOf(BUCKET, JUG, BOWL)

    /** human_fill, as Herblore's vials. */
    const val ANIM_FILL = 832

    /** Ticks between fills, as Herblore's vials. */
    const val FILL_TICKS = 1

    /** What a loc called [name] fills: every vessel, buckets only, or none. */
    fun vessels(name: String?): List<Vessel> = when (name?.lowercase()) {
        WELL -> listOf(BUCKET)
        in SOURCES -> VESSELS
        else -> emptyList()
    }

    internal const val FILL_QUEUE = "water_fill"
}

/**
 * The training dummies (fai_varrock_sword_dummy, the Attack option): each hit gives 5 Attack XP below Attack level 8,
 * at the player's attack speed, each with its message; a ranged style is refused. From the Wiki's Dummy page (read
 * 2026-11-02) and 2004Scape's dummy script (the hit and ranged messages). Guessed: the swing is the player's weapon's
 * attack animation for their style; hits come no faster than their attack speed; at level 8 or more no swing, only the
 * message.
 */
object TrainingDummies {
    const val DUMMY = 1764
    const val ATTACK = 1
    const val XP_TENTHS = 50
    const val LEVEL_CAP = 8
}

/** Buckets, jugs and bowls used on every water source, and Attack on the dummies. */
fun registerWaterAndDummies(world: World) {
    for (type in world.locs.types.all) {
        for (vessel in WaterSources.vessels(type.name)) {
            world.handlers.onObjOnLoc(type.id, vessel.empty) { op -> fill(world, op.loc!!, vessel) }
        }
    }
    world.handlers.onLoc(TrainingDummies.DUMMY, TrainingDummies.ATTACK) { hitDummy(world) }
}

private suspend fun ScriptContext.fill(world: World, source: ZoneLoc, vessel: WaterSources.Vessel) {
    arriveDelay()
    fillOnce(world, source, vessel)
}

/** One vessel filled, and the next queued: walking off or clicking anything else stops it. */
private fun ScriptContext.fillOnce(world: World, source: ZoneLoc, vessel: WaterSources.Vessel) {
    if (!world.locs.exists(source)) return
    val slot = player.inventory.contents().firstOrNull { it.second.id == vessel.empty }?.first ?: return
    player.inventory[slot] = InvObj(vessel.full, 1)
    player.anim(WaterSources.ANIM_FILL)
    val name = world.locs.types[source.id]?.name?.lowercase() ?: "water"
    world.message(player, vessel.line, "source" to name)
    if (player.inventory.count(vessel.empty) == 0L) return
    queue(WaterSources.FILL_QUEUE, QueueType.WEAK, WaterSources.FILL_TICKS) { fillOnce(world, source, vessel) }
}

private suspend fun ScriptContext.hitDummy(world: World) {
    arriveDelay()
    if (world.combat.style(player).type == AttackType.RANGED) {
        world.message(player, DummyLines.NO_RANGED)
        return
    }
    if (player.skills.baseLevel(Skill.ATTACK) >= TrainingDummies.LEVEL_CAP) {
        world.message(player, DummyLines.NOTHING_MORE)
        return
    }
    // As a fight: no faster than the weapon swings.
    if (world.tick < player.nextAttackTick) return
    player.nextAttackTick = world.tick + world.combat.attackSpeed(player)
    player.anim(world.combat.anims(player).attack(world.combat.style(player)))
    player.skills.addTenths(Skill.ATTACK, TrainingDummies.XP_TENTHS)
    world.message(player, DummyLines.HIT)
}
