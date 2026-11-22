package osrsps.game.content

import java.util.WeakHashMap
import kotlin.math.abs
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.Foods
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.encounter.heldTarget
import osrsps.game.npc.Npc
import osrsps.map.Tile

/** Demonic Brutus's lines and the abyssal potato's (keys only; the words are in data/dialogue.txt). */
object DemonicLines : Lines("demonic_brutus", "Demonic Brutus") {
    val HARVEST = line("harvest", "Pluck on the Scar's growth: the potato harvested (OSRS Wiki, Abyssal potato)")
    val EAT = line("eat", "Eat on the abyssal potato (OSRS Wiki, Abyssal potato)")
    val FEED_TITLE = line("feed_title", "Release with an abyssal potato held: the menu's title (no source yet)")
    val FEED_YES = line("feed_yes", "Release with a potato: feed it to him (no source yet)")
    val FEED_NO = line("feed_no", "Release with a potato: don't (no source yet)")
    val MOO = line("moo", "Demonic Brutus's overhead as the fight starts (Transcript:Demonic Brutus, On start)")
    val DYING = line("dying", "Demonic Brutus's overhead as he dies (Transcript:Demonic Brutus, Upon death)")
    val KILL_COUNT = line("kill_count", "a kill of Demonic Brutus: the kill count (words not recorded yet)", "count")
}

/**
 * The abyssal potato and Demonic Brutus (Brutus step 6; the OSRS Wiki's Demonic Brutus, Demonic Brutus/Strategies,
 * Abyssal potato and Transcript:Demonic Brutus, read 2026-11-22; ids, animations and the growth from the 242 cache).
 *
 * - **The potato**: Pluck on the growth north of the fire in the Scar (`cowboss_demonic_potato_multi` 60770, which shows
 *   `cowboss_demonic_potato` 60772 at the end of Desert Treasure II, done as every quest is). Eat: "+1 Strength".
 * - **Feeding it**: Release on the pen's gate with a potato held asks first; fed, the potato is gone and the field
 *   holds Demonic Brutus (`cowboss_hardmode` 15628, 750 hitpoints) instead of Brutus, for that one fight.
 * - **His basic attack**: crush, max 4, "can hit through Protect from Melee" (npc-combat.json).
 * - **His specials**, after three basic attacks, the first at random and then the other in turn: **Charge**, "Brutus
 *   will spawn orthogonally across from you and charge", and **Slam**, he "spawns diagonally across from the player,
 *   stomping the area in front of him"; each time "three ghosts will repeat the attack in rapid succession, going
 *   clockwise, starting with the real Brutus". Each bull's hit is 45 to 56, through any prayer, and lands unless
 *   dodged. The first special starts west or south of the player, and the start swaps west to east (or south to north)
 *   after every Charge.
 *
 * GUESSED until recorded (each marked where it is used): the ticks ([CHARGE_WINDOW], [SLAM_WINDOW] from a bull's
 * appearing to its hit, [GHOST_GAP] between bulls); a charger appearing [CHARGE_DISTANCE] tiles off the player along
 * a three-wide lane centred on them and running as far past them; a slam's bull at the player's corner, its stomp the
 * L of tiles round that corner (the player's tile in it) and its own corner tile, as Brutus's; each bull aimed where
 * the player stands as it appears; the Slam's corners starting clockwise from the Charge's side; his ghosts gone the
 * tick after their hit; a potato from every Pluck; the potato eaten with food's 3-tick delay; the menu and its words.
 * Step 7 brings the faster and anticlockwise specials, his second phase and its orbs, the slippers and the scoreboard's
 * Demonic page.
 */
object DemonicBrutus {
    const val BOSS = 15628
    const val GHOST = 15629
    const val POTATO = 33118
    const val EAT = 1
    const val GROWTH_MULTI = 60770
    const val GROWTH = 60772
    const val PLUCK = 1
    const val EAT_DELAY = Foods.DEFAULT_DELAY

    const val ATTACK_SPEED = 5
    const val BASICS = 3
    const val SPECIAL_MIN = 45
    const val SPECIAL_MAX = 56

