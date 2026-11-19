package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatFont
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.skill.AgilityLines

private val logger = KotlinLogging.logger {}

/** The words every diary shares: the journal's tier headings and the messages when a task or a tier is done. */
object DiaryLines : Lines("diary", "Achievement Diary") {
    val EASY = line("easy", "the journal's Easy heading, and the tier's name in the task message (lower case there on live)")
    val MEDIUM = line("medium", "the journal's Medium heading, and the tier's name in the task message")
    val HARD = line("hard", "the journal's Hard heading, and the tier's name in the task message")
    val ELITE = line("elite", "the journal's Elite heading, and the tier's name in the task message")
    val TASK_DONE = line("task_done", "the chat message when a task is done: well done, a {tier} task in the {area} area, the diary updated", "tier", "area")
    val TIER_DONE = line("tier_done", "the chat message when a tier's last task is done: every {tier} task in {area}, see {taskmaster} for the reward", "tier", "area", "taskmaster")
}

/** The Lumbridge & Draynor Diary's journal: its window title, its own title line, and each task as the journal words it. */
object DiaryLumbridgeLines : Lines("diary_lumbridge", "Lumbridge & Draynor Diary") {
    val WINDOW = line("window", "the journal scroll's title bar (starts \"Achievement Diary\", which RuneLite's Diary Requirements plugin looks for)")
    val TITLE = line("title", "the journal's first line, the diary's name and \"Tasks\" (RuneLite matches it as LUMBRIDGE_&_DRAYNOR_TASKS)")
    val AREA = line("area", "the area's name in the task and tier messages")
    val TASKMASTER = line("taskmaster", "the taskmaster's name in the tier message (Mikasi since 242.1)")
}

/** The Varrock Diary's journal, as [DiaryLumbridgeLines]. */
object DiaryVarrockLines : Lines("diary_varrock", "Varrock Diary") {
    val WINDOW = line("window", "the journal scroll's title bar (starts \"Achievement Diary\")")
    val TITLE = line("title", "the journal's first line (RuneLite matches it as VARROCK_TASKS)")
    val AREA = line("area", "the area's name in the task and tier messages")
    val TASKMASTER = line("taskmaster", "the taskmaster's name in the tier message (Toby)")
}

/** The task lines, one Lines object per diary so each key is `diary_<area>_tasks.<tier>_<n>`. */
object DiaryLumbridgeTaskLines : Lines("diary_lumbridge_tasks", "Lumbridge & Draynor Diary") {
    val ALL: Map<String, Line> = tasksFor(DiaryArea.LUMBRIDGE) { name, where -> line(name, where) }
}

object DiaryVarrockTaskLines : Lines("diary_varrock_tasks", "Varrock Diary") {
    val ALL: Map<String, Line> = tasksFor(DiaryArea.VARROCK) { name, where -> line(name, where) }
}

private fun tasksFor(area: DiaryArea, declare: (String, String) -> Line): Map<String, Line> =
    DiaryTask.entries.filter { it.area == area }.associate { task ->
        task.lineName to declare(task.lineName, "${task.tier.name.lowercase()} task ${task.number}: ${task.about}")
    }

enum class DiaryTier(val heading: Line) {
    EASY(DiaryLines.EASY),
    MEDIUM(DiaryLines.MEDIUM),
    HARD(DiaryLines.HARD),
    ELITE(DiaryLines.ELITE),
}

/**
 * A diary built here. [index] is its key in the cache's enum_595 (the side panel's names and order, client script
 * build_achievement_list), which is also the slot of its row's click box. [taskVarps] are the two varps that hold one
 * bit per task (`lumb_dray_achievement_diary`, `..2`), [counts] the varbit per tier the side panel reads its counts and
 * bars from (`lumbridge_easy_count` ...), [completes] the varbit per tier that turns the row green once all four are
 * set (`lumbridge_diary_easy_complete` ...), and [started] the varbit that turns it from red to yellow. Every id is from
 * the 242 cache (osrs-dumps 2026-11-18b, client procs diary_completion_info and area_task_complete).
 *
 * [rewardVarbits] say which tiers' rewards are claimed (`lumbridge_easy_reward` ..., `varrock_easy_reward`
 * ...), and [rewards] are the item per tier the taskmaster gives (Explorer's ring 1 to 4, Varrock armour 1 to 4).
 */
