package osrsps.game.trail

import osrsps.game.GameLines
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.FrameSlot
import osrsps.game.GroundObjs
import osrsps.game.combat.Drop
import osrsps.game.sendInventory
import osrsps.game.dialogue.ChatAnim
import osrsps.game.script.Handler
import osrsps.game.script.Op
import osrsps.game.script.OpTarget

/** Treasure Trails' messages and item boxes. The clue words themselves come from the cache ([TrailLoader]). */
object TrailLines : Lines("trail", "Treasure Trails") {
    val BOX_REFUSES = line("box_refuses", "Open on a scroll box while holding a clue of its tier (the Wiki's Scroll box (easy) page)")
    val BOX_NO_SPACE = line("box_no_space", "OUR OWN: Open on one of several scroll boxes with a full backpack")
    val ANOTHER_CLUE = line("another_clue", "the item box showing the next clue scroll after a step is solved")
    val CASKET = line("casket", "the item box showing the reward casket after the last step")
    val CHECK_STEPS = line("check_steps", "Check steps on a clue scroll", "count")
    val COORDINATE = line("coordinate", "a coordinate clue's scroll: two lines, latitude then longitude", "lat_deg", "lat_min", "lat_dir", "lon_deg", "lon_min", "lon_dir")
    val NORTH = line("north", "a coordinate clue: the word for north")
    val SOUTH = line("south", "a coordinate clue: the word for south")
    val EAST = line("east", "a coordinate clue: the word for east")
    val WEST = line("west", "a coordinate clue: the word for west")
    val SUSPICION_BEGINNER = line("suspicion_beginner", "a beginner scroll box dropped while the player holds as many as they may (the Wiki's Treasure Trails page)")
    val SUSPICION_EASY = line("suspicion_easy", "an easy scroll box likewise")
    val SUSPICION_MEDIUM = line("suspicion_medium", "a medium scroll box likewise")
    val SUSPICION_HARD = line("suspicion_hard", "a hard scroll box likewise")
    val SUSPICION_ELITE = line("suspicion_elite", "an elite scroll box likewise")
    val SUSPICION_MASTER = line("suspicion_master", "a master scroll box likewise")
    val ALREADY_CLUE = line("already_clue", "Take on a clue scroll while holding one of its tier (the Wiki's Treasure Trails page)")
    val BOXES_DESTROYED = line("boxes_destroyed", "Take on scroll boxes past the tier's cap: the ones over it are destroyed (the Wiki's Treasure Trails page)")
    val WELL_DONE = line("well_done", "opening a reward casket (the Wiki's Reward casket pages)")
    val COMPLETED = line("completed", "after well_done, in red: the caskets of the tier opened so far (tier is easy, medium and so on)", "count", "tier")
    val WORTH = line("worth", "after completed, in red: the reward's Grand Exchange value, with commas", "value")
    val CASE_OPENED = line("case_opened", "opening a scroll case: the tier's cap is one higher (no source seen)")
    val NPC_CLUE = line("npc_clue", "a talk step's NPC, handing over the next clue (no source seen)")
    val NPC_CHALLENGE = line("npc_challenge", "a talk step's NPC, handing over a challenge scroll with a question to answer (no source seen)")
    val CHALLENGE_PROMPT = line("challenge_prompt", "the number prompt for a challenge scroll's answer (no source seen)")
    val NPC_WRONG = line("npc_wrong", "a talk step's NPC, after a wrong answer to the challenge scroll (no source seen)")
    val NPC_RIGHT = line("npc_right", "a talk step's NPC, after the right answer, before the next clue (no source seen)")
    val URI_CLUE = line("uri_clue", "Uri, handing over the next clue to the player who called him (the Wiki's Uri page lists his phrases; which he says when isn't known)")
    val URI_NO_BUSINESS = line("uri_no_business", "Uri, to a player who didn't call him or isn't wearing what the clue asks (no source seen)")
    val URI_NOT_YET = line("uri_not_yet", "Uri, before the clue's second emote is done (no source seen)")
    val CHARLIE_TASK = line("charlie_task", "Charlie the Tramp, setting his task; the clue then shows its words (no source seen)")
    val CHARLIE_WAITING = line("charlie_waiting", "Charlie, talked to again without the task's item (no source seen)")
    val CHARLIE_THANKS = line("charlie_thanks", "Charlie, taking the task's item, before the next clue (no source seen)")
    val LOCKED = line("locked", "Search or Open on a key step's chest or drawers without the key (no source seen)")
    val NPC_BOX = line("npc_box", "a talk step's NPC, handing over a puzzle box or light box to solve first (no source seen)")
    val NPC_BOX_UNSOLVED = line("npc_box_unsolved", "a talk step's NPC, while the player's box isn't solved yet (no source seen)")
    val NPC_BOX_SOLVED = line("npc_box_solved", "a talk step's NPC, taking the solved box, before the next clue (no source seen)")
    val DESTROY_LIGHT_BOX = line("destroy_light_box", "the light box's destroy check text (the Wiki's infobox)")

    fun suspicion(tier: TrailTier) = when (tier) {
        TrailTier.BEGINNER -> SUSPICION_BEGINNER
        TrailTier.EASY -> SUSPICION_EASY
        TrailTier.MEDIUM -> SUSPICION_MEDIUM
        TrailTier.HARD -> SUSPICION_HARD
        TrailTier.ELITE -> SUSPICION_ELITE
        TrailTier.MASTER -> SUSPICION_MASTER
    }
}

