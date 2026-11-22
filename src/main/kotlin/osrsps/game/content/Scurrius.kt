package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.DeathPlace
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.instance.Instance
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** Scurrius's lair's lines (keys only; the words are in data/dialogue.txt). */
object ScurriusLairLines : Lines("scurrius", "Scurrius's lair") {
    val PEEK = line("peek", "Peek (normal) on the broken bars: how many players are in the public lair (no source yet)", "count")
    val PEEK_EMPTY = line("peek_empty", "Peek (normal) on the broken bars with nobody in the public lair (no source yet)")
    val REENTRY = line("reentry", "Climb-through on the broken bars within the 15 seconds after leaving by the sewage water (no source yet)")
    val NO_ROOM = line("no_room", "Climb-through (private) when no instance can be made (no source yet)")
    val EAT_HEALED = line("eat_healed", "Eat on a food pile: healed in full (OSRS Wiki, Food Pile; no transcript)")
    val EAT_WAIT = line("eat_wait", "Eat on a food pile within ten minutes of the last (no transcript)", "minutes")
    val EAT_COMBAT = line("eat_combat", "Eat on a food pile just after being attacked (no transcript)")
    val KILL_COUNT = line("kill_count", "Scurrius killed: the player's kill count of him (the Wiki's Kill count page; no transcript)", "count")
}

/** Historian Aldo's lines (keys only; the words are in data/dialogue.txt, from Transcript:Historian Aldo, read 2026-11-19). */
object HistorianAldoLines : Lines("historian_aldo", "Transcript:Historian Aldo") {
    val HELLO = line("hello", "his greeting")
    val ASK_BARS = line("ask_bars", "the menu's first option, and the player saying it")
    val ASK_SPINE = line("ask_spine", "the menu's second option: the player's line")
    val ASK_SPINE_OPTION = line("ask_spine_option", "the menu's second option as the menu shows it")
    val ASK_WEAPON = line("ask_weapon", "the menu's third option, and the player saying it")
    val GOODBYE = line("goodbye", "the menu's fourth option, and the player saying it")
    val BARS_1 = line("bars_1", "what's through the bars: the discovery, Scurrius")
    val BARS_2 = line("bars_2", "what's through the bars: no ordinary rat")
    val BARS_3 = line("bars_3", "what's through the bars: it absorbed their abilities")
    val BARS_4 = line("bars_4", "what's through the bars: you'll be fine")
    val BARS_5 = line("bars_5", "what's through the bars: bring him the spine")
    val SPINE_1 = line("spine_1", "about the spine: invaluable")
    val SPINE_2 = line("spine_2", "about the spine: a bone weapon")
    val SPINE_3 = line("spine_3", "about the spine: you can make them yourself")
    val SPINE_4 = line("spine_4", "about the spine: or a lamp")
    val WEAPON_NEEDS = line("weapon_needs", "a bone weapon: what to bring")
    val WEAPON_FEE = line("weapon_fee", "a bone weapon: the 50,000 coins")
    val WEAPON_COME_BACK = line("weapon_come_back", "a bone weapon without the materials")
}

