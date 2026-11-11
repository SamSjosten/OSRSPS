package osrsps.game.content

import osrsps.game.World
import osrsps.game.WearPos
import osrsps.game.combat.Poison
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/**
 * The H.A.M. Hideout's guards' and seated members' talk; the words are in the dialogue file, from the Wiki's
 * Transcript:H.A.M. Guard and Transcript:H.A.M. Member (seated), read 2026-11-11, which give both the same menu.
 */
object HamTalkLines : Lines("ham_talk", "Transcript:H.A.M. Guard") {
    val ROBES = line("robes", "a guard's first line when the player wears every piece of the H.A.M. outfit")
    val WHAT_DOING = line("what_doing", "menu option 1, which the player then says")
    val DOING_1 = line("doing_1", "option 1's answer, the first of three picked at random")
    val DOING_2 = line("doing_2", "option 1's answer, the second of three")
    val DOING_3 = line("doing_3", "option 1's answer, the third of three")
    val WHO = line("who", "menu option 2, which the player then says")
    val WHO_ANSWER = line("who_answer", "option 2's answer")
    val ACHIEVE = line("achieve", "menu option 3, which the player then says")
    val ACHIEVE_ANSWER = line("achieve_answer", "option 3's answer")
    val BEASTS = line("beasts", "option 3: the player's reply")
    val TOTALLY = line("totally", "option 3: the last answer")
    val WHERE = line("where", "menu option 4, which the player then says")
    val WHERE_ANSWER = line("where_answer", "option 4's answer, its first box")
    val WHERE_ANSWER_2 = line("where_answer_2", "option 4's answer, its second box")
    val OK_THANKS = line("ok_thanks", "menu option 5, which the player then says")
}

/** Johanhus Ulsbrecht's talk; the words are in the dialogue file, from the Wiki's Transcript:Johanhus Ulsbrecht, read 2026-11-11. */
object JohanhusLines : Lines("johanhus", "Transcript:Johanhus Ulsbrecht") {
    val WELCOME = line("welcome", "Talk-to: his first line")
    val WHAT_ORG = line("what_org", "menu option 1, which the player then says")
    val ORG_ANSWER = line("org_answer", "option 1's answer")
    val HOW = line("how", "option 1: the player asks how")
    val MOBILISING = line("mobilising", "option 1: his answer")
    val STRANGE = line("strange", "option 1: the player's last line")
    val WHO = line("who", "menu option 2, which the player then says")
    val WHO_ANSWER = line("who_answer", "option 2's answer")
    val OK_OPTION = line("ok_option", "menu option 3")
    val OK_SAID = line("ok_said", "menu option 3 as the player says it")
}

/** Jimmy the Chisel's one line; the words are in the dialogue file, from the Wiki's Transcript:Jimmy the Chisel, read 2026-11-11. */
object JimmyTheChiselLines : Lines("jimmy_the_chisel", "Transcript:Jimmy the Chisel") {
    val HELLO = line("hello", "Talk-to: his only line")
}

/** Picking nettles and searching coops; the Wiki gives no messages for either, so every line here is guessed. */
object MisthalinSweepLines : Lines("misthalin_sweep", "Nettles (scenery)") {
    val STUNG = line("stung", "Pick on nettles with no gloves on: the sting (guessed, from memory)")
    val NETTLES_PICKED = line("nettles_picked", "Pick on nettles with gloves on (guessed, from memory)")
    val NETTLES_NO_ROOM = line("nettles_no_room", "Pick on nettles with gloves on and a full backpack (guessed)")
    val EGG_FOUND = line("egg_found", "Search on a coop (guessed)")
    val EGG_NO_ROOM = line("egg_no_room", "Search on a coop with a full backpack (guessed)")
}

/**
 * Misthalin piece 7 step 12a, the sweep: options that had no handler on Misthalin's locs and NPCs, where they belong to
 * this piece. The NPCs the sweep found unspawned are in misthalin-sweep.json.
 *
 * - The H.A.M. Hideout's guards (2536 to 2538) and seated members (2542, 2543) talk as the Wiki's transcripts have it:
 *   five options, the first answered by one of three lines at random, and a guard first greets a player in the whole
 *   H.A.M. outfit (taken to be all seven pieces, inferred). Johanhus Ulsbrecht (2535) and Jimmy the Chisel (1361) talk
 *   as their transcripts do. Step 4a had left Talk-to out.
 * - Nettles (1181 and 5253 to 5258), west of Lumbridge and by Edgeville: picked with anything worn in the hands slot
 *   but beekeeper's gloves they give nettles and go for 13 ticks (the Nettles item's respawn on the Wiki; inferred to be
 *   the plant's); with bare hands they sting for 1 or 2 in a poison-green hitsplat, poisoning no one (Wiki, Nettles
 *   (scenery), read 2026-11-11: "up to 2"; that a sting never does 0 is guessed).
 * - The Lumbridge farms' coops (5571 to 5573) give an egg each search (Wiki, Coop, read 2026-11-11: "Always").
 * - Searching finds nothing in the Digsite barge's crates (9534), the hideout's boxes and crates (3685 to 3687, 4300,
 *   4301, 6911) and the Digsite Dungeon's sacks (2355), with the shared furniture lines.
 *
 * Guessed: every chathead expression, the nettles' and coops' messages, the pick animation (the crops' 827).
 */
object MisthalinSweep {
    const val TALK_TO = 1
    val HAM_GUARDS = listOf(2536, 2537, 2538)
    val HAM_SEATED = listOf(2542, 2543)
    const val JOHANHUS = 2535
    const val JIMMY = 1361