enum class DiaryArea(
    val slug: String,
    val index: Int,
    val taskVarps: Pair<Int, Int>,
    val counts: List<Int>,
    val completes: List<Int>,
    val started: Int,
    val window: Line,
    val title: Line,
    val areaName: Line,
    val taskmaster: Line,
    val rewardVarbits: List<Int>,
    val rewards: List<Int>,
) {
    LUMBRIDGE(
        "lumbridge", 6, 1194 to 1195, listOf(6311, 6312, 6313, 6314), listOf(4495, 4496, 4497, 4498), 4453,
        DiaryLumbridgeLines.WINDOW, DiaryLumbridgeLines.TITLE, DiaryLumbridgeLines.AREA, DiaryLumbridgeLines.TASKMASTER,
        listOf(4535, 4536, 4537, 4538), listOf(13125, 13126, 13127, 13128),
    ),
    VARROCK(
        "varrock", 8, 1176 to 1177, listOf(6319, 6320, 6321, 6322), listOf(4479, 4480, 4481, 4482), 4455,
        DiaryVarrockLines.WINDOW, DiaryVarrockLines.TITLE, DiaryVarrockLines.AREA, DiaryVarrockLines.TASKMASTER,
        listOf(4519, 4520, 4521, 4522), listOf(13104, 13105, 13106, 13107),
    ),
    ;

    val tasks: List<DiaryTask> get() = DiaryTask.entries.filter { it.area == this }

    fun tasks(tier: DiaryTier): List<DiaryTask> = DiaryTask.entries.filter { it.area == this && it.tier == tier }

    companion object {
        fun forSlug(slug: String): DiaryArea? = entries.firstOrNull { it.slug.equals(slug, ignoreCase = true) }

        fun forIndex(index: Int): DiaryArea? = entries.firstOrNull { it.index == index }
    }
}

/**
 * Every task of the diaries built here, in the Wiki's order (the journal's). [second] says which of the area's two task
 * varps holds its [bit]. The bits are Quest Helper's (its achievementdiaries helpers, read 2026-11-19), so inferred, not
 * seen on a live account; Varrock medium skips bit 17 there, as it does here.
 */