/**
 * Scurrius's lair in the Varrock Sewers, east of the manhole (Scurrius step 1: the way in and out, the food piles and
 * Historian Aldo; the OSRS Wiki's Scurrius, Scurrius/Strategies, Broken bars, Sewage water, Food Pile and Historian
 * Aldo with its transcript, read 2026-11-19; ids, ops and places from the 242 cache).
 *
 * - **The broken bars** (`rat_boss_entrance` 14203, 2 by 2 at 3282,9867): Climb-through (normal) puts the player in
 *   the lair itself, the public one, where `rat_boss_normal` (7221, 1,500 hitpoints) stands; Climb-through (private)
 *   puts them in a lair of their own, an [Instance] of its three by three zones where `rat_boss_instance` (7222, 500
 *   hitpoints) stands (free, as the Wiki says). Peek (normal) says how many are in the public one. Either way in is
 *   refused for [REENTRY_TICKS] after leaving ("there's a roughly 15 second delay before they can rejoin").
 * - **The sewage water** (`rat_boss_exit` 14204 at 3288,9867): Cross and Quick-escape both put the player back
 *   outside the bars ([OUTSIDE]) and start the delay. Logging out or dying in a private lair leaves by its exit, as
 *   every instance does.
 * - **The food piles** (`rat_boss_food_pile` 14205 and `_pile2` 14206, three 2 by 2 heaps on the lair's north, south
 *   and east edges): Eat heals the player in full once every ten minutes ([EAT_COOLDOWN_TICKS]), "but only when out of
 *   combat" (Food Pile), which the Strategies page gives as "a couple of seconds after being attacked".
 * - **Scurrius** comes back [RESPAWN] ticks after a kill (the Wiki's respawn), in either lair; how he fights is his own
 *   step. A death in the lair puts the grave beside Historian Aldo, outside ("their grave will appear next to
 *   Historian Aldo just outside Scurrius' lair", Scurrius/Strategies).
 * - **Historian Aldo** (`rat_boss_biologist` 12606, multi-NPC of 7226 and 7615 on varbit `rat_boss_biologist_npc`,
 *   at 3276,9872): Talk-to, his transcript's standard dialogue. Making a bone weapon and trading a spine are the rat
 *   bone weapons' step: until then he always says to come back with the materials.
 *
 * GUESSED until recorded: where the bars and the water put the player ([INSIDE], [OUTSIDE]); no animation or delay on
 * either; Cross asking nothing first; the delay starting only from the water, and a private lair's exit (a log out)
 * not starting it; "a couple of seconds" as [EAT_COMBAT_TICKS]; the cooldown counted in game ticks from the eat and
 * kept only while the server runs (live shows it as `rat_boss_food_pile_player_eat_delay`, whose unit we don't know);
 * Scurrius's spawn tile ([SPAWN], the Wiki's pin as his centre); the grave's tile beside Aldo ([GRAVE]); every
 * chathead expression. INFERRED: the lair is multi-way (his giant rats fight alongside him, and the public lair is
 * fought in groups); Aldo is the first NPC (7226) to a player who hasn't the second's Trade yet. Left out: the patrolling
 * rats (`rat_boss_giant_rat_patrol`, scenery walking to the bars), the shared Group Ironman lair, the entry warning
 * (varbit `rat_boss_enter_warning`, words unknown), and the cannon's refusal.
 */
class ScurriusLairs(private val world: World) {
    /** One player's private lair: the instance, and who it was made for. */
    class Run(val instance: Instance, val owner: String) {
        fun inside(x: Int, z: Int) = Tile(instance.baseX + x - Scurrius.LAIR_X, instance.baseZ + z - Scurrius.LAIR_Z)
        fun outside(x: Int, z: Int) = Tile(x - instance.baseX + Scurrius.LAIR_X, z - instance.baseZ + Scurrius.LAIR_Z)
    }

    private val runs = HashMap<Int, Run>()

    /** The tick each player (by save key) may climb in again after leaving by the water. */
    private val reentry = HashMap<String, Long>()

    /** The tick each player (by save key) may eat from a food pile again. */
    private val eatAgain = HashMap<String, Long>()

    fun runOf(player: Player): Run? = runAt(player.x, player.z)

    fun runAt(x: Int, z: Int): Run? {
        val instance = world.instances.at(x, z) ?: return null
        return runs[instance.id]?.takeIf { it.instance === instance }
    }

    /** Whether [x], [z] on [level] is in a lair: the public one or a private one. */
    fun inLair(level: Int, x: Int, z: Int): Boolean {
        if (level != 0) return false
        if (Scurrius.inPublicLair(x, z)) return true
        val run = runAt(x, z) ?: return false
        val real = run.outside(x, z)
        return Scurrius.inPublicLair(real.x, real.z)
    }

    /** Players in the public lair now. */
    fun inPublic(): Int = world.onlinePlayers.count { it.level == 0 && Scurrius.inPublicLair(it.x, it.z) }

    private fun mayEnter(player: Player): Boolean {
        val until = reentry[player.saveKey] ?: return true
        if (world.tick >= until) {
            reentry.remove(player.saveKey)
            return true
        }
        world.message(player, ScurriusLairLines.REENTRY)
        return false
    }

    suspend fun ScriptContext.climbPublic() {
        arriveDelay()
        if (!mayEnter(player)) return
        world.teleport(player, Scurrius.INSIDE.x, Scurrius.INSIDE.z, 0)
    }

    suspend fun ScriptContext.climbPrivate() {
        arriveDelay()
        if (!mayEnter(player)) return
        val run = open(player) ?: return world.message(player, ScurriusLairLines.NO_ROOM)
        val to = run.inside(Scurrius.INSIDE.x, Scurrius.INSIDE.z)
        world.teleport(player, to.x, to.z, 0)
    }

