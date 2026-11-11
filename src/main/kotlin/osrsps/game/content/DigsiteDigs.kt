package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.script.ScriptContext

/**
 * The Digsite workmen's lines as they stop a player using the wrong tool, the sacks', the buried skeleton's and the
 * panning tray's; from the Wiki's Transcript:Digsite workman, Transcript:Sacks (Digsite), Transcript:Buried skeleton
 * (Digsite) and Transcript:The Dig Site; and our own lines for the digging, the finds and the panning.
 */
object DigsiteDigLines : Lines("digsite_dig", "Transcripts: Digsite workman, Sacks (Digsite), Buried skeleton (Digsite), The Dig Site") {
    val ON_SPADE = line("spade", "a workman, as the player digs soil with a spade")
    val ON_BRUSH = line("brush", "a workman, as the player uses a specimen brush on soil")
    val GLOVES = line("gloves", "a workman, trowel on a level 1 site without leather gloves on")
    val NO_GLOVES = line("no_gloves", "the player's answer")
    val GET_GLOVES = line("get_gloves", "the workman's reply")
    val BOOTS = line("boots", "a workman, trowel on a level 1 site with gloves but no leather boots on")
    val NEED_ROCK_PICK = line("need_rock_pick", "a workman, trowel on a level 2 site")
    val ROCK_PICK_LEVEL_2 = line("rock_pick_level_2", "a workman, rock pick on a level 1 or 3 site")
    val NO_DIGGING_TRAYS = line("no_digging_trays", "a workman, trowel on a specimen tray")
    val OI = line("oi", "a workman, as the player searches a specimen tray with no specimen jar")
    val ERRAND = line("errand", "the menu's option 1, which the player then says")
    val WHOSE_ERRAND = line("whose_errand", "option 1: his reply")
    val DONT_HAVE_ONE = line("dont_have_one", "option 1: the player's answer")
    val SORRY_CANT = line("sorry_cant", "option 1: his last line")
    val SEARCHING_TRAY = line("searching_tray", "the menu's option 2, which the player then says")
    val WHERES_YOUR_JAR = line("wheres_your_jar", "option 2: his reply")
    val AH_DONT_HAVE = line("ah_dont_have", "option 2: the player's answer")
    val BRING_IT = line("bring_it", "option 2: his last line")
    val SKELETON_FRAGILE = line("skeleton_fragile", "a workman, as the player uses a trowel on a buried skeleton")
    val SKELETON_SORRY = line("skeleton_sorry", "the player's answer")
    val SEARCH_SACKS = line("search_sacks", "Search on sacks: the first message")
    val SACKS_NOTHING = line("sacks_nothing", "Search on sacks with nothing in them, or a jar already carried")
    val SOMETHING_UNDER = line("something_under", "the player, finding the jar under a specimen-jar sack")
    val FIND_JAR = line("find_jar", "the item box for the specimen jar")
    val SEARCH_SKELETON = line("search_skeleton", "Search on a buried skeleton: the first message")
    val SKELETON_NOTHING = line("skeleton_nothing", "the second message")
    val FULL_TRAY = line("full_tray", "the player, panning with a full panning tray and no empty one")
    val DIG = line("dig", "OUR OWN: digging at a site: the first message")
    val FIND = line("find", "OUR OWN: a dig, a tray search or a panning tray search that finds an item", "item")
    val FIND_NOTHING = line("find_nothing", "OUR OWN: a dig or a search that finds nothing")
    val NOT_A_SITE = line("not_a_site", "OUR OWN: a digging tool used on soil outside the dig sites")
    val LEVEL_3_KIT = line("level_3_kit", "OUR OWN: a trowel on a level 3 site without a specimen brush and a specimen jar")
    val SEARCH_TRAY = line("search_tray", "OUR OWN: Search on a specimen tray: the first message")
    val PAN = line("pan", "OUR OWN: panning at a panning point")
    val NO_PANNING_TRAY = line("no_panning_tray", "OUR OWN: Pan with no panning tray")
    val SEARCH_PANNING_TRAY = line("search_panning_tray", "OUR OWN: Search on a panning tray full of mud: the first message")
}

/** One line of a finds table: [obj] ([min] to [max]; -1 for nothing), weighted [weight] among its table's others. */
class Find(val obj: Int, val weight: Int, val min: Int = 1, val max: Int = min)