enum class DiaryTask(val area: DiaryArea, val tier: DiaryTier, val number: Int, val second: Boolean, val bit: Int, val about: String) {
    LUMBRIDGE_EASY_1(DiaryArea.LUMBRIDGE, DiaryTier.EASY, 1, false, 1, "a lap of the Draynor Village rooftop course"),
    LUMBRIDGE_EASY_2(DiaryArea.LUMBRIDGE, DiaryTier.EASY, 2, false, 2, "slay a cave bug beneath Lumbridge Swamp"),
    LUMBRIDGE_EASY_3(DiaryArea.LUMBRIDGE, DiaryTier.EASY, 3, false, 3, "Sedridor teleports you to the essence mine"),
    LUMBRIDGE_EASY_4(DiaryArea.LUMBRIDGE, DiaryTier.EASY, 4, false, 4, "craft water runes"),
    LUMBRIDGE_EASY_5(DiaryArea.LUMBRIDGE, DiaryTier.EASY, 5, false, 5, "learn your age from Hans"),
    LUMBRIDGE_EASY_6(DiaryArea.LUMBRIDGE, DiaryTier.EASY, 6, false, 6, "pickpocket a man or woman in Lumbridge"),
    LUMBRIDGE_EASY_7(DiaryArea.LUMBRIDGE, DiaryTier.EASY, 7, false, 7, "chop and burn oak logs in Lumbridge"),
    LUMBRIDGE_EASY_8(DiaryArea.LUMBRIDGE, DiaryTier.EASY, 8, false, 8, "kill a zombie in Draynor Sewers"),
    LUMBRIDGE_EASY_9(DiaryArea.LUMBRIDGE, DiaryTier.EASY, 9, false, 9, "catch anchovies in Al Kharid"),
    LUMBRIDGE_EASY_10(DiaryArea.LUMBRIDGE, DiaryTier.EASY, 10, false, 10, "bake bread on the Lumbridge kitchen range"),
    LUMBRIDGE_EASY_11(DiaryArea.LUMBRIDGE, DiaryTier.EASY, 11, false, 11, "mine iron ore at the Al Kharid mine"),
    LUMBRIDGE_EASY_12(DiaryArea.LUMBRIDGE, DiaryTier.EASY, 12, false, 12, "enter the H.A.M. Hideout"),
    LUMBRIDGE_MEDIUM_1(DiaryArea.LUMBRIDGE, DiaryTier.MEDIUM, 1, false, 13, "a lap of the Al Kharid rooftop course"),
    LUMBRIDGE_MEDIUM_2(DiaryArea.LUMBRIDGE, DiaryTier.MEDIUM, 2, false, 14, "grapple across the River Lum"),
    LUMBRIDGE_MEDIUM_3(DiaryArea.LUMBRIDGE, DiaryTier.MEDIUM, 3, false, 15, "buy an upgraded device from Ava"),
    LUMBRIDGE_MEDIUM_4(DiaryArea.LUMBRIDGE, DiaryTier.MEDIUM, 4, false, 16, "fairy ring to the Wizards' Tower"),
    LUMBRIDGE_MEDIUM_5(DiaryArea.LUMBRIDGE, DiaryTier.MEDIUM, 5, false, 17, "cast Lumbridge Teleport"),
    LUMBRIDGE_MEDIUM_6(DiaryArea.LUMBRIDGE, DiaryTier.MEDIUM, 6, false, 18, "catch salmon in Lumbridge"),
    LUMBRIDGE_MEDIUM_7(DiaryArea.LUMBRIDGE, DiaryTier.MEDIUM, 7, false, 19, "craft a coif in the Lumbridge cow pen"),
    LUMBRIDGE_MEDIUM_8(DiaryArea.LUMBRIDGE, DiaryTier.MEDIUM, 8, false, 20, "chop willow logs in Draynor Village"),
    LUMBRIDGE_MEDIUM_9(DiaryArea.LUMBRIDGE, DiaryTier.MEDIUM, 9, false, 21, "pickpocket Martin the Master Gardener"),
    LUMBRIDGE_MEDIUM_10(DiaryArea.LUMBRIDGE, DiaryTier.MEDIUM, 10, false, 22, "a Slayer task from Chaeldar"),
    LUMBRIDGE_MEDIUM_11(DiaryArea.LUMBRIDGE, DiaryTier.MEDIUM, 11, false, 23, "an essence or eclectic impling in Puro-Puro"),
    LUMBRIDGE_MEDIUM_12(DiaryArea.LUMBRIDGE, DiaryTier.MEDIUM, 12, false, 24, "craft lava runes at the fire altar"),
    LUMBRIDGE_HARD_1(DiaryArea.LUMBRIDGE, DiaryTier.HARD, 1, false, 25, "cast Bones to Peaches in Al Kharid palace"),
    LUMBRIDGE_HARD_2(DiaryArea.LUMBRIDGE, DiaryTier.HARD, 2, false, 26, "squeeze past the jutting wall to the cosmic altar"),
    LUMBRIDGE_HARD_3(DiaryArea.LUMBRIDGE, DiaryTier.HARD, 3, false, 27, "craft 56 cosmic runes at once"),
    LUMBRIDGE_HARD_4(DiaryArea.LUMBRIDGE, DiaryTier.HARD, 4, false, 28, "Waka canoe from Lumbridge to Edgeville"),
    LUMBRIDGE_HARD_5(DiaryArea.LUMBRIDGE, DiaryTier.HARD, 5, false, 29, "100 Tears of Guthix in one visit"),
    LUMBRIDGE_HARD_6(DiaryArea.LUMBRIDGE, DiaryTier.HARD, 6, false, 30, "train from Dorgesh-Kaan to Keldagrim"),
    LUMBRIDGE_HARD_7(DiaryArea.LUMBRIDGE, DiaryTier.HARD, 7, false, 31, "buy Barrows gloves from the Culinaromancer's Chest"),
    LUMBRIDGE_HARD_8(DiaryArea.LUMBRIDGE, DiaryTier.HARD, 8, true, 0, "pick belladonna at Draynor Manor"),
    LUMBRIDGE_HARD_9(DiaryArea.LUMBRIDGE, DiaryTier.HARD, 9, true, 1, "light a mining helmet in the castle basement"),
    LUMBRIDGE_HARD_10(DiaryArea.LUMBRIDGE, DiaryTier.HARD, 10, true, 2, "recharge prayer at Emir's Arena with Smite on"),
    LUMBRIDGE_HARD_11(DiaryArea.LUMBRIDGE, DiaryTier.HARD, 11, true, 3, "craft, string and enchant an amulet of power in Lumbridge"),
    LUMBRIDGE_ELITE_1(DiaryArea.LUMBRIDGE, DiaryTier.ELITE, 1, true, 4, "steal from a Dorgesh-Kaan rich chest"),
    LUMBRIDGE_ELITE_2(DiaryArea.LUMBRIDGE, DiaryTier.ELITE, 2, true, 5, "grapple a pylon on the Dorgesh-Kaan course"),
    LUMBRIDGE_ELITE_3(DiaryArea.LUMBRIDGE, DiaryTier.ELITE, 3, true, 6, "chop magic logs at the Mage Training Arena"),
    LUMBRIDGE_ELITE_4(DiaryArea.LUMBRIDGE, DiaryTier.ELITE, 4, true, 7, "smith an adamant platebody in Draynor Sewers"),
    LUMBRIDGE_ELITE_5(DiaryArea.LUMBRIDGE, DiaryTier.ELITE, 5, true, 8, "craft 140 water runes at once"),
    LUMBRIDGE_ELITE_6(DiaryArea.LUMBRIDGE, DiaryTier.ELITE, 6, true, 9, "the Quest point cape emote in the Wise Old Man's house"),

