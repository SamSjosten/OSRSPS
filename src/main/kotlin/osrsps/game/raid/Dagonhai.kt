package osrsps.game.raid

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.combat.AttackType
import osrsps.game.combat.CombatSpell
import osrsps.game.combat.Element
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.combat.Poison
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc
import osrsps.game.npc.Npcs
import osrsps.game.zone.Projectile
import osrsps.map.Tile

/** What the Dagon'hai shout over their heads (keys only; the words are in data/dialogue.txt on the server). */
object DagonhaiLines : Lines("tfa_dagonhai", "The_Fractured_Archive/Strategies") {
    val CHANNEL = Element.entries.associateWith { line("channel_${it.name.lowercase()}", "one of the trio as their ${it.name.lowercase()} channel into the cauldron begins (the guides')") }
    val SEARCH = line("search", "a Monk as he searches his books for a tome, before he throws it (the guides')")
    val BLOCKED = line("blocked", "a Monk as a player blocks his tome (the guides')")
    val INTERRUPTED = line("interrupted", "the Elder as a channel into the cauldron is stopped (the Wiki's transcript; inferred: whoever's it was)")
}

/**
 * The Dagon'hai Elder and his two Monks (raid step 23), the fourth row's west room: three 2 by 2 casters who share one
 * pool of hitpoints round a ritual cauldron, channelling the elements into it and throwing their gods' tomes at it,
 * with imps from demonic rifts each time their shield is broken.
 *
 * - The fight's own NPC is the cauldron (`tfa_dagonhai_focus`, [CAULDRON]), never fought: its hitpoints are the ritual's
 *   stability, [STABILITY] lost at each of the [BREAKS] shield breaks. The trio stand round it ([MEMBERS]).
 * - **The shared pool** ([pool]): the three are each [POOL] hitpoints, and a hit on any of them comes off all three. A
 *   stunned one takes [STUN_PERCENT]% more. Their Defence drains are each their own. The pool emptied is a **shield
 *   break** ([shieldBreak]): the ritual's stability drops, two demonic rifts open ([rift]), each letting out up to
 *   [RIFT_IMPS] imps, one every [RIFT_EVERY] ticks, and the pool fills again. A player next to a rift keeps its imps in
 *   and drains it, [RIFT_DRAIN] a tick each, losing [RIFT_PRAYER] prayer a tick for it; drained, it closes.
 * - **The Elder's attacks** ([attack]): every [ATTACK_SPEED] ticks a ranged shot or a magic surge at the player he is
 *   set on, a fresh one every [FOCUS_ATTACKS] attacks, and the same at each player next to them.
 * - **Channels** ([channel]): every [CHANNEL_EVERY] ticks one of the three not busy shouts and channels an element into
 *   the cauldron for [CHANNEL_TICKS] ticks. A spell of that element landing on them (not a splash) stops it and stuns
 *   them for [STUN_TICKS] ticks; a channel that runs its course adds [CHARGE_STEP]% to the ritual's charge. At 100% the
 *   trio are **empowered** ([empowered]): the Elder's surges come in twos and no prayer stops them, till the next break.
 * - **Tomes** ([search], [tome]): every [TOME_EVERY] ticks a Monk not busy searches his books for [SEARCH_TICKS] ticks
 *   and throws a tome of Bandos, Zamorak or Saradomin at the cauldron, landing [TOME_FLIGHT] ticks on. A gust on the
 *   floor ([interceptTile]) marks where to stand to block it. Blocked: Bandos hurts the blocker [BANDOS_BLOCKER] and the
 *   rest of the party [BANDOS_WAVE_MIN] to [BANDOS_WAVE_MAX]; Zamorak hurts the blocker [ZAMORAK_BLOCKER], burns them
 *   and drops fire [FIRE_RADIUS] tiles round them; Saradomin marks a blue tile under each player and a yellow beside
 *   them, lightning hurting on the blue and healing and restoring prayer on the yellow. Unblocked, the cauldron takes
 *   it: the charge goes up [CHARGE_STEP]%, and Bandos hurts everyone [BANDOS_HIT_MIN] to [BANDOS_HIT_MAX], Zamorak
 *   drops fire round everyone, Saradomin heals the pool [SARADOMIN_HEAL].
 * - **The fifth break** ([explode]): the cauldron bursts. A Monk picked at random dies, and the Elder and the other Monk
 *   are left as their weakened forms on [WEAKENED_HITPOINTS] hitpoints each, stunned [STUN_TICKS] ticks; killing both
 *   does the room.
 *
 * From the OSRS Wiki (Dagon'hai Elder, Dagon'hai Monk, Imp (Dagon'hai Trio), Demonic rift; read 2026-11-11): the three's
 * stats, bonuses and size, 640 hitpoints, magic max hit 30 and ranged 27, speed 6; the imp's 44 hitpoints; a rift
 * letting out a set number of imps, standing next to it stopping them and draining it. From the guides (osrsbestinslot,
 * bald.gg, osrstoolkit, ironman.guide; read 2026-11-11): the shared pool and per-member drains, 15% more on a stunned
 * one; the Elder's grey ranged shot and magic surge on one player, splashing onto those next to them; the cauldron's
 * channels of 25% each, the matching standard spell stopping one and a splash not; empowered at full, surges through
 * prayer two at a time; the tomes, their warning and gust tiles, a Monk's shout on a block, and what each god's does
 * blocked and not; each break dropping stability by 20% and opening two portals of three imps, standing beside one
 * closing it and draining prayer; after the fifth the cauldron exploding, one Monk dying and the other two on 7
 * hitpoints. From the cache (241): every id below by name, the weakened forms' 7 hitpoints, the cauldron's 1,000, the
 * rift's 20; the room's map: the floor of square 63,87 (4054-4075 x 5589-5609), walled off from the way in east of it.
 *
 * No capture or recording of this fight exists: everything about its timings, places and damage past the Wiki's max
 * hits is guessed (each also in the README): where the cauldron and the three stand; every interval and duration above;
 * the focus changing every four attacks and a random style each attack; the splash being a full shot of its own; the
 * empowered surges' timing; the channel's length and the stun's; which of the three channel (any) and search (the
 * Monks); the gust tile two steps out from the Monk towards the cauldron; every damage and heal figure of the tomes; the
 * fire's 7 by 7; the charge and empowerment going back to nothing at each break; where the rifts open, their imps one
 * every 5 ticks, a rift closing after its third; the stability shown as the cauldron's hitpoints; the weakened two still
 * attacking. Not in: the cauldron moving and its knock-back, player attacks on imps always hitting their max, the
 * ritual's progress bar (the charge is in `::raid`), the shared health bar sent to the two not hit, the Book of Life
 * and Death and the Guthix spellbook in the room.
 */