/**
 * Misthalin piece 7 step 9b: digging, the specimen trays and panning at the Digsite, after The Dig Site (every exam
 * certificate counts as earned). Sources: the OSRS Wiki's Soil, Specimen tray, Panning point, Panning tray, Specimen
 * jar, Sacks (Digsite), Buried skeleton (Digsite), Trowel, Rock pick and Digsite workman pages and the transcripts on
 * [DigsiteDigLines], read 2026-11-11, and the 242 cache's map (475 soil tiles, `soil1` to `soil3`, ground decoration).
 *
 * - A dig is a tool used on soil. Which site it is depends on the tile the player stands on, as the Wiki says: the
 *   training digs (a trowel, 5 XP), level 1 (a trowel with leather gloves and boots worn, 6 XP), level 2 (a rock pick,
 *   7 XP) and level 3 (a trowel with a specimen brush and a specimen jar carried, 8 XP), each with the Wiki's finds
 *   table. The wrong tool brings the nearest Digsite workman's line (or the line as a message when none is near); a
 *   spade or a specimen brush on soil likewise. Level 3's ancient talisman is the quest's, so coins take its place.
 * - A specimen tray searched with a specimen jar gives 0.5 Mining XP and a find from its table; without a jar the
 *   nearest workman stops the player. A trowel on a tray brings a workman's line.
 * - A panning point (Pan, or an empty panning tray used on it) fills the tray with mud for 2 Fishing and 2 Mining XP;
 *   Search on the muddy tray empties it and gives a find from its table. The special cup is the quest's, so it is
 *   nothing now (inferred). One empty tray lies in the panning tent (obj-spawns/digsite.tsv).
 * - The specimen-jar sacks give a jar to a player carrying none; the other sacks and the buried skeletons hold nothing.
 *   A trowel on a skeleton brings a workman's line when one is near.
 *
 * Guessed: the workmen's and the player's chathead expressions; one find per use, after [DIG_TICKS] ticks of the
 * digging animation (the Wiki gives no time); the animations; a workman within [WORKMAN_RANGE] tiles counting as near;
 * a full backpack refusing before the dig; every message marked OUR OWN on [DigsiteDigLines]. Left out: the barrel and
 * chest (the quest's), the bushes, the winches (step 10), the museum's specimen rocks and cleaning, the gold panning
 * tray, Fossil Island's soil.
 */
object DigsiteDigs {
    const val SOIL_1 = 2376
    const val SOIL_2 = 2377
    const val SOIL_3 = 2378
    val SOILS = listOf(SOIL_1, SOIL_2, SOIL_3)

    const val TROWEL = 676
    const val ROCK_PICK = 675
    const val SPECIMEN_BRUSH = 670
    const val SPECIMEN_JAR = 669
    const val SPADE = 952
    const val LEATHER_GLOVES = 1059
    const val LEATHER_BOOTS = 1061
    const val PANNING_TRAY = 677
    const val PANNING_TRAY_MUD = 679

    const val SPECIMEN_TRAY = 2375
    const val TRAY_SEARCH = 2
    const val PANNING_POINT = 2363
    const val PAN = 1
    const val JAR_SACKS = 2356
    const val SACKS = 2354
    const val SEARCH = 1
    const val SKELETON = 19996

    /** The Digsite workmen who stop a player (Talk-to and Steal-from types). */
    val WORKMEN = Digsite.WORKMEN

    const val WORKMAN_RANGE = 10
    /**
     * Guessed, and slow on purpose until live is recorded: at 3 ticks a dig gave about 13.7k Mining XP an hour from level
     * 1 with free tools (the exploit review's R26C-2); 8 gives about a third of that.
     */
    const val DIG_TICKS = 8

    /**
     * Guessed, slow on purpose as [DIG_TICKS] is until live is recorded: at 3 ticks panning gave about 4k Mining and 4k
     * Fishing XP an hour from level 1, more than the slowed digs (the exploit review's R27B-3).
     */
    const val PAN_TICKS = 8

    /** farming_trowel_digging, qip_digisite_rockpick_anim, qip_digsite_panning_01 (guessed). */
    const val ANIM_TROWEL = 2272
    const val ANIM_ROCK_PICK = 4592
    const val ANIM_PAN = 4593

