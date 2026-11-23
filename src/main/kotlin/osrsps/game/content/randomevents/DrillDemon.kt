package osrsps.game.content.randomevents

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** Sergeant Damien's lines; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object DrillDemonLines : Lines("drill_demon", "Transcript:Sergeant Damien") {
    val ATTENTION = line("attention", "Overhead dialogue: upon spawning in, and waiting dialogue 1", "name")
    val DAYDREAMING = line("daydreaming", "Overhead dialogue: waiting dialogue 2", "name")
    val REAR_IN_GEAR = line("rear_in_gear", "Overhead dialogue: waiting dialogue 3", "name")
    val MY_CORPS = line("my_corps", "Overhead dialogue: waiting dialogue 4", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val RECOMMENDED = line("recommended", "Standard dialogue: his question", "name")
    val YES_SIR = line("yes_sir", "Standard dialogue: the first option, and the player's words")
    val NOT_INTERESTED = line("not_interested", "Standard dialogue: the second option, and the player's words")
    val MOVE_YOURSELF = line("move_yourself", "At the training area: after being teleported")
    val NOT_YET = line("not_yet", "Trying to use an exercise mat before an instruction")
    val ORDER_STARJUMPS = line("order_starjumps", "After performing the correct exercise: dialogue 1")
    val ORDER_PUSHUPS = line("order_pushups", "After performing the correct exercise: dialogue 2")
    val ORDER_JOG = line("order_jog", "After performing the correct exercise: dialogue 3")
    val ORDER_SITUPS = line("order_situps", "After performing the correct exercise: dialogue 4")
    val WRONG_PUSHUPS = line("wrong_pushups", "After performing the incorrect exercise: dialogue 1")
    val WRONG_STARJUMPS = line("wrong_starjumps", "After performing the incorrect exercise: dialogue 2")
    val WRONG_SITUPS = line("wrong_situps", "After performing the incorrect exercise: dialogue 3")
    val WRONG_JOG = line("wrong_jog", "After performing the incorrect exercise: dialogue 4")
    val BOX_STARJUMPS = line("box_starjumps", "the item box with the star jump picture")
    val BOX_PUSHUPS = line("box_pushups", "the item box with the push up picture")
    val BOX_JOG = line("box_jog", "the item box with the run picture")
    val BOX_SITUPS = line("box_situps", "the item box with the sit up picture")
    val OKAY = line("okay", "the order's first option, and the player's words")
    val LEAVE = line("leave", "the order's second option, and the player's words")
    val PATHETIC = line("pathetic", "the order's second option: his answer")
    val DONE = line("done", "If the player is finished with the event")
}

/**
 * The Drill Demon (OSRS Wiki, Drill Demon and Sergeant Damien pages and his transcript, read 2026-11-19). Sergeant
 * Damien comes as NPC 6743 (Talk-to, Dismiss) and asks whether his player can be the best; yes takes them to his camp
 * (map square 49,75, [EventAreas]), where he (337) orders an exercise. Each of the four exercise mats has a sign in
 * front of it (barrack_mat_post_1 to _4, drawn from varbits macro_drilldemon_post_1 to _4: 1 jog, 2 sit ups, 3 push
 * ups, 4 star jumps), and the signs are shuffled after every go on a mat. Four right in a row send the player back with
 * a camouflage piece not yet had (always the top first), or once the whole outfit is had, a lamp and the drill emotes
 * (varbit emote_drilldemon). Talking to him in the camp repeats the order, and leaving (LEAVE) gives nothing.
 *
 * Guessed: where the player lands (the middle of the mats); the order the other two pieces come in (bottoms, then
 * helmet); what counts as having a piece (backpack, worn or banked); a wrong exercise starting the four again; how long
 * an exercise takes ([EXERCISE_TICKS]) and its animation (the cache's five-repeat ones, which the emotes also use);
 * that talking to him repeats the same order without shuffling; the signs going blank as the player leaves. Inferred:
 * the NOT_YET line comes when the player reaches a mat before answering an order (a closed order leaves none given).
 */