object Dagonhai {
    /** What `::raid` and the raid flow know the fight by. */
    const val NAME = "dagonhai"

    /** `tfa_dagonhai_focus`: the ritual cauldron, the fight's anchor, never fought. Its 1,000 hitpoints are the ritual's stability. */
    const val CAULDRON = 16649
    const val CAULDRON_SIZE = 3

    /** `tfa_dagonhai_leader`, `tfa_dagonhai_minion_1` and `_2`, and their `_unshielded` forms on 7 hitpoints. */
    const val ELDER = 16643
    const val ELDER_WEAKENED = 16644
    const val MONK_1 = 16645
    const val MONK_1_WEAKENED = 16646
    const val MONK_2 = 16647
    const val MONK_2_WEAKENED = 16648

    /** `tfa_dagonhai_demon` (an Imp) and `tfa_dagonhai_rift` (a Demonic rift). */
    const val IMP = 16650
    const val RIFT = 16651

    /** The three's size (the Wiki's 2 by 2). */
    const val SIZE = 2

    /** The cauldron's south-west tile, the middle of the room's floor. Guessed. */
    const val X = 4063
    const val Z = 5598

    /** The room's floor, the arena; the way in is east of its wall (cache map, square 63,87). */
    const val ARENA_MIN_X = 4054
    const val ARENA_MAX_X = 4075
    const val ARENA_MIN_Z = 5589
    const val ARENA_MAX_Z = 5609

    /** Where the room's transporter puts a player: the east edge of the floor. Guessed, as the other rooms' are the cache's arrival tiles. */
    const val ARENA_ENTRY_X = 4074
    const val ARENA_ENTRY_Z = 5599

    /** A member of the trio: its type, weakened form, south-west tile as an offset from the cauldron's, and its animations' number. */
    class Member(val type: Int, val weakened: Int, val dx: Int, val dz: Int, val number: Int, val monk: Boolean)

    /** The Elder west of the cauldron, the Monks north and south of it. Guessed. */
    val MEMBERS = listOf(
        Member(ELDER, ELDER_WEAKENED, -5, 0, 1, monk = false),
        Member(MONK_1, MONK_1_WEAKENED, 0, 6, 2, monk = true),
        Member(MONK_2, MONK_2_WEAKENED, 0, -5, 3, monk = true),
    )

    /** The Wiki's hitpoints: the shared pool. Not scaled to the party (the Wiki gives one figure). */
    const val POOL = 640

    /** Shield breaks to the cauldron's burst, and the stability each takes from its 1,000 (the guides' 20%). */
    const val BREAKS = 5
    const val STABILITY = 200

    /** The Elder's attack speed (the Wiki's) and max hits (the Wiki's). */
    const val ATTACK_SPEED = 6
    const val MAGIC_MAX = 30
    const val RANGED_MAX = 27

    /** He picks a fresh player to focus every this many attacks. Guessed. */
    const val FOCUS_ATTACKS = 4

    /** An empowered surge's second, this many ticks after the first; each lands this many ticks after it's cast. Guessed. */
    const val SURGE_GAP = 1
    const val SURGE_FLIGHT = 2

    /** A channel: one begins every this many ticks, the first this many in, lasting this long. Guessed. */
    const val CHANNEL_EVERY = 20
    const val FIRST_CHANNEL = 12
    const val CHANNEL_TICKS = 8

    /** What a channel run its course or an unblocked tome adds to the ritual's charge, in percent (the guides' 25%). */
    const val CHARGE_STEP = 25

    /** A stopped channel stuns for this many ticks (guessed), the stunned one taking this much more, in percent (the guides'). */
    const val STUN_TICKS = 5
    const val STUN_PERCENT = 15

    /** A tome: a search every this many ticks, the first this many in; the search's length, the tome's flight. Guessed. */
    const val TOME_EVERY = 30
    const val FIRST_TOME = 22
    const val SEARCH_TICKS = 3
    const val TOME_FLIGHT = 3

    /** The gust: this many tiles out from the Monk's edge towards the cauldron. Guessed. */
    const val GUST_STEPS = 2