/**
 * Treasure Trails: scroll boxes, clue scrolls, reading them, a spade's Dig and the dig steps (step TT1), the reward
 * caskets (TT2), the talk and search steps (TT3), the emote steps with Uri (TT4), the STASH units ([StashUnits], TT5), the puzzle and light boxes ([TrailBoxes], TT6), the fights and key steps ([TrailFights], TT7), and Charlie
 * and the hot-cold steps ([HotCold], TT8), and the clue sources beside drops (TT9: [give] for pickpockets, impling
 * jars and chests, [SkillingClues] for mining, woodcutting and fishing). Sources: the OSRS Wiki's Treasure Trails, Scroll box, Clue scroll, Reward casket and Scroll case pages,
 * read 2026-11-19, and the cache's clue helper tables ([TrailLoader]), which give every step's words and tiles.
 *
 * - Every quest counts as done, X Marks the Spot included, so every clue scroll a monster drops comes as its tier's
 *   scroll box. A player holds at most [cap] boxes and scrolls of a tier together (the Wiki: the cap starts at 2, and
 *   each scroll case opened raises it by one); a box past that is not dropped, and the killer is told so. A pickpocket's,
 *   impling jar's or chest's clue scroll comes the same way ([give]); skilling rolls only the tiers below the cap.
 * - Open on a box takes one and gives a clue scroll of its tier on a new trail, unless a clue of the tier is already in
 *   the backpack or the bank. The trail's length is rolled in the tier's range ([TrailTier.minSteps]); each step is a
 *   built step of the tier ([built]) not yet done on this trail.
 * - Easy to elite steps are each their own item, so the item says which step it is; a scroll picked up that isn't the
 *   trail's step starts a new trail there, as dropping a clue loses its steps (the Wiki's Clue scroll (easy) page).
 *   Beginner and master steps share one item, and the step is kept on the player.
 * - Read shows the clue: its words on trail_cluetext, a coordinate as degrees and minutes, or the map's own interface.
 *   Check steps tells the player how many steps they have done.
 * - Dig (a spade's op1) plays human_dig and, within a tile of a dig step's spot (2004Scape's spade script, after
 *   videos of the live game), solves it; elsewhere nothing interesting happens. A solved step swaps the scroll for the
 *   next one, shown in an item box; the last gives the tier's reward casket instead.
 * - Talk-to on a talk step's NPC (an anagram's, a cipher's, a cryptic's) with its clue in the backpack takes the click
 *   from the NPC's own talk ([intercept]) and gives the next clue; Search or Open on a search step's loc likewise. An
 *   NPC with a challenge question (the clue helper's challenge column) first hands over its challenge scroll, then
 *   takes the answer as a number when talked to again, any of the cache's answers right and a wrong one free. A
 *   step whose NPC isn't spawned or whose loc isn't on its tile never comes up ([available]). An NPC whose step names a
 *   box hands over a puzzle box or light box first, and the clue once it is solved ([TrailBoxes]).
 * - An emote step's first emote, done within [EMOTE_RANGE] of its spot in one of its outfits (the clue helper's
 *   cluehelper_outfit: items by equipment slot, or item groups by obj param 258), calls that tier's Uri beside the
 *   player (the Wiki's Uri page); a step of two emotes has the second done before he gives the clue. He talks only to
 *   the player who called him, dressed as asked, and leaves after [URI_TICKS]. A step with a double agent brings the
 *   agent out first, and Uri comes to the emote once it is dead ([TrailFights]); so does a dig step's wizard.
 * - Open on a reward casket counts the trail done and rolls its rewards ([TrailRewards]: the Wiki's tables, its
 *   1/50 to 1/5 master clue as a master scroll box); the reward screen shows them and the chat says "Well done", the
 *   count and the value. The tier's minor and major scroll cases come with every casket from their count on until
 *   opened; the other milestones (large spade, clueless scroll, heavy casket, scroll sack) with the casket that makes
 *   their count.
 *
 * Steps are built region by region: [built] names the area and the kinds of step that work so far. Until other regions
 * exist, every trail stays inside Misthalin, and a trail is never longer than the steps built for its tier (Sam's
 * choice, 19 Nov 2026: marked temporary, so elite and master trails run as long as there are steps).
 *
 * Guessed: the trail length and a casket's roll count are uniform in their ranges and every built step equally likely
 * (the Wiki gives only ranges and averages); rewards go straight into the backpack, what doesn't fit on the floor, the
 * reward screen only showing them; a scroll case held but not opened isn't given again; the value is the guide prices'
 * sum; how near the spot an emote counts and how long Uri waits; Uri appearing with no spotanim, on a free tile beside
 * the player, seen by everyone; Search and Open both search a step's loc; the NPC's chathead moods; the challenge scroll used up on the right
 * answer; no
 * message on opening a box; one item box per solved step; a box that would pass the cap being kept from the drop (the
 * Wiki's warning setting on); the first item of a step with several. Left out until their steps: a second clue picked
 * up past the cap, the clue box's death rule, the Mimic and its scroll case, the emote milestones (Explore, Uri
 * transform), the Bloodhound pet, the collection log's clue counts (varps completed_clues*), and the Wiki's "worth
 * almost nothing" line for a reward of exactly 1 coin.
 */
class TreasureTrails(private val world: World, val data: TrailData) {
    /** The STASH units of the built regions (TT5). */
    val stashes = StashUnits(world, data)

    /** Puzzle and light boxes (TT6). */
    val boxes = TrailBoxes(world)

    /**
     * Steps of each tier that can come up now: built ones, leaving out a box step whose box the cache doesn't have, as
     * its NPC could never hand one over and the trail would stick there (exploit review round 50, R50C-3).
     */
    private val pools: Map<TrailTier, List<ClueStep>> = TrailTier.entries.associateWith { tier ->
        data.tier(tier).filter { step -> built(step) && (step.challenge as? TrailChallenge.Box)?.let { boxes.accepts(step, it).isNotEmpty() } != false }
    }

    fun pool(tier: TrailTier): List<ClueStep> = pools.getValue(tier)

    /** Wizards, double agents and key monsters (TT7). */
    val fights = TrailFights(world, this)

    /** Hot-cold steps and their strange devices (TT8). */
    val hotCold = HotCold(world, this)

    /** Scroll boxes from mining, woodcutting and fishing (TT9). */
    val skilling = SkillingClues(world, this)

    /** The step each challenge scroll belongs to. */
    private val byChallenge: Map<Int, ClueStep> = data.steps.values.filter { it.challengeItem >= 0 }.associateBy { it.challengeItem }

    private val availability = HashMap<String, Boolean>()

    /**
     * Whether [step]'s target is in the world: a talk step's NPC spawned somewhere, a search step's loc on its tile, a
     * hot-cold step's device giver spawned.
     * Checked once a step, the first time it could come up, so a step whose NPC or loc isn't built never does.
     */
    fun available(step: ClueStep): Boolean = availability.getOrPut(step.key) {
        // A hot-cold step needs its device's giver (Reldo, Jorral) about.
        if (step.type == StepType.HOT_COLD) return@getOrPut world.npcs.all.any { it.type.id in HotCold.GIVERS[step.tier].orEmpty() }
        when (val target = step.targets.first()) {
            is TrailTarget.Npc -> world.npcs.all.any { it.type.id == target.npc || it.type.id == target.fallbackNpc }
            is TrailTarget.Loc -> world.locs.at(target.tile.x, target.tile.z, target.tile.level).any { it.id == target.loc || it.id == target.fallbackLoc }
            else -> true
        }
    }

    /** Uri, called by an emote step: whose he is, for which step, until when, and whether the second emote is done. */
    private class UriVisit(val npc: osrsps.game.npc.Npc, val tier: TrailTier, val step: String, val until: Long, var ready: Boolean)

    private val uris = HashMap<Player, UriVisit>()

