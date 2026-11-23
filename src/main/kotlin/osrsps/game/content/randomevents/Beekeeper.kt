package osrsps.game.content.randomevents

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.XpLamp
import osrsps.game.content.message
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** The Bee keeper's lines; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object BeekeeperLines : Lines("beekeeper", "Transcript:Bee keeper") {
    val HELLO = line("hello", "Overhead dialogue: dialogue 1", "name")
    val PLEASE_HELP = line("please_help", "Overhead dialogue: dialogue 2", "name")
    val NEED_HELP = line("need_help", "Overhead dialogue: dialogue 3", "name")
    val BORROW = line("borrow", "Overhead dialogue: dialogue 4", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val HELP = line("help", "Standard dialogue: his question", "name")
    val OOH = line("ooh", "Standard dialogue: the first option, and the player's words")
    val BUZZ_OFF = line("buzz_off", "Standard dialogue: the second option, and the player's words")
    val DRAG_AWAY = line("drag_away", "Continuation after being teleported: his first line", "name")
    val WHAT_EXPECT = line("what_expect", "Continuation after being teleported: the player's question")
    val JUMBLED = line("jumbled", "Continuation after being teleported: his answer (also after the reminder)")
    val TAKE_A_LOOK = line("take_a_look", "Continuation after being teleported: the player's last line")
    val READY = line("ready", "After closing the interface and talking with him again")
    val REMIND = line("remind", "Talking again: the first option, and the player's words")
    val GET_ON = line("get_on", "Talking again: the second option, and the player's words")
    val NOT_RIGHT = line("not_right", "After building the beehive wrong: his first line")
    val TRIES_5 = line("tries_5", "After building the beehive wrong first time: his second line")
    val TRIES_4 = line("tries_4", "After building the beehive wrong twice: his second line")
    val TRIES_3 = line("tries_3", "After building the beehive wrong three times: his second line")
    val TRIES_2 = line("tries_2", "After building the beehive wrong four times: his second line")
    val TRIES_1 = line("tries_1", "After building the beehive wrong five times: his second line")
    val FED_UP = line("fed_up", "After building the beehive wrong six times: his line")
    val HELP_YOU = line("help_you", "After building the beehive wrong six times: his overhead")
    val TELEPORTED_AWAY = line("teleported_away", "After building the beehive wrong six times: the message")
    val PERFECT = line("perfect", "After building the beehive correctly: his first line")
    val OFFER = line("offer", "After building the beehive correctly: his second line")
    val NOT_THE_PERSON = line("not_the_person", "Talking to Bee keeper looking for another player")
}

/**
 * The Beekeeper (OSRS Wiki, Beekeeper (random event) and Bee keeper pages and his transcript, read 2026-11-19). The Bee
 * keeper comes as NPC 6747 (Talk-to, Dismiss) and asks for help with a hive; yes takes the player to his field (map
 * square 30,78, [EventAreas]), where he (3235) opens the beehive window (420). Its four spinning parts start in
 * `start_1` to `_4` and must be dragged to `dest_1` to `_4` (Lid, Body, Entrance, Legs), then Confirm. The parts are
 * varbits beekeeper_start_1 to _4 and beekeeper_dest_1 to _4 on varp macro_bee_pos_1 (1 lid, 2 body, 3 entrance, 4
 * legs, 0 empty); the client swaps them as it drags (macro_beekeeper_rearrange) and sends the drag, which the server
 * mirrors. Six tries: right, the player goes back with a lamp and a beekeeper's outfit piece not yet had, or with the
 * whole outfit noted flax (1 to 27) beside the lamp; six wrong, the bees chase them home with nothing. Talking to him
 * reopens the window.
 *
 * Guessed: where the player lands and he stands; the parts' starting order (shuffled at random); which missing piece
 * comes (one at random); each flax amount within that range equally likely; how long his last overhead shows before the trip home ([SAY_TICKS]); the drag events the
 * window's parts need (drag depth 1 and drag target, as the bank's slots). Left out: the bee swarms and the player's
 * overhead cries on the sixth wrong try (players have no overhead text here yet), the free-to-play coins, and leaving
 * early by talking (the Wiki says it can be done, but the transcript's talk has no option for it).
 */
object Beekeeper : RandomEvent("beekeeper", 6747) {
    const val FIELD_KEEPER = 3235
    const val INTERFACE = 420
    val STARTS = listOf(10, 11, 12, 13)
    val DESTS = listOf(15, 17, 19, 21)
    const val CONFIRM = 22
    const val TRIES = 6
    const val SAY_TICKS = 1