    /** A new private lair for [player], Scurrius in it; null when the dynamic area has no room. */
    fun open(player: Player): Run? {
        // Ended lairs are let go of here, as nothing else tells this when an instance ends.
        runs.values.removeAll { run -> world.instances.all.none { it === run.instance } }
        val instance = world.instances.create(
            Scurrius.LAIR_X, Scurrius.LAIR_Z, Scurrius.LAIR_ZONES, Scurrius.LAIR_ZONES, Scurrius.OUTSIDE.x, Scurrius.OUTSIDE.z, 0,
        ) ?: return null
        val run = Run(instance, player.saveKey)
        runs[instance.id] = run
        val type = world.npcTypes[Scurrius.BOSS_PRIVATE]
        if (type != null) {
            val at = run.inside(Scurrius.SPAWN.x, Scurrius.SPAWN.z)
            world.spawnNpc(NpcSpawn(type, at.x, at.z, 0, source = "Scurrius's private lair"))
        }
        return run
    }

    /** Cross or Quick-escape on the sewage water: out beside the bars, and the delay before coming back in. */
    suspend fun ScriptContext.leave() {
        arriveDelay()
        if (!inLair(player.level, player.x, player.z)) return world.message(player, GameLines.NOTHING_INTERESTING)
        world.scripts.interrupt(player)
        reentry[player.saveKey] = world.tick + Scurrius.REENTRY_TICKS
        world.teleport(player, Scurrius.OUTSIDE.x, Scurrius.OUTSIDE.z, 0)
    }

    /** Peek (normal): how many are in the public lair. */
    fun peek(player: Player) {
        val count = inPublic()
        if (count == 0) world.message(player, ScurriusLairLines.PEEK_EMPTY) else world.message(player, ScurriusLairLines.PEEK, "count" to count)
    }

    /** Eat on a food pile: full hitpoints, out of combat, once every ten minutes. */
    suspend fun ScriptContext.eat() {
        arriveDelay()
        if (!inLair(player.level, player.x, player.z)) return world.message(player, GameLines.NOTHING_INTERESTING)
        // Out of combat both ways: not hit, and not attacking, within the window (INFERRED: attacking counts too, so a
        // player he isn't targeting can't heal mid-fight in the public lair).
        val hit = player.attackedTick >= 0 && world.tick - player.attackedTick < Scurrius.EAT_COMBAT_TICKS
        val attacking = player.attackedNpcTick >= 0 && world.tick - player.attackedNpcTick < Scurrius.EAT_COMBAT_TICKS
        if (hit || attacking) {
            return world.message(player, ScurriusLairLines.EAT_COMBAT)
        }
        val until = eatAgain[player.saveKey]
        if (until != null && world.tick < until) {
            val minutes = ((until - world.tick) + Scurrius.TICKS_PER_MINUTE - 1) / Scurrius.TICKS_PER_MINUTE
            return world.message(player, ScurriusLairLines.EAT_WAIT, "minutes" to minutes)
        }
        eatAgain[player.saveKey] = world.tick + Scurrius.EAT_COOLDOWN_TICKS
        player.anim(Scurrius.ANIM_EAT)
        val max = player.skills.baseLevel(Skill.HITPOINTS)
        if (player.skills.currentLevel(Skill.HITPOINTS) < max) player.skills.setCurrentLevel(Skill.HITPOINTS, max)
        world.message(player, ScurriusLairLines.EAT_HEALED)
    }

    /** Where a player dying in a lair comes back (the spawn) and their grave goes (beside Aldo); null elsewhere. */
    fun deathPlace(player: Player): DeathPlace? {
        if (!inLair(player.level, player.x, player.z)) return null
        return DeathPlace(keepItems = false, Scurrius.GRAVE.x, Scurrius.GRAVE.z, 0, World.SPAWN_X, World.SPAWN_Z, World.SPAWN_LEVEL)
    }
}

object Scurrius {
    const val BOSS_PUBLIC = 7221
    const val BOSS_PRIVATE = 7222
    const val ALDO = 12606
    const val ALDO_TALK = 7226
    const val ALDO_TRADE = 7615

    const val BARS = 14203
    const val WATER = 14204
    const val FOOD_PILE = 14205
    const val FOOD_PILE_2 = 14206

