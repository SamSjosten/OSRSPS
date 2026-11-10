package osrsps.game.skill

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.arriveDelay
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext
import osrsps.map.CollisionMap
import osrsps.map.LineOfSight

/** Thieving's messages and overhead lines. */
object ThievingLines : Lines("thieving", "Thieving") {
    val IN_COMBAT = line("in_combat", "stealing from a stall in combat (2004Scape's message)")
    val LEVEL = line("level", "stealing below a stall's level: the message box, with the level and the stall's name", "level", "stall")
    val INVENTORY_FULL = line("inventory_full", "stealing with a full inventory: the message box")
    val STOLEN = line("stolen", "a theft from a stall: the message, with the item's name", "item")
    val CAUGHT = line("caught", "a guard or the stall's owner who sees the theft says this overhead")
    val OWNER_ANGRY = line("owner_angry", "the owner, talked to or traded with soon after a theft: overhead")
    val GUARDS = line("guards", "the owner's next overhead line, calling the guards")
}

/** One item a stall can give, and its share of [Stall.loot]'s total weight. */
class StallLoot(val obj: Int, val weight: Int)

/**
 * A stall loc to steal from: the [empty] loc it shows for [respawnTicks] after a theft, the level and XP, what it gives,
 * the [owner] NPC who won't trade for [refuseTicks] after it, and the [guards] who watch it.
 */
class Stall(
    val loc: Int,
    val empty: Int,
    val level: Int,
    val xpTenths: Int,
    val respawnTicks: Int,
    val loot: List<StallLoot>,
    val owner: Int,
    val refuseTicks: Int,
    val guards: Set<Int>,
    /** More NPC types who keep a stall of this type, as East Ardougne's two bakers. */
    val otherOwners: Set<Int> = emptySet(),
) {
    /** Every NPC type who keeps it: [owner] and [otherOwners]. */
    val owners: Set<Int> get() = setOf(owner) + otherOwners
}

/**
 * Stealing from stalls (Draynor piece step 7), the way 2004Scape's stealing scripts have it, with the OSRS Wiki's levels,
 * XP, loot and respawn times:
 *
 * - Every theft succeeds, unless a guard of the stall's within [SIGHT] tiles and in line of sight sees it first (it says
 *   so overhead and attacks), or else its owner does (overhead, and nothing is stolen). The stall between the player and
 *   them blocks their view, as the Wiki's tip has it.
 * - A theft swaps the stall for its empty loc for the respawn time, gives one item from the stall's weighted table, and
 *   the owner won't talk or trade for the refusal time: they say so overhead and their guards in sight attack (2004Scape's
 *   stall_owner_alert_guards).
 * - No stealing while in combat: hit by an NPC in the last [COMBAT_TICKS] ticks (2004Scape counts its own attacks too).
 *
 * Guessed: the refusal timer isn't saved, so it ends at logout or a restart; the empty seed stall (`market` 634, as
 * 2004Scape's stalls use); the per-theft message names the item. Left out: Rocky (the pet), the Watchman random event.
 */
class Thieving(private val world: World, private val collision: CollisionMap) {
    /** The tick each player's ban by each owner ends, by account name and owner NPC type. */
    private val refusedUntil = mutableMapOf<Pair<String, Int>, Long>()

    /** The owners' second lines, each said on its tick ([process]). */
    private val shouts = mutableListOf<Shout>()

    private class Shout(val npc: Npc, val text: String, val tick: Long)

    /** How an owner says a line overhead, on the game thread; tests swap it for a recorder. */
    @Volatile
    internal var ownerSays: (Npc, String) -> Unit = { npc, text -> npc.effects.say(text) }

    private val owners: Set<Int> get() = STALLS.values.flatMapTo(mutableSetOf()) { it.owners }

    fun register() {
        for (stall in STALLS.values) {
            world.handlers.onLoc(stall.loc, stealOption(stall.loc)) { op -> steal(op, stall) }
        }
    }

    /** The stall's Steal option, 1 to 5; 2 when the cache doesn't say (tests). */
    private fun stealOption(locId: Int): Int {
        val ops = world.locs.types[locId]?.ops ?: return 2
        val index = ops.indexOfFirst { it?.startsWith("Steal", ignoreCase = true) == true }
        return if (index >= 0) index + 1 else 2
    }

