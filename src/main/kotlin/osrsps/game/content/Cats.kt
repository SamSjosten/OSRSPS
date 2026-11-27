package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext

/** A pet cat's words (Transcript:Kitten, Transcript:Cat and Transcript:Overgrown cat), as a player who has finished Dragon Slayer II hears them. */
object CatLines : Lines("cat", "Transcript:Kitten, Transcript:Cat, Transcript:Overgrown cat, read 2026-11-19") {
    val ALREADY_FOLLOWER = line("already_follower", "dropping a pet with one out already; our own line")
    val NOT_YOURS = line("not_yours", "another player's cat's options; our own line")
    val DROPPED = line("dropped", "overhead, a kitten or cat put down")

    // Talk-to: the menu and its questions.
    val HOW_ARE_YOU = line("how_are_you", "option and player")
    val HOW_OLD = line("how_old", "option and player")
    val WHERE = line("where", "option and player")
    val WHAT_NOW = line("what_now", "option and player")

    val KITTEN_HAPPY = line("kitten_happy", "kitten, how are you: not very hungry nor lonely")
    val KITTEN_HUNGRY = line("kitten_hungry", "kitten, how are you: very hungry")
    val KITTEN_LONELY = line("kitten_lonely", "kitten, how are you: really needs attention")
    val KITTEN_UNDER_A_MONTH = line("kitten_under_a_month", "kitten, how old: under 15 minutes old")
    val KITTEN_A_MONTH = line("kitten_a_month", "kitten, how old: 15 to 29 minutes old")
    val KITTEN_MONTHS = line("kitten_months", "kitten, how old: older, and more than 15 minutes from grown", "months")
    val KITTEN_ALMOST = line("kitten_almost", "kitten, how old: under 15 minutes from grown")
    val KITTEN_AGE = line("kitten_age", "kitten, how old: the box after", "age_hours", "age_minutes", "left_hours", "left_minutes")
    val GUESSED_AGE = line("guessed_age", "Guess age: the box", "age_hours", "age_minutes", "left_hours", "left_minutes")
    val KITTEN_WHERE: List<Line> = listOf(
        line("kitten_where_gertrude", "kitten, where: Gertrude"),
        line("kitten_where_ratpits", "kitten, where: Ardougne's rat pits"),
        line("kitten_where_sewers", "kitten, where: Varrock's sewers"),
    )
    val KITTEN_WHAT: List<Line> = listOf(line("kitten_what_chase", "kitten, what now: chase"), line("kitten_what_nap", "kitten, what now: nap"))

    val CAT_HOW_ARE_YOU = line("cat_how_are_you", "cat, how are you: before the cat training medal")
    val CAT_HOW_ARE_YOU_MEDAL = line("cat_how_are_you_medal", "cat, how are you: with the cat training medal")
    val CAT_HOW_OLD = line("cat_how_old", "cat, how old")
    val CAT_HOW_OLD_FAT = line("cat_how_old_fat", "cat, how old: close to overgrown")
    val CAT_WHERE = line("cat_where", "cat, where: not in Varrock's sewers")
    val CAT_WHAT = line("cat_what", "cat, what now")

    val OVERGROWN_HOW_ARE_YOU = line("overgrown_how_are_you", "overgrown cat, how are you")
    val OVERGROWN_HOW_OLD: List<Line> = (1..6).map { line("overgrown_how_old_$it", "overgrown cat, how old: page $it (1, 2, 4 and 6 the cat's, 3 and 5 the player's)") }
    val OVERGROWN_WHERE = line("overgrown_where", "overgrown cat, where")
    val OVERGROWN_WHAT: List<Line> = (1..3).map { line("overgrown_what_$it", "overgrown cat, what now: page $it (2 the player's)") }

    // Interact.
    val INTERACT_KITTEN = line("interact_kitten", "Interact's menu title, a kitten")
    val INTERACT_CAT = line("interact_cat", "Interact's menu title, a cat")
    val STROKE = line("stroke", "Interact option")
    val CHASE_VERMIN = line("chase_vermin", "Interact option")
    val GUESS_AGE = line("guess_age", "Interact option, kittens only")
    val SHOO_AWAY = line("shoo_away", "Interact option")
    val STROKED = line("stroked", "the message on Stroke, a kitten or overgrown cat")
    val PURR = line("purr", "overhead, Stroke")
    val KITTEN_STROKE: List<Line> = (1..3).map { line("kitten_stroke_$it", "kitten, Stroke: page $it (2 the kitten's)") }
    val CAT_STROKE: List<Line> = (1..2).map { line("cat_stroke_$it", "cat, Stroke: page $it (1 the player's)") }
    val OVERGROWN_STROKE: List<Line> = (1..2).map { line("overgrown_stroke_$it", "overgrown cat, Stroke: page $it (1 the player's)") }
    val WONDER_AGE = line("wonder_age", "Guess age: the player")
    val SURE_SHOO = line("sure_shoo", "Shoo away's menu title")
    val YES_SHOO = line("yes_shoo", "Shoo away: option 1")
    val NO_SHOO = line("no_shoo", "Shoo away: option 2")
    val NOT_SHOOED = line("not_shooed", "Shoo away, option 2's message")
    val KITTEN_SHOO: List<Line> = (1..3).map { line("kitten_shoo_$it", "kitten, shooed: page $it (2 the kitten's)") }
    val CAT_SHOO: List<Line> = (1..2).map { line("cat_shoo_$it", "cat, shooed: page $it (2 the cat's)") }
    val OVERGROWN_SHOO: List<Line> = (1..2).map { line("overgrown_shoo_$it", "overgrown cat, shooed: page $it (2 the cat's)") }
    val SHOO_CAT = line("shoo_cat", "overhead, the player shooing")
    val MEOW_AWAY = line("meow_away", "overhead, the cat shooed, and one running away")
    val RUN_AWAY = line("run_away", "the message, a cat shooed or run away")