    const val COINS = 995
    const val BONES = 526

    enum class Site(val xpTenths: Int, val finds: List<Find>) {
        TRAINING(
            50,
            listOf(Find(-1, 8), Find(973, 1), Find(COINS, 1), Find(687, 1), Find(674, 1), Find(710, 1)),
        ),
        LEVEL_1(
            60,
            listOf(
                Find(-1, 2), Find(688, 1), Find(710, 1), Find(436, 1), Find(LEATHER_BOOTS, 1), Find(1609, 1), Find(695, 1), Find(1984, 1),
                Find(690, 1), Find(686, 1), Find(BONES, 1),
            ),
        ),
        LEVEL_2(
            70,
            listOf(
                Find(-1, 3), Find(BONES, 2), Find(697, 1), Find(LEATHER_BOOTS, 1), Find(1923, 1), Find(689, 1), Find(698, 1), Find(1627, 1),
                Find(690, 1), Find(1935, 1), Find(1931, 1), Find(434, 1), Find(1625, 1),
            ),
        ),
        LEVEL_3(
            80,
            listOf(
                Find(-1, 1), Find(684, 1), Find(1151, 1), Find(BONES, 1), Find(698, 1), Find(687, 1), Find(689, 1), Find(1237, 1),
                Find(688, 1), Find(694, 1), Find(434, 1), Find(COINS, 3, 10), Find(697, 1), Find(863, 1), Find(LEATHER_BOOTS, 1),
                Find(1733, 1), Find(695, 1), Find(2313, 1),
            ),
        ),
    }

    /** The Wiki's site maps: the training digs, levels 1, 2 and 3, as x and z ranges on plane 0. */
    val AREAS: List<Pair<Site, Pair<IntRange, IntRange>>> = listOf(
        Site.TRAINING to (3367..3373 to 3397..3401),
        Site.TRAINING to (3352..3358 to 3396..3401),
        Site.LEVEL_1 to (3367..3373 to 3403..3415),
        Site.LEVEL_1 to (3360..3364 to 3402..3415),
        Site.LEVEL_2 to (3350..3364 to 3424..3431),
        Site.LEVEL_2 to (3350..3356 to 3415..3419),
        Site.LEVEL_2 to (3367..3373 to 3423..3431),
        Site.LEVEL_3 to (3350..3358 to 3404..3413),
        Site.LEVEL_3 to (3370..3378 to 3437..3443),
    )

    /** The Wiki's specimen tray table, out of 11. */
    val TRAY_FINDS = listOf(
        Find(-1, 2), Find(COINS, 1), Find(1203, 1), Find(973, 1), Find(BONES, 2), Find(687, 1), Find(690, 1), Find(694, 1), Find(674, 1),
    )

    /** The Wiki's muddy panning tray table, out of 40; the quest's special cup (3/40) is nothing now. */
    val PANNING_FINDS = listOf(
        Find(COINS, 4, 1, 10), Find(680, 4), Find(407, 3), Find(1625, 3), Find(1627, 3), Find(-1, 23),
    )

    fun siteAt(x: Int, z: Int, level: Int): Site? =
        if (level != 0) null else AREAS.firstOrNull { (_, area) -> x in area.first && z in area.second }?.first

    fun roll(world: World, finds: List<Find>): Pair<Int, Int> {
        var roll = world.random.nextInt(finds.sumOf { it.weight })
        for (find in finds) {
            if (roll < find.weight) return find.obj to world.random.between(find.min, find.max)
            roll -= find.weight
        }
        error("Unreachable")
    }

    fun nearestWorkman(world: World, player: Player): Npc? =
        world.npcs.near(player.x, player.z, player.level, WORKMAN_RANGE)
            .filter { it.type.id in WORKMEN && !it.outOfPlay }
            .minByOrNull { maxOf(kotlin.math.abs(it.x - player.x), kotlin.math.abs(it.z - player.z)) }

    /** The nearest workman says [lines] (a player's answer in [answers] by index), or the first line shows as a message. */
    suspend fun ScriptContext.workman(world: World, vararg lines: Pair<Line, Boolean>) {
        val npc = nearestWorkman(world, player) ?: return world.message(player, lines.first().first)
        world.dialogues.start(this, npc) {
            for ((line, isPlayer) in lines) if (isPlayer) chatPlayer(ChatAnim.NEUTRAL, line) else chatNpc(ChatAnim.ANGRY, line)
        }
    }