    /** beekeeper_start_1 to _4, then beekeeper_dest_1 to _4. */
    val START_VARBITS = listOf(10601, 10602, 10603, 10604)
    val DEST_VARBITS = listOf(10605, 10606, 10607, 10608)

    const val HAT = 25129
    const val TOP = 25131
    const val LEGS = 25133
    const val GLOVES = 25135
    const val BOOTS = 25137
    val OUTFIT = listOf(HAT, TOP, LEGS, GLOVES, BOOTS)
    const val NOTED_FLAX = 1780
    const val FLAX_MAX = 27

    /** The zones copied: the field of map square 30,78. */
    const val FROM_X = 1920
    const val FROM_Z = 5032
    const val WIDTH_ZONES = 3
    const val HEIGHT_ZONES = 3

    val ARRIVE = Tile(1925, 5045)
    val KEEPER_AT = Tile(1926, 5045)

    private const val DRAG_EVENTS = BankScreen.EVENTS_DRAG_DEPTH1 or BankScreen.EVENTS_DRAG_TARGET

    /** A stay in the field: the parts in the window's eight places (starts then dests), and the tries used. */
    class Field(val keeper: Npc?) {
        val parts = IntArray(8)
        var wrong = 0
    }

    override val teleports = true
    override val nags = with(BeekeeperLines) { listOf(HELLO, PLEASE_HELP, NEED_HELP, BORROW) }
    override val leaving = BeekeeperLines.MAYBE_LATER
    override val notYours = BeekeeperLines.NOT_THE_PERSON

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(BeekeeperLines) {
        chatNpc(ChatAnim.NEUTRAL, HELP, "name" to player.username)
        if (choice(OOH, BUZZ_OFF) != 1) {
            chatPlayer(ChatAnim.ANGRY, BUZZ_OFF)
            return true
        }
        chatPlayer(ChatAnim.HAPPY, OOH)
        val away = events.areas.takeAway(player, this@Beekeeper, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, ARRIVE) ?: return true
        val field = Field(spawnKeeper(world, away))
        away.state = field
        // Jumbled now, so walking off his talk below still leaves a hive to build when he's spoken to.
        jumble(world, field)
        delay(2)
        val keeper = field.keeper ?: return true
        chatNpc(keeper, ChatAnim.NEUTRAL, text(DRAG_AWAY, "name" to player.username))
        chatPlayer(ChatAnim.QUIZ, WHAT_EXPECT)
        chatNpc(keeper, ChatAnim.NEUTRAL, text(JUMBLED))
        chatPlayer(ChatAnim.NEUTRAL, TAKE_A_LOOK)
        open(world, player, field)
        true
    }

