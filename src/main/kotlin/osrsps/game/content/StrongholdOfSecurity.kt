package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/**
 * The Stronghold of Security's lines (keys only; the words are in data/dialogue.txt). The doors' security questions
 * are the Wiki's Doors table, one row per answer: [question] gives each question's words, its answers ([option]) and
 * what the door says back to each ([reply]).
 */
object StrongholdLines : Lines("stronghold_of_security", "Stronghold of Security") {
    val EXPLORER_FIND = line("explorer_find", "Search on the Dead Explorer by the entrance, the notes not yet carried (the Stronghold notes page)")
    val EXPLORER_NOTHING = line("explorer_nothing", "Search on the Dead Explorer while carrying the notes")
    val GIFT_OPEN = line("gift_open", "Open on the Gift of Peace, the first time: the chest's message as it opens (Gift of Peace page)")
    val GIFT_REWARD = line("gift_reward", "Open on the Gift of Peace, the first time: the 2,000 coins and the Flap emote (Gift of Peace page)")
    val GIFT_CLAIMED = line("gift_claimed", "Open on the Gift of Peace once its reward is claimed")
    val PORTAL_REFUSE = line("portal_refuse", "Use on a floor's portal, below its combat level and the floor's reward unclaimed")
    val PORTAL_ENTER = line("portal_enter", "Use on a floor's portal, let through: the message as it takes the player to the reward room")
    val GRAIN_OPEN = line("grain_open", "Search on the Grain of Plenty, the first time: the message as it opens (Grain of Plenty page)")
    val GRAIN_REWARD = line("grain_reward", "Search on the Grain of Plenty, the first time: the 3,000 coins and the Slap Head emote")
    val GRAIN_CLAIMED = line("grain_claimed", "Search on the Grain of Plenty once its reward is claimed")
    val BOX_OPEN = line("box_open", "Open on the Box of Health, the first time: the message as it opens (Box of Health page)")
    val BOX_REWARD = line("box_reward", "Open on the Box of Health, the first time: the 5,000 coins, the Idea emote and the stats restored")
    val BOX_CLAIMED = line("box_claimed", "Open on the Box of Health once its reward is claimed")
    val CRADLE_OPEN = line("cradle_open", "Search on the Cradle of Life, the first time: the message as it opens (Cradle of Life page)")
    val CRADLE_REWARD = line("cradle_reward", "Search on the Cradle of Life, the first time: the Stamp emote and the boots to choose from")
    val CRADLE_AGAIN = line("cradle_again", "Search on the Cradle of Life once its reward is claimed, before the boots to choose from")
    val CRADLE_FANCY = line("cradle_fancy", "The Cradle of Life's choice of boots: the fancy boots' option")
    val CRADLE_FIGHTING = line("cradle_fighting", "The Cradle of Life's choice of boots: the fighting boots' option")
    val CRADLE_GIVEN = line("cradle_given", "The Cradle of Life, the boots picked given: the box with the boots", "name")
    val NOTICE_READ = line("notice_read", "Read on the notice in the Sepulchre of Death, by the Cradle of Life: the players thanked")

    /** The Doors table's questions, in its order: how many answers each has and which is right (from 1). */
    val QUESTIONS: List<Question> = listOf(
        3 to 3, 2 to 1, 2 to 2, 3 to 3, 3 to 2, 3 to 1, 2 to 2, 2 to 1, 3 to 3, 3 to 3, 2 to 2,
        3 to 3, 3 to 1, 3 to 2, 3 to 1, 3 to 1, 3 to 3, 3 to 2, 3 to 2, 2 to 2, 3 to 2, 3 to 2,
        2 to 2, 2 to 1, 3 to 1, 3 to 3, 3 to 2, 3 to 1, 3 to 3, 3 to 2, 3 to 2, 3 to 3, 3 to 3,
    ).mapIndexed { i, (answers, right) ->
        val n = i + 1
        Question(
            n,
            line("q$n", "Doors, the security questions table: question $n's words"),
            (1..answers).map { line("q${n}_option$it", "Doors, question $n: answer $it's words") },
            (1..answers).map { line("q${n}_reply$it", "Doors, question $n: what the door says to answer $it") },
            right,
        )
    }

    /** One security question: its words, its answers, the door's reply to each, and the right answer (from 1). */
    class Question(val number: Int, val question: Line, val options: List<Line>, val replies: List<Line>, val right: Int)
}