    /** The tomes' figures. The guides': Bandos's 55 on the blocker, Zamorak's about 25, Saradomin's heal of about 50. The rest guessed. */
    const val BANDOS_BLOCKER = 55
    const val BANDOS_WAVE_MIN = 10
    const val BANDOS_WAVE_MAX = 20
    const val BANDOS_HIT_MIN = 30
    const val BANDOS_HIT_MAX = 40
    const val ZAMORAK_BLOCKER = 25
    const val BURN_HITS = 4
    const val BURN_DAMAGE = 3
    const val BURN_EVERY = 3
    const val FIRE_RADIUS = 3
    const val FIRE_MIN = 8
    const val FIRE_MAX = 15
    const val FIRE_WARN = 2
    const val SARADOMIN_HEAL = 50
    const val LIGHTNING_WARN = 4
    const val LIGHTNING_DAMAGE = 20
    const val LIGHTNING_HEAL = 10

    /** A break's rifts: where they may open (offsets from the cauldron's south-west tile). Guessed. */
    val RIFT_SPOTS = listOf(Tile(-7, 5), Tile(7, 5), Tile(-7, -5), Tile(7, -5))

    /** A rift lets out this many imps, one every this many ticks; a player next to it drains this much a tick, losing this much prayer. Guessed. */
    const val RIFT_IMPS = 3
    const val RIFT_EVERY = 5
    const val RIFT_DRAIN = 2
    const val RIFT_PRAYER = 1

    /** The weakened forms' hitpoints, the cache's. */
    const val WEAKENED_HITPOINTS = 7

    /** Animations, by name from the cache. */
    const val ANIM_RANGED = 14593
    const val ANIM_MAGIC = 14597
    const val ANIM_SURGE = 14604
    fun animChannel(number: Int) = 14607 + 5 * (number - 1)
    fun animChannelEnd(number: Int) = animChannel(number) + 2
    fun animInterrupted(number: Int) = animChannel(number) + 4
    fun animSearch(monk: Int) = if (monk == 2) 14622 else 14630
    fun animSearchEnd(monk: Int) = animSearch(monk) + 1
    fun animDeath(number: Int) = 14651 + number
    const val ANIM_CAULDRON_ZAMORAK = 14657
    const val ANIM_CAULDRON_SARADOMIN = 14659
    const val ANIM_CAULDRON_BANDOS = 14661
    const val ANIM_CAULDRON_GUTHIX = 14678
    const val ANIM_IMP_SPAWN = 13837

    /** Spotanims, by name from the cache. */
    const val SPOT_RANGED = 4071
    const val SPOT_RANGED_HIT = 4072
    const val SPOT_SURGE = 4078
    const val SPOT_SARADOMIN_DAMAGE_MARK = 4063
    const val SPOT_SARADOMIN_HEAL_MARK = 4064
    const val SPOT_SARADOMIN_HEAL = 4066
    const val SPOT_SARADOMIN_LIGHTNING = 4068
    const val SPOT_SARADOMIN_HEAL_LIGHTNING = 4069
    const val SPOT_ZAMORAK_FIRE = 4149
    const val SPOT_BANDOS_WAVE = 4156
    const val SPOT_CAULDRON_BURST = 4161
    const val SPOT_IMP_SPAWN = 3735

    /** A channel's spotanim on the channeller, by their number and element; the one as it ends is the next id. */
    fun spotChannel(number: Int, element: Element): Int = 4079 + 8 * (number - 1) + 2 * ELEMENT_ORDER.indexOf(element)

    private val ELEMENT_ORDER = listOf(Element.FIRE, Element.WATER, Element.AIR, Element.EARTH)

    /** The gods' tomes. */
    enum class God(val throwAnim: Int, val projectile: Int, val impact: Int, val floor: Int) {
        ZAMORAK(14624, 4113, 4116, 4119),
        BANDOS(14625, 4115, 4118, 4135),
        SARADOMIN(14626, 4114, 4117, 4127),
        ;

        /** Its throw for Monk [number] (2 or 3): the first Monk's `_01`, the second's `_02`. */
        fun throwAnim(number: Int) = if (number == 2) throwAnim else throwAnim + 8
    }

    /** The eight directions of the gust's floor spotanims, in the cache's order. */
    private val DIRECTIONS = listOf(Tile(0, 1), Tile(1, 1), Tile(1, 0), Tile(1, -1), Tile(0, -1), Tile(-1, -1), Tile(-1, 0), Tile(-1, 1))

    val RANGED = NpcRangedAttack(anim = ANIM_RANGED, projectile = SPOT_RANGED, impact = SPOT_RANGED_HIT, maxHit = RANGED_MAX)
    val MAGIC = NpcRangedAttack(anim = ANIM_MAGIC, projectile = SPOT_SURGE, maxHit = MAGIC_MAX)

    /** The fight, or null when the cache hasn't the cauldron and the three (a test world's). */
    fun def(types: NpcTypes): EncounterDef? {
        val cauldron = types[CAULDRON] ?: return null
        val members = MEMBERS.map { types[it.type] ?: return null }
        val imp = types[IMP]
        val rift = types[RIFT]
        return EncounterDef(
            NAME, cauldron,
            listOf(
                PhaseDef(
                    "ritual",
                    cycle = listOf(CycleStep(1) { tick(imp, rift) }),
                    onStart = { begin(members) },
                ),
            ),
            scriptedAttacks = true,
            status = {
                val names = listOf("Elder", "Monk", "Monk")
                val alive = state.members.mapIndexed { i, npc -> "${names[i]} ${hp(npc)}" }.joinToString(", ")
                "pool ${state.pool}/$POOL, $alive; stability ${stability()}/${BREAKS * STABILITY}, charge ${state.charge}%" +
                    (if (state.empowered) " (empowered)" else "") + ", ${adds.count { it.type.id == IMP }} imps, ${adds.count { it.type.id == RIFT }} rifts"
            },
            held = { state.members.mapNotNull { alive(it) } },
            // The three on the pool, either form; the cauldron is the boss, the imps and rifts its adds.
            bosses = MEMBERS.flatMap { listOf(it.type, it.weakened) }.toSet(),
            // On the pool, what the pool lost counts; weakened, each on their own hitpoints, a hit counts as dealt.
            passedOn = MEMBERS.map { it.type }.toSet(),
        )
    }