    /** The steps of [tier] that can come up now: built and [available]. */
    fun ready(tier: TrailTier): List<ClueStep> = pool(tier).filter(::available)

    /**
     * The tier of clue scroll [obj] is, null if it isn't one: a step's own item, a shared scroll, or any other item
     * with a tier's param that is read (a step outside the built regions). The old caskets, keys and puzzle boxes carry
     * the param too but aren't read, so they aren't clue scrolls.
     */
    fun tierOf(obj: Int): TrailTier? {
        data.byItem[obj]?.let { return it.tier }
        TrailTier.entries.firstOrNull { it.sharedClue == obj }?.let { return it }
        val type = world.objTypes[obj] ?: return null
        if (type.iop(1) != READ) return null
        return TrailTier.entries.firstOrNull { type.intParam(it.param) == 1 }
    }

    /** The item a clue scroll on [step] is. */
    fun item(step: ClueStep): Int = step.items.firstOrNull() ?: step.tier.sharedClue

    private fun Inventory.hasClue(tier: TrailTier) = contents().any { (_, obj) -> obj.count > 0 && tierOf(obj.id) == tier }

    /** Whether [player] holds a clue scroll of [tier] in the backpack or the bank. */
    fun holdsClue(player: Player, tier: TrailTier): Boolean = player.inventory.hasClue(tier) || player.bank.hasClue(tier)

    /** How many scroll boxes and clue scrolls of [tier] [player] may hold together: [BOX_CAP], and one per scroll case opened. */
    fun cap(player: Player, tier: TrailTier): Int = BOX_CAP + SCROLL_CASES.getValue(tier).count { it in player.trails.cases }

    /** Scroll boxes and clue scrolls of [tier] the player holds, against [cap]. */
    fun held(player: Player, tier: TrailTier): Long =
        player.inventory.count(tier.scrollBox) + player.bank.count(tier.scrollBox) + if (holdsClue(player, tier)) 1 else 0

    /**
     * What a monster's drop of clue scroll [obj] becomes for [killer]: its tier's scroll box, or null when the killer
     * holds as many as they may (told so). With no killer the box drops anyway.
     */
    fun dropFor(killer: Player?, obj: ObjType): ObjType? {
        val tier = tierOf(obj.id) ?: return obj
        val box = world.objTypes[tier.scrollBox] ?: return obj
        if (killer != null && held(killer, tier) >= cap(killer, tier)) {
            world.message(killer, TrailLines.suspicion(tier))
            return null
        }
        return box
    }

    /**
     * Gives [player] [count] of [type] from a source that isn't a monster's drop (a pickpocket, an impling jar, a chest):
     * a clue scroll as its tier's scroll box, or nothing past the cap (told so), into the backpack or on their tile.
     */
    fun give(player: Player, type: ObjType, count: Int) {
        val given = dropFor(player, type) ?: return
        world.groundObjs.give(player, given, count)
    }

    /**
     * Take on a scroll box or clue scroll on the ground (exploit review R45C-2: the cap and the one clue a tier hold on
     * pickup too, as on live). A clue scroll of a tier already held stays where it is; scroll boxes past the cap are
     * destroyed, the rest taken (the Wiki's Treasure Trails page for both messages).
     */
    suspend fun take(context: ScriptContext, obj: osrsps.game.zone.ZoneObj) {
        val player = context.player
        // From beside the tile (on a table), a tick's wait and human_pickuptable first, as GroundObjs takes anything
        // else; the checks below are made after it (Codex review on #907).
        if (player.x != obj.x || player.z != obj.z) {
            context.delay(1)
            player.anim(GroundObjs.ANIM_PICKUP_TABLE)
        }
        val box = TrailTier.entries.firstOrNull { it.scrollBox == obj.id }
        if (box == null) {
            val tier = tierOf(obj.id)
            if (tier != null && holdsClue(player, tier)) return world.message(player, TrailLines.ALREADY_CLUE)
            world.groundObjs.take(player, obj)
            return
        }
        val room = (cap(player, box) - held(player, box)).coerceAtLeast(0L)
        if (obj.count.toLong() <= room) {
            world.groundObjs.take(player, obj)
            return
        }
        if (world.groundObjs.takeUpTo(player, obj, room.toInt())) world.message(player, TrailLines.BOXES_DESTROYED)
    }

    /** Starts a new trail of [tier] for [player] at [first] (a random built step when null), returning its clue item. */
    fun start(player: Player, tier: TrailTier, first: ClueStep? = null): Int? {
        val pool = ready(tier)
        val step = first ?: pool.randomOrNull() ?: return null
        // Temporary (Sam, 19 Nov 2026): no longer than the steps built, so a trail never repeats a step.
        val length = world.random.between(tier.minSteps, tier.maxSteps).coerceAtMost(maxOf(pool.size, 1))
        player.trails[tier] = Trail(step.key, length)
        return item(step)
    }

    private fun List<ClueStep>.randomOrNull(): ClueStep? = if (isEmpty()) null else this[world.random.nextInt(size)]

    /**
     * The step the clue scroll [obj] (of [tier]) is on. An own-step item that isn't the trail's step starts a new trail
     * there.
     */
    fun stepOf(player: Player, tier: TrailTier, obj: Int): ClueStep? {
        val own = data.byItem[obj]
        val trail = player.trails[tier]
        if (own != null) {
            if (trail == null || trail.step != own.key) start(player, tier, own)
            return own
        }
        if (obj != tier.sharedClue) return null
        return trail?.let { data[it.step] } ?: run {
            // A shared scroll with no trail behind it (from before trails, or `::item`): it starts one.
            start(player, tier)
            player.trails[tier]?.let { data[it.step] }
        }
    }

    fun openBox(context: ScriptContext, slot: Int, tier: TrailTier) {
        val player = context.player
        if (player.inventory[slot]?.id != tier.scrollBox) return
        if (holdsClue(player, tier)) return world.message(player, TrailLines.BOX_REFUSES)
        val stack = player.inventory[slot]!!.count
        if (stack > 1 && player.inventory.freeSlots == 0) return world.message(player, TrailLines.BOX_NO_SPACE)
        val clue = start(player, tier) ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        val type = world.objTypes[clue] ?: return
        player.inventory.remove(slot, 1)
        if (player.inventory[slot] == null) player.inventory[slot] = InvObj(clue, 1) else player.inventory.add(type, 1)
    }