    private fun spawnKeeper(world: World, away: EventAreas.Away): Npc? {
        val type = world.npcTypes[FIELD_KEEPER] ?: return null
        val tile = away.inside(KEEPER_AT.x, KEEPER_AT.z)
        return world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, source = "beekeeper field for ${away.owner}", respawns = false))
    }

    /** The four parts shuffled into the start places, the slots empty. */
    private fun jumble(world: World, field: Field) {
        val parts = mutableListOf(1, 2, 3, 4)
        for (i in parts.size - 1 downTo 1) {
            val j = world.random.nextInt(i + 1)
            parts[i] = parts[j].also { parts[j] = parts[i] }
        }
        for (i in 0 until 4) {
            field.parts[i] = parts[i]
            field.parts[4 + i] = 0
        }
    }

    private fun open(world: World, player: Player, field: Field) {
        for ((i, varbit) in (START_VARBITS + DEST_VARBITS).withIndex()) setVarbit(player, varbit, field.parts[i])
        world.interfaces.sendVarpNow(player, MACRO_BEE_POS)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        for (component in STARTS + DESTS) player.session.queue(IfSetEventsV2(INTERFACE, component, -1, -1, DRAG_EVENTS, 0))
        player.session.queue(IfSetEventsV2(INTERFACE, CONFIRM, -1, -1, 0, BankScreen.opMask(1..1)))
    }

    private fun fieldOf(world: World, player: Player): Pair<EventAreas.Away, Field>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val field = away.state as? Field ?: return null
        return away to field
    }

    /** A part dragged from one place to another: the two swap, as the client's own script swaps them. */
    fun drag(world: World, player: Player, interfaceId: Int, from: Int, toInterfaceId: Int, to: Int): Boolean {
        if (interfaceId != INTERFACE || toInterfaceId != INTERFACE) return false
        val (_, field) = fieldOf(world, player) ?: return true
        if (!hiveOpen(player)) return true
        val places = STARTS + DESTS
        val a = places.indexOf(from)
        val b = places.indexOf(to)
        if (a < 0 || b < 0 || a == b) return true
        field.parts[a] = field.parts[b].also { field.parts[b] = field.parts[a] }
        setVarbit(player, (START_VARBITS + DEST_VARBITS)[a], field.parts[a])
        setVarbit(player, (START_VARBITS + DEST_VARBITS)[b], field.parts[b])
        return true
    }

    /** Confirm: right, the reward; wrong, a try gone; the sixth wrong, home with nothing. */
    suspend fun ScriptContext.confirm(world: World) {
        val (away, field) = fieldOf(world, player) ?: return
        val keeper = field.keeper ?: return
        if (!hiveOpen(player)) return
        world.interfaces.closeModals(player)
        val right = (0 until 4).all { field.parts[4 + it] == it + 1 }
        world.dialogues.start(this, keeper) {
            with(BeekeeperLines) {
                // Back first, then the reward (what doesn't fit goes on the ground there, not in the field that is
                // about to end), then his lines, so shutting them neither loses nor repeats it. Guessed: his lines come
                // after the teleport, in both endings.
                if (right) {
                    world.randomEvents.areas.bringBack(player, away)
                    reward(world, player)
                    chatNpc(ChatAnim.HAPPY, PERFECT)
                    chatNpc(ChatAnim.NEUTRAL, OFFER)
                    return@start
                }
                field.wrong++
                if (field.wrong >= TRIES) {
                    keeper.effects.say(text(HELP_YOU))
                    world.message(player, TELEPORTED_AWAY)
                    // A tick in the field first, so his overhead reaches the player while he's still in view.
                    delay(SAY_TICKS)
                    if (fieldOf(world, player)?.second !== field) return@start
                    world.randomEvents.areas.bringBack(player, away)
                    chatNpc(ChatAnim.SHOCKED, FED_UP)
                    return@start
                }
                chatNpc(ChatAnim.NEUTRAL, NOT_RIGHT)
                chatNpc(ChatAnim.NEUTRAL, listOf(TRIES_5, TRIES_4, TRIES_3, TRIES_2, TRIES_1)[field.wrong - 1])
                delay(1)
                open(world, player, field)
            }
        }
    }

    /** Only while the beehive window is open: a drag or Confirm sent without it is ignored. */
    private fun hiveOpen(player: Player) = INTERFACE in player.modals.values

    /** Talk-to on him in the field: the window again, with the reminder if asked. */
    suspend fun ScriptContext.talkInField(world: World, npc: Npc) {
        val (away, field) = fieldOf(world, player) ?: return
        // Out of tries already (the trip home was cut short by walking off): home now.
        if (field.wrong >= TRIES) return world.randomEvents.areas.bringBack(player, away)
        world.dialogues.start(this, npc) {
            with(BeekeeperLines) {
                chatNpc(ChatAnim.NEUTRAL, READY)
                if (choice(REMIND, GET_ON) == 1) {
                    chatPlayer(ChatAnim.QUIZ, REMIND)
                    chatNpc(ChatAnim.NEUTRAL, JUMBLED)
                } else {
                    chatPlayer(ChatAnim.NEUTRAL, GET_ON)
                }
                delay(1)
                open(world, player, field)
            }
        }
    }

    private fun reward(world: World, player: Player) {
        world.randomEvents.reward(player, XpLamp.LAMP)
        val missing = OUTFIT.filter { !owns(player, it) }
        if (missing.isNotEmpty()) world.randomEvents.reward(player, missing[world.random.nextInt(missing.size)])
        else world.randomEvents.reward(player, NOTED_FLAX, 1 + world.random.nextInt(FLAX_MAX))
    }

    private fun owns(player: Player, id: Int) = player.inventory.count(id) > 0 || player.equipment.count(id) > 0 || player.bank.count(id) > 0

    private fun setVarbit(player: Player, id: Int, value: Int) {
        if (player.vars.types.varbit(id) != null) player.vars.setVarbit(id, value)
    }

    /** macro_bee_pos_1. */
    const val MACRO_BEE_POS = 805

    fun register(world: World) {
        world.handlers.onNpc(FIELD_KEEPER, RandomEvents.TALK_TO) { op -> talkInField(world, op.npc ?: return@onNpc) }
        world.handlers.onButton(INTERFACE, CONFIRM) { confirm(world) }
    }
}