    // Chase vermin.
    val NO_VERMIN = line("no_vermin", "Chase vermin with no rat near")
    val GO_ON = line("go_on", "overhead, the player sending the cat at a rat")
    val POUNCE = line("pounce", "overhead, the cat at the rat")
    val EEK = line("eek", "overhead, the rat")
    val GOT_AWAY = line("got_away", "the message, the rat gets away")
    val WELL_DONE = line("well_done", "overhead, the player, the rat caught")
    val TOO_BIG = line("too_big", "an overgrown cat's Chase vermin")
    val MILESTONES: Map<Int, Line> = mapOf(10 to line("rats_10", "10 rats caught"), 50 to line("rats_50", "50 rats caught"), 100 to line("rats_100", "100 rats caught"))

    // Care.
    val THINK_HUNGRY = line("think_hungry", "the player, the kitten hungry")
    val HUNGRY = line("hungry", "the message, the kitten hungry")
    val MEOW_HUNGRY = line("meow_hungry", "overhead, the kitten very hungry")
    val THINK_VERY_HUNGRY = line("think_very_hungry", "the player, the kitten very hungry")
    val VERY_HUNGRY = line("very_hungry", "the message, the kitten very hungry")
    val THINK_ATTENTION = line("think_attention", "the player, the kitten wanting attention")
    val ATTENTION = line("attention", "the message, the kitten wanting attention")
    val MEOW_LONELY = line("meow_lonely", "overhead, the kitten really wanting attention")
    val THINK_LONELY = line("think_lonely", "the player, the kitten really wanting attention")
    val REALLY_ATTENTION = line("really_attention", "the message, the kitten really wanting attention")
    val GOBBLES = line("gobbles", "the message, fish used on a kitten")
    val PURR_FED = line("purr_fed", "overhead, fish or wool used on a kitten")
    val LAPS = line("laps", "the message, milk used on a kitten")
    val PURPURR = line("purpurr", "overhead, milk used on a kitten")
    val WOOL = line("wool", "the player, wool used on a kitten")
    val DONT_FEED = line("dont_feed", "the player, fish used on a grown cat")
    val GROWN_MEOW = line("grown_meow", "overhead, the kitten grown")
    val GROWN = line("grown", "the box, the kitten grown")
    val OVERGROWN = line("overgrown", "the box, the cat overgrown")
}

/**
 * Pet cats (post-quest step 13; every account has finished Gertrude's Cat, which lets Gertrude sell kittens, and Dragon
 * Slayer II, so cats talk without a catspeak amulet). From the Wiki's Kitten, Cat, Overgrown cat, Rat and Follower pages
 * and the three cats' transcripts, read 2026-11-19.
 *
 * - **A follower**: dropping a kitten or cat puts it down beside the player, following them; one follower at a time.
 *   Pick-up puts it back in the backpack. It goes with them through teleports and stairs, and logs out and back in with
 *   them, kept by the cache's `follower_obj` varp (448) as its item. `follower_npc` (447) holds its NPC's index, which
 *   the client reads as the player's own follower (inferred from the names).
 * - **A kitten's care**, only while it follows: hungry 24 minutes after it last ate, very hungry 3 minutes on, gone 3
 *   minutes after that; wanting attention 25 minutes after a fresh start, really wanting it 7 minutes on, gone 7 minutes
 *   after. Fish or milk feeds it; a stroke holds it off 18 minutes, a second stroke 25; a ball of wool 51.
 * - **Growing up**: every 150 ticks a kitten or cat follows, outside dialogue, counts one growth cycle. A session's
 *   part cycle is lost at logout or pickup, as on live. At 120 the kitten is a cat, which needs no care.
 * - **Talk-to and Interact**: the catspeak menus; Stroke, Chase vermin, Guess age (kittens) and Shoo away.
 * - **Chase vermin**: the cat goes for the nearest rat within [VERMIN_RANGE]: a kitten catches it one time in ten, a cat
 *   half the time; an overgrown cat won't. Caught rats are counted, with the player's lines at 10, 50 and 100.
 *
 * Guessed: an overgrown cat at 220 cycles after it grew (the Wiki's "five to six hours"); the kitten's "months" (an
 * hour's growth is four); where a dropped cat lands (beside the player, west first); that the player's lines on the
 * kitten's care and the chase are overhead; that Chase, the cats' op 4, does what Interact's Chase vermin does; the
 * range a follower is put back beside its player from ([CATCH_UP]); that a caught rat goes the way one killed does,
 * with no drop; the pick-up animation.
 * Left out: hell-cats (Evil Dave's basement), lazy and wily cats (Ratcatchers' Felkrash, outside Misthalin), the
 * menagerie and pet feeder (Construction), the location lines (Ardougne's rat pits, Varrock's sewers), the cat ears
 * talk, Call follower, selling cats in West Ardougne, the cat training medal, a cat running off on its player's death.
 */
class Cats(private val world: World, private val collision: osrsps.map.CollisionMap) {
    /** A colour's kitten, cat and overgrown cat: their items and their NPCs. */
    enum class Colour(val kittenObj: Int, val catObj: Int, val overgrownObj: Int, val kittenNpc: Int, val catNpc: Int, val overgrownNpc: Int) {
        GREY_BLACK(1555, 1561, 1567, 5591, 1619, 5598),
        WHITE(1556, 1562, 1568, 5592, 1620, 5599),
        BROWN(1557, 1563, 1569, 5593, 1621, 5600),
        BLACK(1558, 1564, 1570, 5594, 1622, 5601),
        GREY_BROWN(1559, 1565, 1571, 5595, 1623, 5602),
        GREY_BLUE(1560, 1566, 1572, 5596, 1624, 5603),
    }