    fun read(context: ScriptContext, slot: Int) {
        val player = context.player
        val obj = player.inventory[slot]?.id ?: return
        byChallenge[obj]?.let { step ->
            val question = step.challenge as? TrailChallenge.Question ?: return
            world.interfaces.openModal(player, FrameSlot.MAINMODAL, CLUE_TEXT)
            world.interfaces.setText(player, CLUE_TEXT, CLUE_TEXT_WORDS, question.text)
            return
        }
        val tier = tierOf(obj) ?: return
        val step = stepOf(player, tier, obj) ?: return
        val map = mapInterface(step)
        if (map != null) {
            world.interfaces.openModal(player, FrameSlot.MAINMODAL, map)
            return
        }
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, CLUE_TEXT)
        world.interfaces.setText(player, CLUE_TEXT, CLUE_TEXT_WORDS, words(player, step))
    }

    /** The step whose words [player]'s clue of [tier] shows: Charlie's task once he has set one, else [step]. */
    private fun shown(player: Player, tier: TrailTier, step: ClueStep): ClueStep =
        player.trails[tier]?.task?.let { data[it] } ?: step

    /** The scroll's words: the cache's clue text, a coordinate written out, or a hot-cold step's lines. */
    fun words(player: Player, step: ClueStep): String {
        if (step.type == StepType.HOT_COLD) return world.textFor(player)[if (step.tier == TrailTier.MASTER) HotColdLines.CLUE_MASTER else HotColdLines.CLUE_BEGINNER]
        val text = shown(player, step.tier, step).text.orEmpty()
        if (step.type != StepType.COORDINATE) return text
        val match = COORDINATE.matchEntire(text.trim()) ?: return text
        val (latDeg, latMin, ns, lonDeg, lonMin, ew) = match.destructured
        val words = world.textFor(player)
        return words.get(
            TrailLines.COORDINATE,
            "lat_deg" to latDeg, "lat_min" to latMin, "lat_dir" to words[if (ns == "N") TrailLines.NORTH else TrailLines.SOUTH],
            "lon_deg" to lonDeg, "lon_min" to lonMin, "lon_dir" to words[if (ew == "E") TrailLines.EAST else TrailLines.WEST],
        )
    }

    fun checkSteps(context: ScriptContext, slot: Int) {
        val player = context.player
        val obj = player.inventory[slot]?.id ?: return
        val tier = tierOf(obj) ?: return
        stepOf(player, tier, obj) ?: return
        world.message(player, TrailLines.CHECK_STEPS, "count" to (player.trails[tier]?.done?.size ?: 0))
    }

    /** A spade's Dig: solves every carried clue whose dig spot is within a tile. */
    suspend fun dig(context: ScriptContext) {
        val player = context.player
        player.anim(ANIM_DIG)
        for ((slot, obj) in player.inventory.contents()) {
            val tier = tierOf(obj.id) ?: continue
            val step = stepOf(player, tier, obj.id) ?: continue
            if (!isDig(step) || step !in pool(tier) || !dressed(player, step)) continue
            val spot = step.targets.first().tile
            val range = if (step.type == StepType.HOT_COLD) HotCold.digRange(tier) else DIG_RANGE
            if (spot.level != player.level || maxOf(kotlin.math.abs(spot.x - player.x), kotlin.math.abs(spot.z - player.z)) > range) continue
            if (mustFight(player, tier, step)) return
            solve(context, slot, tier, step)
            return
        }
        world.message(player, GameLines.NOTHING_INTERESTING)
    }

    /**
     * [player] did emote [index] (the emote tab's index). An emote step held whose first emote it is, done near the
     * step's spot in the step's outfit, calls Uri beside the player; a step's second emote, done while he is there,
     * lets him hand over the clue.
     */
    fun emoted(player: Player, index: Int) {
        uris[player]?.let { visit ->
            val step = data[visit.step] ?: return
            // The second emote counts only at the spot and dressed as the step asks, as the first.
            if (step.emotes.getOrNull(1) == index && atSpot(player, step) && dressed(player, step)) visit.ready = true
            return
        }
        for ((_, obj) in player.inventory.contents()) {
            val tier = tierOf(obj.id) ?: continue
            if (obj.id in byChallenge) continue
            val step = stepOf(player, tier, obj.id) ?: continue
            if (step.type != StepType.EMOTE || step !in pool(tier) || step.emotes.firstOrNull() != index) continue
            if (!atSpot(player, step) || !dressed(player, step)) continue
            if (mustFight(player, tier, step)) return
            callUri(player, tier, step)
            return
        }
    }

    /** Whether [player] is within [EMOTE_RANGE] of [step]'s spot, on its level. */
    private fun atSpot(player: Player, step: ClueStep): Boolean {
        val spot = step.targets.first().tile
        return spot.level == player.level && maxOf(kotlin.math.abs(spot.x - player.x), kotlin.math.abs(spot.z - player.z)) <= EMOTE_RANGE
    }

    /** Whether [step]'s fight stands between [player] and its clue: it is brought out (or kept waiting) if so. */
    private fun mustFight(player: Player, tier: TrailTier, step: ClueStep): Boolean {
        if (step.combatNpcs.isEmpty() || player.trails[tier]?.fought == true) return false
        return fights.start(player, tier, step)
    }

    /** Whether [player] wears what [step] asks: any one of its outfits, or anything when it asks nothing. */
    fun dressed(player: Player, step: ClueStep): Boolean = step.outfits.isEmpty() || step.outfits.any { outfit ->
        outfit.fits({ slot -> player.equipment[slot]?.id }) { id -> world.objTypes[id]?.intParam(TrailOutfit.PARAM_ITEM_GROUP) }
    }

    private fun callUri(player: Player, tier: TrailTier, step: ClueStep) {
        val type = world.npcTypes[URI.getValue(tier)] ?: return
        val tile = listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1).map { (dx, dz) -> player.x + dx to player.z + dz }
            .firstOrNull { (x, z) -> world.canStand(x, z, player.level) } ?: (player.x to player.z)
        val npc = world.spawnNpc(osrsps.game.npc.NpcSpawn(type, tile.first, tile.second, player.level, source = "trail uri", respawns = false)) ?: return
        uris[player] = UriVisit(npc, tier, step.key, world.tick + URI_TICKS, ready = step.emotes.size < 2)
    }

    /** Uri leaves when his time is up or the player who called him has gone. Run once a tick. */
    fun process() {
        val gone = uris.filter { (player, visit) -> player.loggedOut || world.tick >= visit.until }
        for ((player, visit) in gone) {
            uris.remove(player)
            world.removeNpc(visit.npc)
        }
        fights.process()
    }

    /** Talk-to on Uri: the next clue for the player who called him, dressed and with every emote done. */
    suspend fun talkToUri(context: ScriptContext, npc: osrsps.game.npc.Npc) {
        val player = context.player
        val visit = uris[player]?.takeIf { it.npc === npc }
        val step = visit?.let { data[it.step] }
        if (visit == null || step == null || player.trails[visit.tier]?.step != step.key || !dressed(player, step)) {
            world.dialogues.start(context, npc) { chatNpc(ChatAnim.NEUTRAL, TrailLines.URI_NO_BUSINESS) }
            return
        }
        if (!visit.ready) {
            world.dialogues.start(context, npc) { chatNpc(ChatAnim.NEUTRAL, TrailLines.URI_NOT_YET) }
            return
        }
        world.dialogues.start(context, npc) { chatNpc(ChatAnim.NEUTRAL, TrailLines.URI_CLUE) }
        // The line waits on the player: he may have left, or the outfit come off, meanwhile.
        if (uris[player] !== visit || player.trails[visit.tier]?.step != step.key || !dressed(player, step)) return
        uris.remove(player)
        world.removeNpc(npc)
        solve(context, clueSlot(player, visit.tier) ?: return, visit.tier, step)
    }

    /**
     * The clue step [player]'s click takes, if any: Talk-to on a talk step's NPC, or Search or Open on a search step's
     * loc, while the step's clue scroll is in the backpack. Registered as a first check, so it comes before the NPC's or
     * loc's own handler.
     */
    fun intercept(player: Player, op: Op): Handler? {
        val target = when (op.target) {
            OpTarget.NPC -> if (world.npcTypes[op.id]?.op(op.option) == TALK) op else return null
            OpTarget.LOC -> if (world.locs.types[op.id]?.ops?.getOrNull(op.option - 1) in SEARCH_OPS) op else return null
            else -> return null
        }
        for ((slot, obj) in player.inventory.contents()) {
            val tier = tierOf(obj.id) ?: continue
            if (obj.id in byChallenge) continue
            val step = stepOf(player, tier, obj.id) ?: continue
            // Undressed for a step that names an outfit, the NPC or loc does what it always does.
            if (step !in pool(tier) || !dressed(player, step)) continue
            if (step.type == StepType.HOT_COLD && target.target == OpTarget.NPC && op.id in HotCold.GIVERS[tier].orEmpty()) {
                val npc = target.npc ?: continue
                val device = HotCold.DEVICES[tier] ?: continue
                if (player.inventory.count(device) > 0 || player.bank.count(device) > 0) continue
                return { hotCold.giveDevice(this, npc, tier) }
            }
            when (val goal = step.targets.first()) {
                is TrailTarget.Npc -> if (target.target == OpTarget.NPC && (op.id == goal.npc || op.id == goal.fallbackNpc || target.npc?.type?.id == goal.npc)) {
                    val npc = target.npc ?: continue
                    return { talk(this, npc, tier, step) }
                }
                is TrailTarget.Loc -> {
                    val loc = target.loc ?: continue
                    if (target.target == OpTarget.LOC && loc.x == goal.tile.x && loc.z == goal.tile.z && loc.level == goal.tile.level) {
                        return { solve(this, slot, tier, step) }
                    }
                }
                is TrailTarget.Key -> {
                    val loc = target.loc ?: continue
                    if (target.target == OpTarget.LOC && loc.x == goal.tile.x && loc.z == goal.tile.z && loc.level == goal.tile.level) {
                        return { unlock(this, tier, step, goal) }
                    }
                }
                else -> {}
            }
        }
        return null
    }

    /**
     * A talk step's NPC, spoken to with its clue: the next clue, or first a challenge scroll whose question the player
     * answers when they talk again (the Wiki's Challenge scroll page: "talk to the NPC again after receiving the scroll
     * to answer", no penalty for a wrong answer).
     */
    suspend fun talk(context: ScriptContext, npc: osrsps.game.npc.Npc, tier: TrailTier, step: ClueStep) {
        val player = context.player
        (step.challenge as? TrailChallenge.Box)?.let { box -> return talkBox(context, npc, tier, step, box) }
        if (isCharlie(step)) return talkCharlie(context, npc, tier, step)
        val question = step.challenge as? TrailChallenge.Question
        if (question == null || step.challengeItem < 0) {
            world.dialogues.start(context, npc) { chatNpc(ChatAnim.HAPPY, TrailLines.NPC_CLUE) }
            solve(context, clueSlot(player, tier) ?: return, tier, step)
            return
        }
        if (!holds(player, step.challengeItem)) {
            val scroll = world.objTypes[step.challengeItem] ?: return
            world.dialogues.start(context, npc) { chatNpc(ChatAnim.NEUTRAL, TrailLines.NPC_CHALLENGE) }
            if (player.inventory.add(scroll, 1) == 0) world.message(player, GameLines.NO_SPACE)
            return
        }
        val answer = context.countDialog(world.textFor(player)[TrailLines.CHALLENGE_PROMPT])
        if (answer !in question.answers) {
            world.dialogues.start(context, npc) { chatNpc(ChatAnim.CONFUSED, TrailLines.NPC_WRONG) }
            return
        }
        world.dialogues.start(context, npc) { chatNpc(ChatAnim.HAPPY, TrailLines.NPC_RIGHT) }
        // Only once the step goes on is the challenge scroll used up, from the backpack or else the bank, so leaving the
        // dialogue above keeps it. Guessed: live takes it back as well.
        val slot = clueSlot(player, tier) ?: return
        if (player.trails[tier]?.step != step.key || !holds(player, step.challengeItem) || !dressed(player, step)) return
        val held = player.inventory.contents().firstOrNull { it.second.id == step.challengeItem }
        if (held != null) player.inventory.remove(held.first, 1) else removeFromBank(player, step.challengeItem)
        solve(context, slot, tier, step)
    }

    /**
     * Charlie the Tramp's beginner step (the Wiki's Charlie the Tramp page): he sets one of his tasks, a beginner skill
     * challenge whose words the clue then shows, and takes its item for the next clue. Any such item counts, from
     * anywhere (the Wiki: since 30 November 2022).
     */
    private suspend fun talkCharlie(context: ScriptContext, npc: osrsps.game.npc.Npc, tier: TrailTier, step: ClueStep) {
        val player = context.player
        val trail = player.trails[tier] ?: return
        val task = trail.task?.let { data[it] }
        if (task == null) {
            val tasks = data.tier(tier).filter { it.type == StepType.SKILL_CHALLENGE && it.id in CHARLIE_ITEMS }
            val chosen = tasks.randomOrNull() ?: return
            trail.task = chosen.key
            world.dialogues.start(context, npc) { chatNpc(ChatAnim.NEUTRAL, TrailLines.CHARLIE_TASK) }
            return
        }
        val item = CHARLIE_ITEMS[task.id] ?: return
        val held = player.inventory.contents().firstOrNull { it.second.id == item }
        if (held == null) {
            world.dialogues.start(context, npc) { chatNpc(ChatAnim.NEUTRAL, TrailLines.CHARLIE_WAITING) }
            return
        }
        world.dialogues.start(context, npc) { chatNpc(ChatAnim.HAPPY, TrailLines.CHARLIE_THANKS) }
        // Only once the step goes on is the item taken, so leaving the dialogue above keeps it.
        val slot = clueSlot(player, tier) ?: return
        if (player.trails[tier]?.step != step.key || trail.task != task.key) return
        val still = player.inventory.contents().firstOrNull { it.second.id == item } ?: return
        player.inventory.remove(still.first, 1)
        trail.task = null
        solve(context, slot, tier, step)
    }

    /**
     * A talk step whose NPC wants a box solved first: the box if the player has none of its kind, the next clue for a
     * solved one (the NPC taking the box), or a word to keep at it.
     */
    private suspend fun talkBox(context: ScriptContext, npc: osrsps.game.npc.Npc, tier: TrailTier, step: ClueStep, box: TrailChallenge.Box) {
        val player = context.player
        // Only a box this step takes counts: another step's box neither blocks this one nor answers it.
        val accepted = boxes.accepts(step, box)
        val held = player.inventory.contents().map { it.second.id }.filter { it in accepted }.let { ids -> ids.firstOrNull { boxes.solved(player, it) } ?: ids.firstOrNull() }
        if (held == null) {
            // A banked box of this step's is kept as it is, not replaced by a fresh one that would scramble its saved
            // state. Guessed: the NPC says to keep at it, as for an unsolved box.
            if (accepted.any { player.bank.count(it) > 0 }) {
                world.dialogues.start(context, npc) { chatNpc(ChatAnim.NEUTRAL, TrailLines.NPC_BOX_UNSOLVED) }
                return
            }
            val item = boxes.boxFor(step, box) ?: return
            world.dialogues.start(context, npc) { chatNpc(ChatAnim.NEUTRAL, TrailLines.NPC_BOX) }
            boxes.give(player, item)
            return
        }
        if (!boxes.solved(player, held)) {
            world.dialogues.start(context, npc) { chatNpc(ChatAnim.NEUTRAL, TrailLines.NPC_BOX_UNSOLVED) }
            return
        }
        world.dialogues.start(context, npc) { chatNpc(ChatAnim.HAPPY, TrailLines.NPC_BOX_SOLVED) }
        // Only once the dialogue is through is the box taken, so leaving it keeps the solved box; checked again first.
        val slot = clueSlot(player, tier) ?: return
        if (player.trails[tier]?.step != step.key || player.inventory.count(held) == 0L || !boxes.solved(player, held) || !dressed(player, step)) return
        boxes.take(player, held)
        solve(context, slot, tier, step)
    }

    /** Search or Open on a key step's chest or drawers: with the key, it is used up and the next clue comes. */
    private suspend fun unlock(context: ScriptContext, tier: TrailTier, step: ClueStep, goal: TrailTarget.Key) {
        val player = context.player
        val key = player.inventory.contents().firstOrNull { it.second.id == goal.key } ?: return world.message(player, TrailLines.LOCKED)
        player.inventory.remove(key.first, 1)
        solve(context, clueSlot(player, tier) ?: return, tier, step)
    }

    /** Where [tier]'s clue scroll is in the backpack now: it may have moved while a dialogue was open. */
    private fun clueSlot(player: Player, tier: TrailTier): Int? =
        player.inventory.contents().firstOrNull { (_, obj) -> obj.id !in byChallenge && tierOf(obj.id) == tier }?.first

    /** [step] of the clue in [slot] is done: the next clue, or the reward casket after the last. */
    suspend fun solve(context: ScriptContext, slot: Int, tier: TrailTier, step: ClueStep) {
        val player = context.player
        val trail = player.trails[tier] ?: return
        if (trail.step != step.key) return
        // A dig, talk or search step that names an outfit (a cryptic's "Equip three Zamorak related items.") wants it
        // worn, as an emote step does (exploit review 49, R49A-2). Guessed: nothing happens without it, no message.
        if (step.type != StepType.EMOTE && !dressed(player, step)) return
        trail.done += step.key
        val next = ready(tier).filter { it.key !in trail.done }
        if (trail.done.size >= trail.length || next.isEmpty()) {
            player.trails[tier] = null
            player.inventory.remove(slot)
            val casket = world.objTypes[tier.rewardCasket] ?: return
            if (player.inventory.count(casket.id) == 0L) player.inventory[slot] = InvObj(casket.id, 1) else player.inventory.add(casket, 1)
            world.dialogues.start(context) { objbox(casket.id, TrailLines.CASKET) }
            return
        }
        val following = next[world.random.nextInt(next.size)]
        trail.step = following.key
        trail.fought = false
        trail.task = null
        player.inventory[slot] = InvObj(item(following), 1)
        world.dialogues.start(context) { objbox(item(following), TrailLines.ANOTHER_CLUE) }
    }

    /**
     * Open on a reward casket: one casket's rewards ([TrailRewards]) and any milestone its count reaches, shown on the
     * reward screen and put in the backpack (what doesn't fit on the floor under the player), then the three messages.
     */
    fun openCasket(context: ScriptContext, slot: Int, tier: TrailTier) {
        val player = context.player
        if (player.inventory[slot]?.id != tier.rewardCasket) return
        val rewards = world.trailRewards[tier] ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        player.inventory.remove(slot, 1)
        val count = player.trails.completed(tier) + 1
        player.trails.setCompleted(tier, count)
        val drops = ArrayList(rewards.roll(world.random))
        masterBox(player, tier)?.let { drops += Drop(it, 1) }
        for (milestone in rewards.milestones) {
            val id = milestone.type.id
            // A scroll case comes with every casket from its count on until it is opened (the Wiki's Scroll case page).
            val due = if (id in CASE_ITEMS) count >= milestone.count && id !in player.trails.cases && !holds(player, id) else count == milestone.count
            if (due) drops += Drop(milestone.type, 1)
        }
        val screen = Inventory(REWARD_INV, REWARD_SLOTS)
        // Every drop goes on the screen: stackables merge, and what doesn't fit in its slots is only not shown.
        for (drop in drops) screen.add(drop.type, drop.count)
        sendInventory(player, screen) { p, message -> p.session.queue(message) }
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, REWARD_SCREEN)
        for (drop in drops) {
            giveOrDrop(player, drop)
            world.collectionLog.add(player, drop.type.id, drop.count)
        }
        world.message(player, TrailLines.WELL_DONE)
        world.message(player, TrailLines.COMPLETED, "count" to count, "tier" to tier.key)
        // Coins count at face value: they have no guide price.
        val value = drops.sumOf { (if (it.type.id == COINS) 1L else world.itemPrices.price(it.type) ?: 0L) * it.count }
        world.message(player, TrailLines.WORTH, "value" to "%,d".format(value))
    }

    /**
     * The master scroll box an easy, medium, hard or elite casket can hold (TT10; the Wiki's Clue scroll (master) page:
     * 1/50, 1/30, 1/15 and 1/5), unless the player holds as many master boxes and clues as they may. Every roll hits
     * for a player on `::clue lucky`. Left out: Watson, who swaps one clue of each lower tier for a master (Hosidius,
     * outside the built regions).
     */
    private fun masterBox(player: Player, tier: TrailTier): ObjType? {
        val chance = MASTER_CHANCES[tier] ?: return null
        if (held(player, TrailTier.MASTER) >= cap(player, TrailTier.MASTER)) return null
        if (player.saveKey !in skilling.lucky && world.random.nextInt(chance) != 0) return null
        return world.objTypes[TrailTier.MASTER.scrollBox]
    }

    private fun removeFromBank(player: Player, id: Int) {
        val slot = player.bank.contents().firstOrNull { it.second.id == id }?.first ?: return
        player.bank.remove(slot, 1)
    }

    private fun holds(player: Player, id: Int) = player.inventory.count(id) > 0 || player.bank.count(id) > 0

    private fun giveOrDrop(player: Player, drop: Drop) {
        val added = player.inventory.add(drop.type, drop.count)
        if (added >= drop.count) return
        val stacks = if (drop.type.stackable) listOf(drop.count - added) else List(drop.count - added) { 1 }
        for (n in stacks) {
            world.groundObjs.addPrivate(drop.type, n, player.x, player.z, player.level, player.saveKey, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.DROP_DESPAWN_TICKS)
        }
    }

    /** Open on a scroll case: gone, and its tier's cap one higher for good. */
    fun openCase(context: ScriptContext, slot: Int, id: Int) {
        val player = context.player
        if (player.inventory[slot]?.id != id) return
        player.inventory.remove(slot, 1)
        player.trails.cases += id
        world.message(player, TrailLines.CASE_OPENED)
    }

    /** The map interface a map step shows, from the step's item (or the beginner step's id). */
    fun mapInterface(step: ClueStep): Int? = if (step.type != StepType.MAP) null else
        if (step.tier == TrailTier.BEGINNER) BEGINNER_MAPS[step.id] else step.items.firstNotNullOfOrNull { MAPS[it] }

    companion object {
        /** The scroll boxes and clue scrolls of a tier a player may hold together, before scroll cases. */
        const val BOX_CAP = 2

        /** The 1-in chance of a master scroll box in each tier's casket (the Wiki's Clue scroll (master) page). */
        val MASTER_CHANCES: Map<TrailTier, Int> = mapOf(TrailTier.EASY to 50, TrailTier.MEDIUM to 30, TrailTier.HARD to 15, TrailTier.ELITE to 5)

        /** Coins, worth one each in a casket's value. */
        const val COINS = 995

        /** trail_rewardscreen, which draws inv trail_rewardinv (12 slots) when it loads (osrs-dumps). */
        const val REWARD_SCREEN = 73
        const val REWARD_INV = 141
        const val REWARD_SLOTS = 12

        /** Each tier's minor and major scroll cases. */
        val SCROLL_CASES: Map<TrailTier, List<Int>> = mapOf(
            TrailTier.BEGINNER to listOf(30902, 30904), TrailTier.EASY to listOf(30906, 30908), TrailTier.MEDIUM to listOf(30910, 30912),
            TrailTier.HARD to listOf(30914, 30916), TrailTier.ELITE to listOf(30918, 30920), TrailTier.MASTER to listOf(30922, 30924),
        )
        val CASE_ITEMS: Set<Int> = SCROLL_CASES.values.flatten().toSet()

        /** `trail_cluetext` and its words component (`text`). */
        const val CLUE_TEXT = 203
        const val CLUE_TEXT_WORDS = 2

        /** `human_dig`. */
        const val ANIM_DIG = 830
        const val SPADE = 952
        const val DIG_RANGE = 1

        /** The NPC option a talk step takes, and the loc options a search step takes (guessed: either works). */
        const val TALK = "Talk-to"
        val SEARCH_OPS = setOf("Search", "Open")

        /** The kinds of step whose NPC or loc TT3 builds; Charlie's, Falo's, emotes and the rest come in their own steps. */
        val TALK_TYPES = setOf(StepType.ANAGRAM, StepType.CIPHER, StepType.CRYPTIC, StepType.MAP)

        /** How near an emote step's spot its emote counts, in tiles. Guessed: the clue helper gives one tile. */
        const val EMOTE_RANGE = 4

        /** How long Uri stays if no one talks to him, in ticks. Guessed. */
        const val URI_TICKS = 200

        /** Uri of each tier: trail_beginner_uri to trail_master_uri. */
        val URI: Map<TrailTier, Int> = mapOf(
            TrailTier.BEGINNER to 8638, TrailTier.EASY to 1774, TrailTier.MEDIUM to 1775, TrailTier.HARD to 1776, TrailTier.ELITE to 6584, TrailTier.MASTER to 7311,
        )

        /** Every clue scroll's op1. */
        private const val READ = "Read"

        val DIG_TYPES = setOf(StepType.MAP, StepType.COORDINATE, StepType.CRYPTIC, StepType.HOT_COLD)

        /** Charlie the Tramp, and the item each of his tasks (the beginner skill challenges) asks: the Wiki's list. */
        const val CHARLIE = 5209
        val CHARLIE_ITEMS: Map<Int, Int> = mapOf(549 to 333, 550 to 351, 551 to 345, 552 to 335, 553 to 440, 554 to 1203, 555 to 1129, 556 to 1095)

        fun isCharlie(step: ClueStep): Boolean = step.tier == TrailTier.BEGINNER && (step.targets.firstOrNull() as? TrailTarget.Npc)?.npc == CHARLIE

        /** A coordinate step's text in the cache: `00.20S 23.15E`. */
        private val COORDINATE = Regex("""(\d+)\.(\d+)([NS]) (\d+)\.(\d+)([EW])""")

        /**
         * Map clue items and the interface each shows. 2713, 2716, 2827, 2722: 2004Scape's trail scripts (trail_map01,
         * 02, 03, 05; map05 is 350 in RuneLite's InterfaceID). 3518 (Wizards' Tower): inferred, trail_map11 being the
         * beginner map RuneLite places there.
         */
        val MAPS: Map<Int, Int> = mapOf(2713 to 346, 2716 to 347, 2827 to 348, 2722 to 350, 3518 to 356)

        /**
         * Beginner map steps (the clue helper's id) and their interfaces, from RuneLite's BeginnerMapClue: Champions'
         * Guild trail_map01, Varrock East Mine 02, Draynor bank 03, Falador stones 06, Wizards' Tower 11.
         */
        val BEGINNER_MAPS: Map<Int, Int> = mapOf(101 to 346, 102 to 347, 103 to 348, 104 to 351, 105 to 356)

        /**
         * The regions built so far: Misthalin as Sam outlined it (x 3070-3400, z 3140-3523, without the desert corner
         * east of x 3274 and south of z 3328), its upper floors and the dungeons beneath it (z + 6400).
         */
        /**
         * Whether [step] can come up yet: inside the regions built so far and of a kind built so far. TT1 builds the
         * dig steps (a map, a coordinate without a fight, a cryptic dig) in Misthalin; TT3 the talk steps (an NPC,
         * with or without a challenge scroll's question, or with a puzzle or light box from TT6) and the search steps
         * (a loc that needs no key).
         */
        fun built(step: ClueStep): Boolean {
            val target = step.targets.firstOrNull() ?: return false
            if (!inBuiltRegion(target.tile) || step.targets.size != 1) return false
            // A fight is built when the cache names its NPCs (TT7).
            if (step.combat && step.combatNpcs.isEmpty()) return false
            if (step.type == StepType.EMOTE) return step.emotes.isNotEmpty()
            if (step.type == StepType.HOT_COLD) return target is TrailTarget.Dig && step.tier in HotCold.DEVICES
            return isDig(step) || (step.type in TALK_TYPES && (target is TrailTarget.Npc || target is TrailTarget.Loc || target is TrailTarget.Key))
        }

        fun isDig(step: ClueStep): Boolean =
            step.type in DIG_TYPES && step.targets.size == 1 && step.targets.first() is TrailTarget.Dig

        fun inBuiltRegion(tile: TrailTile): Boolean {
            val z = if (tile.z >= 6400) tile.z - 6400 else tile.z
            if (tile.x !in 3070..3400 || z !in 3140..3523) return false
            return !(tile.x > 3274 && z < 3328)
        }
    }
}