    /** Whether ([x], [z]) is on the floor of the room of a cauldron whose south-west tile began on ([spawnX], [spawnZ]). */
    fun inArena(spawnX: Int, spawnZ: Int, x: Int, z: Int): Boolean =
        x - spawnX in ARENA_MIN_X - X..ARENA_MAX_X - X && z - spawnZ in ARENA_MIN_Z - Z..ARENA_MAX_Z - Z

    /**
     * The tile to stand on to block a tome thrown from a Monk whose south-west tile is ([fromX], [fromZ]) at a cauldron
     * whose south-west tile is ([toX], [toZ]): [GUST_STEPS] steps from the Monk's middle towards the cauldron's, past his
     * edge. With the direction's index in the cache's eight.
     */
    fun interceptTile(fromX: Int, fromZ: Int, toX: Int, toZ: Int): Pair<Tile, Int> {
        val dx = (toX + CAULDRON_SIZE / 2 - (fromX + SIZE / 2)).sign
        val dz = (toZ + CAULDRON_SIZE / 2 - (fromZ + SIZE / 2)).sign
        // Out of the Monk's 2 by 2 on the side it faces, then the steps.
        val startX = if (dx > 0) fromX + SIZE - 1 else if (dx < 0) fromX else fromX
        val startZ = if (dz > 0) fromZ + SIZE - 1 else if (dz < 0) fromZ else fromZ
        val direction = DIRECTIONS.indexOf(Tile(dx, dz)).coerceAtLeast(0)
        return Tile(startX + dx * GUST_STEPS, startZ + dz * GUST_STEPS) to direction
    }

    /** What the pool loses when [lost] comes off a member, stunned or not. */
    fun poolLoss(lost: Int, stunned: Boolean): Int = if (stunned) lost + lost * STUN_PERCENT / 100 else lost

    /** A channel under way: its element, and the world tick it ends on. */
    private class Channel(val element: Element, val ends: Long) {
        /** Who stopped it with a spell of its element, as their spell landed; null while it runs. */
        var stoppedBy: String? = null
    }

    /** A thrown tome: its god, the gust tile, and the world tick it lands on. */
    private class Tome(val god: God, val monk: Npc, val tile: Tile, val lands: Long)

    /** A rift: its imps let out so far and the world tick of the next. */
    private class Rift(var imps: Int, var next: Long)

    /** What a fight keeps between ticks, by its cauldron. */
    private class State {
        /** The three, as [MEMBERS] lists them, and each one's hitpoints as last set. */
        val members = ArrayList<Npc>()
        val seen = WeakHashMap<Npc, Int>()

        var pool = POOL
        var breaks = 0
        var charge = 0
        var empowered = false

        /** The Elder's next attack (world tick), whom he is set on and how many attacks on them so far. */
        var nextAttack = 0L
        var focus: Player? = null
        var focusAttacks = 0

        /** Each member's channel, stun (until when) and search. */
        val channels = WeakHashMap<Npc, Channel>()
        val stunnedUntil = WeakHashMap<Npc, Long>()
        val searching = WeakHashMap<Npc, God>()
        var nextChannel = 0L
        var nextTome = 0L
        val tomes = ArrayList<Tome>()

        /** In the order they opened, so their imps come out (and take their rolls) the same way every run. */
        val rifts = LinkedHashMap<Npc, Rift>()

        /** The fifth break's burst has come; the two left, as its weakened forms. */
        var exploded = false
        val survivors = ArrayList<Npc>()
    }

    private val states = WeakHashMap<Npc, State>()

    /**
     * Each member's fight, so a spell landing on one finds it ([spellLanded]). Weakly held both ways: a [State] holds
     * its members, so a strong value would keep each key, and with it every fight, forever (Codex review on #634).
     */
    private val byMember = WeakHashMap<Npc, WeakReference<State>>()

    private val EncounterContext.state: State get() = states.getOrPut(boss) { State() }

    private fun EncounterContext.inWorld(npc: Npc): Boolean = world.npcs[npc.index] === npc

    /**
     * A hit from the room itself (no NPC) still puts [player] in combat, holding a logout or a teleport cast, as the
     * encounter's hazards do (Codex review on #634).
     */
    private fun EncounterContext.inCombat(player: Player) {
        if (!player.dead) player.attackedTick = world.tick
    }

    private fun EncounterContext.alive(npc: Npc?): Npc? = npc?.takeIf { !it.dead && inWorld(it) }

    private fun hp(npc: Npc): String = "${if (npc.dead) 0 else npc.hitpoints} hp"

    private fun EncounterContext.stability(): Int = (BREAKS - state.breaks) * STABILITY

    private fun EncounterContext.text(line: Line): String = world.dialogueText.get(line)

    private fun EncounterContext.number(npc: Npc): Int = MEMBERS.first { it.type == npc.spawn.type.id }.number

    private fun EncounterContext.stunned(npc: Npc): Boolean = world.tick < (state.stunnedUntil[npc] ?: 0L)

    private fun EncounterContext.busy(npc: Npc): Boolean = npc in state.channels || npc in state.searching || stunned(npc)