    private suspend fun ScriptContext.steal(op: Op, stall: Stall) {
        val loc = op.loc ?: return
        arriveDelay()
        // Another thief may have emptied it while this one arrived.
        if (!world.locs.exists(loc)) return
        val text = world.textFor(player)
        if (player.attackedTick >= 0 && player.attackedTick + COMBAT_TICKS > tick) {
            player.message(text[ThievingLines.IN_COMBAT])
            return
        }
        if (player.skills.currentLevel(Skill.THIEVING) < stall.level) {
            val name = world.locs.types[stall.loc]?.name ?: "${stall.loc}"
            world.dialogues.start(this) { mesbox(ThievingLines.LEVEL, "level" to stall.level, "stall" to name.lowercase()) }
            return
        }
        if (player.inventory.freeSlots == 0) {
            world.dialogues.start(this) { mesbox(ThievingLines.INVENTORY_FULL) }
            return
        }
        val guard = watching(player, stall.guards)
        if (guard != null) {
            guard.effects.say(text[ThievingLines.CAUGHT])
            world.meleeCombat.provoke(guard, player, tick)
            return
        }
        val owner = watching(player, stall.owners)
        if (owner != null) {
            owner.effects.say(text[ThievingLines.CAUGHT])
            return
        }
        val obj = pick(stall)
        val type = world.objTypes[obj] ?: error("No obj type $obj for stall ${stall.loc}")
        world.locs.replace(loc, stall.empty, revertAfter = stall.respawnTicks) ?: return
        player.anim(ANIM_STEAL)
        player.inventory.add(type, 1)
        player.skills.addTenths(Skill.THIEVING, stall.xpTenths)
        player.message(text[ThievingLines.STOLEN, "item" to type.name])
        for (keeper in stall.owners) refusedUntil[player.username to keeper] = tick + stall.refuseTicks
    }

    /** One item from [stall]'s table, by weight. */
    fun pick(stall: Stall): Int {
        var roll = world.random.nextInt(stall.loot.sumOf { it.weight })
        for (entry in stall.loot) {
            if (roll < entry.weight) return entry.obj
            roll -= entry.weight
        }
        error("Unreachable")
    }

    /** The nearest NPC of [types] within [SIGHT] tiles that can see [player] and isn't already fighting them. */
    private fun watching(player: Player, types: Set<Int>): Npc? =
        world.npcs.near(player.x, player.z, player.level, SIGHT)
            .filter { it.spawn.type.id in types && !it.outOfPlay && it.target !== player }
            .filter { LineOfSight.clear(collision, player.level, it.x, it.z, it.type.size, player.x, player.z) }
            .minByOrNull { maxOf(kotlin.math.abs(it.x - player.x), kotlin.math.abs(it.z - player.z)) }

    /**
     * Whether [owner] won't deal with [player] after a theft from their stall: if so they say so overhead and call the
     * guards in sight, who attack. Content checks this before the owner's talk or shop opens.
     */
    fun refuses(player: Player, owner: Npc, tick: Long): Boolean {
        if (owner.spawn.type.id !in owners) return false
        val until = refusedUntil[player.username to owner.spawn.type.id] ?: return false
        if (until <= tick) {
            refusedUntil.remove(player.username to owner.spawn.type.id)
            return false
        }
        val text = world.textFor(player)
        ownerSays(owner, text[ThievingLines.OWNER_ANGRY])
        // The call for the guards comes on the next tick (2004Scape's stall_owner_alert_guards); they attack at once.
        shouts += Shout(owner, text[ThievingLines.GUARDS], tick + 1)
        val guards = STALLS.values.filter { owner.spawn.type.id in it.owners }.flatMapTo(mutableSetOf()) { it.guards }
        for (guard in world.npcs.near(owner.x, owner.z, owner.level, SIGHT)) {
            if (guard.spawn.type.id !in guards || guard.outOfPlay || guard.target === player) continue
            if (!LineOfSight.clear(collision, owner.level, guard.x, guard.z, guard.type.size, player.x, player.z)) continue
            world.meleeCombat.provoke(guard, player, tick)
        }
        return true
    }

    /** The NPC phase: each owner whose second line is due says it, if they still stand. */
    fun process() {
        if (shouts.isEmpty()) return
        val tick = world.tick
        val iterator = shouts.iterator()
        while (iterator.hasNext()) {
            val shout = iterator.next()
            if (shout.tick > tick) continue
            iterator.remove()
            if (!shout.npc.outOfPlay && world.npcs[shout.npc.index] === shout.npc) ownerSays(shout.npc, shout.text)
        }
    }

    /** Every owner forgets [player]'s thefts: `::reset`, and logging out (the bans aren't saved). */
    fun forgive(player: Player) {
        refusedUntil.keys.removeAll { it.first == player.username }
    }