/**
 * The Stronghold of Security's entrance and first floor, the Vault of War (Edgeville piece step 12), from the OSRS Wiki's
 * Stronghold of Security, Gate of War, Gift of Peace and Stronghold notes pages (read 2026-11-03) and the 241 cache's
 * map (map square 29,81).
 *
 * - The entrance in the Barbarian Village mine (`Entrance` 20790 at 3081,3420) climbs down to the floor's ladder
 *   (20784 at 1859,5244), onto [LANDING]; that ladder, and the one in the reward room (1913,5226), climb up beside the
 *   entrance ([Church.STRONGHOLD], where Count Check sends players). The landing and the reward room's way up are
 *   guessed until recorded.
 * - The floor's rooms are split by pairs of doors (`Gate of War` 19206 and 19207, double leaves on a wall) with an
 *   empty passage between them. Open takes the player through to the other side at once ([through]). The second door
 *   of a pair, opened from the passage between them ([inPassage]), may ask a security question first: the gate's head
 *   asks (as NPC `sos_door_war` 2494), the player picks an answer, the gate replies, and only the right answer lets
 *   them through. The Gate of War page: no questions once the floor's reward is claimed. How often a door asks
 *   ([ASK_CHANCE]) and which question it picks (any, evenly) are guessed; the Wiki says a door may let a player
 *   through without asking.
 * - The Dead Explorer (20788 at 1860,5240) gives the Stronghold notes (9004) to a player not carrying them; reading
 *   them (the book) is left for later.
 * - The Gift of Peace (20656 at 1907,5222), opened once: 2,000 coins, the Flap emote (varbit `sos_emote_flap`, which
 *   is also how the floor remembers its reward is claimed), Hitpoints and Prayer back to full and its jingle
 *   (`sos_peace`). Every account counts as having two-factor authentication (the plan's decision). Opened again, it
 *   says so. The emote tab shows Flap unlocked; playing emotes isn't built yet.
 * - The portal by the entrance (20786 at 1863,5238) takes a player with the reward claimed, or of combat level 26 and
 *   up, to the reward room ([PORTAL_LANDING], guessed); anyone else is told no.
 * - The goblins, minotaurs, rats and wolves fight and drop as npc-combat.json and npc-drops.json have them
 *   (npc-spawns/stronghold-of-security.json), not aggressive; the floor is multi-way (multi-combat.tsv).
 *
 * The second floor, the Catacomb of Famine (Edgeville piece step 13a, map square 31,81), works as the first ([FLOORS]):
 *
 * - The reward room's ladder down (20785 at 1902,5222) lands beside the floor's ladder up (19003 at 2042,5246), which
 *   climbs back up beside the ladder down (the Boney ladder page: a floor's first ladder leads up to the floor above).
 *   Its four ropes (19001) climb up beside the entrance (inferred). The landings are guessed.
 * - Its Rickety doors (17009 and 17100) ask as the Gates of War do, as the door of famine (`sos_door_fam` 2495), and
 *   stop asking once the Grain of Plenty is searched.
 * - The Grain of Plenty (19000 at 2021,5215), searched once: 3,000 coins, the Slap Head emote (`sos_emote_doh`),
 *   Hitpoints and Prayer back to full, its jingle (`sos_plenty`).
 * - The portal (19005 at 2039,5240) lets through at combat level 51 or with the grain searched.
 * - Flesh crawlers, zombies, giant rats and rats as their Wiki pages; the crawlers and zombies are aggressive.
 *
 * The third floor, the Pit of Pestilence (step 13b, map square 33,82), is reached by the Catacomb's ladder down (19004)
 * and works as the others, its Box of Health restoring every lowered stat.
 *
 * The fourth, the Sepulchre of Death (step 14a, map square 36,81):
 *
 * - The Pit's dripping vine down (23706 at 2148,5284) lands by the Boney ladder (23921 at 2358,5216), which climbs back
 *   beside the vine (the Boney ladder page). Its two bone chains (23732) climb back to the floor's start (inferred, as
 *   the Catacomb's ropes). The landings are guessed.
 * - Its Portals of Death (23727 and 23728) ask as the door of death (`sos_door_death` 2497).
 * - The Cradle of Life (23731 at 2344,5214), searched: the first time the Stamp emote (`sos_emote_stamp`), Hitpoints and
 *   Prayer back to full and its jingle (`sos_life`), no coins (the Wiki's 10,000 for the whole stronghold is the
 *   first three rewards', inferred); then, and on every search after, a pair of fancy or fighting boots ([chooseBoots]).
 * - The portal (23922 at 2365,5212) has no combat level: it lets through once the Cradle is searched (the Portal page:
 *   it "only works after the player has looted that level's treasure"; the Stronghold page gives no level for it).
 * - The notice (7054) by the Cradle can be read.
 */