    const val CLIMB_PUBLIC = 1
    const val CLIMB_PRIVATE = 2
    const val PEEK = 3
    const val CROSS = 1
    const val QUICK_ESCAPE = 2
    const val EAT = 1
    const val TALK_TO = 1

    /** The lair's locs whose handlers work in a private lair's copy too: the water out and the food piles. */
    val INSTANCE_LOCS: Set<Int> = setOf(WATER, FOOD_PILE, FOOD_PILE_2)

    /** The cache's `total_rat_boss_kills`. */
    const val KILL_COUNT_VARP = 4079

    /** The lair's map: three by three zones from here, holding the lair and its sewage water. */
    const val LAIR_X = 3288
    const val LAIR_Z = 9856
    const val LAIR_ZONES = 3

    /** Outside the bars, on the sewer side; inside, east of the water; where Scurrius stands (south-west tile). */
    val OUTSIDE = Tile(3281, 9867)
    val INSIDE = Tile(3290, 9867)
    val SPAWN = Tile(3298, 9866)

    /** Where a grave from the lair goes: beside Aldo. */
    val GRAVE = Tile(3277, 9871)

    const val RESPAWN = 30
    const val REENTRY_TICKS = 25
    const val EAT_COOLDOWN_TICKS = 1_000
    const val EAT_COMBAT_TICKS = 4
    const val TICKS_PER_MINUTE = 100

    /** Eating at a food pile: the player's eat animation. Guessed. */
    const val ANIM_EAT = 829

    /** Whether [x], [z] (level 0, the real map) is in the public lair: its three by three zones. */
    fun inPublicLair(x: Int, z: Int): Boolean = x in LAIR_X until LAIR_X + LAIR_ZONES * 8 && z in LAIR_Z until LAIR_Z + LAIR_ZONES * 8

    internal suspend fun Dialogue.aldo() = with(HistorianAldoLines) {
        chatNpc(ChatAnim.HAPPY, HELLO)
        while (true) {
            when (choice(ASK_BARS, ASK_SPINE_OPTION, ASK_WEAPON, GOODBYE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, ASK_BARS)
                    chatNpc(ChatAnim.HAPPY, BARS_1)
                    chatNpc(ChatAnim.HAPPY, BARS_2)
                    chatNpc(ChatAnim.HAPPY, BARS_3)
                    chatNpc(ChatAnim.NEUTRAL, BARS_4)
                    chatNpc(ChatAnim.NEUTRAL, BARS_5)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, ASK_SPINE)
                    chatNpc(ChatAnim.HAPPY, SPINE_1)
                    chatNpc(ChatAnim.NEUTRAL, SPINE_2)
                    chatNpc(ChatAnim.NEUTRAL, SPINE_3)
                    chatNpc(ChatAnim.NEUTRAL, SPINE_4)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, ASK_WEAPON)
                    chatNpc(ChatAnim.NEUTRAL, WEAPON_NEEDS)
                    chatNpc(ChatAnim.NEUTRAL, WEAPON_FEE)
                    chatNpc(ChatAnim.NEUTRAL, WEAPON_COME_BACK)
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, GOODBYE)
                    return
                }
            }
        }
    }
}

fun registerScurrius(world: World, lairs: ScurriusLairs) {
    with(Scurrius) {
        world.handlers.onLoc(BARS, CLIMB_PUBLIC) { with(lairs) { climbPublic() } }
        world.handlers.onLoc(BARS, CLIMB_PRIVATE) { with(lairs) { climbPrivate() } }
        world.handlers.onLoc(BARS, PEEK) {
            arriveDelay()
            lairs.peek(player)
        }
        world.handlers.onLoc(WATER, CROSS) { with(lairs) { leave() } }
        world.handlers.onLoc(WATER, QUICK_ESCAPE) { with(lairs) { leave() } }
        world.handlers.onLoc(FOOD_PILE, EAT) { with(lairs) { eat() } }
        world.handlers.onLoc(FOOD_PILE_2, EAT) { with(lairs) { eat() } }
        for (id in listOf(ALDO, ALDO_TALK, ALDO_TRADE)) {
            world.handlers.onNpc(id, TALK_TO) { op -> world.dialogues.start(this, op.npc) { aldo() } }
        }
        // Inferred: the lair is multi-way, public and private, as his giant rats fight alongside him.
        val multi = world.multiCombat.dynamic
        world.multiCombat.dynamic = { level, x, z -> (lairs.runAt(x, z) != null && lairs.inLair(level, x, z)) || multi(level, x, z) }
    }
}
