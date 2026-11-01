package osrsps.game.skill

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.StatusEffects
import osrsps.game.content.Townsfolk
import osrsps.game.content.arriveDelay
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.script.ScriptContext

/** Pickpocketing's messages, the NPCs' overhead lines when they catch a thief, and the coin pouch's. */
object PickpocketLines : Lines("pickpocket", "Pickpocketing") {
    val ATTEMPT = line("attempt", "pickpocketing: the first message, with whose pocket (man, woman, Master Farmer)", "who")
    val SUCCESS = line("success", "a pickpocket that works: the message", "who")
    val FAIL = line("fail", "a pickpocket that fails: the message", "who")
    val STUNNED = line("stunned", "after a failed pickpocket: the stun's message")
    val LEVEL = line("level", "pickpocketing below the NPC's level: the message", "level", "who")
    val IN_COMBAT = line("in_combat", "pickpocketing while in combat")
    val DEAD = line("dead", "the NPC died before the pickpocket landed")
    val INVENTORY_FULL = line("inventory_full", "pickpocketing with no room for what it gives")
    val POUCHES_FULL = line("pouches_full", "pickpocketing while holding the most coin pouches")
    val CAUGHT_CITIZEN = line("caught_citizen", "a man or woman who catches a thief: overhead")
    val CAUGHT_FARMER = line("caught_farmer", "a Master Farmer who catches a thief: overhead")
    val POUCH_OPEN = line("pouch_open", "Open on one coin pouch, with the coins it gave", "coins")
    val POUCH_OPEN_ALL = line("pouch_open_all", "Open-all on coin pouches, with the coins they gave", "coins")
    val POUCH_NO_ROOM = line("pouch_no_room", "opening a coin pouch with no room for the coins")
}

/** What a pickpocket gives: [min] to [max] of [obj], weighted as [weight] among its table's others. */
class PocketLoot(val obj: Int, val min: Int, val max: Int, val weight: (farming: Int) -> Int)

/**
 * An NPC to pickpocket: [who] for the messages, the level, the XP in tenths, the [low] and [high] of its [SuccessRoll]
 * out of 256, the damage when caught, its [caught] line, and either a coin [pouch] holding [coins] each or a seed [loot]
 * table.
 */
class Pocket(
    val npcs: Collection<Int>,
    val who: String,
    val level: Int,
    val xpTenths: Int,
    val low: Int,
    val high: Int,
    val damage: Int,
    val caught: osrsps.game.dialogue.Line,
    val pouch: Int? = null,
    val coins: Int = 0,
    val loot: List<PocketLoot> = emptyList(),
)

/**
 * Pickpocketing (Draynor piece step 8), as the OSRS Wiki's Pickpocketing, Man, Master Farmer and Coin pouch pages give
 * it (read 2026-10-30) and 2004Scape's pickpocket script runs it:
 *
 * - An attempt says so, then a tick later lands or not: the chance is the NPC's [SuccessRoll] (man and woman 180 to
 *   240 out of 256, 70.7% at level 1 to 94.1% at 99, as the Wiki's chart; Master Farmer 90 to 240, the Wiki's 91/256 to
 *   241/256).
 * - Success plays the pickpocket animation, gives the XP and the loot: men and women a coin pouch (3 coins each), which
 *   stack to [MAX_POUCHES] and must be opened before pickpocketing again; the Master Farmer a seed from his table.
 * - Failure: the NPC says its line overhead, faces the player and hits them for its damage, and they are stunned for
 *   [StatusEffects.PICKPOCKET_TICKS] with the thieving birds ([StatusEffects.stun]), which blocks the next attempt.
 * - No pickpocketing in combat (hit by an NPC in the last [COMBAT_TICKS] ticks, 2004Scape's %lastcombat + 8), below the
 *   level, with no room for the loot, or while stunned.
 *
 * Guessed: the Master Farmer's herb seeds move linearly from their Farming 1 rates to their Farming 85 ones (the Wiki
 * gives the two ends and says they scale up to 85, not how); a next attempt waits [ATTEMPT_TICKS] (the Wiki's 2 ticks a
 * try); the stunned message; no animation for an NPC with no attack animation in npc-combat.json. Left out: Rocky (the
 * pet), gloves of silence, the Ardougne diary and cloak, the thieving cape's boost, the Rogue outfit's extra loot, and
 * pickpocketing NPCs outside Misthalin's builds so far (farmers, H.A.M. members, warriors, guards and on).
 */
class Pickpocketing(private val world: World) {
    /** The tick each player can next try, by account name. */
    private val nextAttempt = mutableMapOf<String, Long>()

    fun register() {
        for (pocket in POCKETS) {
            for (npc in pocket.npcs) world.handlers.onNpc(npc, PICKPOCKET) { op -> pick(op.npc ?: return@onNpc, pocket) }
        }
        val pouch = world.objTypes[CITIZEN_POUCH] ?: return
        option(pouch.iops, "Open")?.let { world.handlers.onObj(CITIZEN_POUCH, it) { op -> open(player, op.sub, all = false) } }
        option(pouch.iops, "Open-all")?.let { world.handlers.onObj(CITIZEN_POUCH, it) { op -> open(player, op.sub, all = true) } }
    }