    private fun worn(player: Player, slot: Int, obj: Int) = player.equipment[slot]?.id == obj

    private fun give(world: World, player: Player, obj: Int, count: Int) {
        if (obj < 0) {
            world.message(player, DigsiteDigLines.FIND_NOTHING)
            return
        }
        val type = world.objTypes[obj] ?: return
        // Whatever a full coin stack can't take lands on the player's tile, so no find is lost.
        world.groundObjs.give(player, type, count)
        world.message(player, DigsiteDigLines.FIND, "item" to type.name)
    }

    /** A digging [tool] (trowel, rock pick, spade or brush) used on soil. */
    suspend fun ScriptContext.dig(world: World, tool: Int) = with(DigsiteDigLines) {
        arriveDelay()
        when (tool) {
            SPADE -> return@with workman(world, ON_SPADE to false)
            SPECIMEN_BRUSH -> return@with workman(world, ON_BRUSH to false)
        }
        val site = siteAt(player.x, player.z, player.level) ?: return@with world.message(player, NOT_A_SITE)
        when {
            tool == ROCK_PICK && site != Site.LEVEL_2 ->
                if (site == Site.TRAINING) return@with world.message(player, NOT_A_SITE) else return@with workman(world, ROCK_PICK_LEVEL_2 to false)
            tool == TROWEL && site == Site.LEVEL_2 -> return@with workman(world, NEED_ROCK_PICK to false)
            tool == TROWEL && site == Site.LEVEL_1 && !worn(player, WearPos.HANDS, LEATHER_GLOVES) ->
                return@with workman(world, GLOVES to false, NO_GLOVES to true, GET_GLOVES to false)
            tool == TROWEL && site == Site.LEVEL_1 && !worn(player, WearPos.FEET, LEATHER_BOOTS) -> return@with workman(world, BOOTS to false)
            tool == TROWEL && site == Site.LEVEL_3 && (player.inventory.count(SPECIMEN_BRUSH) == 0L || player.inventory.count(SPECIMEN_JAR) == 0L) ->
                return@with world.message(player, LEVEL_3_KIT)
        }
        if (player.inventory.freeSlots == 0) return@with world.message(player, GameLines.NO_SPACE)
        world.message(player, DIG)
        player.anim(if (tool == ROCK_PICK) ANIM_ROCK_PICK else ANIM_TROWEL)
        delay(DIG_TICKS)
        player.skills.addTenths(Skill.MINING, site.xpTenths)
        val (obj, count) = roll(world, site.finds)
        give(world, player, obj, count)
    }

    suspend fun ScriptContext.searchTray(world: World) = with(DigsiteDigLines) {
        arriveDelay()
        if (player.inventory.count(SPECIMEN_JAR) == 0L) {
            val npc = nearestWorkman(world, player) ?: return@with world.message(player, OI)
            world.dialogues.start(this@searchTray, npc) {
                chatNpc(ChatAnim.ANGRY, OI)
                if (choice(ERRAND, SEARCHING_TRAY) == 1) {
                    chatPlayer(ChatAnim.NEUTRAL, ERRAND)
                    chatNpc(ChatAnim.ANGRY, WHOSE_ERRAND)
                    chatPlayer(ChatAnim.SAD, DONT_HAVE_ONE)
                    chatNpc(ChatAnim.ANGRY, SORRY_CANT)
                } else {
                    chatPlayer(ChatAnim.NEUTRAL, SEARCHING_TRAY)
                    chatNpc(ChatAnim.ANGRY, WHERES_YOUR_JAR)
                    chatPlayer(ChatAnim.SAD, AH_DONT_HAVE)
                    chatNpc(ChatAnim.ANGRY, BRING_IT)
                }
            }
            return@with
        }
        if (player.inventory.freeSlots == 0) return@with world.message(player, GameLines.NO_SPACE)
        world.message(player, SEARCH_TRAY)
        player.anim(ANIM_TROWEL)
        delay(DIG_TICKS)
        player.skills.addTenths(Skill.MINING, 5)
        val (obj, count) = roll(world, TRAY_FINDS)
        give(world, player, obj, count)
    }