    enum class Stage { KITTEN, CAT, OVERGROWN }

    /** A cat as an item or an NPC. */
    data class Kind(val colour: Colour, val stage: Stage) {
        val obj: Int get() = when (stage) {
            Stage.KITTEN -> colour.kittenObj
            Stage.CAT -> colour.catObj
            Stage.OVERGROWN -> colour.overgrownObj
        }
        val npc: Int get() = when (stage) {
            Stage.KITTEN -> colour.kittenNpc
            Stage.CAT -> colour.catNpc
            Stage.OVERGROWN -> colour.overgrownNpc
        }
    }

    /** One player's follower out in the world, and the ticks towards its next growth cycle this session. */
    private class Out(val npc: Npc, var kind: Kind, var growthTicks: Int = 0) {
        /** The tick a Chase vermin run ends; until then the cat goes for the rat and doesn't follow. */
        var chasingUntil: Long = 0
    }

    private val out = HashMap<String, Out>()

    /** [player]'s cat following them, or null. */
    fun follower(player: Player): Npc? = out[player.saveKey]?.npc

    /** What [player]'s follower is, or null with none out. */
    fun followerKind(player: Player): Kind? = out[player.saveKey]?.kind

    /**
     * What [player]'s follower is: the one out, or one saved in `follower_obj` still waiting at login for room in the
     * world, which counts as theirs too (Codex review on #980). Null with neither.
     */
    private fun heldFollower(player: Player): Kind? =
        followerKind(player) ?: if (hasVarp(player, FOLLOWER_OBJ)) kindOfObj(player.vars[FOLLOWER_OBJ]) else null

    /** Whether [player] has a kitten or cat (not overgrown) out, or waiting to come out, which Gertrude counts as owned. */
    fun followingCat(player: Player): Boolean = heldFollower(player)?.let { it.stage != Stage.OVERGROWN } ?: false

    // ---- The vars -------------------------------------------------------------------------------------------------

    private fun hasVarp(player: Player, varp: Int) = varp < player.vars.types.varpCount

    private fun catVarp(player: Player): Int? = player.vars.types.namedVarps[CustomVars.CAT]
    private fun careVarp(player: Player): Int? = player.vars.types.namedVarps[CustomVars.CAT_CARE]

    /** The growth cycles [player]'s cat has had. */
    fun growth(player: Player): Int = catVarp(player)?.let { player.vars[it] and 0xFFFF } ?: 0

    private fun setGrowth(player: Player, cycles: Int) {
        val varp = catVarp(player) ?: return
        player.vars[varp] = (player.vars[varp] and 0xFFFF.inv()) or cycles.coerceIn(0, 0xFFFF)
    }

    /** The rats [player]'s cats have caught. */
    fun ratsCaught(player: Player): Int = catVarp(player)?.let { player.vars[it] ushr 16 } ?: 0

    private fun setRats(player: Player, rats: Int) {
        val varp = catVarp(player) ?: return
        player.vars[varp] = (player.vars[varp] and 0xFFFF) or (rats.coerceIn(0, 0x7FFF) shl 16)
    }

    /** Ticks until [player]'s kitten runs off hungry, [HUNGER_TICKS] just fed. */
    fun hungerLeft(player: Player): Int = care(player)?.first ?: HUNGER_TICKS

    /** Ticks until [player]'s kitten runs off lonely. */
    fun attentionLeft(player: Player): Int = care(player)?.second ?: ATTENTION_TICKS

    /** The kitten's hunger and attention ticks left, or null before its first time out (a fresh kitten). */
    private fun care(player: Player): Pair<Int, Int>? {
        val varp = careVarp(player) ?: return null
        val packed = player.vars[varp]
        if (packed and CARED == 0) return null
        return (packed and 0xFFF) to ((packed ushr 12) and 0x3FFF)
    }

    private fun setCare(player: Player, hunger: Int, attention: Int, stroked: Boolean = strokeHolds(player)) {
        val varp = careVarp(player) ?: return
        player.vars[varp] = CARED or (if (stroked) STROKED else 0) or hunger.coerceIn(0, 0xFFF) or (attention.coerceIn(0, 0x3FFF) shl 12)
    }

    /** Whether a stroke still holds off the kitten's loneliness: set by a stroke, gone once its warning comes. */
    private fun strokeHolds(player: Player): Boolean {
        val varp = careVarp(player) ?: return false
        return player.vars[varp] and STROKED != 0
    }

    /** A new cat: no growth, no care yet. The rats caught stay, as the Wiki has the medal count cats a player has owned. */
    private fun forget(player: Player) {
        setGrowth(player, 0)
        careVarp(player)?.let { player.vars[it] = 0 }
    }

    /** Gertrude sold [player] a kitten: it starts with no growth and fresh care. */
    fun newKitten(player: Player) = forget(player)

    private fun setFollowerVarps(player: Player, npc: Npc?, obj: Int) {
        if (hasVarp(player, FOLLOWER_NPC)) player.vars[FOLLOWER_NPC] = npc?.index ?: -1
        if (hasVarp(player, FOLLOWER_OBJ)) player.vars[FOLLOWER_OBJ] = obj
    }

    // ---- Out and back in ----------------------------------------------------------------------------------------

    /** The Drop option on a cat in slot [slot]: put down to follow, unless a follower is out already. */
    fun drop(player: Player, slot: Int) {
        val held = player.inventory[slot] ?: return
        val kind = kindOfObj(held.id) ?: return
        if (heldFollower(player) != null) {
            player.message(world.textFor(player)[CatLines.ALREADY_FOLLOWER])
            return
        }
        if (player.inventory.remove(slot, 1) == 0) return
        if (kind.stage == Stage.KITTEN && care(player) == null) setCare(player, HUNGER_TICKS, ATTENTION_TICKS)
        val npc = spawn(player, kind) ?: run {
            // No room for it in the world: back in the backpack.
            world.objTypes[held.id]?.let { player.inventory.add(it, 1) }
            return
        }
        npc.effects.say(world.textFor(player)[CatLines.DROPPED])
    }