/** Scroll boxes, clue scrolls, reward caskets, scroll cases, the spade, Uri and the STASH units. */
fun registerTreasureTrails(world: World) {
    val trails = world.trails
    for (tier in TrailTier.entries) {
        world.handlers.onObj(tier.scrollBox, 1) { op -> trails.openBox(this, op.sub, tier) }
        world.handlers.onObj(tier.rewardCasket, 1) { op -> trails.openCasket(this, op.sub, tier) }
        for (case in TreasureTrails.SCROLL_CASES.getValue(tier)) world.handlers.onObj(case, 1) { op -> trails.openCase(this, op.sub, case) }
        if (tier.sharedClue >= 0) {
            world.handlers.onObj(tier.sharedClue, 1) { op -> trails.read(this, op.sub) }
            world.handlers.onObj(tier.sharedClue, 2) { op -> trails.checkSteps(this, op.sub) }
        }
    }
    for (item in trails.data.byItem.keys) {
        world.handlers.onObj(item, 1) { op -> trails.read(this, op.sub) }
        world.handlers.onObj(item, 2) { op -> trails.checkSteps(this, op.sub) }
    }
    world.handlers.onObj(TreasureTrails.SPADE, 1) { trails.dig(this) }
    // Every clue scroll in the cache (a Read op and a tier's param), not only the ones a built step names.
    val pickups = TrailTier.entries.map { it.scrollBox } + TrailTier.entries.map { it.sharedClue }.filter { it >= 0 } + trails.data.byItem.keys +
        (0 until world.objTypes.size).filter { trails.tierOf(it) != null }
    for (id in pickups.distinct()) world.handlers.onGroundObj(id, GroundObjs.TAKE_OPTION) { op -> trails.take(this, op.groundObj ?: return@onGroundObj) }
    // Talk and search steps take their NPC's or loc's click ahead of its own handler.
    world.handlers.first(trails::intercept)
    for (uri in TreasureTrails.URI.values) world.handlers.onNpc(uri, 1) { op -> trails.talkToUri(this, op.npc ?: return@onNpc) }
    for (box in TrailBoxes.PUZZLES.keys + TrailBoxes.LIGHT_BOX) world.handlers.onObj(box, 1) { op -> trails.boxes.open(this, op.sub) }
    world.handlers.onObj(TrailBoxes.LIGHT_BOX, TrailBoxes.DESTROY) { op ->
        val slot = op.sub
        world.dialogues.start(this) {
            if (!confirmDestroy(TrailBoxes.LIGHT_BOX, 1, text(GameLines.DESTROY_TITLE), text(TrailLines.DESTROY_LIGHT_BOX))) return@start
            // The box may have moved while the check was open: only the one in that slot goes, and its lights with it.
            if (player.inventory[slot]?.id != TrailBoxes.LIGHT_BOX) return@start
            player.inventory.remove(slot, 1)
            if (player.inventory.count(TrailBoxes.LIGHT_BOX) == 0L && player.bank.count(TrailBoxes.LIGHT_BOX) == 0L) trails.boxes.forget(player, TrailBoxes.LIGHT_BOX)
        }
    }
    world.handlers.onButton(TrailBoxes.SLIDE_PUZZLE, TrailBoxes.SLIDE_PIECES) { op -> trails.boxes.slide(this, op.sub) }
    for ((index, button) in TrailBoxes.LIGHT_SWITCHES.withIndex()) world.handlers.onButton(TrailBoxes.LIGHT_PUZZLE, button) { trails.boxes.flip(this, index) }
    val fighters = trails.data.steps.values.flatMap { it.combatNpcs }.toSet()
    world.npcScripts.onDeath(fighters) { trails.fights.died(it) }
    for (device in HotCold.DEVICES.values) world.handlers.onObj(device, 1) { op -> trails.hotCold.feel(this, op.sub) }
    for (stash in trails.stashes.units.values) {
        world.handlers.onLoc(stash.loc, StashUnits.BUILD) { trails.stashes.build(this, stash) }
        world.handlers.onLoc(stash.loc, StashUnits.SEARCH) { trails.stashes.search(this, stash) }
    }
    for (scroll in trails.data.steps.values.map { it.challengeItem }.filter { it >= 0 }.distinct()) {
        world.handlers.onObj(scroll, 1) { op -> trails.read(this, op.sub) }
    }
}