object StrongholdOfSecurity {
    const val ENTRANCE = 20790
    val ENTRANCE_TILE = Tile(3081, 3420)
    const val CLIMB_DOWN = 1

    const val LADDER_UP = 20784
    const val CLIMB_UP = 1

    /** The floor's two ladders up: by the entrance and in the reward room. */
    val LADDER_UP_TILES = listOf(Tile(1859, 5244), Tile(1913, 5226))

    /** Where climbing down puts the player: south of the floor's ladder up. Guessed. */
    val LANDING = Tile(1859, 5243)

    const val GATE_OF_WAR_LEFT = 19206
    const val GATE_OF_WAR_RIGHT = 19207
    val WAR_DOORS = setOf(GATE_OF_WAR_LEFT, GATE_OF_WAR_RIGHT)
    const val OPEN = 1

    /** `sos_door_war`: the Gate of War's head, which asks the questions. */
    const val GATE_OF_WAR_NPC = 2494

    /** A door that may ask asks 1 time in this many (guessed). */
    const val ASK_CHANCE = 2

    /** How far apart (in tiles) a pair's two walls may be: the cache's pairs are 3 and 4 apart. */
    val PASSAGE_WIDTHS = 2..5

    const val DEAD_EXPLORER = 20788
    val DEAD_EXPLORER_TILE = Tile(1860, 5240)
    const val SEARCH = 1
    const val NOTES = 9004

    const val GIFT_OF_PEACE = 20656
    val GIFT_OF_PEACE_TILE = Tile(1907, 5222)
    const val COINS = 995
    const val GIFT_COINS = 2000

    /** `sos_emote_flap`: the Flap emote, unlocked by the Gift of Peace; set once the floor's reward is claimed. */
    const val VARBIT_FLAP = 2309

    /** `sos_peace`, the Gift of Peace jingle. */
    const val JINGLE_PEACE = 1076

    const val PORTAL = 20786
    val PORTAL_TILE = Tile(1863, 5238)
    const val USE = 1
    const val PORTAL_LEVEL = 26

    /** Where the portal puts the player: the reward room, east of the Gift of Peace. Guessed. */
    val PORTAL_LANDING = Tile(1910, 5222)

    /** The Vault of War: map square 29,81, the ground level. */
    fun inVaultOfWar(level: Int, x: Int, z: Int) = level == 0 && x in 1856..1919 && z in 5184..5247

    /** The Vault of War's ladder down in the reward room, and where climbing back up from the floor below puts the player. */
    const val WAR_LADDER_DOWN = 20785
    val WAR_LADDER_DOWN_TILE = Tile(1902, 5222)
    val WAR_LADDER_DOWN_LANDING = Tile(1903, 5222)

    const val RICKETY_DOOR_LEFT = 17009
    const val RICKETY_DOOR_RIGHT = 17100
    const val FAMINE_DOOR_NPC = 2495
    const val FAMINE_LADDER_UP = 19003
    val FAMINE_LADDER_UP_TILE = Tile(2042, 5246)

    /** Where the ladder down from the Vault of War puts the player: south of the Catacomb of Famine's ladder up. Guessed. */
    val FAMINE_LANDING = Tile(2042, 5245)
    const val FAMINE_ROPE = 19001
    val FAMINE_ROPE_TILES = listOf(Tile(2011, 5192), Tile(2017, 5210), Tile(2031, 5189), Tile(2040, 5208))
    const val GRAIN_OF_PLENTY = 19000
    val GRAIN_OF_PLENTY_TILE = Tile(2021, 5215)
    const val GRAIN_COINS = 3000

    /** `sos_emote_doh`: the Slap Head emote, unlocked by the Grain of Plenty. */
    const val VARBIT_SLAP_HEAD = 2310
    const val JINGLE_PLENTY = 1171
    const val FAMINE_PORTAL = 19005
    val FAMINE_PORTAL_TILE = Tile(2039, 5240)