    private fun spawn(player: Player, kind: Kind, growthTicks: Int = 0): Npc? {
        val type = world.npcTypes[kind.npc] ?: return null
        val (x, z) = besidePlayer(player)
        val npc = world.spawnNpc(NpcSpawn(type, x, z, player.level, source = "${player.username}'s cat", respawns = false)) ?: return null
        out[player.saveKey] = Out(npc, kind, growthTicks)
        setFollowerVarps(player, npc, kind.obj)
        world.npcs.follow(npc, player)
        watch(player.saveKey, npc)
        return npc
    }

    /** A free tile beside [player], west, east, south then north; their own tile if none is. */
    private fun besidePlayer(player: Player): Pair<Int, Int> {
        for ((dx, dz) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            val x = player.x + dx
            val z = player.z + dz
            if (world.canStep(player.level, player.x, player.z, dx, dz) && world.npcs.near(x, z, player.level, 0).isEmpty()) return x to z
        }
        return player.x to player.z
    }

    /** Pick-up on [npc]: back in the backpack, if it is the player's and there is room. */
    suspend fun ScriptContext.pickUp(npc: Npc) {
        val mine = out[player.saveKey]
        if (mine?.npc !== npc) {
            player.message(world.textFor(player)[CatLines.NOT_YOURS])
            return
        }
        val type = world.objTypes[mine.kind.obj] ?: return
        if (player.inventory.add(type, 1) != 1) {
            player.message(world.textFor(player)[GameLines.NO_SPACE])
            return
        }
        player.anim(ANIM_PICK_UP)
        despawn(player)
        setFollowerVarps(player, null, -1)
    }

    /** Takes [player]'s follower out of the world, its vars left as they are. */
    private fun despawn(player: Player) {
        val gone = out.remove(player.saveKey) ?: return
        if (world.npcs[gone.npc.index] === gone.npc) world.removeNpc(gone.npc)
    }

    /** Logging in: a follower out when they left comes back beside them, its part cycle lost. */
    fun login(player: Player) {
        if (!hasVarp(player, FOLLOWER_OBJ)) return
        val obj = player.vars[FOLLOWER_OBJ]
        val kind = kindOfObj(obj)
        if (kind == null) {
            if (obj > 0 && obj in CAT_OBJS) setFollowerVarps(player, null, -1)
            return
        }
        respawnAtLogin(player, 1)
    }

    /**
     * The follower back beside [player] in [ticks]; with no room in the world yet it tries again, the cat kept saved.
     * Each try spawns what `follower_obj` holds then, so one put back by `::reset` meanwhile isn't brought out twice
     * (Codex review on #980).
     */
    private fun respawnAtLogin(player: Player, ticks: Int) {
        world.npcScripts.later(ticks) {
            if (player.loggedOut || out.containsKey(player.saveKey)) return@later
            val kind = kindOfObj(player.vars[FOLLOWER_OBJ]) ?: return@later
            if (spawn(player, kind) == null) respawnAtLogin(player, LOGIN_RETRY_TICKS)
        }
    }

    /** Logging out: the follower goes, its item kept in `follower_obj` for next time. */
    fun logout(player: Player) = despawn(player)

    /**
     * `::reset` putting back [player]'s vars from before their scenario: the follower out now goes before, and the
     * one the restored `follower_obj` holds comes back after, so the live cat and the vars agree (Codex review on #980).
     */
    fun beforeRestore(player: Player) = despawn(player)

    fun afterRestore(player: Player) = login(player)

    /**
     * Each tick a follower is out: it keeps up with its player (put back beside them after a teleport or on another
     * level), a kitten's care runs down, and it grows.
     */
    private fun watch(key: String, npc: Npc) {
        world.npcScripts.later(1) {
            val mine = out[key]
            if (mine?.npc !== npc) return@later
            val player = world.onlinePlayers.firstOrNull { it.saveKey == key }
            if (player == null) {
                despawn(key)
                return@later
            }
            if (npc.level != player.level || maxOf(kotlin.math.abs(npc.x - player.x), kotlin.math.abs(npc.z - player.z)) > CATCH_UP) {
                despawn(player)
                spawn(player, mine.kind, mine.growthTicks) ?: return@later
                // The tick still counts for the cat put back beside them.
                val moved = out[key] ?: return@later
                if (moved.kind.stage == Stage.KITTEN && !tend(player, moved)) return@later
                grow(player, moved)
                return@later
            }
            if (world.tick >= mine.chasingUntil && npc.following !== player) world.npcs.follow(npc, player)
            if (mine.kind.stage == Stage.KITTEN && !tend(player, mine)) return@later
            grow(player, mine)
            watch(key, npc)
        }
    }

    private fun despawn(key: String) {
        val gone = out.remove(key) ?: return
        if (world.npcs[gone.npc.index] === gone.npc) world.removeNpc(gone.npc)
    }