    VARROCK_EASY_1(DiaryArea.VARROCK, DiaryTier.EASY, 1, false, 1, "browse Thessalia's store"),
    VARROCK_EASY_2(DiaryArea.VARROCK, DiaryTier.EASY, 2, false, 2, "Aubury teleports you to the essence mine"),
    VARROCK_EASY_3(DiaryArea.VARROCK, DiaryTier.EASY, 3, false, 3, "mine iron in the south-east Varrock mine"),
    VARROCK_EASY_4(DiaryArea.VARROCK, DiaryTier.EASY, 4, false, 4, "make a normal plank at the sawmill"),
    VARROCK_EASY_5(DiaryArea.VARROCK, DiaryTier.EASY, 5, false, 5, "enter the Stronghold of Security's second level"),
    VARROCK_EASY_6(DiaryArea.VARROCK, DiaryTier.EASY, 6, false, 6, "jump the fence south of Varrock"),
    VARROCK_EASY_7(DiaryArea.VARROCK, DiaryTier.EASY, 7, false, 7, "chop a dying tree in the Lumber Yard"),
    VARROCK_EASY_8(DiaryArea.VARROCK, DiaryTier.EASY, 8, false, 8, "buy a newspaper"),
    VARROCK_EASY_9(DiaryArea.VARROCK, DiaryTier.EASY, 9, false, 9, "give a dog a bone"),
    VARROCK_EASY_10(DiaryArea.VARROCK, DiaryTier.EASY, 10, false, 10, "spin a bowl and fire it in Barbarian Village"),
    VARROCK_EASY_11(DiaryArea.VARROCK, DiaryTier.EASY, 11, false, 11, "talk to Haig Halen with 50 kudos"),
    VARROCK_EASY_12(DiaryArea.VARROCK, DiaryTier.EASY, 12, false, 12, "craft earth runes"),
    VARROCK_EASY_13(DiaryArea.VARROCK, DiaryTier.EASY, 13, false, 13, "catch trout at Barbarian Village"),
    VARROCK_EASY_14(DiaryArea.VARROCK, DiaryTier.EASY, 14, false, 14, "steal from the tea stall"),
    VARROCK_MEDIUM_1(DiaryArea.VARROCK, DiaryTier.MEDIUM, 1, false, 15, "the Apothecary makes you a strength potion"),
    VARROCK_MEDIUM_2(DiaryArea.VARROCK, DiaryTier.MEDIUM, 2, false, 16, "enter the Champions' Guild"),
    VARROCK_MEDIUM_3(DiaryArea.VARROCK, DiaryTier.MEDIUM, 3, false, 18, "pick a colour for your kitten"),
    VARROCK_MEDIUM_4(DiaryArea.VARROCK, DiaryTier.MEDIUM, 4, false, 19, "use the spirit tree north of Varrock"),
    VARROCK_MEDIUM_5(DiaryArea.VARROCK, DiaryTier.MEDIUM, 5, false, 20, "the 4 Stronghold of Security emotes"),
    VARROCK_MEDIUM_6(DiaryArea.VARROCK, DiaryTier.MEDIUM, 6, false, 21, "enter the Tolna dungeon"),
    VARROCK_MEDIUM_7(DiaryArea.VARROCK, DiaryTier.MEDIUM, 7, false, 22, "teleport to the Digsite with a pendant"),
    VARROCK_MEDIUM_8(DiaryArea.VARROCK, DiaryTier.MEDIUM, 8, false, 23, "cast Varrock Teleport"),
    VARROCK_MEDIUM_9(DiaryArea.VARROCK, DiaryTier.MEDIUM, 9, false, 24, "a Slayer task from Vannaka"),
    VARROCK_MEDIUM_10(DiaryArea.VARROCK, DiaryTier.MEDIUM, 10, false, 25, "make 20 mahogany planks at once"),
    VARROCK_MEDIUM_11(DiaryArea.VARROCK, DiaryTier.MEDIUM, 11, false, 26, "pick a White Tree fruit"),
    VARROCK_MEDIUM_12(DiaryArea.VARROCK, DiaryTier.MEDIUM, 12, false, 27, "the balloon from Varrock"),
    VARROCK_MEDIUM_13(DiaryArea.VARROCK, DiaryTier.MEDIUM, 13, false, 28, "a lap of the Varrock rooftop course"),
    VARROCK_HARD_1(DiaryArea.VARROCK, DiaryTier.HARD, 1, false, 29, "trade furs for a spottier cape and wear it"),
    VARROCK_HARD_2(DiaryArea.VARROCK, DiaryTier.HARD, 2, false, 30, "talk to Orlando Smith with 153 kudos"),
    VARROCK_HARD_3(DiaryArea.VARROCK, DiaryTier.HARD, 3, false, 31, "make a Waka canoe near Edgeville"),
    VARROCK_HARD_4(DiaryArea.VARROCK, DiaryTier.HARD, 4, true, 0, "teleport to Paddewwa"),
    VARROCK_HARD_5(DiaryArea.VARROCK, DiaryTier.HARD, 5, true, 1, "skull sceptre to Barbarian Village"),
    VARROCK_HARD_6(DiaryArea.VARROCK, DiaryTier.HARD, 6, true, 2, "chop yew in Varrock and burn it atop the church"),
    VARROCK_HARD_7(DiaryArea.VARROCK, DiaryTier.HARD, 7, true, 3, "the estate agent decorates your house with Fancy Stone"),
    VARROCK_HARD_8(DiaryArea.VARROCK, DiaryTier.HARD, 8, true, 4, "2 yew roots from the palace tree patch"),
    VARROCK_HARD_9(DiaryArea.VARROCK, DiaryTier.HARD, 9, true, 5, "pray at the palace altar with Smite on"),
    VARROCK_HARD_10(DiaryArea.VARROCK, DiaryTier.HARD, 10, true, 6, "squeeze through the Edgeville Dungeon pipe"),
    VARROCK_ELITE_1(DiaryArea.VARROCK, DiaryTier.ELITE, 1, true, 7, "a super combat potion in Varrock west bank"),
    VARROCK_ELITE_2(DiaryArea.VARROCK, DiaryTier.ELITE, 2, true, 8, "Lunar Plank Make 20 mahogany at the Lumber Yard"),
    VARROCK_ELITE_3(DiaryArea.VARROCK, DiaryTier.ELITE, 3, true, 9, "bake a summer pie in the Cooks' Guild"),
    VARROCK_ELITE_4(DiaryArea.VARROCK, DiaryTier.ELITE, 4, true, 10, "smith and fletch ten rune darts in Varrock"),
    VARROCK_ELITE_5(DiaryArea.VARROCK, DiaryTier.ELITE, 5, true, 11, "craft 100 earth runes at once"),
    ;