    // Guessed: Brutus's own windows for the real bull, and two ticks between bulls.
    const val CHARGE_WINDOW = 3
    const val SLAM_WINDOW = 4
    const val GHOST_GAP = 2
    const val CHARGE_DISTANCE = 4
    const val BULLS = 4

    const val ANIM_ATTACK = BrutusFight.ANIM_ATTACK
    const val ANIM_CHARGE = 13778
    const val ANIM_GHOST_CHARGE = 13779
    const val ANIM_SLAM = 13791
    const val ANIM_GHOST_SLAM = 13792
    const val ANIM_FADE_IN = 13793
    const val ANIM_GHOST_FADE_IN = 13794
    val SPOT_STOMP = BrutusFight.SPOT_STOMP

    /** The sides a charger comes from, clockwise from the west; the corners a slam comes from, clockwise from the south-west. */
    val SIDES = listOf(-1 to 0, 0 to 1, 1 to 0, 0 to -1)
    val CORNERS = listOf(-1 to -1, -1 to 1, 1 to 1, 1 to -1)

    enum class Special { CHARGE, SLAM }

    /** A bull of a special: where it stood, and the player's tile it was aimed at. */
    data class Bull(val special: Special, val at: Tile, val aim: Tile, val index: Int)

    class Fight {
        var basicsLeft = BASICS
        var last: Special? = null
        var busyUntil = -1L
        /** The side the next Charge starts from, an index of [SIDES]; -1 until the first special picks west or south. */
        var start = -1
        var clockwise = true
        /** Every bull so far, for tests. */
        val bulls = ArrayList<Bull>()
        /** Whether he has said his "Moo", as he first takes the player on. */
        var mooed = false
        val specials = ArrayList<Special>()
    }

    private val fights = WeakHashMap<Npc, Fight>()