    /** A kitten's hunger and loneliness, a tick on; false once it has run off. */
    private fun tend(player: Player, mine: Out): Boolean {
        val (hunger, attention) = care(player) ?: (HUNGER_TICKS to ATTENTION_TICKS)
        val h = hunger - 1
        val a = attention - 1
        setCare(player, h, a, strokeHolds(player) && a > ATTENTION_AT)
        val text = world.textFor(player)
        when (h) {
            HUNGRY_AT -> warn(player, null, CatLines.THINK_HUNGRY, CatLines.HUNGRY)
            VERY_HUNGRY_AT -> warn(player, mine.npc, CatLines.THINK_VERY_HUNGRY, CatLines.VERY_HUNGRY, CatLines.MEOW_HUNGRY)
        }
        when (a) {
            ATTENTION_AT -> warn(player, null, CatLines.THINK_ATTENTION, CatLines.ATTENTION)
            REALLY_ATTENTION_AT -> warn(player, mine.npc, CatLines.THINK_LONELY, CatLines.REALLY_ATTENTION, CatLines.MEOW_LONELY)
        }
        if (h > 0 && a > 0) return true
        mine.npc.effects.say(text[CatLines.MEOW_AWAY])
        runAway(player)
        return false
    }

    private fun warn(player: Player, npc: Npc?, said: Line, message: Line, meow: Line? = null) {
        val text = world.textFor(player)
        if (meow != null) npc?.effects?.say(text[meow])
        player.effects.say(text[said])
        player.message("<col=$RED>${text[message]}</col>")
    }

    /** The cat is gone for good: shooed, or neglected. */
    private fun runAway(player: Player) {
        player.message(world.textFor(player)[CatLines.RUN_AWAY])
        val gone = out.remove(player.saveKey)
        if (gone != null) world.npcScripts.later(RUN_OFF_TICKS) { if (world.npcs[gone.npc.index] === gone.npc) world.removeNpc(gone.npc) }
        setFollowerVarps(player, null, -1)
        forget(player)
    }

    /** A tick's growth, outside dialogue: a cycle every [GROWTH_CYCLE_TICKS], the kitten a cat at [GROWN_CYCLES]. */
    private fun grow(player: Player, mine: Out) {
        if (mine.kind.stage == Stage.OVERGROWN || player.chatModal >= 0) return
        if (++mine.growthTicks < GROWTH_CYCLE_TICKS) return
        mine.growthTicks = 0
        val cycles = growth(player) + 1
        setGrowth(player, cycles)
        val next = when {
            mine.kind.stage == Stage.KITTEN && cycles >= GROWN_CYCLES -> Stage.CAT
            mine.kind.stage == Stage.CAT && cycles >= OVERGROWN_CYCLES -> Stage.OVERGROWN
            else -> return
        }
        val kind = mine.kind.copy(stage = next)
        val type = world.npcTypes[kind.npc] ?: return
        val npc = mine.npc
        // A new type, so a new NPC where the old one stood.
        world.removeNpc(npc)
        val grown = world.spawnNpc(NpcSpawn(type, npc.x, npc.z, npc.level, source = "${player.username}'s cat", respawns = false)) ?: return
        out[player.saveKey] = Out(grown, kind)
        setFollowerVarps(player, grown, kind.obj)
        world.npcs.follow(grown, player)
        watch(player.saveKey, grown)
        if (next == Stage.CAT) {
            careVarp(player)?.let { player.vars[it] = 0 }
            grown.effects.say(world.textFor(player)[CatLines.GROWN_MEOW])
        }
        world.scripts.start(player) {
            world.dialogues.start(this) { mesbox(if (next == Stage.CAT) CatLines.GROWN else CatLines.OVERGROWN) }
        }
    }

    // ---- Talk-to and Interact -----------------------------------------------------------------------------------

    private fun ScriptContext.mine(npc: Npc): Out? {
        val mine = out[player.saveKey]
        if (mine?.npc === npc) return mine
        player.message(world.textFor(player)[CatLines.NOT_YOURS])
        return null
    }