    val varp: Int get() = if (second) area.taskVarps.second else area.taskVarps.first

    /** The key's name inside the area's task lines: `easy_1`. */
    val lineName: String get() = "${tier.name.lowercase()}_$number"

    val line: Line get() = when (area) {
        DiaryArea.LUMBRIDGE -> DiaryLumbridgeTaskLines.ALL.getValue(lineName)
        DiaryArea.VARROCK -> DiaryVarrockTaskLines.ALL.getValue(lineName)
    }
}

/**
 * The achievement diaries: which tasks a player has done, the side panel's counts and the journal scroll.
 *
 * Live's state is kept in live's vars, so RuneLite plugins read the same thing they would on live: a bit per task in
 * the area's two task varps, then the counts, the tier-complete flags and the started flag the side panel (area_task,
 * 259) draws from. The panel is the client's own: it builds its rows from those varbits as they arrive.
 *
 * Inferred: a diary counts as started (its row yellow) from its first task, as the Wiki says tasks need no taskmaster to
 * begin. Guessed: nothing but the chat messages marks a task or a tier done (no jingle, no popup); tasks done before
 * the diaries existed here don't count, as on live when the diaries came out.
 */
class Diaries(private val world: World, private val font: ChatFont) {
    fun done(player: Player, task: DiaryTask): Boolean = player.vars[task.varp] and (1 shl task.bit) != 0