    /** Where the Catacomb of Famine's portal puts the player: east of the Grain of Plenty. Guessed. */
    val FAMINE_PORTAL_LANDING = Tile(2023, 5215)

    /** The Catacomb of Famine: map square 31,81, the ground level. */
    fun inCatacombOfFamine(level: Int, x: Int, z: Int) = level == 0 && x in 1984..2047 && z in 5184..5247

    /** The Catacomb of Famine's ladder down, and where climbing back up from the Pit of Pestilence puts the player. */
    const val FAMINE_LADDER_DOWN = 19004
    val FAMINE_LADDER_DOWN_TILE = Tile(2026, 5218)

    /** South of the ladder down. Guessed. */
    val FAMINE_LADDER_DOWN_LANDING = Tile(2026, 5217)

    const val OOZING_BARRIER_LEFT = 23653
    const val OOZING_BARRIER_RIGHT = 23654
    const val PESTILENCE_DOOR_NPC = 2496

    /** The Pit of Pestilence's dripping vine up, by its arrival; the ladder from the Catacomb puts the player north of it. Guessed. */
    const val PESTILENCE_VINE_UP = 23705
    val PESTILENCE_VINE_UP_TILE = Tile(2123, 5251)
    val PESTILENCE_LANDING = Tile(2123, 5252)

    /** The goo covered vine in the reward room, back to the start of the floor, as the Catacomb's ropes (inferred). */
    const val PESTILENCE_ROPE = 23703
    val PESTILENCE_ROPE_TILE = Tile(2150, 5278)
    const val BOX_OF_HEALTH = 23709
    val BOX_OF_HEALTH_TILE = Tile(2144, 5280)
    const val BOX_COINS = 5000

    /** `sos_emote_idea`: the Idea emote, unlocked by the Box of Health. */
    const val VARBIT_IDEA = 2311
    const val JINGLE_HEALTH = 1075
    const val PESTILENCE_PORTAL = 23707
    val PESTILENCE_PORTAL_TILE = Tile(2120, 5258)

    /** Where the Pit of Pestilence's portal puts the player: east of the Box of Health. Guessed. */
    val PESTILENCE_PORTAL_LANDING = Tile(2145, 5280)

    /** The Pit of Pestilence: map square 33,82, the ground level. */
    fun inPitOfPestilence(level: Int, x: Int, z: Int) = level == 0 && x in 2112..2175 && z in 5248..5311

    /** The Pit of Pestilence's dripping vine down, in its reward room, and where the Boney ladder puts the player back. Guessed. */
    const val PESTILENCE_VINE_DOWN = 23706
    val PESTILENCE_VINE_DOWN_TILE = Tile(2148, 5284)
    val PESTILENCE_VINE_DOWN_LANDING = Tile(2148, 5283)

    const val PORTAL_OF_DEATH_LEFT = 23727
    const val PORTAL_OF_DEATH_RIGHT = 23728
    const val DEATH_DOOR_NPC = 2497

    /** The Sepulchre of Death's Boney ladder up, by its arrival; the vine from the Pit puts the player south of it. Guessed. */
    const val DEATH_LADDER_UP = 23921
    val DEATH_LADDER_UP_TILE = Tile(2358, 5216)
    val DEATH_LANDING = Tile(2358, 5215)

    /** The floor's two bone chains, back to its start as the Catacomb's ropes (inferred). */
    const val BONE_CHAIN = 23732
    val BONE_CHAIN_TILES = listOf(Tile(2309, 5240), Tile(2350, 5215))
    const val CRADLE_OF_LIFE = 23731
    val CRADLE_OF_LIFE_TILE = Tile(2344, 5214)

    /** `sos_emote_stamp`: the Stamp emote, unlocked by the Cradle of Life. */
    const val VARBIT_STAMP = 2312

    /** `sos_life`, the Cradle of Life jingle. */
    const val JINGLE_LIFE = 1055
    const val FANCY_BOOTS = 9005
    const val FIGHTING_BOOTS = 9006
    const val DEATH_PORTAL = 23922
    val DEATH_PORTAL_TILE = Tile(2365, 5212)

    /** Where the Sepulchre of Death's portal puts the player: east of the Cradle of Life. Guessed. */
    val DEATH_PORTAL_LANDING = Tile(2345, 5214)

    /** `sos_thankplayers`, the notice by the Cradle of Life. */
    const val NOTICE = 7054
    val NOTICE_TILE = Tile(2349, 5211)
    const val READ = 1