    /**
     * A spell of [player]'s landed on [npc], [hit] or splashed. If [npc] is one of the trio channelling and the spell's
     * element is the channel's, and it hit, the channel stops (the fight's next tick says so).
     */
    fun spellLanded(player: Player, npc: Npc, spell: CombatSpell, hit: Boolean) {
        val state = byMember[npc]?.get() ?: return
        val channel = state.channels[npc] ?: return
        if (hit && spell.element == channel.element && channel.stoppedBy == null) channel.stoppedBy = player.username
    }

    /** The fight begins: the three round the cauldron, all on the pool. */
    private fun EncounterContext.begin(types: List<NpcType>) {
        boss.unleashed = true
        world.npcs.stopFollowing(boss)
        // Its hitpoints are the stability, which no hit changes (it can't be fought).
        boss.holdsAtZero = true
        for ((i, member) in MEMBERS.withIndex()) {
            val npc = spawnAdd(types[i], boss.x + member.dx, boss.z + member.dz) ?: continue
            npc.scriptedAttacks = true
            npc.unleashed = true
            npc.holdsAtZero = true
            npc.hitpoints = POOL
            state.members += npc
            state.seen[npc] = POOL
            byMember[npc] = WeakReference(state)
        }
        state.nextAttack = world.tick + ATTACK_SPEED
        state.nextChannel = world.tick + FIRST_CHANNEL
        state.nextTome = world.tick + FIRST_TOME
        log("the cauldron at ${boss.x},${boss.z}; ${state.members.size} of the trio round it on $POOL")
    }

    /** One tick of the fight. */
    private fun EncounterContext.tick(imp: NpcType?, rift: NpcType?) {
        // One that couldn't be spawned (no NPC slot): nothing happens, and the raid takes the fight back (ArchiveRaids).
        if (state.members.size < MEMBERS.size) return
        if (state.exploded) {
            survivors()
            return
        }
        pool()
        if (state.pool <= 0) shieldBreak(rift)
        if (state.exploded) return
        channels()
        if (world.tick >= state.nextChannel) channel()
        tomes()
        if (world.tick >= state.nextTome) search()
        if (world.tick >= state.nextAttack) attack()
        rifts(imp)
        boss.hitpoints = stability().coerceAtLeast(0)
    }

    /**
     * The pool: what each of the three lost since last tick comes off it (15% more for a stunned one), and all three are
     * set to it.
     */
    private fun EncounterContext.pool() {
        var lost = 0
        val each = LinkedHashMap<Npc, Int>()
        for (npc in state.members) {
            val before = state.seen[npc] ?: continue
            val now = if (npc.dead) 0 else npc.hitpoints
            if (now < before) poolLoss(before - now, stunned(npc)).also { each[npc] = it; lost += it }
        }
        if (lost > 0) {
            // What the pool really lost, shared over the three by what each took: each was hit as if it had the
            // whole pool, so in the tick it breaks their losses add up to more than it had (exploit review R28A-2).
            val taken = minOf(lost, state.pool)
            var left = taken
            for ((npc, loss) in each) {
                val share = if (npc === each.keys.last()) left else (taken.toLong() * loss / lost).toInt()
                world.archiveRaids.passOn(npc, share)
                left -= share
            }
            state.pool = (state.pool - lost).coerceAtLeast(0)
            log("the pool takes $lost, ${state.pool} left")
        }
        setPool()
    }

    private fun EncounterContext.setPool() {
        for (npc in state.members) {
            if (npc.dead) continue
            npc.hitpoints = state.pool
            state.seen[npc] = state.pool
        }
    }

    /**
     * The pool emptied: the ritual's stability drops; after the fifth the cauldron bursts. Otherwise two rifts open, the
     * pool fills again, and the charge and any channel go (guessed).
     */
    private fun EncounterContext.shieldBreak(rift: NpcType?) {
        state.breaks++
        log("shield break ${state.breaks}; stability ${stability()}")
        for (npc in state.members) state.channels.remove(npc)
        state.charge = 0
        state.empowered = false
        if (state.breaks >= BREAKS) {
            explode()
            return
        }
        state.pool = POOL
        setPool()
        if (rift != null) {
            val spots = RIFT_SPOTS.indices.toMutableList()
            repeat(2) {
                val spot = RIFT_SPOTS[spots.removeAt(roll(0, spots.size - 1))]
                val npc = spawnAdd(rift, boss.x + spot.x, boss.z + spot.z) ?: return@repeat
                npc.scriptedAttacks = true
                state.rifts[npc] = Rift(0, world.tick + RIFT_EVERY)
                log("a rift opens at ${npc.x},${npc.z}")
            }
        }
    }

    /** The cauldron bursts: a Monk dies, the other two are left weakened on 7 hitpoints, stunned. */
    private fun EncounterContext.explode() {
        state.exploded = true
        world.zones.mapAnim(boss.x + CAULDRON_SIZE / 2, boss.z + CAULDRON_SIZE / 2, boss.level, SPOT_CAULDRON_BURST)
        boss.hitpoints = 0
        val monks = state.members.filter { m -> MEMBERS.first { it.type == m.spawn.type.id }.monk }
        val dies = monks[roll(0, monks.size - 1)]
        for (npc in state.members) {
            state.searching.remove(npc)
            if (npc === dies) {
                npc.holdsAtZero = false
                npc.hitpoints = 1
                world.meleeCombat.hit(npc, null, 1, world.tick, finishing = true)
                log("the cauldron bursts: a Monk dies")
                continue
            }
            val member = MEMBERS.first { it.type == npc.spawn.type.id }
            world.npcTypes[member.weakened]?.let { world.npcs.transform(npc, it, Npcs.FOREVER) }
            npc.holdsAtZero = false
            npc.scaledHitpoints = WEAKENED_HITPOINTS
            npc.hitpoints = WEAKENED_HITPOINTS
            state.stunnedUntil[npc] = world.tick + STUN_TICKS
            state.survivors += npc
        }
        state.tomes.clear()
        state.nextAttack = world.tick + STUN_TICKS + ATTACK_SPEED
        log("${state.survivors.size} left on $WEAKENED_HITPOINTS, stunned")
    }

