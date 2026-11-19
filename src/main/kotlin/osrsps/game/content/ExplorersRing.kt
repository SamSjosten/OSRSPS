package osrsps.game.content

import java.time.LocalDate
import java.time.ZoneOffset
import osrsps.game.EquipmentTab
import osrsps.game.Movement
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.Teleblock
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/**
 * The Explorer's rings' messages. The Wiki's ring pages say that a message counts what's left after each use, and that
 * Check lists what's left, but they don't give the words; none are recorded yet.
 */
object ExplorersRingLines : Lines("explorers_ring", "Explorer's ring 1 to 4 (the messages aren't on the Wiki)") {
    val ENERGY = line("energy", "Energy: run energy restored, and how many restores are left today", "left")
    val ENERGY_NONE = line("energy_none", "Energy: no restores left today")
    val TELEPORTS_LEFT = line("teleports_left", "Teleport on ring 2: how many of its three teleports are left today", "left")
    val TELEPORT_NONE = line("teleport_none", "Teleport on ring 2: no teleports left today")
    val CHECK = line("check", "Check on ring 1: restores and alchemy casts left today", "energy", "alchemy")
    val CHECK_TELEPORTS = line("check_teleports", "Check on ring 2: restores, teleports and alchemy casts left today", "energy", "teleports", "alchemy")
    val CHECK_UNLIMITED = line("check_unlimited", "Check on rings 3 and 4: restores and alchemy casts left today, teleports unlimited", "energy", "alchemy")
    val FUNCTIONS_TITLE = line("functions_title", "Functions: the menu's title (guessed)")
    val FUNCTION_ENERGY = line("function_energy", "Functions: the restore option (guessed: the worn op's name)")
    val FUNCTION_ALCHEMY = line("function_alchemy", "Functions: the alchemy option (guessed: the worn op's name)")
    val ALCHEMY_NOT_BUILT = line("alchemy_not_built", "Alchemy: our own words, as the free alchemy isn't built (no alchemy spells yet)")
}

/**
 * The Explorer's rings (13125 to 13128), the Lumbridge & Draynor Diary's rewards, from the Wiki's pages for each ring
 * (read 2026-11-19).
 *
 * - Energy restores run energy: 50% two, three and four times a day (rings 1 to 3), all of it three times (ring 4).
 * - Teleport goes to the cabbage patch by South Falador Farm: three a day on ring 2, as many as wanted on rings 3 and 4.
 * - Check counts what's left; Functions (rings 2 to 4 in the backpack) offers what the worn ring's other options do.
 * - The uses come back each day, by UTC day, and a higher ring's count starts from what the lower ones used that day.
 *
 * Left out: the free alchemy (30 casts a day), as no alchemy spell is built. Guessed: what Functions offers, a restore
 * used on full energy still counting, the teleport's animation and graphic (the standard spellbook's, as no recording
 * shows the cabbage one) and its tile (the middle of the Wiki's map square), and the teleport stopping above level 20
 * Wilderness as spells do. The Wiki says the uses come back "after logging out and back in" on a new day; here they come
 * back at the day's turn, without the relog.
 */
class ExplorersRing(private val world: World, private val today: () -> Long = { LocalDate.now(ZoneOffset.UTC).toEpochDay() }) {
    /** A ring tier's daily uses: its energy restores, how much each gives in percent, and its teleports (null: unlimited, 0: none). */
    class Tier(val obj: Int, val restores: Int, val percent: Int, val teleports: Int?)

    fun register() {
        for (tier in TIERS) {
            val type = world.objTypes[tier.obj]
            for ((name, action) in actions(tier)) {
                invOption(type, name)?.let { world.handlers.onObj(tier.obj, it) { action() } }
                wornOption(type, name)?.let { world.handlers.onWorn(tier.obj, it) { action() } }
            }
            invOption(type, "Functions")?.let { world.handlers.onObj(tier.obj, it) { functions(tier) } }
        }
    }

    private fun actions(tier: Tier): List<Pair<String, suspend ScriptContext.() -> Unit>> = listOf(
        "Energy" to { energy(tier) },
        "Teleport" to { teleport(tier) },
        "Check" to { check(tier) },
        "Alchemy" to { world.message(player, ExplorersRingLines.ALCHEMY_NOT_BUILT) },
    )

    private suspend fun ScriptContext.functions(tier: Tier) {
        var picked = 0
        world.dialogues.start(this) {
            picked = choice(ExplorersRingLines.FUNCTION_ENERGY, ExplorersRingLines.FUNCTION_ALCHEMY, title = text(ExplorersRingLines.FUNCTIONS_TITLE))
        }
        when (picked) {
            1 -> energy(tier)
            2 -> world.message(player, ExplorersRingLines.ALCHEMY_NOT_BUILT)
        }
    }