    /** The Sepulchre of Death: map square 36,81, the ground level. */
    fun inSepulchreOfDeath(level: Int, x: Int, z: Int) = level == 0 && x in 2304..2367 && z in 5184..5247

    /**
     * One floor: its doors and the head that asks at them, the varbit its reward sets (its emote's), the reward
     * (loc, tile, option, coins, jingle and lines), and its portal (loc, tile, combat level and landing). A portal of
     * no combat level lets through only once the floor's reward is claimed.
     */
    class Floor(
        val name: String,
        val doors: Set<Int>,
        val doorNpc: Int,
        val varbit: Int,
        val contains: (level: Int, x: Int, z: Int) -> Boolean,
        val reward: Int,
        val rewardTile: Tile,
        val rewardOption: Int,
        val coins: Int,
        val jingle: Int,
        val opened: Line,
        val rewarded: Line,
        val claimed: Line,
        val portal: Int,
        val portalTile: Tile,
        val portalLevel: Int?,
        val portalLanding: Tile,
        /** Whether the reward restores every lowered stat (the Box of Health), not hitpoints and prayer alone. */
        val restoresAll: Boolean = false,
        /** Whether the reward gives a choice of boots, each time it is searched (the Cradle of Life). */
        val boots: Boolean = false,
    )

    val FLOORS: List<Floor> = listOf(
        Floor(
            "Vault of War", WAR_DOORS, GATE_OF_WAR_NPC, VARBIT_FLAP, ::inVaultOfWar,
            GIFT_OF_PEACE, GIFT_OF_PEACE_TILE, OPEN, GIFT_COINS, JINGLE_PEACE,
            StrongholdLines.GIFT_OPEN, StrongholdLines.GIFT_REWARD, StrongholdLines.GIFT_CLAIMED,
            PORTAL, PORTAL_TILE, PORTAL_LEVEL, PORTAL_LANDING,
        ),
        Floor(
            "Catacomb of Famine", setOf(RICKETY_DOOR_LEFT, RICKETY_DOOR_RIGHT), FAMINE_DOOR_NPC, VARBIT_SLAP_HEAD,
            ::inCatacombOfFamine, GRAIN_OF_PLENTY, GRAIN_OF_PLENTY_TILE, SEARCH, GRAIN_COINS, JINGLE_PLENTY,
            StrongholdLines.GRAIN_OPEN, StrongholdLines.GRAIN_REWARD, StrongholdLines.GRAIN_CLAIMED,
            FAMINE_PORTAL, FAMINE_PORTAL_TILE, 51, FAMINE_PORTAL_LANDING,
        ),
        // The Box of Health restores "all stats including hitpoints, prayer points and strength" (OSRS Wiki, Box of Health).
        Floor(
            "Pit of Pestilence", setOf(OOZING_BARRIER_LEFT, OOZING_BARRIER_RIGHT), PESTILENCE_DOOR_NPC, VARBIT_IDEA,
            ::inPitOfPestilence, BOX_OF_HEALTH, BOX_OF_HEALTH_TILE, OPEN, BOX_COINS, JINGLE_HEALTH,
            StrongholdLines.BOX_OPEN, StrongholdLines.BOX_REWARD, StrongholdLines.BOX_CLAIMED,
            PESTILENCE_PORTAL, PESTILENCE_PORTAL_TILE, 76, PESTILENCE_PORTAL_LANDING, restoresAll = true,
        ),
        // The Cradle of Life: no coins (the stronghold's 10,000 is the first three rewards' 2,000 + 3,000 + 5,000,
        // INFERRED), the Stamp emote, hitpoints and prayer, and boots each time; its portal has no combat level.
        Floor(
            "Sepulchre of Death", setOf(PORTAL_OF_DEATH_LEFT, PORTAL_OF_DEATH_RIGHT), DEATH_DOOR_NPC, VARBIT_STAMP,
            ::inSepulchreOfDeath, CRADLE_OF_LIFE, CRADLE_OF_LIFE_TILE, SEARCH, 0, JINGLE_LIFE,
            StrongholdLines.CRADLE_OPEN, StrongholdLines.CRADLE_REWARD, StrongholdLines.CRADLE_AGAIN,
            DEATH_PORTAL, DEATH_PORTAL_TILE, null, DEATH_PORTAL_LANDING, boots = true,
        ),
    )