    /** After the burst: the weakened Elder still attacks (guessed); once both are gone the cauldron goes, which does the room. */
    private fun EncounterContext.survivors() {
        if (state.survivors.none { inWorld(it) }) {
            log("the trio gone")
            boss.dead = true
            world.removeNpc(boss)
            return
        }
        val elder = state.survivors.firstOrNull { it.spawn.type.id == ELDER }?.let { alive(it) }
        if (elder != null && !stunned(elder) && world.tick >= state.nextAttack) attack()
    }

    /** The Elder's attack at the player he is set on, and the same at each player next to them; empowered, his surge comes twice through prayer. */
    private fun EncounterContext.attack() {
        state.nextAttack = world.tick + ATTACK_SPEED
        val elder = alive(state.members.firstOrNull()) ?: return
        if (busy(elder)) return
        val focus = state.focus?.takeIf { f -> players.any { it === f } && state.focusAttacks < FOCUS_ATTACKS } ?: randomPlayer()?.also {
            state.focus = it
            state.focusAttacks = 0
        } ?: return
        state.focusAttacks++
        val magic = roll(0, 1) == 1
        val near = players.filter { it !== focus && max(abs(it.x - focus.x), abs(it.z - focus.z)) <= 1 }
        if (magic && state.empowered) {
            elder.face(focus)
            elder.effects.anim(ANIM_SURGE)
            for (player in listOf(focus) + near) {
                for (n in 0..1) {
                    after(SURGE_GAP * n + SURGE_FLIGHT) {
                        if (players.any { it === player }) {
                            val dealt = world.meleeCombat.hitPlayer(player, elder, roll(0, MAGIC_MAX), world.tick)
                            log("an empowered surge hits ${player.username} for $dealt")
                        }
                    }
                    surge(elder, player, n)
                }
            }
            log("two empowered surges at ${focus.username}")
            return
        }
        for (player in listOf(focus) + near) {
            world.meleeCombat.fireAt(elder, player, world.tick, if (magic) MAGIC else RANGED, if (magic) AttackType.MAGIC else AttackType.RANGED)
        }
        log("${if (magic) "a surge" else "a shot"} at ${focus.username}" + if (near.isEmpty()) "" else ", and at ${near.joinToString { it.username }} beside them")
    }

    private fun EncounterContext.surge(from: Npc, to: Player, n: Int) {
        world.zones.projectile(
            Projectile(
                id = SPOT_SURGE, fromX = from.x + SIZE / 2, fromZ = from.z + SIZE / 2, level = from.level, toX = to.x, toZ = to.z,
                startHeight = 80, endHeight = 30, startTime = n * SURGE_GAP * CYCLES_PER_TICK, endTime = (n * SURGE_GAP + SURGE_FLIGHT) * CYCLES_PER_TICK,
                angle = 16, progress = 64, sourceIndex = from.index + 1, targetIndex = -(to.index + 1),
            ),
        )
    }

    /** One of the three not busy begins a channel of an element picked at random. */
    private fun EncounterContext.channel() {
        state.nextChannel = world.tick + CHANNEL_EVERY
        val free = state.members.filter { alive(it) != null && !busy(it) }
        if (free.isEmpty()) return
        val npc = free[roll(0, free.size - 1)]
        val element = Element.entries[roll(0, Element.entries.size - 1)]
        state.channels[npc] = Channel(element, world.tick + CHANNEL_TICKS)
        val number = number(npc)
        npc.effects.anim(animChannel(number))
        npc.effects.spotanim(0, spotChannel(number, element), 0)
        npc.effects.say(text(DagonhaiLines.CHANNEL.getValue(element)))
        log("member $number channels ${element.name.lowercase()}")
    }

    /** Channels stopped by a spell, or run their course into the cauldron's charge. */
    private fun EncounterContext.channels() {
        for (npc in state.members) {
            val channel = state.channels[npc] ?: continue
            val number = number(npc)
            val by = channel.stoppedBy
            if (by != null) {
                state.channels.remove(npc)
                state.stunnedUntil[npc] = world.tick + STUN_TICKS
                npc.effects.anim(animInterrupted(number))
                state.members.firstOrNull { it.spawn.type.id == ELDER }?.let { alive(it) }?.effects?.say(text(DagonhaiLines.INTERRUPTED))
                log("$by stops member $number's ${channel.element.name.lowercase()} channel; stunned")
                continue
            }
            if (world.tick < channel.ends) continue
            state.channels.remove(npc)
            npc.effects.anim(animChannelEnd(number))
            npc.effects.spotanim(0, spotChannel(number, channel.element) + 1, 0)
            log("member $number's ${channel.element.name.lowercase()} channel goes into the cauldron")
            charge()
        }
    }

    /** The charge up a step; at 100% the trio are empowered. */
    private fun EncounterContext.charge() {
        state.charge = (state.charge + CHARGE_STEP).coerceAtMost(100)
        log("charge ${state.charge}%")
        if (state.charge >= 100 && !state.empowered) {
            state.empowered = true
            boss.effects.anim(ANIM_CAULDRON_GUTHIX)
            log("the trio are empowered")
        }
    }