object DrillDemon : RandomEvent("drill_demon", 6743) {
    const val CAMP_SERGEANT = 337
    const val USE = 1

    /** One exercise: its sign's varbit value, its order lines, the item box's picture and the animation. */
    enum class Exercise(val sign: Int, val picture: Int, val anim: Int) {
        JOG(1, 10947, 2764),
        SITUPS(2, 10948, 2763),
        PUSHUPS(3, 10946, 2762),
        STARJUMPS(4, 10949, 2761),
    }

    /** The mats by the post they stand behind (1 to 4, west to east). */
    val MATS = mapOf(20810 to 1, 16508 to 2, 9313 to 3, 20801 to 4)

    /** macro_drilldemon_post_1 to _4 (on varp macro_transmit). */
    val POST_VARBITS = listOf(1335, 1336, 1337, 1338)
    const val EMOTES_VARBIT = 4732

    const val TOP = 6654
    const val BOTTOMS = 6655
    const val HELM = 6656
    val PIECES = listOf(TOP, BOTTOMS, HELM)

    const val ROUNDS = 4
    const val EXERCISE_TICKS = 6

    /** The zones copied: the camp's training ground in map square 49,75. */
    const val FROM_X = 3152
    const val FROM_Z = 4808
    const val WIDTH_ZONES = 2
    const val HEIGHT_ZONES = 3

    val ARRIVE = Tile(3163, 4820)
    val SERGEANT_AT = Tile(3163, 4818)

    /** A stay in the camp. */
    class Camp(val sergeant: Npc?) {
        var wanted = Exercise.JOG
        var given = false
        var right = 0

        /** The exercise on each post's sign, post 1 first. */
        var signs: List<Exercise> = Exercise.entries
    }