    fun claimed(player: Player, floor: Floor): Boolean =
        player.vars.types.varbit(floor.varbit) != null && player.vars.getVarbit(floor.varbit) != 0

    fun mayUsePortal(player: Player, floor: Floor): Boolean =
        claimed(player, floor) || floor.portalLevel != null && player.skills.combatLevel >= floor.portalLevel

    fun isAt(loc: ZoneLoc, tile: Tile) = loc.level == 0 && loc.x == tile.x && loc.z == tile.z


    /**
     * A wall's crossing line: for a door on the west (angle 0) or east (2) edge of its tile the x between the tiles
     * either side, for one on the south (3) or north (1) edge the z; the player crosses from line - 1 to line or back.
     */
    fun crossing(door: ZoneLoc): Int = when (door.angle) {
        0 -> door.x
        1 -> door.z + 1
        2 -> door.x + 1
        else -> door.z
    }

    private fun alongX(door: ZoneLoc) = door.angle == 1 || door.angle == 3

    /**
     * Where door [door] takes a player at [x], [z]: across its wall from the side they stand on, in the row along the
     * wall they stand in when that is a leaf of the same door ([isLeaf] says which tiles are), else in the clicked
     * leaf's. A player beside it along the wall counts, since the route reaches a wall from those tiles too; anyone
     * else is carried nowhere (null).
     */
    fun through(door: ZoneLoc, x: Int, z: Int, isLeaf: (along: Int) -> Boolean): Tile? {
        val line = crossing(door)
        val (across, along, doorAlong) = if (alongX(door)) Triple(z, x, door.x) else Triple(x, z, door.z)
        if (along !in doorAlong - 1..doorAlong + 1) return null
        val to = when (across) {
            line - 1 -> line
            line -> line - 1
            else -> return null
        }
        val row = if (along == doorAlong || isLeaf(along)) along else doorAlong
        return if (alongX(door)) Tile(row, to) else Tile(to, row)
    }

    /**
     * Whether a player at [x], [z] going through [door] stands in the passage of its pair, so that this is the pair's
     * second door: another door's wall ([wallAt] gives the crossing line of a door wall at a crossing coordinate and
     * row, or null) lies [PASSAGE_WIDTHS] behind them, in the same row.
     */
    fun inPassage(door: ZoneLoc, x: Int, z: Int, wallAt: (across: Int, along: Int) -> Int?): Boolean {
        val line = crossing(door)
        val (across, along) = if (alongX(door)) z to x else x to z
        val behind = if (across == line) 1 else if (across == line - 1) -1 else return false
        for (width in PASSAGE_WIDTHS) {
            val other = line + behind * width
            // The other wall's leaf sits on the tile either side of its crossing line.
            for (tile in listOf(other - 1, other)) if (wallAt(tile, along) == other) return true
        }
        return false
    }
}