    fun count(player: Player, area: DiaryArea, tier: DiaryTier): Int = area.tasks(tier).count { done(player, it) }

    fun tierDone(player: Player, area: DiaryArea, tier: DiaryTier): Boolean = area.tasks(tier).all { done(player, it) }

    /**
     * Marks [task] done, with its message and, if it was the tier's last, the tier's. False when it was done already, so
     * content can call this on every completion of the action.
     */
    fun complete(player: Player, task: DiaryTask): Boolean {
        if (done(player, task)) return false
        player.vars[task.varp] = player.vars[task.varp] or (1 shl task.bit)
        refresh(player, task.area)
        val text = world.textFor(player)
        val tier = text[task.tier.heading].lowercase()
        val area = text[task.area.areaName]
        player.message(text[DiaryLines.TASK_DONE, "tier" to tier, "area" to area])
        if (tierDone(player, task.area, task.tier)) {
            player.message(text[DiaryLines.TIER_DONE, "tier" to tier, "area" to area, "taskmaster" to text[task.area.taskmaster]])
        }
        return true
    }

    /** [count] of [obj] made or gathered at once: any task it does ([DiaryHooks.MADE]). */
    fun made(player: Player, obj: Int, count: Int) = DiaryHooks.made(DiaryHooks.MADE, player, obj, count).forEach { complete(player, it) }

    /** [log] lit by the player ([DiaryHooks.BURNED]). */
    fun burned(player: Player, log: Int) = DiaryHooks.made(DiaryHooks.BURNED, player, log, 1).forEach { complete(player, it) }

    /** An NPC the player killed ([DiaryHooks.KILLED]). */
    fun killed(player: Player, npc: Npc) = DiaryHooks.named(DiaryHooks.KILLED, player, npc.type.name).forEach { complete(player, it) }

    /** An NPC the player pickpocketed ([DiaryHooks.PICKPOCKETED]). */
    fun pickpocketed(player: Player, npc: Npc) = DiaryHooks.named(DiaryHooks.PICKPOCKETED, player, npc.type.name).forEach { complete(player, it) }

    /** Pray-at on an altar with Smite on ([DiaryHooks.SMITE_ALTARS]). */
    fun prayedWithSmite(player: Player) {
        for ((zone, task) in DiaryHooks.SMITE_ALTARS) if (zone.has(player)) complete(player, task)
    }

    /** The Skill Cape emote in a quest point cape. */
    fun questCapeEmote(player: Player) {
        if (DiaryZone.WISE_OLD_MAN_HOUSE.has(player)) complete(player, DiaryTask.LUMBRIDGE_ELITE_6)
    }

    /** A teleport spell cast (not a tablet broken), by its spell obj. */
    fun teleported(player: Player, spellObj: Int) {
        if (spellObj == LUMBRIDGE_TELEPORT_SPELL) complete(player, DiaryTask.LUMBRIDGE_MEDIUM_5)
    }

    /** A lap of the course whose lap message is [lapLine]. */
    fun lap(player: Player, lapLine: Line) {
        when (lapLine) {
            AgilityLines.DRAYNOR_LAPS -> complete(player, DiaryTask.LUMBRIDGE_EASY_1)
            AgilityLines.ALKHARID_LAPS -> complete(player, DiaryTask.LUMBRIDGE_MEDIUM_1)
        }
    }