    override val teleports = true
    override val greeting = DrillDemonLines.ATTENTION
    override val nags = with(DrillDemonLines) { listOf(ATTENTION, DAYDREAMING, REAR_IN_GEAR, MY_CORPS) }
    override val leaving = DrillDemonLines.MAYBE_LATER

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(DrillDemonLines) {
        chatNpc(ChatAnim.ANGRY, RECOMMENDED, "name" to player.username)
        if (choice(YES_SIR, NOT_INTERESTED) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, NOT_INTERESTED)
            return true
        }
        chatPlayer(ChatAnim.HAPPY, YES_SIR)
        val away = events.areas.takeAway(player, this@DrillDemon, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, ARRIVE) ?: return true
        val camp = Camp(spawn(world, away))
        away.state = camp
        shuffle(world, player, camp)
        delay(2)
        val sergeant = camp.sergeant ?: return true
        chatNpc(sergeant, ChatAnim.ANGRY, text(MOVE_YOURSELF))
        order(world, sergeant, away, camp, wrong = false)
        true
    }

    private fun spawn(world: World, away: EventAreas.Away): Npc? {
        val type = world.npcTypes[CAMP_SERGEANT] ?: return null
        val tile = away.inside(SERGEANT_AT.x, SERGEANT_AT.z)
        return world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, source = "drill demon camp for ${away.owner}", respawns = false))
    }

    /** A new exercise and the signs shuffled. */
    private fun shuffle(world: World, player: Player, camp: Camp) {
        val signs = Exercise.entries.toMutableList()
        for (i in signs.size - 1 downTo 1) {
            val j = world.random.nextInt(i + 1)
            signs[i] = signs[j].also { signs[j] = signs[i] }
        }
        camp.signs = signs
        camp.wanted = Exercise.entries[world.random.nextInt(Exercise.entries.size)]
        camp.given = false
        for ((post, varbit) in POST_VARBITS.withIndex()) setVarbit(player, varbit, signs[post].sign)
    }

    /** His order for [Camp.wanted], its picture, and Okay or leave. */
    private suspend fun Dialogue.order(world: World, sergeant: Npc, away: EventAreas.Away, camp: Camp, wrong: Boolean) = with(DrillDemonLines) {
        val (said, box) = when (camp.wanted) {
            Exercise.JOG -> (if (wrong) WRONG_JOG else ORDER_JOG) to BOX_JOG
            Exercise.SITUPS -> (if (wrong) WRONG_SITUPS else ORDER_SITUPS) to BOX_SITUPS
            Exercise.PUSHUPS -> (if (wrong) WRONG_PUSHUPS else ORDER_PUSHUPS) to BOX_PUSHUPS
            Exercise.STARJUMPS -> (if (wrong) WRONG_STARJUMPS else ORDER_STARJUMPS) to BOX_STARJUMPS
        }
        camp.given = false
        chatNpc(sergeant, ChatAnim.ANGRY, text(said))
        objbox(camp.wanted.picture, box)
        if (choice(OKAY, LEAVE) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, OKAY)
            camp.given = true
            return
        }
        chatPlayer(ChatAnim.NEUTRAL, LEAVE)
        chatNpc(sergeant, ChatAnim.ANGRY, text(PATHETIC))
        leave(world, player, away)
    }

    private fun campOf(world: World, player: Player): Pair<EventAreas.Away, Camp>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val camp = away.state as? Camp ?: return null
        return away to camp
    }

    /** Talk-to on him in the camp: the same order again. */
    suspend fun ScriptContext.talkInCamp(world: World, npc: Npc) {
        val (away, camp) = campOf(world, player) ?: return
        world.dialogues.start(this, npc) { order(world, npc, away, camp, wrong = false) }
    }

    /** Use on mat [post]: the exercise on its sign, then his verdict. */
    suspend fun ScriptContext.exercise(world: World, post: Int) {
        val (away, camp) = campOf(world, player) ?: return
        val sergeant = camp.sergeant ?: return
        if (!camp.given) {
            world.dialogues.start(this, sergeant) {
                chatNpc(sergeant, ChatAnim.ANGRY, text(DrillDemonLines.NOT_YET))
                order(world, sergeant, away, camp, wrong = false)
            }
            return
        }
        val done = camp.signs[post - 1]
        player.anim(done.anim)
        delay(EXERCISE_TICKS)
        if (campOf(world, player)?.second !== camp) return
        val right = done == camp.wanted
        camp.right = if (right) camp.right + 1 else 0
        if (camp.right >= ROUNDS) return finish(world, sergeant, away)
        shuffle(world, player, camp)
        world.dialogues.start(this, sergeant) { order(world, sergeant, away, camp, wrong = !right) }
    }

    private suspend fun ScriptContext.finish(world: World, sergeant: Npc, away: EventAreas.Away) {
        val piece = PIECES.firstOrNull { !owns(player, it) }
        leave(world, player, away)
        // Given once back (what doesn't fit goes on the ground there, not in the camp that is about to end) and before
        // his line, so shutting it neither loses nor repeats the reward. Guessed: his line comes after the teleport.
        if (piece != null) {
            world.randomEvents.reward(player, piece)
        } else {
            world.randomEvents.reward(player, XpLamp.LAMP)
            setVarbit(player, EMOTES_VARBIT, 1)
        }
        world.dialogues.start(this, sergeant) { chatNpc(sergeant, ChatAnim.HAPPY, text(DrillDemonLines.DONE)) }
    }

    private fun leave(world: World, player: Player, away: EventAreas.Away) {
        for (varbit in POST_VARBITS) setVarbit(player, varbit, 0)
        world.randomEvents.areas.bringBack(player, away)
    }

    private fun owns(player: Player, id: Int) = player.inventory.count(id) > 0 || player.equipment.count(id) > 0 || player.bank.count(id) > 0

    /** Sets varbit [id], or does nothing when the cache has no such varbit (tests). */
    private fun setVarbit(player: Player, id: Int, value: Int) {
        if (player.vars.types.varbit(id) != null) player.vars.setVarbit(id, value)
    }

    /** The camp's locs whose handlers work in its copies. */
    val INSTANCE_LOCS: Set<Int> get() = MATS.keys

    fun register(world: World) {
        world.handlers.onNpc(CAMP_SERGEANT, RandomEvents.TALK_TO) { op -> talkInCamp(world, op.npc ?: return@onNpc) }
        for ((mat, post) in MATS) world.handlers.onLoc(mat, USE) { exercise(world, post) }
    }
}