    private suspend fun ScriptContext.pick(npc: Npc, pocket: Pocket) {
        arriveDelay()
        val text = world.textFor(player)
        if (world.statusEffects.isStunned(player, tick)) return
        if ((nextAttempt[player.username] ?: 0L) > tick) return
        if (player.skills.currentLevel(Skill.THIEVING) < pocket.level) {
            player.message(text[PickpocketLines.LEVEL, "level" to pocket.level, "who" to pocket.who])
            return
        }
        if (player.attackedTick >= 0 && player.attackedTick + COMBAT_TICKS > tick) {
            player.message(text[PickpocketLines.IN_COMBAT])
            return
        }
        if (pocket.pouch != null && player.inventory.count(pocket.pouch) >= MAX_POUCHES) {
            player.message(text[PickpocketLines.POUCHES_FULL])
            return
        }
        val roomNeeded = pocket.pouch == null || player.inventory.count(pocket.pouch) == 0L
        if (roomNeeded && player.inventory.freeSlots == 0) {
            player.message(text[PickpocketLines.INVENTORY_FULL])
            return
        }
        nextAttempt[player.username] = tick + ATTEMPT_TICKS
        player.message(text[PickpocketLines.ATTEMPT, "who" to pocket.who])
        delay(1)
        if (npc.dead) {
            player.message(text[PickpocketLines.DEAD])
            return
        }
        val thieving = player.skills.currentLevel(Skill.THIEVING)
        if (!SuccessRoll.roll(world.random, pocket.low, pocket.high, thieving)) {
            caught(npc, pocket)
            return
        }
        player.anim(ANIM_PICKPOCKET)
        player.skills.addTenths(Skill.THIEVING, pocket.xpTenths)
        give(player, pocket)
        player.message(text[PickpocketLines.SUCCESS, "who" to pocket.who])
    }

    private fun ScriptContext.caught(npc: Npc, pocket: Pocket) {
        val text = world.textFor(player)
        npc.effects.say(text[pocket.caught])
        npc.face(player)
        world.combat.npcCombat[npc.type]?.anims?.attack?.takeIf { it >= 0 }?.let { npc.effects.anim(it) }
        player.message(text[PickpocketLines.FAIL, "who" to pocket.who])
        world.statusEffects.stun(player, StatusEffects.PICKPOCKET_TICKS, pocket.damage, npc, StatusEffects.THIEVING_SPOTANIM)
        player.message(text[PickpocketLines.STUNNED])
        nextAttempt[player.username] = tick + StatusEffects.PICKPOCKET_TICKS + AFTER_STUN_TICKS
    }

    private fun give(player: Player, pocket: Pocket) {
        if (pocket.pouch != null) {
            world.objTypes[pocket.pouch]?.let { player.inventory.add(it, 1) }
            return
        }
        val (obj, count) = pick(pocket, player.skills.currentLevel(Skill.FARMING))
        world.objTypes[obj]?.let { player.inventory.add(it, count) }
    }

    /** One item and how many from [pocket]'s table, by weight at [farming]. */
    fun pick(pocket: Pocket, farming: Int): Pair<Int, Int> {
        val weights = pocket.loot.map { it.weight(farming) }
        var roll = world.random.nextInt(weights.sum())
        for ((i, entry) in pocket.loot.withIndex()) {
            if (roll < weights[i]) return entry.obj to world.random.between(entry.min, entry.max)
            roll -= weights[i]
        }
        error("Unreachable")
    }

    /** Open (one) or Open-all on the coin pouches in [slot]: [COINS_PER_CITIZEN_POUCH] coins each. */
    private fun open(player: Player, slot: Int, all: Boolean) {
        val held = player.inventory[slot]?.takeIf { it.id == CITIZEN_POUCH } ?: return
        val coins = world.objTypes[COINS] ?: return
        val text = world.textFor(player)
        val pouches = if (all) held.count else 1
        val gain = pouches * COINS_PER_CITIZEN_POUCH
        val have = player.inventory.count(COINS)
        // The coins need a stack of their own, or the slot the last pouch leaves.
        if (have == 0L && player.inventory.freeSlots == 0 && pouches < held.count) {
            player.message(text[PickpocketLines.POUCH_NO_ROOM])
            return
        }
        if (have + gain > Int.MAX_VALUE) {
            player.message(text[PickpocketLines.POUCH_NO_ROOM])
            return
        }
        player.inventory.remove(slot, pouches)
        player.inventory.add(coins, gain)
        player.message(text[if (all) PickpocketLines.POUCH_OPEN_ALL else PickpocketLines.POUCH_OPEN, "coins" to gain])
    }

    /** Every pickpocket wait for [player] ends (`::reset`). */
    fun forgive(player: Player) {
        nextAttempt.remove(player.username)
    }