    suspend fun ScriptContext.talk(npc: Npc) {
        val mine = mine(npc) ?: return
        world.dialogues.start(this, npc) {
            when (choice(CatLines.HOW_ARE_YOU, CatLines.HOW_OLD, CatLines.WHERE, CatLines.WHAT_NOW)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, CatLines.HOW_ARE_YOU)
                    when (mine.kind.stage) {
                        Stage.KITTEN -> chatNpc(ChatAnim.HAPPY, kittenMood(player))
                        Stage.CAT -> chatNpc(ChatAnim.HAPPY, CatLines.CAT_HOW_ARE_YOU)
                        Stage.OVERGROWN -> chatNpc(ChatAnim.ANGRY, CatLines.OVERGROWN_HOW_ARE_YOU)
                    }
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, CatLines.HOW_OLD)
                    when (mine.kind.stage) {
                        Stage.KITTEN -> {
                            kittenHowOld()
                            kittenAge(CatLines.KITTEN_AGE)
                        }
                        Stage.CAT -> chatNpc(ChatAnim.HAPPY, if (OVERGROWN_CYCLES - growth(player) <= FAT_CYCLES) CatLines.CAT_HOW_OLD_FAT else CatLines.CAT_HOW_OLD)
                        Stage.OVERGROWN -> CatLines.OVERGROWN_HOW_OLD.forEachIndexed { i, line ->
                            if (i == 2 || i == 4) chatPlayer(ChatAnim.CONFUSED, line) else chatNpc(ChatAnim.ANGRY, line)
                        }
                    }
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, CatLines.WHERE)
                    when (mine.kind.stage) {
                        Stage.KITTEN -> chatNpc(ChatAnim.HAPPY, CatLines.KITTEN_WHERE[world.random.nextInt(CatLines.KITTEN_WHERE.size)])
                        Stage.CAT -> chatNpc(ChatAnim.HAPPY, CatLines.CAT_WHERE)
                        Stage.OVERGROWN -> chatNpc(ChatAnim.NEUTRAL, CatLines.OVERGROWN_WHERE)
                    }
                }
                4 -> {
                    chatPlayer(ChatAnim.QUIZ, CatLines.WHAT_NOW)
                    when (mine.kind.stage) {
                        Stage.KITTEN -> chatNpc(ChatAnim.HAPPY, CatLines.KITTEN_WHAT[world.random.nextInt(CatLines.KITTEN_WHAT.size)])
                        Stage.CAT -> chatNpc(ChatAnim.HAPPY, CatLines.CAT_WHAT)
                        Stage.OVERGROWN -> {
                            chatNpc(ChatAnim.NEUTRAL, CatLines.OVERGROWN_WHAT[0])
                            chatPlayer(ChatAnim.NEUTRAL, CatLines.OVERGROWN_WHAT[1])
                            chatNpc(ChatAnim.NEUTRAL, CatLines.OVERGROWN_WHAT[2])
                        }
                    }
                }
            }
        }
    }

    private fun kittenMood(player: Player): Line = when {
        hungerLeft(player) <= VERY_HUNGRY_AT -> CatLines.KITTEN_HUNGRY
        attentionLeft(player) <= REALLY_ATTENTION_AT -> CatLines.KITTEN_LONELY
        else -> CatLines.KITTEN_HAPPY
    }

    private suspend fun Dialogue.kittenHowOld() {
        val minutes = growth(player) * GROWTH_CYCLE_MINUTES_X2 / 2
        val left = (GROWN_CYCLES - growth(player)).coerceAtLeast(0) * GROWTH_CYCLE_MINUTES_X2 / 2
        when {
            left < 15 -> chatNpc(ChatAnim.HAPPY, CatLines.KITTEN_ALMOST)
            minutes < 15 -> chatNpc(ChatAnim.HAPPY, CatLines.KITTEN_UNDER_A_MONTH)
            minutes < 30 -> chatNpc(ChatAnim.HAPPY, CatLines.KITTEN_A_MONTH)
            else -> chatNpc(ChatAnim.HAPPY, CatLines.KITTEN_MONTHS, "months" to minutes / 15)
        }
    }

    private suspend fun Dialogue.kittenAge(box: Line) {
        val minutes = growth(player) * GROWTH_CYCLE_MINUTES_X2 / 2
        val left = (GROWN_CYCLES - growth(player)).coerceAtLeast(0) * GROWTH_CYCLE_MINUTES_X2 / 2
        mesbox(box, "age_hours" to minutes / 60, "age_minutes" to minutes % 60, "left_hours" to left / 60, "left_minutes" to left % 60)
    }

    suspend fun ScriptContext.interact(npc: Npc) {
        val mine = mine(npc) ?: return
        val kitten = mine.kind.stage == Stage.KITTEN
        world.dialogues.start(this, npc) {
            val title = text(if (kitten) CatLines.INTERACT_KITTEN else CatLines.INTERACT_CAT)
            val options = if (kitten) {
                listOf(CatLines.STROKE, CatLines.CHASE_VERMIN, CatLines.GUESS_AGE, CatLines.SHOO_AWAY)
            } else {
                listOf(CatLines.STROKE, CatLines.CHASE_VERMIN, CatLines.SHOO_AWAY)
            }
            when (options[choice(*options.toTypedArray(), title = title) - 1]) {
                CatLines.STROKE -> stroke(mine)
                CatLines.CHASE_VERMIN -> {
                    chase(this@interact, mine)
                }
                CatLines.GUESS_AGE -> {
                    chatPlayer(ChatAnim.QUIZ, CatLines.WONDER_AGE)
                    kittenAge(CatLines.GUESSED_AGE)
                }
                CatLines.SHOO_AWAY -> shoo(mine)
            }
        }
    }

    private suspend fun Dialogue.stroke(mine: Out) {
        val text = world.textFor(player)
        player.anim(ANIM_STROKE)
        mine.npc.effects.say(text[CatLines.PURR])
        when (mine.kind.stage) {
            Stage.KITTEN -> {
                player.message(text[CatLines.STROKED])
                val (hunger, attention) = care(player) ?: (HUNGER_TICKS to ATTENTION_TICKS)
                // A second stroke while the first's holds is the 25 minutes of a fresh start (Wiki: "stroked more than once").
                val again = strokeHolds(player)
                setCare(player, hunger, maxOf(attention, if (again) ATTENTION_TICKS else STROKE_TICKS), stroked = true)
                chatPlayer(ChatAnim.HAPPY, CatLines.KITTEN_STROKE[0])
                chatNpc(ChatAnim.HAPPY, CatLines.KITTEN_STROKE[1])
                chatPlayer(ChatAnim.HAPPY, CatLines.KITTEN_STROKE[2])
            }
            Stage.CAT -> {
                chatPlayer(ChatAnim.HAPPY, CatLines.CAT_STROKE[0])
                chatNpc(ChatAnim.HAPPY, CatLines.CAT_STROKE[1])
            }
            Stage.OVERGROWN -> {
                player.message(text[CatLines.STROKED])
                chatPlayer(ChatAnim.HAPPY, CatLines.OVERGROWN_STROKE[0])
                chatNpc(ChatAnim.HAPPY, CatLines.OVERGROWN_STROKE[1])
            }
        }
    }

    private suspend fun Dialogue.shoo(mine: Out) {
        if (choice(CatLines.YES_SHOO, CatLines.NO_SHOO, title = text(CatLines.SURE_SHOO)) != 1) {
            player.message(text(CatLines.NOT_SHOOED))
            return
        }
        val pages = when (mine.kind.stage) {
            Stage.KITTEN -> CatLines.KITTEN_SHOO
            Stage.CAT -> CatLines.CAT_SHOO
            Stage.OVERGROWN -> CatLines.OVERGROWN_SHOO
        }
        // The kitten's are player, kitten, player; the others' player, cat.
        pages.forEachIndexed { i, line -> if (i == 1) chatNpc(ChatAnim.SAD, line) else chatPlayer(ChatAnim.ANGRY, line) }
        if (out[player.saveKey] !== mine) return
        player.effects.say(text(CatLines.SHOO_CAT))
        mine.npc.effects.say(text(CatLines.MEOW_AWAY))
        runAway(player)
    }

    /** The Chase op, and Interact's Chase vermin. */
    suspend fun ScriptContext.chaseOp(npc: Npc) {
        val mine = mine(npc) ?: return
        chase(this, mine)
    }

    private suspend fun chase(context: ScriptContext, mine: Out) {
        val player = context.player
        val text = world.textFor(player)
        if (mine.kind.stage == Stage.OVERGROWN) {
            player.message(text[CatLines.TOO_BIG])
            return
        }
        val cat = mine.npc
        val rat = world.npcs.near(cat.x, cat.z, cat.level, VERMIN_RANGE)
            .filter { it.type.name == RAT && !it.dead }
            .minByOrNull { maxOf(kotlin.math.abs(it.x - cat.x), kotlin.math.abs(it.z - cat.z)) }
        if (rat == null) {
            player.message(text[CatLines.NO_VERMIN])
            return
        }
        player.effects.say(text[CatLines.GO_ON])
        // Free of the player's interaction and of following, so it walks to the rat while the player waits.
        world.npcs.release(cat)
        world.npcs.stopFollowing(cat)
        // Through the NPC phase of the tick the chase ends on too: the op runs after that tick's NPC moves, so a
        // follow then would undo the last step before the catch is judged (Codex review on #980).
        mine.chasingUntil = world.tick + CHASE_TICKS + 1
        cat.destination = osrsps.map.Tile(rat.x, rat.z)
        cat.effects.say(text[CatLines.POUNCE])
        context.delay(CHASE_TICKS)
        if (out[player.saveKey] !== mine) return
        mine.chasingUntil = 0
        world.npcs.follow(cat, player)
        // Only a rat the cat got beside, as an NPC reaches what it fights: one behind a wall or too far to reach in
        // the chase gets away (Codex review on #980).
        val reached = cat.level == rat.level &&
            osrsps.map.Reach.reachedEntity(collision, cat.level, cat.x, cat.z, cat.type.size, rat.x, rat.z, rat.type.size, rat.type.size)
        if (!reached || rat.dead || rat.hidden || world.npcs[rat.index] !== rat) {
            player.message(text[CatLines.GOT_AWAY])
            return
        }
        rat.effects.say(text[CatLines.EEK])
        val odds = if (mine.kind.stage == Stage.KITTEN) KITTEN_CATCH else CAT_CATCH
        if (!world.random.chance(odds, 100)) {
            player.message(text[CatLines.GOT_AWAY])
            return
        }
        catch(rat)
        player.effects.say(text[CatLines.WELL_DONE])
        val rats = ratsCaught(player) + 1
        setRats(player, rats)
        CatLines.MILESTONES[rats]?.let { line -> world.dialogues.start(context) { chatPlayer(ChatAnim.HAPPY, line) } }
    }

    /**
     * The rat dies to the cat: dead where it stands for its death animation's ticks, so clients see the animation and
     * nothing can attack it, then gone with nothing dropped, and a fresh rat from its spawn at the respawn time, as a
     * combat death brings back, so no damage or poison carries over (Codex review on #980).
     */
    private fun catch(rat: Npc) {
        val def = world.combat.npcCombat[rat.type]
        if (def != null && def.anims.death >= 0) rat.effects.anim(def.anims.death)
        rat.dead = true
        // Its fight and its walk end with it, as a combat death's do, so the body doesn't follow anyone.
        rat.target = null
        rat.retreatingFrom = null
        world.npcs.stopFollowing(rat)
        world.npcs.stopFollowingNpc(rat)
        rat.destination = null
        val deathTicks = maxOf(def?.deathTicks ?: 1, 1)
        // It stands for its death ticks: an idle rat would otherwise wander off mid-animation (Codex review on #980).
        rat.holdTicks = deathTicks + 1
        val respawn = maxOf(def?.respawnTicks ?: RAT_RESPAWN_TICKS, deathTicks)
        world.npcScripts.later(deathTicks) {
            if (world.npcs[rat.index] !== rat) return@later
            world.removeNpc(rat)
            if (!rat.spawn.respawns) return@later
            // A rat's respawn (2 ticks) is shorter than its death, so the new one comes as the body goes.
            val left = respawn - deathTicks
            if (left > 0) world.npcScripts.later(left) { world.spawnNpc(rat.spawn) } else world.spawnNpc(rat.spawn)
        }
    }

    // ---- Items used on the cat ----------------------------------------------------------------------------------

    suspend fun ScriptContext.useOn(npc: Npc, obj: Int, slot: Int) {
        val mine = mine(npc) ?: return
        val text = world.textFor(player)
        val type = world.objTypes[obj] ?: return
        val milk = obj == BUCKET_OF_MILK
        // A banknote carries its item's name, but is no food.
        val food = milk || (!type.isCert && isFish(type.name))
        if (mine.kind.stage != Stage.KITTEN) {
            if (food || obj == BALL_OF_WOOL) world.dialogues.start(this, npc) { chatPlayer(ChatAnim.NEUTRAL, CatLines.DONT_FEED) }
            else player.message(text[GameLines.NOTHING_INTERESTING])
            return
        }
        val (hunger, attention) = care(player) ?: (HUNGER_TICKS to ATTENTION_TICKS)
        when {
            obj == BALL_OF_WOOL -> {
                setCare(player, hunger, maxOf(attention, WOOL_TICKS))
                npc.effects.say(text[CatLines.PURR_FED])
                world.dialogues.start(this, npc) { chatPlayer(ChatAnim.HAPPY, CatLines.WOOL) }
            }
            food -> {
                if (player.inventory[slot]?.id != obj) return
                if (milk) {
                    player.inventory.remove(slot, 1)
                    world.objTypes[BUCKET]?.let { player.inventory.add(it, 1) }
                    player.message(text[CatLines.LAPS])
                    npc.effects.say(text[CatLines.PURPURR])
                } else {
                    player.inventory.remove(slot, 1)
                    player.message(text[CatLines.GOBBLES])
                    npc.effects.say(text[CatLines.PURR_FED])
                }
                setCare(player, HUNGER_TICKS, attention)
            }
            else -> player.message(text[GameLines.NOTHING_INTERESTING])
        }
    }

    companion object {
        /** The cache's follower varps (gameval `follower_npc`, `follower_obj`). */
        const val FOLLOWER_NPC = 447
        const val FOLLOWER_OBJ = 448

        const val PICK_UP = 1
        const val TALK_TO = 3
        const val CHASE = 4
        const val INTERACT = 5

        const val BALL_OF_WOOL = 1759
        const val BUCKET_OF_MILK = 1927
        const val BUCKET = 1925
        const val RAT = "Rat"

        /** The Wiki's growth: a cycle every 150 ticks (90 seconds), grown at 120. */
        const val GROWTH_CYCLE_TICKS = 150
        const val GROWN_CYCLES = 120
        /** Guessed: overgrown 220 cycles (5.5 hours) after grown, the Wiki's "five to six hours". */
        const val OVERGROWN_CYCLES = GROWN_CYCLES + 220
        /** Guessed: the cat's "beginning to get a bit fat" in its last 30 minutes. */
        const val FAT_CYCLES = 20
        /** Three minutes a cycle, halved: 90 seconds. */
        const val GROWTH_CYCLE_MINUTES_X2 = 3

        /** Hunger: fed, 24 + 3 + 3 minutes to running off; hungry at 6 minutes left, very hungry at 3. */
        const val HUNGER_TICKS = 3000
        const val HUNGRY_AT = 600
        const val VERY_HUNGRY_AT = 300
        /** Attention: fresh, 25 + 7 + 7 minutes to running off; wanting it at 14 minutes left, really at 7. */
        const val ATTENTION_TICKS = 3900
        const val ATTENTION_AT = 1400
        const val REALLY_ATTENTION_AT = 700
        /** A stroke: 18 minutes to the warning. */
        const val STROKE_TICKS = 1800 + ATTENTION_AT
        /** A ball of wool: 51 minutes to the warning. */
        const val WOOL_TICKS = 5100 + ATTENTION_AT

        /** The Wiki's (Rat) catch odds, in a hundred. */
        const val KITTEN_CATCH = 10
        const val CAT_CATCH = 50

        /** Guessed: how far a cat looks for a rat, how long the chase takes, and a rat's respawn with no combat data. */
        const val VERMIN_RANGE = 8
        const val CHASE_TICKS = 3

        /** Guessed: how soon a follower with no room in the world at login tries again. */
        const val LOGIN_RETRY_TICKS = 10
        const val RAT_RESPAWN_TICKS = 10

        /** Guessed: a follower further than this from its player, or on another level, is put back beside them. */
        const val CATCH_UP = 12

        /** Guessed: how long a cat that ran off stays to meow. */
        const val RUN_OFF_TICKS = 2

        /** Guessed: the pick-up animation (a ground item's) and the stroke's. */
        const val ANIM_PICK_UP = 827
        const val ANIM_STROKE = 827

        const val RED = "ef1020"

        /** Set in the care varp once a kitten has been out. */
        private const val CARED = 1 shl 30
        private const val STROKED = 1 shl 29

        val KINDS: List<Kind> = Colour.entries.flatMap { colour -> Stage.entries.map { Kind(colour, it) } }
        val CAT_OBJS: Set<Int> = KINDS.map { it.obj }.toSet()
        val CAT_NPCS: Set<Int> = KINDS.map { it.npc }.toSet()

        fun kindOfObj(obj: Int): Kind? = KINDS.firstOrNull { it.obj == obj }

        /**
         * Fish a kitten eats (Wiki, Kitten): every raw or cooked fish but the eels it refuses and Barbarian fishing's.
         * The cooked karambwan is the one cooked fish whose name says so.
         */
        private val FISH = Regex(
            "^(Raw |Cooked )?(shrimps|anchovies|sardine|herring|mackerel|trout|cod|pike|salmon|tuna|rainbow fish|lobster|bass|swordfish|" +
                "monkfish|karambwanji|karambwan|shark|sea turtle|manta ray|anglerfish|dark crab|lava eel|cave eel|" +
                "poison karambwan|frog spawn)$",
            RegexOption.IGNORE_CASE,
        )

        fun isFish(name: String): Boolean = FISH.matches(name) && !name.equals("Raw cave eel", ignoreCase = true)
    }
}

fun registerCats(world: World, cats: Cats) = with(cats) {
    for (kind in Cats.KINDS) {
        world.handlers.onObj(kind.obj, DROP_OP) { op -> drop(player, op.sub) }
        world.handlers.onNpc(kind.npc, Cats.PICK_UP) { op -> pickUp(op.npc!!) }
        world.handlers.onNpc(kind.npc, Cats.TALK_TO) { op -> talk(op.npc!!) }
        world.handlers.onNpc(kind.npc, Cats.CHASE) { op -> chaseOp(op.npc!!) }
        world.handlers.onNpc(kind.npc, Cats.INTERACT) { op -> interact(op.npc!!) }
        world.handlers.onObjOnNpc(kind.npc) { op -> useOn(op.npc!!, op.obj, op.sub) }
    }
}

/** An item's Drop is its op 5. */
private const val DROP_OP = 5