    /** Restores run energy, if the day has a restore left. */
    private fun ScriptContext.energy(tier: Tier) {
        val used = used(player, ENERGY_USED)
        if (used >= tier.restores) return world.message(player, ExplorersRingLines.ENERGY_NONE)
        player.charges[ENERGY_USED] = used + 1
        player.runEnergy = (player.runEnergy + Movement.MAX_ENERGY * tier.percent / 100).coerceAtMost(Movement.MAX_ENERGY)
        world.message(player, ExplorersRingLines.ENERGY, "left" to tier.restores - used - 1)
    }

    private suspend fun ScriptContext.teleport(tier: Tier) {
        val limit = tier.teleports
        if (limit == 0) return
        val used = used(player, TELEPORTS_USED)
        if (limit != null && used >= limit) return world.message(player, ExplorersRingLines.TELEPORT_NONE)
        val refusal = Teleblock.refusal(player, Wilderness.level(player.x, player.z))
        if (refusal.isNotEmpty()) {
            for (line in refusal) world.message(player, line)
            return
        }
        if (limit != null) {
            player.charges[TELEPORTS_USED] = used + 1
            world.message(player, ExplorersRingLines.TELEPORTS_LEFT, "left" to limit - used - 1)
        }
        world.interfaces.closeModals(player)
        player.scripts.dropWeak()
        player.waypoints.clear()
        player.interaction = null
        player.anim(TELEPORT_ANIM)
        player.spotanim(TELEPORT_SPOTANIM, TELEPORT_HEIGHT)
        delay(TELEPORT_TICKS)
        world.teleport(player, CABBAGE_PATCH.first, CABBAGE_PATCH.second, 0)
        player.anim(-1)
    }

    private fun ScriptContext.check(tier: Tier) {
        val energy = tier.restores - used(player, ENERGY_USED)
        // No alchemy is built, so none is used: a day's 30 always show.
        when (val limit = tier.teleports) {
            0 -> world.message(player, ExplorersRingLines.CHECK, "energy" to energy, "alchemy" to ALCHEMY_CASTS)
            null -> world.message(player, ExplorersRingLines.CHECK_UNLIMITED, "energy" to energy, "alchemy" to ALCHEMY_CASTS)
            else -> world.message(player, ExplorersRingLines.CHECK_TELEPORTS, "energy" to energy, "teleports" to limit - used(player, TELEPORTS_USED), "alchemy" to ALCHEMY_CASTS)
        }
    }

    /** Today's uses of [charge]: 0 once the day has turned since the last use, when the count starts again. */
    private fun used(player: Player, charge: String): Int {
        val day = today().toInt()
        if (player.charges[DAY] != day) {
            player.charges[DAY] = day
            player.charges[ENERGY_USED] = 0
            player.charges[TELEPORTS_USED] = 0
        }
        return player.charges[charge]
    }

    companion object {
        val TIERS = listOf(
            Tier(13125, restores = 2, percent = 50, teleports = 0),
            Tier(13126, restores = 3, percent = 50, teleports = 3),
            Tier(13127, restores = 4, percent = 50, teleports = null),
            Tier(13128, restores = 3, percent = 100, teleports = null),
        )

        /** The Wiki's teleport map: the cabbage patch by South Falador Farm. */
        val CABBAGE_PATCH = 3052 to 3291

        const val ALCHEMY_CASTS = 30

        const val DAY = "explorers_ring_day"
        const val ENERGY_USED = "explorers_ring_energy"
        const val TELEPORTS_USED = "explorers_ring_teleports"

        /** Guessed: the standard spellbook's teleport. */
        const val TELEPORT_ANIM = 714
        const val TELEPORT_SPOTANIM = 111
        const val TELEPORT_HEIGHT = 92
        const val TELEPORT_TICKS = 3

        /** The backpack's option called [name], 1 to 5, from the cache; the ring pages' layout when it doesn't say (tests). */
        internal fun invOption(type: ObjType?, name: String): Int? {
            val ops = type?.iops ?: return FALLBACK_IOPS.indexOf(name).takeIf { it >= 0 }?.plus(1)
            return ops.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }?.plus(1)
        }

        private fun wornOption(type: ObjType?, name: String): Int? =
            type?.let { t -> (1..EquipmentTab.WORN_OPS).firstOrNull { t.stringParam(EquipmentTab.PARAM_WORN_OP1 + it - 1).equals(name, ignoreCase = true) } }

        /** Rings 2 to 4 in the backpack (the Wiki): Wear, Check, Teleport, Functions, Drop. */
        private val FALLBACK_IOPS = listOf("Wear", "Check", "Teleport", "Functions", "Drop")
    }
}