    /**
     * Takes [task] back off, for `::diary reset`; the tier's flag goes with it, and its claimed reward, so a test can
     * claim it again (an admin's command only, so the second lamp is no dupe a player can reach).
     */
    fun undo(player: Player, task: DiaryTask) {
        player.vars[task.varp] = player.vars[task.varp] and (1 shl task.bit).inv()
        refresh(player, task.area)
        val reward = task.area.rewardVarbits[task.tier.ordinal]
        if (player.vars.types.varbit(reward) != null) player.vars.setVarbit(reward, 0)
    }

    /** Sets the panel's varbits for [area] from its task bits. */
    fun refresh(player: Player, area: DiaryArea) {
        if (!hasVarbits(player)) return
        for (tier in DiaryTier.entries) {
            player.vars.setVarbit(area.counts[tier.ordinal], count(player, area, tier))
            player.vars.setVarbit(area.completes[tier.ordinal], if (tierDone(player, area, tier)) 1 else 0)
        }
        player.vars.setVarbit(area.started, if (area.tasks.any { done(player, it) }) 1 else 0)
    }

    /** Whether [tier]'s reward in [area] has been claimed; never, in a world whose cache lacks the reward varbits. */
    fun claimed(player: Player, area: DiaryArea, tier: DiaryTier): Boolean {
        val varbit = area.rewardVarbits[tier.ordinal]
        return player.vars.types.varbit(varbit) != null && player.vars.getVarbit(varbit) == 1
    }

    /** The lowest tier whose tasks are all done and whose reward isn't claimed yet, or null. */
    fun claimable(player: Player, area: DiaryArea): DiaryTier? =
        DiaryTier.entries.firstOrNull { tierDone(player, area, it) && !claimed(player, area, it) }

    /** Whether every tier below [tier] is done, which claiming [tier] needs (the Wiki's diary pages). */
    fun previousDone(player: Player, area: DiaryArea, tier: DiaryTier): Boolean =
        DiaryTier.entries.take(tier.ordinal).all { tierDone(player, area, it) }

    /** The highest tier claimed in [area], whose item the taskmaster gives again. */
    fun highestClaimed(player: Player, area: DiaryArea): DiaryTier? =
        DiaryTier.entries.lastOrNull { claimed(player, area, it) }

    /**
     * Claims [tier] in [area]: the reward varbit, then the tier's item and lamp, each to the inventory or, without room,
     * to the floor under the player (guessed for Mikasi, who asks for no space; Toby checks first). Earlier tiers' items
     * stay with the player, as on live (the Wiki's item pages list each tier as its own item).
     */
    fun claim(player: Player, area: DiaryArea, tier: DiaryTier) {
        player.vars.setVarbit(area.rewardVarbits[tier.ordinal], 1)
        give(player, area.rewards[tier.ordinal])
        give(player, AntiqueLamps.Lamp.entries[tier.ordinal].obj)
    }

    /** The highest claimed tier's item again, for "Can I have another ring?" and its Varrock twin. */
    fun replace(player: Player, area: DiaryArea) {
        highestClaimed(player, area)?.let { give(player, area.rewards[it.ordinal]) }
    }

    /**
     * Whether the player has the highest claimed tier's item carried, worn or banked; earlier tiers' items don't count,
     * so a banked Explorer's ring 1 doesn't stop a lost ring 4 being given back (guessed: the Wiki says only that the
     * taskmaster replaces a lost reward).
     */
    fun ownsReward(player: Player, area: DiaryArea): Boolean {
        val obj = area.rewards[(highestClaimed(player, area) ?: return false).ordinal]
        return player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0 || player.bank.count(obj) > 0
    }

    private fun give(player: Player, obj: Int) {
        val type = world.objTypes[obj] ?: return logger.warn { "No item $obj in the cache" }
        world.groundObjs.give(player, type, 1)
    }

    /** At login, in case a save's bits and counts disagree (a save from a server that changed the task list). */
    fun login(player: Player) {
        for (area in DiaryArea.entries) refresh(player, area)
    }