    /** A Monk not busy searches his books for a tome of a god picked at random; the gust shows where to block it. */
    private fun EncounterContext.search(forced: God? = null) {
        state.nextTome = world.tick + TOME_EVERY
        val monks = state.members.filter { m -> MEMBERS.first { it.type == m.spawn.type.id }.monk && alive(m) != null }
        // A search asked for ([searchFor]) takes a busy Monk when none is free.
        val free = monks.filter { !busy(it) }.ifEmpty { if (forced != null) monks.filter { it !in state.searching } else emptyList() }
        if (free.isEmpty()) return
        val monk = free[roll(0, free.size - 1)]
        val god = forced ?: God.entries[roll(0, God.entries.size - 1)]
        val number = number(monk)
        state.searching[monk] = god
        monk.effects.anim(animSearch(number))
        monk.effects.say(text(DagonhaiLines.SEARCH))
        val (tile, direction) = interceptTile(monk.x, monk.z, boss.x, boss.z)
        world.zones.mapAnim(tile.x, tile.z, boss.level, god.floor + direction)
        log("member $number searches for a ${god.name.lowercase()} tome; the gust at ${tile.x},${tile.z}")
        after(SEARCH_TICKS) {
            if (state.searching.remove(monk) == null || alive(monk) == null || state.exploded) return@after
            monk.effects.anim(god.throwAnim(number))
            world.zones.projectile(
                Projectile(
                    id = god.projectile, fromX = monk.x + SIZE / 2, fromZ = monk.z + SIZE / 2, level = monk.level,
                    toX = boss.x + CAULDRON_SIZE / 2, toZ = boss.z + CAULDRON_SIZE / 2, startHeight = 60, endHeight = 40,
                    startTime = 0, endTime = TOME_FLIGHT * CYCLES_PER_TICK, angle = 16, progress = 64,
                    sourceIndex = monk.index + 1, targetIndex = boss.index + 1,
                ),
            )
            state.tomes += Tome(god, monk, tile, world.tick + TOME_FLIGHT)
            log("member $number throws the ${god.name.lowercase()} tome")
        }
    }

    /**
     * A Monk of [fight] searches for a tome of [god] now, as the fight's own searches do with a god picked at random:
     * how the headless tests throw each god's tome in turn. Call it with the world paused.
     */
    internal fun searchFor(fight: EncounterContext, god: God) = fight.search(god)

    /** Tomes landing: on whoever stands on the gust, or in the cauldron. */
    private fun EncounterContext.tomes() {
        for (tome in state.tomes.toList()) {
            if (world.tick < tome.lands) continue
            state.tomes -= tome
            val blocker = players.firstOrNull { it.x == tome.tile.x && it.z == tome.tile.z && !it.dead }
            if (blocker != null) blocked(tome, blocker) else unblocked(tome)
        }
    }

    private fun EncounterContext.blocked(tome: Tome, blocker: Player) {
        if (alive(tome.monk) != null) tome.monk.effects.say(text(DagonhaiLines.BLOCKED))
        blocker.effects.spotanim(0, tome.god.impact, 0, 0)
        log("${blocker.username} blocks the ${tome.god.name.lowercase()} tome")
        when (tome.god) {
            God.BANDOS -> {
                val dealt = world.meleeCombat.hitPlayer(blocker, tome.monk, BANDOS_BLOCKER, world.tick)
                log("it hits ${blocker.username} for $dealt")
                for (player in players) {
                    if (player === blocker) continue
                    player.effects.spotanim(0, SPOT_BANDOS_WAVE, 0, 0)
                    val wave = world.meleeCombat.hitPlayer(player, tome.monk, roll(BANDOS_WAVE_MIN, BANDOS_WAVE_MAX), world.tick)
                    log("the shockwave hits ${player.username} for $wave")
                }
            }
            God.ZAMORAK -> {
                val dealt = world.meleeCombat.hitPlayer(blocker, tome.monk, ZAMORAK_BLOCKER, world.tick)
                log("it hits ${blocker.username} for $dealt and burns them")
                for (n in 1..BURN_HITS) {
                    after(n * BURN_EVERY) {
                        if (players.any { it === blocker }) {
                            val burnt = world.meleeCombat.statusHit(blocker, BURN_DAMAGE, Poison.POISON_HITSPLAT)
                            log("the burn hits ${blocker.username} for $burnt")
                        }
                    }
                }
                fire(Tile(blocker.x, blocker.z), keepClear = true)
            }
            God.SARADOMIN -> {
                blocker.effects.spotanim(1, SPOT_SARADOMIN_HEAL, 0, 0)
                lightning()
            }
        }
    }

    private fun EncounterContext.unblocked(tome: Tome) {
        log("the ${tome.god.name.lowercase()} tome reaches the cauldron")
        charge()
        when (tome.god) {
            God.BANDOS -> {
                boss.effects.anim(ANIM_CAULDRON_BANDOS)
                for (player in players) {
                    player.effects.spotanim(0, SPOT_BANDOS_WAVE, 0, 0)
                    val dealt = world.meleeCombat.hitPlayer(player, null, roll(BANDOS_HIT_MIN, BANDOS_HIT_MAX), world.tick)
                    inCombat(player)
                    log("the cauldron's shockwave hits ${player.username} for $dealt")
                }
            }
            God.ZAMORAK -> {
                boss.effects.anim(ANIM_CAULDRON_ZAMORAK)
                for (player in players) fire(Tile(player.x, player.z), keepClear = false)
            }
            God.SARADOMIN -> {
                boss.effects.anim(ANIM_CAULDRON_SARADOMIN)
                state.pool = (state.pool + SARADOMIN_HEAL).coerceAtMost(POOL)
                setPool()
                log("the pool heals to ${state.pool}")
            }
        }
    }