    val NETTLES = listOf(1181, 5253, 5254, 5255, 5256, 5257, 5258)
    const val PICK = 1
    const val NETTLES_ITEM = 4241
    const val BEEKEEPER_GLOVES = 25135
    const val NETTLES_RESPAWN = 13
    const val STING_MAX = 2

    val COOPS = listOf(5571, 5572, 5573)
    const val EGG = 1944
    const val SEARCH = 1

    val EMPTY_CRATES = listOf(9534, 4300, 4301, 6911)
    val EMPTY_BOXES = listOf(3685, 3686, 3687)
    const val DUNGEON_SACKS = 2355

    internal suspend fun Dialogue.hamTalk(world: World, guard: Boolean) = with(HamTalkLines) {
        if (guard && HamHideout.piecesWorn(player) == HamHideout.CLOTHING.size) chatNpc(ChatAnim.HAPPY, ROBES)
        while (true) {
            when (choice(WHAT_DOING, WHO, ACHIEVE, WHERE, OK_THANKS)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT_DOING)
                    chatNpc(ChatAnim.NEUTRAL, world.random.pick(listOf(DOING_1, DOING_2, DOING_3)))
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHO)
                    chatNpc(ChatAnim.NEUTRAL, WHO_ANSWER)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, ACHIEVE)
                    chatNpc(ChatAnim.NEUTRAL, ACHIEVE_ANSWER)
                    chatPlayer(ChatAnim.CONFUSED, BEASTS)
                    chatNpc(ChatAnim.ANGRY, TOTALLY)
                }
                4 -> {
                    chatPlayer(ChatAnim.QUIZ, WHERE)
                    chatNpc(ChatAnim.NEUTRAL, WHERE_ANSWER)
                    chatNpc(ChatAnim.NEUTRAL, WHERE_ANSWER_2)
                    return@with
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, OK_THANKS)
                    return@with
                }
            }
        }
    }

    internal suspend fun Dialogue.johanhus() = with(JohanhusLines) {
        chatNpc(ChatAnim.NEUTRAL, WELCOME)
        while (true) {
            when (choice(WHAT_ORG, WHO, OK_OPTION)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT_ORG)
                    chatNpc(ChatAnim.NEUTRAL, ORG_ANSWER)
                    chatPlayer(ChatAnim.QUIZ, HOW)
                    chatNpc(ChatAnim.NEUTRAL, MOBILISING)
                    chatPlayer(ChatAnim.NEUTRAL, STRANGE)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHO)
                    chatNpc(ChatAnim.HAPPY, WHO_ANSWER)
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, OK_SAID)
                    return@with
                }
            }
        }
    }

    /** Whether [player] has gloves on that keep nettles off: anything in the hands slot but beekeeper's gloves. */
    fun glovesOn(player: osrsps.game.Player): Boolean {
        val worn = player.equipment[WearPos.HANDS]?.id ?: return false
        return worn != BEEKEEPER_GLOVES
    }

    internal suspend fun ScriptContext.pickNettles(world: World, loc: ZoneLoc) {
        arriveDelay()
        if (!glovesOn(player)) {
            world.message(player, MisthalinSweepLines.STUNG)
            world.meleeCombat.statusHit(player, 1 + world.random.nextInt(STING_MAX), Poison.POISON_HITSPLAT)
            return
        }
        if (player.inventory.freeSlots == 0) return world.message(player, MisthalinSweepLines.NETTLES_NO_ROOM)
        player.anim(Crops.ANIM_PICK)
        delay(1)
        if (!world.locs.exists(loc)) return
        world.locs.remove(loc, NETTLES_RESPAWN)
        world.objTypes[NETTLES_ITEM]?.let { player.inventory.add(it, 1) }
        world.message(player, MisthalinSweepLines.NETTLES_PICKED)
    }

    internal suspend fun ScriptContext.searchCoop(world: World) {
        arriveDelay()
        if (player.inventory.freeSlots == 0) return world.message(player, MisthalinSweepLines.EGG_NO_ROOM)
        world.objTypes[EGG]?.let { player.inventory.add(it, 1) }
        world.message(player, MisthalinSweepLines.EGG_FOUND)
    }
}

fun registerMisthalinSweep(world: World) = with(MisthalinSweep) {
    for (guard in HAM_GUARDS) world.handlers.onNpc(guard, TALK_TO) { op -> world.dialogues.start(this, op.npc) { hamTalk(world, guard = true) } }
    for (member in HAM_SEATED) world.handlers.onNpc(member, TALK_TO) { op -> world.dialogues.start(this, op.npc) { hamTalk(world, guard = false) } }
    world.handlers.onNpc(JOHANHUS, TALK_TO) { op -> world.dialogues.start(this, op.npc) { johanhus() } }
    world.handlers.onNpc(JIMMY, TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.HAPPY, JimmyTheChiselLines.HELLO) }
    }
    for (nettles in NETTLES) world.handlers.onLoc(nettles, PICK) { op -> pickNettles(world, op.loc!!) }
    for (coop in COOPS) world.handlers.onLoc(coop, SEARCH) { searchCoop(world) }
    for (crate in EMPTY_CRATES) registerSearch(world, crate, SEARCH, SearchLines.CRATE_EMPTY)
    for (boxes in EMPTY_BOXES) registerSearch(world, boxes, SEARCH, SearchLines.BOXES_EMPTY)
    registerSearch(world, DUNGEON_SACKS, SEARCH, SearchLines.SACKS_EMPTY)
}