    companion object {
        /** Pickpocket is op 3 on every NPC here (the 241 cache). */
        const val PICKPOCKET = 3

        /** human_pickpocket. */
        const val ANIM_PICKPOCKET = 881

        const val COMBAT_TICKS = 8
        const val ATTEMPT_TICKS = 2

        /** After a stun ends, the next pickpocket waits this much longer (StatusEffects: nine ticks' stun, eight more). */
        const val AFTER_STUN_TICKS = 8
        const val MAX_POUCHES = 28

        const val COINS = 995

        /** pickpocket_coin_pouch_citizen, the men's and women's. */
        const val CITIZEN_POUCH = 22521
        const val COINS_PER_CITIZEN_POUCH = 3

        val MASTER_FARMERS = listOf(5730, 5731)

        private fun option(iops: List<String?>, name: String): Int? =
            iops.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }?.plus(1)

        /** A seed that is 1 in [rarity] of all the Master Farmer's pickpockets, as the Wiki gives it. */
        private fun seed(obj: Int, rarity: Double, max: Int = 1, min: Int = 1) =
            PocketLoot(obj, min, max) { weight(rarity) }

        /** A herb seed whose rarity moves from [atOne] at Farming 1 to [at85] at 85 and over (guessed: linearly). */
        private fun herb(obj: Int, atOne: Double, at85: Double) = PocketLoot(obj, 1, 1) { farming ->
            val f = (farming.coerceIn(1, 85) - 1) / 84.0
            weight(atOne + (at85 - atOne) * f)
        }

        private fun weight(rarity: Double): Int = Math.round(10_000_000 / rarity).toInt()

        /** The Wiki's Master Farmer pickpocket table, 2026-10-30, each seed's rate and quantity. Rocky left out. */
        val MASTER_FARMER_LOOT = listOf(
            seed(5318, 5.65, 4), // potato_seed
            seed(5319, 7.53, 3), // onion_seed
            seed(5324, 14.4, 3), // cabbage_seed
            seed(5322, 15.7, 2), // tomato_seed
            seed(5320, 45.2, 2), // sweetcorn_seed
            seed(5323, 90.4), // strawberry_seed
            seed(5321, 189.0), // watermelon_seed
            seed(22879, 260.0), // snape_grass_seed
            seed(5305, 18.0, 12), // barley_seed
            seed(5307, 18.0, 9), // hammerstone_hop_seed
            seed(5308, 23.9, 6), // asgarnian_hop_seed
            seed(5306, 24.1, 9), // jute_seed
            seed(5309, 36.1, 6), // yanillian_hop_seed
            seed(5310, 72.2, 6), // krandorian_hop_seed
            seed(5311, 142.0, 3), // wildblood_hop_seed
            seed(5096, 21.8), // marigold_seed
            seed(5098, 32.9), // nasturtium_seed
            seed(5097, 50.9), // rosemary_seed
            seed(5099, 68.9), // woad_seed
            seed(5100, 86.3), // limpwurt_seed
            seed(5101, 25.8), // redberry_bush_seed
            seed(5102, 36.8), // cadavaberry_bush_seed
            seed(5103, 51.5), // dwellberry_bush_seed
            seed(5104, 129.0), // jangerberry_bush_seed
            seed(5105, 355.0), // whiteberry_bush_seed
            seed(5106, 937.0), // poisonivy_bush_seed
            seed(5282, 492.0), // mushroom_seed
            seed(5281, 820.0), // belladonna_seed
            seed(5280, 1_230.0), // cactus_seed
            seed(21490, 1_892.0), // seaweed_seed
            seed(22873, 2_460.0), // potato_cactus_seed
            herb(5291, 58.36, 67.2), // guam_seed
            seed(5292, 95.6), // marrentill_seed
            seed(5293, 140.0), // tarromin_seed
            seed(5294, 206.0), // harralander_seed
            herb(5295, 555.83, 268.75), // ranarr_seed
            seed(5296, 443.0), // toadflax_seed
            seed(5297, 651.0), // irit_seed
            seed(5298, 947.0), // avantoe_seed
            seed(5299, 1_389.0), // kwuarm_seed
            herb(5300, 3_835.23, 1_854.4), // snapdragon_seed
            seed(5301, 2_976.0), // cadantine_seed
            seed(5302, 4_167.0), // lantadyme_seed
            seed(5303, 6_944.0), // dwarf_weed_seed
            herb(5304, 19_176.14, 9_271.98), // torstol_seed
        )

        /** Men and women: level 1, 8 XP, 1 damage. Master Farmer: level 38, 43 XP, 3 damage. */
        val POCKETS = listOf(
            Pocket(Townsfolk.MEN, "man", 1, 80, 180, 240, 1, PickpocketLines.CAUGHT_CITIZEN, CITIZEN_POUCH, COINS_PER_CITIZEN_POUCH),
            Pocket(Townsfolk.WOMEN, "woman", 1, 80, 180, 240, 1, PickpocketLines.CAUGHT_CITIZEN, CITIZEN_POUCH, COINS_PER_CITIZEN_POUCH),
            Pocket(MASTER_FARMERS, "Master Farmer", 38, 430, 90, 240, 3, PickpocketLines.CAUGHT_FARMER, loot = MASTER_FARMER_LOOT),
        )
    }
}