    companion object {
        /** How far a guard or owner sees a theft (2004Scape's npc_huntall and npc_find, 5 tiles). */
        const val SIGHT = 5

        /** No stealing for this long after an NPC's hit (2004Scape's %lastcombat + 10). */
        const val COMBAT_TICKS = 10

        /** human_pickuptable. */
        const val ANIM_STEAL = 832

        const val OLIVIA = 500
        const val FORTUNATO = 1260

        /** tea_seller, Varrock's Tea Seller, who keeps the tea stall. */
        const val TEA_SELLER = 8687

        /** farming_market_guard, Draynor's two Market Guards. */
        const val MARKET_GUARD = 5732

        /** A ban of 30 minutes (Olivia, the Wiki's Seed stall page) is 3,000 ticks; 20 (the Wiki's Stalls page), 2,000. */
        const val OLIVIA_REFUSE_TICKS = 3_000
        const val STALL_REFUSE_TICKS = 2_000

        /** The Wiki's Seed stall (Drop Rate Project rates, out of 1,000) and Market stall (wine, out of 100), 2026-10-30. */
        val SEED_LOOT = listOf(
            StallLoot(5307, 120), // hammerstone_hop_seed
            StallLoot(5318, 119), // potato_seed
            StallLoot(5096, 119), // marigold_seed
            StallLoot(5305, 118), // barley_seed
            StallLoot(5319, 89), // onion_seed
            StallLoot(5308, 83), // asgarnian_hop_seed
            StallLoot(5324, 71), // cabbage_seed
            StallLoot(5309, 47), // yanillian_hop_seed
            StallLoot(5097, 36), // rosemary_seed
            StallLoot(5098, 35), // nasturtium_seed
            StallLoot(5322, 35), // tomato_seed
            StallLoot(5306, 35), // jute_seed
            StallLoot(5320, 30), // sweetcorn_seed
            StallLoot(5310, 24), // krandorian_hop_seed
            StallLoot(5323, 18), // strawberry_seed
            StallLoot(5311, 12), // wildblood_hop_seed
            StallLoot(5321, 9), // watermelon_seed
        )
        /** cup_of_tea, the tea stall's only item (the Wiki's Tea stall page). */
        val TEA_LOOT = listOf(StallLoot(1978, 1))
        val WINE_LOOT = listOf(
            StallLoot(1935, 39), // jug_empty
            StallLoot(1937, 20), // jug_water
            StallLoot(1987, 17), // grapes
            StallLoot(1993, 13), // jug_wine
            StallLoot(7919, 11), // rag_bottle_wine
        )

        /**
         * By loc type. Seed stall: level 27, 10 XP, back in 2.4 seconds (4 ticks). Wine stall (`rag_market_stall`, "Market
         * stall"): level 22, 27 XP, 4.8 seconds (8 ticks), empty as `rag_market_stall_empty`. Varrock step 13, the tea
         * stall (`tea_stall`, the Wiki's Tea stall page, read 2026-11-03): level 5, 16 XP, back in 2.4 seconds (4 ticks), a
         * cup of tea each time; no guards (the Wiki: Varrock's are too far off to come). Guessed: it empties to `market`
         * as the seed stall does, and its owner's ban is the Stalls page's 20 minutes.
         */
        /** `market`, the empty stall the seed and tea stalls show, and (GUESSED) East Ardougne's. */
        const val EMPTY_STALL = 634

        /** East Ardougne's stalls (Thieving 2 step T3): `bakers_stall_stealing` and the rest, as the cache names them. */
        const val BAKERY_STALL = 11730
        const val SILK_STALL = 11729
        const val GEM_STALL = 11731
        const val FUR_STALL = 11732
        const val SPICE_STALL = 11733
        const val SILVER_STALL = 11734

        /** Their keepers: `baker_merchant_ardougne` and `_ardougne2`, and the `*_merchant_ardougne` types. */
        const val ARDOUGNE_BAKER = 8724
        const val ARDOUGNE_BAKER_2 = 8725
        const val ARDOUGNE_SILVER_MERCHANT = 8722
        const val ARDOUGNE_GEM_MERCHANT = 8723
        const val ARDOUGNE_SPICE_SELLER = 8726
        const val ARDOUGNE_FUR_TRADER = 8727
        const val ARDOUGNE_SILK_MERCHANT = 8728

        /** Who watches East Ardougne's stalls (2004Scape's stealing.dbrow): the guards, then knights, then paladins. */
        val ARDOUGNE_GUARDS = setOf(Pickpocketing.ARDOUGNE_GUARD)
        val ARDOUGNE_GUARDS_AND_KNIGHTS = ARDOUGNE_GUARDS + Pickpocketing.KNIGHTS_OF_ARDOUGNE
        val ARDOUGNE_WATCH = ARDOUGNE_GUARDS_AND_KNIGHTS + Pickpocketing.PALADINS

        /** The Wiki's Bakery stall: a cake 13/20, bread 5/20, a chocolate slice 2/20. */
        val BAKERY_LOOT = listOf(StallLoot(1891, 13), StallLoot(2309, 5), StallLoot(1901, 2))
        val SILK_LOOT = listOf(StallLoot(950, 1))
        val FUR_LOOT = listOf(StallLoot(958, 1)) // grey_wolf_fur
        /** The Wiki's Silver stall: silver ore 16/20, a silver bar 3/20, a tiara 1/20. */
        val SILVER_LOOT = listOf(StallLoot(442, 16), StallLoot(2355, 3), StallLoot(5525, 1))
        val SPICE_LOOT = listOf(StallLoot(2007, 1)) // spicespot
        /** The Wiki's Gem stall: uncut sapphire 105/128, emerald 17, ruby 5, diamond 1. */
        val GEM_LOOT = listOf(StallLoot(1623, 105), StallLoot(1621, 17), StallLoot(1619, 5), StallLoot(1617, 1))

        /**
         * East Ardougne's market (Thieving 2 step T3; the Wiki's Stall (Thievable), Bakery, Silk, Fur, Silver, Spice and Gem
         * stall pages, read 2026-11-10). Level, XP and respawn: bakery 5, 16, 2.4 s (4 ticks); silk 20, 24, 4.8 s (8); fur
         * 35, 45, 7.2 s (12); silver 50, 205, 19.2 s (32); spice 65, 92, 6 s (10); gem 75, 408, 60 s (100). Their keepers
         * as mejrs's live NPC list puts them. Guessed: which guards watch which stall (2004Scape's), the empty stall
         * (`market`), and the keepers' ban (the Stalls page's 20 minutes). Left out: Keldagrim's, Kourend's, Prifddinas's,
         * Civitas illa Fortis's, Port Roberts's, Rellekka's and Ape Atoll's stalls, and the Ardougne diary.
         */
        val ARDOUGNE_STALLS = listOf(
            Stall(BAKERY_STALL, EMPTY_STALL, 5, 160, 4, BAKERY_LOOT, ARDOUGNE_BAKER, STALL_REFUSE_TICKS, ARDOUGNE_GUARDS, setOf(ARDOUGNE_BAKER_2)),
            Stall(SILK_STALL, EMPTY_STALL, 20, 240, 8, SILK_LOOT, ARDOUGNE_SILK_MERCHANT, STALL_REFUSE_TICKS, ARDOUGNE_GUARDS_AND_KNIGHTS),
            Stall(FUR_STALL, EMPTY_STALL, 35, 450, 12, FUR_LOOT, ARDOUGNE_FUR_TRADER, STALL_REFUSE_TICKS, ARDOUGNE_GUARDS_AND_KNIGHTS),
            Stall(SILVER_STALL, EMPTY_STALL, 50, 2050, 32, SILVER_LOOT, ARDOUGNE_SILVER_MERCHANT, STALL_REFUSE_TICKS, ARDOUGNE_WATCH),
            Stall(SPICE_STALL, EMPTY_STALL, 65, 920, 10, SPICE_LOOT, ARDOUGNE_SPICE_SELLER, STALL_REFUSE_TICKS, ARDOUGNE_WATCH),
            Stall(GEM_STALL, EMPTY_STALL, 75, 4080, 100, GEM_LOOT, ARDOUGNE_GEM_MERCHANT, STALL_REFUSE_TICKS, ARDOUGNE_WATCH),
        )

        val STALLS: Map<Int, Stall> = (
            listOf(
                Stall(7053, EMPTY_STALL, 27, 100, 4, SEED_LOOT, OLIVIA, OLIVIA_REFUSE_TICKS, setOf(MARKET_GUARD)),
                Stall(14011, 14012, 22, 270, 8, WINE_LOOT, FORTUNATO, STALL_REFUSE_TICKS, setOf(MARKET_GUARD)),
                Stall(635, EMPTY_STALL, 5, 160, 4, TEA_LOOT, TEA_SELLER, STALL_REFUSE_TICKS, emptySet()),
            ) + ARDOUGNE_STALLS
            ).associateBy { it.loc }
    }
}