    /** Fire on the floor round [at], [FIRE_RADIUS] tiles each way, on the arena; [keepClear] leaves [at] itself out. */
    private fun EncounterContext.fire(at: Tile, keepClear: Boolean) {
        val tiles = ArrayList<Tile>()
        for (dx in -FIRE_RADIUS..FIRE_RADIUS) for (dz in -FIRE_RADIUS..FIRE_RADIUS) {
            if (keepClear && dx == 0 && dz == 0) continue
            val tile = Tile(at.x + dx, at.z + dz)
            if (inArena(boss.spawn.x, boss.spawn.z, tile.x, tile.z)) tiles += tile
        }
        if (tiles.isNotEmpty()) hazard(tiles, -1, FIRE_WARN, 1, FIRE_MIN, FIRE_MAX, SPOT_ZAMORAK_FIRE)
    }

    /** Saradomin's lightning: a blue tile under each player and a yellow beside them; on the blue it hurts, on the yellow it heals and restores prayer. */
    private fun EncounterContext.lightning() {
        val blue = ArrayList<Tile>()
        val yellow = ArrayList<Tile>()
        for (player in players) {
            blue += Tile(player.x, player.z)
            // A side the player can step to, on the floor (Codex review on #634); none, and they get no yellow.
            val sides = (0..3).map { DIRECTIONS[it * 2] }.filter { side ->
                inArena(boss.spawn.x, boss.spawn.z, player.x + side.x, player.z + side.z) &&
                    world.canStep(player.level, player.x, player.z, side.x, side.z)
            }
            if (sides.isEmpty()) continue
            val side = sides[roll(0, sides.size - 1)]
            yellow += Tile(player.x + side.x, player.z + side.z)
        }
        for (tile in blue) world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_SARADOMIN_DAMAGE_MARK)
        for (tile in yellow) world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_SARADOMIN_HEAL_MARK)
        log("lightning marked: blue ${blue.joinToString(" ") { "${it.x},${it.z}" }}, yellow ${yellow.joinToString(" ") { "${it.x},${it.z}" }}")
        after(LIGHTNING_WARN) {
            for (tile in blue) world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_SARADOMIN_LIGHTNING)
            for (tile in yellow) world.zones.mapAnim(tile.x, tile.z, boss.level, SPOT_SARADOMIN_HEAL_LIGHTNING)
            for (player in players) {
                val here = Tile(player.x, player.z)
                if (here in yellow) {
                    // Up to the base level, never taking off a boost above it (a brew's).
                    val hitpoints = player.hitpoints
                    player.hitpoints = hitpoints + LIGHTNING_HEAL.coerceAtMost((player.skills.baseLevel(Skill.HITPOINTS) - hitpoints).coerceAtLeast(0))
                    val prayer = player.skills.currentLevel(Skill.PRAYER)
                    player.skills.setCurrentLevel(Skill.PRAYER, prayer + LIGHTNING_HEAL.coerceAtMost((player.skills.baseLevel(Skill.PRAYER) - prayer).coerceAtLeast(0)))
                    log("the yellow lightning heals ${player.username}: hitpoints $hitpoints to ${player.hitpoints}, prayer $prayer to ${player.skills.currentLevel(Skill.PRAYER)}")
                } else if (here in blue) {
                    val dealt = world.meleeCombat.hitPlayer(player, null, LIGHTNING_DAMAGE, world.tick)
                    inCombat(player)
                    log("the blue lightning hits ${player.username} for $dealt")
                }
            }
        }
    }

    /** The rifts: imps out of each in turn, unless a player stands beside it, draining it and their prayer; drained or emptied, it closes. */
    private fun EncounterContext.rifts(imp: NpcType?) {
        for ((npc, rift) in state.rifts.entries.toList()) {
            if (alive(npc) == null) {
                state.rifts.remove(npc)
                continue
            }
            val beside = players.filter { !it.dead && besideRift(npc, it) }
            if (beside.isNotEmpty()) {
                npc.hitpoints = (npc.hitpoints - RIFT_DRAIN * beside.size).coerceAtLeast(0)
                for (player in beside) {
                    val prayer = player.skills.currentLevel(Skill.PRAYER)
                    player.skills.setCurrentLevel(Skill.PRAYER, max(0, prayer - RIFT_PRAYER))
                }
                if (npc.hitpoints <= 0) {
                    closeRift(npc, "drained")
                    continue
                }
                // Held shut: no imp while someone stands by it.
                rift.next = world.tick + RIFT_EVERY
                continue
            }
            if (world.tick < rift.next || imp == null) continue
            rift.next = world.tick + RIFT_EVERY
            val out = spawnAdd(imp, npc.x, npc.z)
            if (out != null) {
                rift.imps++
                out.unleashed = true
                out.effects.anim(ANIM_IMP_SPAWN)
                out.effects.spotanim(0, SPOT_IMP_SPAWN, 0)
                randomPlayer()?.let { world.meleeCombat.aggress(out, it, world.tick) }
                log("an imp out of the rift at ${npc.x},${npc.z}")
            }
            if (rift.imps >= RIFT_IMPS) closeRift(npc, "emptied")
        }
    }

    private fun besideRift(rift: Npc, player: Player): Boolean = max(abs(player.x - rift.x), abs(player.z - rift.z)) == 1

    private fun EncounterContext.closeRift(npc: Npc, why: String) {
        state.rifts.remove(npc)
        world.removeNpc(npc)
        log("a rift closes, $why")
    }

    private const val CYCLES_PER_TICK = 30
}