fun registerStrongholdOfSecurity(world: World) {
    with(StrongholdOfSecurity) {
        world.handlers.onLoc(ENTRANCE, CLIMB_DOWN) { op ->
            if (!isAt(op.loc!!, ENTRANCE_TILE)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            player.anim(Climbing.ANIM_PICKUP_FLOOR)
            delay(1)
            world.teleport(player, LANDING.x, LANDING.z, 0)
        }
        world.handlers.onLoc(LADDER_UP, CLIMB_UP) { op ->
            val loc = op.loc!!
            if (LADDER_UP_TILES.none { isAt(loc, it) }) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            player.anim(Climbing.ANIM_REACH_FOR_LADDER)
            delay(1)
            world.teleport(player, Church.STRONGHOLD.x, Church.STRONGHOLD.z, 0)
        }

        world.handlers.onLoc(DEAD_EXPLORER, SEARCH) { op ->
            if (!isAt(op.loc!!, DEAD_EXPLORER_TILE)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            if (player.inventory.count(NOTES) > 0L) return@onLoc world.message(player, StrongholdLines.EXPLORER_NOTHING)
            val notes = world.objTypes[NOTES] ?: return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            world.groundObjs.give(player, notes, 1)
            world.message(player, StrongholdLines.EXPLORER_FIND)
        }

        // Between the floors: the Vault of War's ladder down and the Catacomb of Famine's ladder back up. Its ropes "return
        // you to the start of the level" (Jagex's Catacomb of Famine guide, 2011.rs knowledge base; inferred for OSRS).
        world.handlers.onLoc(WAR_LADDER_DOWN, CLIMB_DOWN) { op ->
            if (!isAt(op.loc!!, WAR_LADDER_DOWN_TILE)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            climbTo(world, Climbing.ANIM_PICKUP_FLOOR, FAMINE_LANDING)
        }
        world.handlers.onLoc(FAMINE_LADDER_UP, CLIMB_UP) { op ->
            if (!isAt(op.loc!!, FAMINE_LADDER_UP_TILE)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            climbTo(world, Climbing.ANIM_REACH_FOR_LADDER, WAR_LADDER_DOWN_LANDING)
        }
        world.handlers.onLoc(FAMINE_ROPE, CLIMB_UP) { op ->
            val loc = op.loc!!
            if (FAMINE_ROPE_TILES.none { isAt(loc, it) }) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            climbTo(world, Climbing.ANIM_REACH_FOR_LADDER, FAMINE_LANDING)
        }

        world.handlers.onLoc(FAMINE_LADDER_DOWN, CLIMB_DOWN) { op ->
            if (!isAt(op.loc!!, FAMINE_LADDER_DOWN_TILE)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            climbTo(world, Climbing.ANIM_PICKUP_FLOOR, PESTILENCE_LANDING)
        }
        world.handlers.onLoc(PESTILENCE_VINE_UP, CLIMB_UP) { op ->
            if (!isAt(op.loc!!, PESTILENCE_VINE_UP_TILE)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            climbTo(world, Climbing.ANIM_REACH_FOR_LADDER, FAMINE_LADDER_DOWN_LANDING)
        }
        world.handlers.onLoc(PESTILENCE_ROPE, CLIMB_UP) { op ->
            if (!isAt(op.loc!!, PESTILENCE_ROPE_TILE)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            climbTo(world, Climbing.ANIM_REACH_FOR_LADDER, PESTILENCE_LANDING)
        }

        world.handlers.onLoc(PESTILENCE_VINE_DOWN, CLIMB_DOWN) { op ->
            if (!isAt(op.loc!!, PESTILENCE_VINE_DOWN_TILE)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            climbTo(world, Climbing.ANIM_PICKUP_FLOOR, DEATH_LANDING)
        }
        world.handlers.onLoc(DEATH_LADDER_UP, CLIMB_UP) { op ->
            if (!isAt(op.loc!!, DEATH_LADDER_UP_TILE)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            climbTo(world, Climbing.ANIM_REACH_FOR_LADDER, PESTILENCE_VINE_DOWN_LANDING)
        }
        world.handlers.onLoc(BONE_CHAIN, CLIMB_UP) { op ->
            val loc = op.loc!!
            if (BONE_CHAIN_TILES.none { isAt(loc, it) }) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            climbTo(world, Climbing.ANIM_REACH_FOR_LADDER, DEATH_LANDING)
        }
        world.handlers.onLoc(NOTICE, READ) { op ->
            if (!isAt(op.loc!!, NOTICE_TILE)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            world.dialogues.start(this) { mesbox(StrongholdLines.NOTICE_READ) }
        }

        for (floor in FLOORS) {
            for (leaf in floor.doors) world.handlers.onLoc(leaf, OPEN) { op -> throughDoor(world, op.loc!!, floor) }

            world.handlers.onLoc(floor.reward, floor.rewardOption) { op ->
                if (!isAt(op.loc!!, floor.rewardTile)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
                arriveDelay()
                if (claimed(player, floor) || player.vars.types.varbit(floor.varbit) == null) {
                    return@onLoc world.dialogues.start(this) {
                        mesbox(floor.claimed)
                        if (floor.boots) chooseBoots(world, player)
                    }
                }
                // Claimed before the box shows, so nothing during it can open the reward twice.
                player.vars.setVarbit(floor.varbit, 1)
                if (floor.coins > 0) world.objTypes[COINS]?.let { world.groundObjs.give(player, it, floor.coins) }
                val hitpoints = player.skills.baseLevel(Skill.HITPOINTS)
                if (player.hitpoints < hitpoints) player.hitpoints = hitpoints
                // Lowered stats only: a boost stays.
                for (skill in if (floor.restoresAll) Skill.entries.filter { it != Skill.HITPOINTS } else listOf(Skill.PRAYER)) {
                    val base = player.skills.baseLevel(skill)
                    if (player.skills.currentLevel(skill) < base) player.skills.setCurrentLevel(skill, base)
                }
                player.jingle(floor.jingle)
                world.dialogues.start(this) {
                    mesbox(floor.opened)
                    mesbox(floor.rewarded)
                    if (floor.boots) chooseBoots(world, player)
                }
            }

            world.handlers.onLoc(floor.portal, USE) { op ->
                if (!isAt(op.loc!!, floor.portalTile)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
                arriveDelay()
                if (!mayUsePortal(player, floor)) return@onLoc world.dialogues.start(this) { mesbox(StrongholdLines.PORTAL_REFUSE) }
                world.message(player, StrongholdLines.PORTAL_ENTER)
                world.teleport(player, floor.portalLanding.x, floor.portalLanding.z, 0)
            }
        }
    }
}

/**
 * The Cradle of Life's boots: fancy or fighting, each the same, given at once (on the floor if the pack is full). Every
 * account counts as having two-factor authentication and none is a Jagex Account ([Church]), so the fancier boots
 * aren't offered.
 */
private suspend fun Dialogue.chooseBoots(world: World, player: Player) {
    val picked = choice(StrongholdLines.CRADLE_FANCY, StrongholdLines.CRADLE_FIGHTING)
    val type = world.objTypes[if (picked == 1) StrongholdOfSecurity.FANCY_BOOTS else StrongholdOfSecurity.FIGHTING_BOOTS] ?: return
    world.groundObjs.give(player, type, 1)
    objbox(type.id, text(StrongholdLines.CRADLE_GIVEN, "name" to type.name))
}

/** A ladder or rope between places: the climbing animation, a tick, then [to] on the ground level. */
private suspend fun ScriptContext.climbTo(world: World, anim: Int, to: Tile) {
    arriveDelay()
    player.anim(anim)
    delay(1)
    world.teleport(player, to.x, to.z, 0)
}

/**
 * Open on a door leaf [door] of [floor]: through it at once, unless this is the second door of its pair, the floor's
 * reward isn't claimed and the door asks a question ([StrongholdOfSecurity.ASK_CHANCE]), in which case only the right
 * answer lets the player through.
 */
private suspend fun ScriptContext.throughDoor(world: World, door: ZoneLoc, floor: StrongholdOfSecurity.Floor) {
    val doors = floor.doors
    with(StrongholdOfSecurity) {
        if (!floor.contains(door.level, door.x, door.z)) return world.message(player, GameLines.NOTHING_INTERESTING)
        val alongX = door.angle == 1 || door.angle == 3
        // A door leaf of the set on a wall parallel to this one, [across] being its tile's x (or z) across the wall.
        val leafAt = { across: Int, along: Int ->
            val (x, z) = if (alongX) along to across else across to along
            world.locs[x, z, door.level, 0]?.takeIf { it.id in doors && (it.angle and 1) == (door.angle and 1) }
        }
        val doorAcross = if (alongX) door.z else door.x
        val from = Tile(player.x, player.z)
        val to = through(door, from.x, from.z) { along -> leafAt(doorAcross, along)?.angle == door.angle }
            ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        arriveDelay()
        // The passage is checked in the row the player crosses in (the leaf's), not the one they stand in: from the tile
        // beside a door along its wall they cross in the leaf's row, and the pair's other door is in that row.
        val row = if (alongX) Tile(to.x, from.z) else Tile(from.x, to.z)
        val asks = !claimed(player, floor) &&
            inPassage(door, row.x, row.z) { across, along -> leafAt(across, along)?.let(::crossing) } &&
            world.random.nextInt(ASK_CHANCE) == 0
        if (asks) {
            val type = world.npcTypes[floor.doorNpc]
            val question = StrongholdLines.QUESTIONS[world.random.nextInt(StrongholdLines.QUESTIONS.size)]
            var right = false
            world.dialogues.start(this@throughDoor) {
                if (type != null) chatNpc(type, type.name, ChatAnim.NEUTRAL, question.question) else mesbox(question.question)
                val picked = choice(*question.options.toTypedArray())
                val reply = question.replies[picked - 1]
                if (type != null) chatNpc(type, type.name, ChatAnim.NEUTRAL, reply) else mesbox(reply)
                right = picked == question.right
            }
            // Only from where they answered: a player who moved off meanwhile goes nowhere.
            if (!right || player.x != from.x || player.z != from.z || player.level != door.level) return
        }
        world.teleport(player, to.x, to.z, door.level)
    }
}