    suspend fun ScriptContext.pan(world: World) = with(DigsiteDigLines) {
        arriveDelay()
        val slot = player.inventory.contents().firstOrNull { it.second.id == PANNING_TRAY }?.first
        if (slot == null) {
            if (player.inventory.count(PANNING_TRAY_MUD) > 0) {
                world.dialogues.start(this@pan) { chatPlayer(ChatAnim.NEUTRAL, FULL_TRAY) }
            } else {
                world.message(player, NO_PANNING_TRAY)
            }
            return@with
        }
        world.message(player, PAN)
        player.anim(ANIM_PAN)
        delay(PAN_TICKS)
        if (player.inventory[slot]?.id != PANNING_TRAY) return@with
        player.inventory[slot] = InvObj(PANNING_TRAY_MUD, 1)
        player.skills.addTenths(Skill.FISHING, 20)
        player.skills.addTenths(Skill.MINING, 20)
    }

    fun ScriptContext.searchPanningTray(world: World, slot: Int) = with(DigsiteDigLines) {
        if (player.inventory[slot]?.id != PANNING_TRAY_MUD) return@with
        // Refused before the roll, as the digs and the specimen trays are, so searching again can't pick a find that fits.
        if (player.inventory.freeSlots == 0) return@with world.message(player, GameLines.NO_SPACE)
        val (obj, count) = roll(world, PANNING_FINDS)
        player.inventory[slot] = InvObj(PANNING_TRAY, 1)
        world.message(player, SEARCH_PANNING_TRAY)
        give(world, player, obj, count)
    }

    suspend fun ScriptContext.searchJarSacks(world: World) = with(DigsiteDigLines) {
        world.message(player, SEARCH_SACKS)
        if (player.inventory.count(SPECIMEN_JAR) > 0) return@with world.message(player, SACKS_NOTHING)
        if (player.inventory.freeSlots == 0) return@with world.message(player, GameLines.NO_SPACE)
        world.dialogues.start(this@searchJarSacks) {
            chatPlayer(ChatAnim.HAPPY, SOMETHING_UNDER)
            world.objTypes[SPECIMEN_JAR]?.let { world.groundObjs.give(player, it, 1) }
            objbox(SPECIMEN_JAR, FIND_JAR)
        }
    }
}

/** Digging, the specimen trays, panning, the sacks and the buried skeletons. */
fun registerDigsiteDigs(world: World) {
    with(DigsiteDigs) {
        for (soil in SOILS) for (tool in listOf(TROWEL, ROCK_PICK, SPADE, SPECIMEN_BRUSH)) world.handlers.onObjOnLoc(soil, tool) { dig(world, tool) }
        world.handlers.onLoc(SPECIMEN_TRAY, TRAY_SEARCH) { searchTray(world) }
        world.handlers.onObjOnLoc(SPECIMEN_TRAY, TROWEL) { workman(world, DigsiteDigLines.NO_DIGGING_TRAYS to false) }
        world.handlers.onLoc(PANNING_POINT, PAN) { pan(world) }
        world.handlers.onObjOnLoc(PANNING_POINT, PANNING_TRAY) { pan(world) }
        world.handlers.onObjOnLoc(PANNING_POINT, PANNING_TRAY_MUD) { pan(world) }
        world.objTypes[PANNING_TRAY_MUD]?.iops?.indexOfFirst { it == "Search" }?.takeIf { it >= 0 }?.let { index ->
            world.handlers.onObj(PANNING_TRAY_MUD, index + 1) { op -> searchPanningTray(world, op.sub) }
        }
        world.handlers.onLoc(JAR_SACKS, SEARCH) { searchJarSacks(world) }
        world.handlers.onLoc(SACKS, SEARCH) {
            world.message(player, DigsiteDigLines.SEARCH_SACKS)
            world.message(player, DigsiteDigLines.SACKS_NOTHING)
        }
        world.handlers.onLoc(SKELETON, SEARCH) {
            world.message(player, DigsiteDigLines.SEARCH_SKELETON)
            world.message(player, DigsiteDigLines.SKELETON_NOTHING)
        }
        world.handlers.onObjOnLoc(SKELETON, TROWEL) {
            if (nearestWorkman(world, player) != null) workman(world, DigsiteDigLines.SKELETON_FRAGILE to false, DigsiteDigLines.SKELETON_SORRY to true)
        }
    }
}