    fun fight(boss: Npc): Fight = fights.getOrPut(boss) { Fight() }

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[BOSS] ?: return null
        return EncounterDef(
            "demonic_brutus", type,
            listOf(
                PhaseDef(
                    "fight", cycle = listOf(CycleStep(1) { tick() }),
                    onStart = { boss.unleashed = true },
                ),
            ),
            scriptedAttacks = true,
        )
    }

    /** The order the bulls of a special come from, starting at [first] of four, clockwise or not. */
    fun order(first: Int, clockwise: Boolean): List<Int> = (0 until BULLS).map { ((first + if (clockwise) it else -it) % 4 + 4) % 4 }

    /** A charger's south-west tile from [side] of the player at [px], [pz], and where its run ends. */
    fun chargeFrom(px: Int, pz: Int, side: Pair<Int, Int>): Pair<Tile, Tile> {
        val (sx, sz) = side
        fun start(p: Int, s: Int) = if (s < 0) p - CHARGE_DISTANCE - 2 else p + CHARGE_DISTANCE
        fun end(p: Int, s: Int) = if (s < 0) p + CHARGE_DISTANCE else p - CHARGE_DISTANCE - 2
        return if (sx != 0) Tile(start(px, sx), pz - 1) to Tile(end(px, sx), pz - 1) else Tile(px - 1, start(pz, sz)) to Tile(px - 1, end(pz, sz))
    }

    /** The tiles a charge from [from] to [to] runs over: every tile either footprint, or anything between, covers. */
    fun chargeTiles(from: Tile, to: Tile, size: Int = 3): List<Tile> {
        val tiles = ArrayList<Tile>()
        for (x in minOf(from.x, to.x) until maxOf(from.x, to.x) + size) for (z in minOf(from.z, to.z) until maxOf(from.z, to.z) + size) tiles += Tile(x, z)
        return tiles
    }

    /** A slam's bull's south-west tile at [corner] of the player, its corner touching theirs diagonally. */
    fun slamFrom(px: Int, pz: Int, corner: Pair<Int, Int>, size: Int = 3): Tile =
        Tile(if (corner.first < 0) px - size else px + 1, if (corner.second < 0) pz - size else pz + 1)

    /** The tiles a slam from [corner] lands on: the L round the player's corner and the bull's corner tile. */
    fun slamTiles(at: Tile, corner: Pair<Int, Int>, size: Int = 3): List<Tile> = BrutusFight.stompTiles(at.x, at.z, size, -corner.first to -corner.second)

    private fun EncounterContext.tick() {
        val fight = fight(boss)
        if (world.tick < fight.busyUntil) return
        val target = heldTarget() ?: nearest() ?: return
        boss.face(target)
        if (!fight.mooed) {
            // Guessed: "On start" as he first takes the player on, when they can see it.
            fight.mooed = true
            boss.effects.say(world.dialogueText[DemonicLines.MOO])
        }
        if (!world.meleeCombat.reaches(boss, target)) {
            world.npcs.follow(boss, target)
            return
        }
        world.npcs.stopFollowing(boss)
        if (world.tick < boss.nextAttackTick) return
        if (fight.basicsLeft > 0) {
            world.meleeCombat.swingAt(boss, target, world.tick, ANIM_ATTACK)
            fight.basicsLeft--
            log("butts ${target.username}")
            return
        }
        if (fight.start < 0) fight.start = if (roll(0, 1) == 0) 0 else 3
        val pick = when (fight.last) {
            null -> if (roll(0, 1) == 0) Special.CHARGE else Special.SLAM
            Special.CHARGE -> Special.SLAM
            Special.SLAM -> Special.CHARGE
        }
        special(fight, target, pick)
        fight.last = pick
        fight.specials += pick
        fight.basicsLeft = BASICS
    }

    private fun EncounterContext.nearest(): Player? {
        val pick = players.filter { !it.dead }.minByOrNull { abs(it.x - boss.x) + abs(it.z - boss.z) } ?: return null
        if (boss.target !== pick) {
            boss.target = null
            world.meleeCombat.aggress(boss, pick, world.tick)
        }
        return pick
    }

    /** One special: the real bull, then three ghosts, each [GHOST_GAP] ticks after the last. */
    private fun EncounterContext.special(fight: Fight, target: Player, special: Special) {
        world.npcs.stopFollowing(boss)
        val window = if (special == Special.CHARGE) CHARGE_WINDOW else SLAM_WINDOW
        // Guessed: the Slam's first corner the one clockwise from the Charge's side (west's is the north-west).
        val first = if (special == Special.CHARGE) fight.start else (fight.start + 1) % 4
        val order = order(first, fight.clockwise)
        order.forEachIndexed { nth, index ->
            if (nth == 0) bull(fight, target, special, index, window, null) else after(nth * GHOST_GAP) {
                if (!boss.dead) bull(fight, target, special, index, window, world.npcTypes[GHOST])
            }
        }
        if (special == Special.CHARGE) fight.start = (fight.start + 2) % 4
        val end = world.tick + (BULLS - 1) * GHOST_GAP + window + 1
        fight.busyUntil = end
        boss.nextAttackTick = end + ATTACK_SPEED
        log("${special.name.lowercase()} from ${order.joinToString { index -> if (special == Special.CHARGE) sideName(SIDES[index]) else sideName(CORNERS[index]) }} at ${target.username}")
    }

    /** One bull, the real one when [ghost] is null: it appears, and [window] ticks later its charge or slam lands. */
    private fun EncounterContext.bull(fight: Fight, target: Player, special: Special, index: Int, window: Int, ghost: osrsps.game.NpcType?) {
        if (target.dead) return
        val (at, tiles, end) = when (special) {
            Special.CHARGE -> {
                val (from, to) = chargeFrom(target.x, target.z, SIDES[index])
                Triple(from, chargeTiles(from, to), to)
            }
            Special.SLAM -> {
                val from = slamFrom(target.x, target.z, CORNERS[index])
                Triple(from, slamTiles(from, CORNERS[index]), from)
            }
        }
        val npc = if (ghost == null) {
            world.npcs.teleport(boss, at.x, at.z)
            boss.effects.anim(ANIM_FADE_IN)
            boss
        } else {
            spawnAdd(ghost, at.x, at.z)?.also { it.effects.anim(ANIM_GHOST_FADE_IN) }
        }
        npc?.face(target)
        fight.bulls += Bull(special, at, Tile(target.x, target.z), index)
        val spot = if (special == Special.SLAM) SPOT_STOMP[fight.bulls.size % SPOT_STOMP.size] else -1
        hazard(tiles, -1, window, 1, SPECIAL_MIN, SPECIAL_MAX, spot)
        after(window) {
            if (npc == null || npc.dead && npc === boss) return@after
            npc.effects.anim(
                when {
                    special == Special.CHARGE && npc === boss -> ANIM_CHARGE
                    special == Special.CHARGE -> ANIM_GHOST_CHARGE
                    npc === boss -> ANIM_SLAM
                    else -> ANIM_GHOST_SLAM
                },
            )
            if (end != at) world.npcs.teleport(npc, end.x, end.z)
        }
        if (npc != null && npc !== boss) after(window + 1) { world.removeNpc(npc) }
    }

    private fun sideName(side: Pair<Int, Int>): String =
        (if (side.second > 0) "north" else if (side.second < 0) "south" else "") + (if (side.first > 0) "east" else if (side.first < 0) "west" else "")

    /** Pluck on the growth: a potato. */
    suspend fun osrsps.game.script.ScriptContext.pluck(world: World) {
        arriveDelay()
        val type = world.objTypes[POTATO] ?: return
        if (player.inventory.add(type, 1) < 1) return world.message(player, GameLines.NO_SPACE)
        world.message(player, DemonicLines.HARVEST)
    }

    /** Eat on the potato: +1 Strength, never lowering a higher boost. */
    fun eat(world: World, player: Player, slot: Int) {
        if (player.dead || player.inventory[slot]?.id != POTATO || world.tick < player.foodDelayUntil) return
        player.foodDelayUntil = world.tick + EAT_DELAY
        player.inventory[slot] = null
        player.anim(Foods.EAT_ANIM)
        val boosted = player.skills.baseLevel(Skill.STRENGTH) + 1
        if (player.skills.currentLevel(Skill.STRENGTH) < boosted) player.skills.setCurrentLevel(Skill.STRENGTH, boosted)
        world.message(player, DemonicLines.EAT)
    }
}