    /** Skipped, with nothing said, when the cache has no diary varbits, as in the headless tests' worlds that don't use them. */
    private fun hasVarbits(player: Player) = player.vars.types.varbit(DiaryArea.LUMBRIDGE.counts[0]) != null

    /**
     * The journal lines for [area]: its title, then each tier's heading and tasks, each task wrapped to the scroll's
     * width with every line of a done one struck through (RuneLite's Diary Requirements plugin joins wrapped lines back
     * up and reads `<str>` off each). Guessed: the blank lines, and the headings' colour.
     */
    fun journalLines(player: Player, area: DiaryArea): List<String> {
        val text = world.textFor(player)
        val lines = mutableListOf(text[area.title])
        for (tier in DiaryTier.entries) {
            lines += ""
            lines += "<col=$HEADING_COLOUR>${text[tier.heading]}</col>"
            for (task in area.tasks(tier)) {
                val strike = if (done(player, task)) "<str>" else ""
                for (part in font.splitLines(text[task.line], LINE_WIDTH)) lines += strike + part
            }
        }
        return lines.take(LINES)
    }

    /**
     * Opens [area]'s journal. The words go before the scroll opens, so a plugin reading them as it loads finds them,
     * and every one of the scroll's 200 lines is set, so none is left over from another journal; then the client's line
     * count script (6845, which RuneLite calls `DIARY_UPDATE_LINECOUNT`) sizes the scroll. Inferred: that order and
     * the script's first argument (1, back to the top).
     */
    fun openJournal(player: Player, area: DiaryArea) {
        val lines = journalLines(player, area)
        world.interfaces.setText(player, JOURNAL, JOURNAL_TITLE, world.textFor(player)[area.window])
        for (i in 0 until LINES) world.interfaces.setText(player, JOURNAL, FIRST_LINE + i, lines.getOrElse(i) { "" })
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, JOURNAL)
        world.interfaces.runClientScript(player, LINE_COUNT_SCRIPT, listOf(1, lines.size))
    }

    companion object {
        /** area_task, the quest tab's Achievement Diaries page, and its taskbox, which holds a click box per diary. */
        const val PANEL = 259
        const val PANEL_TASKBOX = 2
        const val PANEL_OPEN = 1

        /** The click boxes are the taskbox's children 0 to 11, one per enum_595 key (build_achievement_list). */
        const val PANEL_ROWS = 12

        /** journalscroll, its title and its first text line (qj1; qj200 is 203). */
        const val JOURNAL = 741
        const val JOURNAL_TITLE = 2
        const val FIRST_LINE = 4
        const val LINES = 200

        /** Client script that sizes journalscroll's text layer for a line count (`script6845` in osrs-dumps). */
        const val LINE_COUNT_SCRIPT = 6845

        /** `31_lumbridge_teleport`, the standard book's Lumbridge Teleport. */
        const val LUMBRIDGE_TELEPORT_SPELL = 3289

        /** The text layer's width: each line is as wide (the cache's qj components). */
        const val LINE_WIDTH = 404

        /** Guessed: the dark red the quest journals use for headings. */
        const val HEADING_COLOUR = "800000"

        /** The interface and components the server touches, for the startup check against the cache's names. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            PANEL to ("area_task" to mapOf(PANEL_TASKBOX to "taskbox")),
            JOURNAL to ("journalscroll" to mapOf(JOURNAL_TITLE to "title", FIRST_LINE to "qj1", FIRST_LINE + LINES - 1 to "qj200")),
        )
    }
}

/**
 * The side panel's "Open <diary> Journal" (op 1 on a row's click box) opens that diary's journal. A diary not built
 * here does nothing: its tasks aren't keyed; nor does a click while another interface is open or a script runs. The
 * rows' second op, Wiki, opens a web page on live and isn't enabled ([osrsps.game.Interfaces.openJournalTab]).
 */
fun registerDiaries(world: World) {
    world.handlers.onButton(Diaries.PANEL, Diaries.PANEL_TASKBOX, Diaries.PANEL_OPEN) { op ->
        val area = DiaryArea.forIndex(op.sub) ?: return@onButton
        // A stale or forged click with the bank or another modal open would swap only the main modal and strand the rest.
        if (world.scripts.busy(player)) return@onButton
        world.diaries.openJournal(player, area)
    }
}
