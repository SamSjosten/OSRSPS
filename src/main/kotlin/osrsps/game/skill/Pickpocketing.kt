package osrsps.game.skill

import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.StatusEffects
import osrsps.game.content.HamHideout
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
    val CAUGHT_CITIZEN = line("caught_citizen", "a man, woman, guard or warrior who catches a thief: overhead")
    val CAUGHT_FARMER = line("caught_farmer", "a Master Farmer who catches a thief: overhead")
    val CAUGHT_HAM = line("caught_ham", "a H.A.M. member who catches a thief: overhead; words unsourced")
    val POUCH_OPEN = line("pouch_open", "Open on one coin pouch, with the coins it gave", "coins")
    val POUCH_OPEN_ALL = line("pouch_open_all", "Open-all on coin pouches, with the coins they gave", "coins")
    val POUCH_NO_ROOM = line("pouch_no_room", "opening a coin pouch with no room for the coins")
}

/** What a pickpocket gives: [min] to [max] of [obj] (-1 for nothing), weighted as [weight] among its table's others. */
class PocketLoot(val obj: Int, val min: Int, val max: Int, val weight: (farming: Int) -> Int)

/**
 * An NPC to pickpocket: [who] for the messages, the level, the XP in tenths, the [low] and [high] of its [SuccessRoll]
 * out of 256, the damage when caught ([damageMin] to [damage]), its [caught] line, and either a coin [pouch] holding
 * [coins] to [coinsMax] each or a [loot] table, which may hold a [lootPouch]. Pickpocket is its [op]; a catch stuns for
 * [stunTicks]; [concussions] hands a catch to the H.A.M. Hideout's count ([HamHideout.caught]). [extra] comes with every
 * success beside the pouch (a paladin's chaos runes, a watchman's bread). [heatDamage] hits on a success too, unless ice
 * gloves are worn (the TzHaar-Hur's heat).
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
    val op: Int = Pickpocketing.PICKPOCKET,
    val damageMin: Int = damage,
    val coinsMax: Int = coins,
    val lootPouch: Int? = null,
    val concussions: Boolean = false,
    val stunTicks: Int = StatusEffects.PICKPOCKET_TICKS,
    val extra: List<PocketLoot> = emptyList(),
    val heatDamage: Int = 0,
) {
    /** The coin pouch it gives, from its own pouch or its table. */
    val anyPouch: Int? get() = pouch ?: lootPouch
}

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
 *   [StatusEffects.PICKPOCKET_TICKS] with the thieving birds ([StatusEffects.stun]); the next attempt waits
 *   [ATTEMPT_TICKS] plus [FAIL_TICKS], and food eaten meanwhile adds its attack delay ([Player.nextPickpocketTick]).
 * - No pickpocketing in combat (hit by an NPC in the last [COMBAT_TICKS] ticks, 2004Scape's %lastcombat + 8), below the
 *   level, with no room for the loot, or while stunned.
 *
 * Guessed: the Master Farmer's herb seeds move linearly from their Farming 1 rates to their Farming 85 ones (the Wiki
 * gives the two ends and says they scale up to 85, not how); a next attempt waits [ATTEMPT_TICKS] (the Wiki's 2 ticks a
 * try); the stunned message; no animation for an NPC with no attack animation in npc-combat.json. Left out: Rocky (the
 * pet), the Ardougne diary and cloak, the thieving cape's boost, the Rogue outfit's extra loot, and
 * pickpocketing NPCs outside Misthalin's builds so far (other towns' guards and on; farmers came with Thieving 2).
 *
 * H.A.M. members (Misthalin piece 7 step 4a, the Wiki's H.A.M. Member page): level 15, 22.2 XP, 136/256 at 1 to 240/256 at
 * 99 (135 to 239), 1 to 3 damage, a 4-second stun ([HAM_STUN_TICKS]), their own table ([HAM_LOOT]) and coin pouch (1 to
 * 21 coins), Pickpocket as their op1; a catch counts toward being thrown out ([HamHideout.caught]) in place of the
 * stunned message (guessed).
 */
class Pickpocketing(private val world: World) {
    /** The gloves of silence and the dodgy necklace. */
    val gear = PickpocketGear(world)

    fun register() {
        gear.register()
        for (pocket in POCKETS) {
            for (npc in pocket.npcs) world.handlers.onNpc(npc, pocket.op) { op -> pick(op.npc ?: return@onNpc, pocket) }
        }
        for ((id, coins) in POUCH_COINS) {
            val pouch = world.objTypes[id] ?: continue
            option(pouch.iops, "Open")?.let { world.handlers.onObj(id, it) { op -> open(player, op.sub, id, coins, all = false) } }
            option(pouch.iops, "Open-all")?.let { world.handlers.onObj(id, it) { op -> open(player, op.sub, id, coins, all = true) } }
        }
    }

    private suspend fun ScriptContext.pick(npc: Npc, pocket: Pocket) {
        arriveDelay()
        val text = world.textFor(player)
        if (world.statusEffects.isStunned(player, tick)) return
        if (player.nextPickpocketTick > tick) return
        if (player.skills.currentLevel(Skill.THIEVING) < pocket.level) {
            player.message(text[PickpocketLines.LEVEL, "level" to pocket.level, "who" to pocket.who])
            return
        }
        if (player.attackedTick >= 0 && player.attackedTick + COMBAT_TICKS > tick) {
            player.message(text[PickpocketLines.IN_COMBAT])
            return
        }
        val pouch = pocket.anyPouch
        if (pouch != null && player.inventory.count(pouch) >= MAX_POUCHES) {
            player.message(text[PickpocketLines.POUCHES_FULL])
            return
        }
        val roomNeeded = (if (pocket.pouch == null || player.inventory.count(pocket.pouch) == 0L) 1 else 0) +
            pocket.extra.count { extra -> world.objTypes[extra.obj]?.stackable != true || player.inventory.count(extra.obj) == 0L }
        if (player.inventory.freeSlots < roomNeeded || !stacksFit(player, pocket)) {
            player.message(text[PickpocketLines.INVENTORY_FULL])
            return
        }
        player.nextPickpocketTick = tick + ATTEMPT_TICKS
        player.message(text[PickpocketLines.ATTEMPT, "who" to pocket.who])
        delay(1)
        // Hidden in that tick (World.hide), it is out of reach; nothing comes of the attempt.
        if (npc.hidden) return
        if (npc.dead) {
            player.message(text[PickpocketLines.DEAD])
            return
        }
        val thieving = player.skills.currentLevel(Skill.THIEVING)
        if (!SuccessRoll.roll(world.random, gear.low(player, pocket.low), gear.high(player, pocket.high), thieving)) {
            caught(npc, pocket)
            return
        }
        player.anim(ANIM_PICKPOCKET)
        player.skills.addTenths(Skill.THIEVING, pocket.xpTenths)
        give(player, pocket)
        player.message(text[PickpocketLines.SUCCESS, "who" to pocket.who])
        if (pocket.heatDamage > 0 && player.equipment[WearPos.HANDS]?.id != ICE_GLOVES) {
            // No source: the heat is no blow, so it starts no retaliation.
            world.meleeCombat.hitPlayer(player, null, pocket.heatDamage, tick)
        }
    }

    private fun ScriptContext.caught(npc: Npc, pocket: Pocket) {
        val text = world.textFor(player)
        npc.effects.say(text[pocket.caught])
        npc.face(player)
        world.combat.npcCombat[npc.type]?.anims?.attack?.takeIf { it >= 0 }?.let { npc.effects.anim(it) }
        player.message(text[PickpocketLines.FAIL, "who" to pocket.who])
        gear.glovesFailed(player)
        if (gear.necklaceSaves(player)) return
        val damage = world.random.between(pocket.damageMin, pocket.damage)
        world.statusEffects.stun(player, pocket.stunTicks, damage, npc, StatusEffects.THIEVING_SPOTANIM)
        player.nextPickpocketTick = tick + ATTEMPT_TICKS + FAIL_TICKS
        // A blow that kills ends it there: no concussion, and no teleport moving the grave off the death tile.
        if (player.dead) return
        if (pocket.concussions) HamHideout.caught(world, player) else player.message(text[PickpocketLines.STUNNED])
    }

    /**
     * Whether every stack a success could add to still has room: each [Pocket.extra], and the most of each table item
     * the player already holds a stack of. A stack can't pass [Inventory.MAX_STACK], and part of a reward would
     * otherwise be lost while the XP and the rest came anyway (live's inv_add refuses a whole add; guessed that the
     * pickpocket is refused before it starts, as with no room).
     */
    private fun stacksFit(player: Player, pocket: Pocket): Boolean {
        fun fits(obj: Int, count: Int): Boolean {
            if (world.objTypes[obj]?.stackable != true) return true
            return player.inventory.count(obj) + count <= Inventory.MAX_STACK
        }
        return pocket.extra.all { fits(it.obj, it.min) } && pocket.loot.all { it.obj < 0 || fits(it.obj, it.max) }
    }

    private fun give(player: Player, pocket: Pocket) {
        for (extra in pocket.extra) world.objTypes[extra.obj]?.let { player.inventory.add(it, extra.min) }
        if (pocket.pouch != null) {
            world.objTypes[pocket.pouch]?.let { player.inventory.add(it, 1) }
            return
        }
        val (obj, count) = pick(pocket, player.skills.currentLevel(Skill.FARMING))
        if (obj < 0) return
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

    /**
     * Open (one) or Open-all on the [pouch]es in [slot]: [coinsEach] coins each, rolled for each pouch. Open-all on pouches
     * of rolled coins opens at most [MAX_POUCHES] (all play can stack; a bigger stack is an admin's `::item`), so one
     * click never rolls more than that.
     */
    private fun open(player: Player, slot: Int, pouch: Int, coinsEach: IntRange, all: Boolean) {
        val held = player.inventory[slot]?.takeIf { it.id == pouch } ?: return
        val coins = world.objTypes[COINS] ?: return
        val text = world.textFor(player)
        val fixed = coinsEach.first == coinsEach.last
        val pouches = if (!all) 1 else if (fixed) held.count else held.count.coerceAtMost(MAX_POUCHES)
        val gain = if (fixed) {
            pouches.toLong() * coinsEach.first
        } else {
            (0 until pouches).sumOf { world.random.between(coinsEach.first, coinsEach.last).toLong() }
        }
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
        player.inventory.add(coins, gain.toInt())
        player.message(text[if (all) PickpocketLines.POUCH_OPEN_ALL else PickpocketLines.POUCH_OPEN, "coins" to gain])
    }

    /** Every pickpocket wait for [player] ends (`::reset`). */
    fun forgive(player: Player) {
        player.nextPickpocketTick = 0
    }

    companion object {
        /** Pickpocket is op 3 on every NPC here (the 241 cache). */
        const val PICKPOCKET = 3

        /** human_pickpocket. */
        const val ANIM_PICKPOCKET = 881

        const val COMBAT_TICKS = 8
        const val ATTEMPT_TICKS = 2

        /**
         * A failed pickpocket's next one waits this much longer than [ATTEMPT_TICKS]: the Wiki's eight ticks, its average
         * of 2 + 8(1 - p) ticks a try. The nine-tick stun only stops walking past that.
         */
        const val FAIL_TICKS = 8
        const val MAX_POUCHES = 28

        const val COINS = 995

        /** pickpocket_coin_pouch_citizen, the men's and women's. */
        const val CITIZEN_POUCH = 22521
        const val COINS_PER_CITIZEN_POUCH = 3

        /** pickpocket_coin_pouch_guard: 30 coins each (the Wiki's Guard page). */
        const val GUARD_POUCH = 22527
        const val COINS_PER_GUARD_POUCH = 30

        /** pickpocket_coin_pouch_warrior: 18 coins each (the Wiki's Warrior woman page). */
        const val WARRIOR_POUCH = 22524
        const val COINS_PER_WARRIOR_POUCH = 18

        /** Varrock's guards since November 2022: fai_varrock_guard02, its five variants and the captain's look. */
        val VARROCK_GUARDS = (11911..11917).toList()

        /** warrior_woman, Varrock Palace's two. */
        const val WARRIOR_WOMAN = 3260

        /** al_kharid_man and al_kharid_warrior, Al Kharid's. */
        const val AL_KHARID_MAN = 3261
        const val AL_KHARID_WARRIOR = 3292

        /** guard1, Edgeville's six. */
        const val EDGEVILLE_GUARD = 3254

        /** farmer1 to farmer3 and the November 2022 looks, farmer1_f to farmer3_f and farmer4. */
        val FARMERS = listOf(3114, 3243, 3244, 11918, 11919, 11920, 11921)

        /** pickpocket_coin_pouch_farmer: 9 coins each (the Wiki's Coin pouch page). */
        const val FARMER_POUCH = 22522
        const val COINS_PER_FARMER_POUCH = 9

        /** potato_seed. */
        const val POTATO_SEED = 5318

        /** Thieving 2 step T1b's NPCs (the 242 cache's types). */
        val ROGUES = listOf(526)
        val KNIGHTS_OF_ARDOUGNE = listOf(3297, 3300, 8854, 11902, 11936)
        val WATCHMEN = listOf(5420)
        val PALADINS = listOf(3293, 3294, 8853, 11901, 11930, 11931, 11932, 11933)
        val HEROES = listOf(3295, 11934, 11935)

        /** gnome, browclothedgnome, darkskinned_gnome. */
        val GNOME_MEN = listOf(6094, 6095, 6096)

        /** The two gnome women and three gnome children, whose pocket gives 133.5 XP to the men's 133.3. */
        val GNOME_WOMEN_AND_CHILDREN = listOf(6086, 6087, 6077, 6078, 6079)

        val GNOMES = GNOME_MEN + GNOME_WOMEN_AND_CHILDREN

        /** Warriors since November 2022 (warrior_woman's variants and warrior_man's), Ardougne's and the Fishing Guild's. */
        val WARRIORS_2022 = listOf(11925, 11926, 11927, 11928, 11929)

        const val ROGUE_POUCH = 22525
        const val KNIGHT_POUCH = 22531
        const val WATCHMAN_POUCH = 22533
        const val PALADIN_POUCH = 22535
        const val GNOME_POUCH = 22536
        const val HERO_POUCH = 22537

        /** The Wiki's 4.8-second stun (knights, paladins) and 6-second one (heroes), at 0.6 seconds a tick. */
        const val STUN_4_8_SECONDS = 8
        const val STUN_6_SECONDS = 10

        /** Thieving 2 step T1c's NPCs (the 242 cache's types). dorgesh_male_1 to 9 and dorgesh_female_1 to 9. */
        val CAVE_GOBLINS = (2268..2285).toList()

        /** Rellekka's Agnar, Freidir, Borrokar, Lanzig, Pontak, Lensa, Jennella, Sassilik and Inga. */
        val FREMENNIK_CITIZENS = listOf(3937, 3938, 3939, 3940, 3941, 3943, 3944, 3945, 3946)

        /** The Bandit Camp's bandits (fourdiamonds_sword_bandit_1 and _free); Pollnivneach's wait for blackjacks. */
        val DESERT_BANDITS = listOf(690, 695)

        /** varlamore_wealthy_citizen_a to d. */
        val WEALTHY_CITIZENS = listOf(13302, 13303, 13304, 13305)

        /** Darkmeyer's thirty vyres, each a named type. */
        val VYRES = (9685..9714).toList()

        /** Lletya's Goreu, Arvel and Mawrth, and Prifddinas's citizens (prif_citizen_*), each a named type. */
        val ELVES = listOf(5297, 5299, 5300) + (9054..9090).toList() + (9106..9117).toList()

        /** Mor Ul Rek's tzhaar_hur_city1 to 6. */
        val TZHAAR_HURS = (7682..7687).toList()

        const val CAVE_GOBLIN_POUCH = 22526
        const val FREMENNIK_POUCH = 22528
        const val DESERT_BANDIT_POUCH = 22530
        const val WEALTHY_CITIZEN_POUCH = 28822
        const val VYRE_POUCH = 24703
        const val ELF_POUCH = 22538

        /** The Wiki's 4-second stun (wealthy citizens), at 0.6 seconds a tick, rounded up. */
        const val STUN_4_SECONDS = 7

        /** What a TzHaar-Hur's heat does on a success without [ICE_GLOVES], as its caught blow. */
        const val TZHAAR_HEAT = 4
        const val ICE_GLOVES = 1580
        const val TOKKUL = 6529

        /** The wealthy citizens' Pickpocket is their first op. */
        const val WEALTHY_PICKPOCKET = 1

        /** The Wiki's Cave goblin table, out of 20: the pouch 7, each other item 1. */
        val CAVE_GOBLIN_LOOT = listOf(
            line(CAVE_GOBLIN_POUCH, 7), line(10964, 1), line(10963, 1), line(10965, 1), line(10962, 1), line(10961, 1), line(10960, 1),
            line(4548, 1), line(10981, 1, 2), line(440, 1, 4), line(4537, 1), line(1939, 1), line(590, 1), line(596, 1),
            // pouch, bat shish, coated frogs' legs, fingers, frogburger, frogspawn gumbo, green gloop soup, bullseye lantern,
            // 1 to 2 cave goblin wire, 1 to 4 iron ore, oil lantern, swamp tar, tinderbox, unlit torch
        )

        /** The Wiki's Bandit (Bandit Camp) table, out of 7. */
        val DESERT_BANDIT_LOOT = listOf(line(DESERT_BANDIT_POUCH, 5), line(179, 1), line(1523, 1)) // pouch, antipoison(1), lockpick

        /** The Wiki's Wealthy citizen table, out of 85: the 1/85 left (an easy clue, left out) gives nothing. */
        val WEALTHY_CITIZEN_LOOT = listOf(line(WEALTHY_CITIZEN_POUCH, 79), line(29325, 5), line(-1, 1)) // pouch, house keys

        /** The Wiki's Vyre table, out of 132. */
        val VYRE_LOOT = listOf(
            line(VYRE_POUCH, 109), line(560, 8, 2, 2), line(565, 2, 4, 4), line(24774, 6), line(1619, 5), line(1601, 1), line(24785, 1),
            // pouch, 2 death runes, 4 blood runes, blood pint, uncut ruby, diamond, cooked mystery meat
        )

        /** The Wiki's Elf table, out of 128; Prifddinas's crystal shards and enhanced seeds left out. */
        val ELF_LOOT = listOf(
            line(ELF_POUCH, 105), line(560, 8, 2, 2), line(561, 5, 3, 3), line(1993, 6), line(1601, 1), line(569, 2), line(444, 1),
            // pouch, 2 death runes, 3 nature runes, jug of wine, diamond, fire orb, gold ore
        )

        /** The Wiki's TzHaar-Hur table, out of 195. */
        val TZHAAR_HUR_LOOT = listOf(
            line(TOKKUL, 182, 7, 3), line(1623, 5), line(1621, 4), line(1619, 3), line(1617, 1),
            // 3 to 7 tokkul, uncut sapphire, emerald, ruby, diamond
        )

        private fun line(obj: Int, weight: Int, max: Int = 1, min: Int = 1) = PocketLoot(obj, min, max) { weight }

        /** The Wiki's Rogue table, out of 144. Rocky left out. */
        val ROGUE_LOOT = listOf(
            line(ROGUE_POUCH, 123), line(556, 9, 8, 8), line(1993, 6), line(1523, 5), line(1219, 1), // pouch, air runes, jug of wine, lockpick, iron dagger(p)
        )

        /** The Wiki's Gnome table, out of 128; the medium clue (1/150) left out with Treasure Trails. */
        val GNOME_LOOT = listOf(
            line(52, 56, 4, 2), line(GNOME_POUCH, 30), line(2150, 24), line(444, 8), line(557, 5), line(2162, 3), line(569, 2),
            // arrow shafts 2 to 4, pouch, swamp toad, gold ore, earth rune, king worm, fire orb
        )

        /** The Wiki's Hero table, out of 128; the elite clue (1/900) left out with Treasure Trails. */
        val HERO_LOOT = listOf(
            line(HERO_POUCH, 105), line(560, 8, 2, 2), line(1993, 6), line(565, 5), line(1601, 1), line(569, 2), line(444, 1),
            // pouch, 2 death runes, jug of wine, blood rune, diamond, fire orb, gold ore
        )

        /** The Wiki's Farmer pickpocket table: the coin pouch 123/128, a potato seed 5/128. Rocky left out. */
        val FARMER_LOOT = listOf(PocketLoot(FARMER_POUCH, 1, 1) { 123 }, PocketLoot(POTATO_SEED, 1, 1) { 5 })

        val MASTER_FARMERS = listOf(5730, 5731)

        /** favour_male_ham_civilian and favour_female_ham_civilian: Pickpocket is their op1, not op3. */
        val HAM_MEMBERS = listOf(2540, 2541)
        const val HAM_PICKPOCKET = 1

        /** pickpocket_coin_pouch_ham: 1 to 21 coins each (the Wiki's Coin pouch page). */
        const val HAM_POUCH = 22523

        /** The Wiki's 4-second stun for a H.A.M. member's catch, rounded to whole ticks (6.7 to 7; the rounding guessed). */
        const val HAM_STUN_TICKS = 7
        val HAM_POUCH_COINS = 1..21

        /** One line of the H.A.M. table, out of [HAM_SLOTS]. */
        private fun ham(obj: Int, weight: Int, max: Int = 1, min: Int = 1) = PocketLoot(obj, min, max) { weight }

        /** The H.A.M. table's slots: the Wiki's 102nds times 11, so the herbs' 561sts are whole. */
        const val HAM_SLOTS = 1122

        /**
         * The Wiki's H.A.M. Member pickpocket table (read 2026-11-10), each x/102 as 11x/1122 and each herb's x/561 as
         * 2x/1122. The easy clue (1/50) is left out (no Treasure Trails yet) and counted as nothing, as the Wiki says a
         * clue that can't be given is; nothing is 2/102 with it. Left out: Rocky, the higher robe rate during Death to
         * the Dorgeshuun.
         */
        val HAM_LOOT = listOf(
            ham(4298, 11), ham(4300, 11), ham(4302, 11), ham(4304, 11), ham(4306, 11), ham(4308, 11), ham(4310, 11), // H.A.M. clothing
            ham(882, 33, 13), // bronze_arrow 1-13
            ham(1351, 33), ham(1205, 33), ham(1265, 33), // bronze axe, dagger, pickaxe
            ham(1349, 33), ham(1203, 33), ham(1267, 33), // iron axe, dagger, pickaxe
            ham(1129, 33), // leather body
            ham(886, 22, 13), // steel_arrow 1-13
            ham(1353, 22), ham(1207, 22), ham(1269, 22), // steel axe, dagger, pickaxe
            ham(688, 44), // buttons
            ham(HAM_POUCH, 187), // coin pouch
            ham(314, 33, 7), // feather 1-7
            ham(946, 22), ham(1733, 22), ham(321, 22), ham(2138, 22), ham(590, 22), ham(1625, 22), ham(453, 22), ham(440, 22),
            ham(1511, 33), ham(1739, 33), // logs, cowhide
            ham(1734, 33, 10), // thread 1-10
            ham(697, 44), ham(686, 44), // damaged armour, rusty sword
            ham(1627, 22), // uncut jade
            ham(199, 12), ham(201, 6), ham(203, 4), // grimy guam, marrentill, tarromin
            ham(-1, 22), // nothing (and the left-out clue)
        )

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

        /**
         * Men and women: level 1, 8 XP, 1 damage. Master Farmer: level 38, 43 XP, 3 damage. Varrock step 13 (the Wiki's
         * Pickpocketing, Guard and Warrior woman pages, read 2026-11-03): guards level 40, 46.8 XP, 2 damage; warrior
         * women level 25, 26 XP, 2 damage. The warrior's 100 to 240 is worked back from the Wiki's chart (135/256 at 25,
         * 241/256 at 99). The guard's 50 to 240 is GUESSED: the Wiki's chart wasn't readable, so it is the pair with the
         * others' 240 that reaches 100% at 95 with the Ardougne diary's 10%, the Pickpocketing page's 100% level; the
         * Guard page's chart, read 2026-11-10, gives the same 50 to 240.
         * Guessed too: that guards and warrior women say the townsfolk's caught line, and the lower case "guard" and
         * "warrior" in the messages (the Wiki gives neither).
         *
         * Thieving 2 step T1a (the Wiki's Thieving, Farmer, Al Kharid warrior, Guard and Coin pouch pages, read
         * 2026-11-10): Al Kharid's man (3261) is a man; Edgeville's guards (guard1, 3254) are guards. Farmers: level
         * 10, 14.5 XP, 150 to 240 (the Wiki's chart), 1 damage, a farmer's coin pouch of 9 coins 123/128 or a potato
         * seed 5/128. Al Kharid warriors: the warrior's numbers (Mod Ash: the same code), 100 to 240 as their chart.
         * Guessed: that farmers say the townsfolk's caught line, and "farmer" and "Al Kharid warrior" in the messages.
         *
         * Thieving 2 step T1b (the Wiki's Rogue, Knight of Ardougne, Watchman, Paladin, Gnome, Gnome woman, Gnome
         * child, Hero, Warrior (Thieving) and Coin pouch pages, read 2026-11-10; each pair is its chart's "Normal" line):
         * rogues 32, 36.5 XP, 75 to 240, 2 damage, a table out of 144 with a pouch of 25 to 40; Knights of Ardougne 55,
         * 84.3 XP, 50 to 240, 3 damage, a pouch of 50; watchmen 65, 137.5 XP, 15 to 160, 3 damage, a pouch of 60 and a
         * bread; paladins 70, 131.8 XP, 40 to 170, 3 damage, a pouch of 80 and 2 chaos runes; gnomes (men, women and
         * children alike, but for the women's and children's 133.5 XP) 75, 133.3 XP, 43 to 175, 1 damage, a table out of 128 with a pouch of 300; heroes 80, 163.3 XP,
         * 39 to 160, 3 damage, a table out of 128 with a pouch of 200 to 300. The Wiki's stun times, 4.8 seconds for
         * knights and paladins and 6 for heroes, are 8 and 10 ticks; its 5 seconds stay the Stun page's 9 ticks.
         * Guessed: their caught line (the townsfolk's), the words in the messages, and "gnome" in the women's and
         * children's messages too. Left out: the clue scrolls, Rocky, the Ardougne diary.
         *
         * Thieving 2 step T1c (the Wiki's Cave goblin (Dorgesh-Kaan), Fremennik citizen, Bandit (Bandit Camp), Wealthy
         * citizen, Vyre, Elf (Thieving), TzHaar-Hur and Coin pouch pages, read 2026-11-10): cave goblins 36, 40 XP, 1
         * damage, a table out of 20 with a pouch of 10 to 50; Fremennik citizens 45, 65 XP, 2 damage, a pouch of 40;
         * desert bandits 53, 79.4 XP, 50 to 240, 3 damage, a table out of 7 with a pouch of 30; wealthy citizens 50, 96
         * XP, 35 to 200, 3 damage, a table out of 85 with a pouch of 85 and house keys, Pickpocket their first op, a
         * 4-second stun (7 ticks); vyres 82, 306.9 XP, 8 to 128, 5 damage, a table out of 132 with a pouch of 230 to
         * 315; elves 85, 353.3 XP, 6 to 100, 5 damage, a table out of 128 with a pouch of 280 to 350; TzHaar-Hur 90,
         * 103.4 XP, -200 to 200, 4 damage, a table out of 195 (tokkul, uncut gems) and 4 more on every success without
         * ice gloves. Vyres', elves' and TzHaar-Hur's 6 seconds are 10 ticks. Where a Wiki table says coins, the Coin
         * pouch page gives that NPC a pouch, so a pouch it is. GUESSED: the cave goblins' and Fremennik citizens' charts
         * (the Wiki has none: 75 to 240 and 65 to 240, between their neighbours'), every caught line, the names in the
         * messages. Left out: the urchins' distractions, Prifddinas's crystal shards and seeds, the quests and the fire
         * cape that gate the areas, the clue scrolls, Rocky.
         */
        val POCKETS = listOf(
            Pocket(Townsfolk.MEN + AL_KHARID_MAN, "man", 1, 80, 180, 240, 1, PickpocketLines.CAUGHT_CITIZEN, CITIZEN_POUCH, COINS_PER_CITIZEN_POUCH),
            Pocket(Townsfolk.WOMEN, "woman", 1, 80, 180, 240, 1, PickpocketLines.CAUGHT_CITIZEN, CITIZEN_POUCH, COINS_PER_CITIZEN_POUCH),
            Pocket(MASTER_FARMERS, "Master Farmer", 38, 430, 90, 240, 3, PickpocketLines.CAUGHT_FARMER, loot = MASTER_FARMER_LOOT),
            Pocket(VARROCK_GUARDS + EDGEVILLE_GUARD, "guard", 40, 468, 50, 240, 2, PickpocketLines.CAUGHT_CITIZEN, GUARD_POUCH, COINS_PER_GUARD_POUCH),
            Pocket(listOf(WARRIOR_WOMAN) + WARRIORS_2022, "warrior", 25, 260, 100, 240, 2, PickpocketLines.CAUGHT_CITIZEN, WARRIOR_POUCH, COINS_PER_WARRIOR_POUCH),
            Pocket(listOf(AL_KHARID_WARRIOR), "Al Kharid warrior", 25, 260, 100, 240, 2, PickpocketLines.CAUGHT_CITIZEN, WARRIOR_POUCH, COINS_PER_WARRIOR_POUCH),
            Pocket(FARMERS, "farmer", 10, 145, 150, 240, 1, PickpocketLines.CAUGHT_CITIZEN, loot = FARMER_LOOT, coins = COINS_PER_FARMER_POUCH, lootPouch = FARMER_POUCH),
            Pocket(ROGUES, "rogue", 32, 365, 75, 240, 2, PickpocketLines.CAUGHT_CITIZEN, loot = ROGUE_LOOT, coins = 25, coinsMax = 40, lootPouch = ROGUE_POUCH),
            Pocket(
                KNIGHTS_OF_ARDOUGNE, "Knight of Ardougne", 55, 843, 50, 240, 3, PickpocketLines.CAUGHT_CITIZEN, KNIGHT_POUCH, 50,
                stunTicks = STUN_4_8_SECONDS,
            ),
            Pocket(WATCHMEN, "watchman", 65, 1375, 15, 160, 3, PickpocketLines.CAUGHT_CITIZEN, WATCHMAN_POUCH, 60, extra = listOf(line(2309, 1))),
            Pocket(
                PALADINS, "paladin", 70, 1318, 40, 170, 3, PickpocketLines.CAUGHT_CITIZEN, PALADIN_POUCH, 80, stunTicks = STUN_4_8_SECONDS,
                extra = listOf(line(562, 1, 2, 2)),
            ),
            Pocket(GNOME_MEN, "gnome", 75, 1333, 43, 175, 1, PickpocketLines.CAUGHT_CITIZEN, loot = GNOME_LOOT, coins = 300, lootPouch = GNOME_POUCH),
            Pocket(
                GNOME_WOMEN_AND_CHILDREN, "gnome", 75, 1335, 43, 175, 1, PickpocketLines.CAUGHT_CITIZEN, loot = GNOME_LOOT, coins = 300,
                lootPouch = GNOME_POUCH,
            ),
            Pocket(
                HEROES, "hero", 80, 1633, 39, 160, 3, PickpocketLines.CAUGHT_CITIZEN, loot = HERO_LOOT, coins = 200, coinsMax = 300, lootPouch = HERO_POUCH,
                stunTicks = STUN_6_SECONDS,
            ),
            Pocket(
                CAVE_GOBLINS, "cave goblin", 36, 400, 75, 240, 1, PickpocketLines.CAUGHT_CITIZEN, loot = CAVE_GOBLIN_LOOT, coins = 10, coinsMax = 50,
                lootPouch = CAVE_GOBLIN_POUCH,
            ),
            Pocket(FREMENNIK_CITIZENS, "Fremennik citizen", 45, 650, 65, 240, 2, PickpocketLines.CAUGHT_CITIZEN, FREMENNIK_POUCH, 40),
            Pocket(
                DESERT_BANDITS, "bandit", 53, 794, 50, 240, 3, PickpocketLines.CAUGHT_CITIZEN, loot = DESERT_BANDIT_LOOT, coins = 30,
                lootPouch = DESERT_BANDIT_POUCH,
            ),
            Pocket(
                WEALTHY_CITIZENS, "wealthy citizen", 50, 960, 35, 200, 3, PickpocketLines.CAUGHT_CITIZEN, loot = WEALTHY_CITIZEN_LOOT, coins = 85,
                lootPouch = WEALTHY_CITIZEN_POUCH, op = WEALTHY_PICKPOCKET, stunTicks = STUN_4_SECONDS,
            ),
            Pocket(
                VYRES, "vyre", 82, 3069, 8, 128, 5, PickpocketLines.CAUGHT_CITIZEN, loot = VYRE_LOOT, coins = 230, coinsMax = 315,
                lootPouch = VYRE_POUCH, stunTicks = STUN_6_SECONDS,
            ),
            Pocket(
                ELVES, "elf", 85, 3533, 6, 100, 5, PickpocketLines.CAUGHT_CITIZEN, loot = ELF_LOOT, coins = 280, coinsMax = 350,
                lootPouch = ELF_POUCH, stunTicks = STUN_6_SECONDS,
            ),
            Pocket(
                TZHAAR_HURS, "TzHaar-Hur", 90, 1034, -200, 200, 4, PickpocketLines.CAUGHT_CITIZEN, loot = TZHAAR_HUR_LOOT,
                stunTicks = STUN_6_SECONDS, heatDamage = TZHAAR_HEAT,
            ),
            Pocket(
                HAM_MEMBERS, "H.A.M. member", 15, 222, 135, 239, 3, PickpocketLines.CAUGHT_HAM, loot = HAM_LOOT, op = HAM_PICKPOCKET,
                damageMin = 1, lootPouch = HAM_POUCH, concussions = true, stunTicks = HAM_STUN_TICKS,
            ),
        )

        /** Each coin pouch and the coins it holds. */
        val POUCH_COINS: Map<Int, IntRange> =
            POCKETS.mapNotNull { pocket -> pocket.anyPouch?.takeIf { pocket.coins > 0 }?.let { it to pocket.coins..pocket.coinsMax } }.toMap() +
                (HAM_POUCH to HAM_POUCH_COINS)
    }
}