fun registerDemonicBrutus(world: World, pens: BrutusPens) {
    with(DemonicBrutus) {
        for (growth in listOf(GROWTH_MULTI, GROWTH)) world.handlers.onLoc(growth, PLUCK) { pluck(world) }
        world.handlers.onObj(POTATO, EAT) { op -> eat(world, player, op.sub) }
        val def = def(world) ?: return
        world.npcScripts.onTick(listOf(BOSS)) { npc ->
            if (npc.dead || world.encounters.all.any { it.boss === npc && !it.ended }) return@onTick
            val run = pens.runAt(npc.x, npc.z) ?: return@onTick
            world.encounters.start(def, npc, { x, z -> pens.runAt(x, z) === run })
        }
        world.npcScripts.onDeath(listOf(BOSS)) { boss ->
            boss.effects.say(world.dialogueText[DemonicLines.DYING])
            val run = pens.runAt(boss.x, boss.z) ?: return@onDeath
            val player = world.onlinePlayers.firstOrNull { it.saveKey == run.owner && !it.loggedOut } ?: return@onDeath
            val kc = Brutus.HARDMODE_KILL_COUNT_VARP
            if (kc < player.vars.types.varpCount) {
                player.vars[kc] = player.vars[kc] + 1
                world.message(player, DemonicLines.KILL_COUNT, "count" to player.vars[kc])
            }
        }
        // Guessed: his drops on his tile nearest the player, as Brutus's.
        world.dropTiles[BOSS] = { boss, killer ->
            if (killer == null) Tile(boss.x, boss.z) else Brutus.nearestTile(boss.x, boss.z, boss.type.size, killer.x, killer.z)
        }
    }
}
