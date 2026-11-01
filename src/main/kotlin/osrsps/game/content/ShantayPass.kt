package osrsps.game.content

import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.shop.ShopScreen

/** Shantay's lines; the words are in the dialogue file, from the Wiki's Transcript:Shantay, read 2026-10-30. */
object ShantayLines : Lines("shantay", "Transcript:Shantay") {
    val HELLO = line("hello", "the first talk: his first line")
    val SEE_YOU = line("see_you", "the first talk: his second line, about the desert")
    val HELLO_AGAIN = line("hello_again", "every later talk: his greeting")
    val WHAT_IS = line("what_is", "the main menu, option 1, which the player then says")
    val SELL = line("sell", "the main menu, option 2 (and in later menus), which the player then says")
    val MUST_GO = line("must_go", "the main menu, option 3 (and in later menus), which the player then says")
    val BUY_PASS = line("buy_pass", "the main menu, option 4 (and Buy-pass), which the player then says")
    val THIS_IS = line("this_is", "what is this place: his first answer")
    val MY_MEN = line("my_men", "what is this place: his second, asking what the player is")
    val OUTLAW = line("outlaw", "that menu, option 1, which the player then says")
    val NOVICE = line("novice", "that menu, option 2, which the player then says")
    val ADVENTURER = line("adventurer", "that menu, option 3, which the player then says")
    val VERY_FUNNY = line("very_funny", "outlaw: his first line")
    val ARREST = line("arrest", "outlaw: he calls the guards")
    val ARRESTED = line("arrested", "outlaw: the box as the player is put in the cell")
    val PAY_NOW = line("pay_now", "outlaw: he offers release for a fine of five gold")
    val YES_OKAY = line("yes_okay", "the fine menu, option 1, which the player then says")
    val NOT_HAVING = line("not_having", "the fine menu, option 2, which the player then says")
    val SENSES = line("senses", "paying: his first line")
    val HANDS_OVER = line("hands_over", "paying: the box as the player hands over five coins")
    val KEEP_PEACE = line("keep_peace", "paying: his thanks")
    val UNLOCKS = line("unlocks", "paying: the box as he unlocks the cell")
    val NO_CASH = line("no_cash", "paying with no five coins held: his line")
    val YOUR_BANK = line("your_bank", "no coins held but five in the bank: his line")
    val WHICH = line("which", "after that: his question")
    val PAY_FINE = line("pay_fine", "the bank menu, option 1, which the player then says")
    val NEED_BANK = line("need_bank", "his answer, before the bank opens")
    val VERY_WELL = line("very_well", "the bank menu, option 2: his line before the prison")
    val TRANSPORTED = line("transported", "no coins anywhere, or refusing twice: his first line")
    val LESSON = line("lesson", "his second line")
    val PRISON = line("prison", "the box as the player arrives in the prison")
    val CHOICE = line("choice", "refusing: he gives the choice")
    val WILL_YOU = line("will_you", "refusing: his question")
    val DO_YOUR_WORST = line("do_your_worst", "the refuse menu, option 2")
    val NOVICE_1 = line("novice_1", "novice: his first line")
    val NOVICE_2 = line("novice_2", "novice: his second")
    val NOVICE_3 = line("novice_3", "novice: his third")
    val ADVENTURER_1 = line("adventurer_1", "adventurer: his first line")
    val ADVENTURER_2 = line("adventurer_2", "adventurer: his second")
    val ADVENTURER_3 = line("adventurer_3", "adventurer: his third")
    val WHY = line("why", "after either: option 3, which the player then says")
    val GESTURES = line("gestures", "why: the box as he opens his arms")
    val INSULT = line("insult", "why: his first answer")
    val REPAIR = line("repair", "why: his second")
    val ABSOLUTELY = line("absolutely", "his answer before the shop opens")
    val SO_LONG = line("so_long", "his goodbye")
    val NO_COINS = line("no_coins", "a pass with fewer than five coins: his line")
    val NO_ROOM = line("no_room", "a pass with a full backpack: his line")
    val BOUGHT = line("bought", "the item box when the player buys a pass")
    val LOCKED = line("locked", "the cell door while jailed: the box saying it's locked")
    val WALKS_OVER = line("walks_over", "the cell door while jailed: the box as he walks over")
    val JAIL_PAY = line("jail_pay", "the cell door while jailed: his offer")
}

/** The Shantay Guards' lines; the words are in the dialogue file, from the Wiki's Transcript:Shantay Guard, read 2026-10-30. */
object ShantayGuardLines : Lines("shantay_guard", "Transcript:Shantay Guard") {
    val GO_TALK = line("go_talk", "the guard you can fight: his one line")
    val HELLO = line("hello", "the gate guard: his greeting")
    val DESERT = line("desert", "the gate guard's menu, option 1, which the player then says")
    val NOTHING = line("nothing", "the gate guard's menu, option 2, which the player then says")
    val NICE_DAY = line("nice_day", "his goodbye")
    val NEED_PASS = line("need_pass", "no Shantay pass: he sends the player to Shantay")
}

/**
 * The gate's words that aren't the guards': Look-at (2004Scape's three boxes, guessed for live) and what Go-through says
 * with a pass while the desert beyond isn't built (ours, not Jagex's).
 */
object ShantayGateLines : Lines("shantay_gate", "2004Scape shantay_pass.rs2") {
    val LOOK_1 = line("look_1", "Look-at: the first box, the gate and its poster")
    val LOOK_2 = line("look_2", "Look-at: the poster's warning")
    val LOOK_3 = line("look_3", "Look-at: people pass anyway")
    val DESERT_CLOSED = line("desert_closed", "ours: the desert beyond isn't open yet (until the Desert piece)")
}

/** Irena's line after Tourist Trap; the words are in the dialogue file, from the Wiki's Transcript:Irena, read 2026-10-30. */
object IrenaLines : Lines("irena", "Transcript:Irena") {
    val THANKS = line("thanks", "her one line after Tourist Trap")
}

/** The Port Sarim prison's cell door; words to come from a recording (the Wiki gives none). */
object PortSarimJailLines : Lines("port_sarim_jail", "Door (Port Sarim jail)") {
    val LOCKED = line("locked", "Open on the locked cell door")
    val PICK = line("pick", "Pick-lock: the attempt")
    val PICK_FAIL = line("pick_fail", "Pick-lock: a failed attempt")
    val PICK_DONE = line("pick_done", "Pick-lock: the lock picked, the player steps out")
}

/**
 * The Shantay Pass, north side (OSRS Wiki, Shantay Pass, Shantay, Shantay Guard and Shantay Pass Shop, read 2026-10-30;
 * the flows are 2004Scape's shantay*.rs2 where the Wiki is silent). As after Tourist Trap.
 *
 * - Shantay (4642): his talk, the outlaw's arrest into the cell west of him and the fine of five coins (from the
 *   backpack, else the bank, else the Port Sarim prison), Trade (the Shantay Pass Shop) and Buy-pass (5 coins).
 * - The cell door (`shantay_prisondoor` 2692): locked while the player is jailed (Shantay offers release), a plain metal
 *   door (`poshdooropen` 1541) otherwise.
 * - The Port Sarim prison: the player lands in a cell (2004Scape's tile); Pick-lock on the cell door
 *   (`port_sarim_jaildoor` 9565) lets them out, 4 Thieving XP, 0.5 on a fail. The Wiki gives the chance as 0% to 82%
 *   at level 1 and 39% to 127% at 99, "rising over 4 attempts": read as rising evenly from the first attempt to the
 *   fourth, and with level between (guessed).
 * - The gate guard (`shantay_guard_still` 4648) and the gate (4031): no pass, the guard sends the player to Shantay;
 *   with one, the desert isn't built yet, so the gate says so (ours) and keeps the pass. From the south side the
 *   gate always lets the player north, as 2004Scape has it.
 * - The guard you can fight (4643): one line; combat in npc-combat.json.
 * - The bank chest (`thbankchest` 2693): Use opens the bank; Collect does nothing yet, as booths.
 * - Irena (4641, as after Tourist Trap): one line.
 *
 * Left out: the Tourist Trap lines, the treasure trail steps, the jail's "you should be in jail" line (no way out of
 * the cell but paying), the Kalphite cave (Slayer), the cacti and the desert itself (the Desert piece), the Free-to-play
 * refusals. Guessed: the cell's inside tile, expressions, and that the narrated lines are boxes.
 */
object ShantayPass {
    const val SHANTAY = 4642
    const val GUARD = 4643
    const val GATE_GUARD = 4648
    const val IRENA = 4641
    const val TALK_TO = 1
    const val BUY_PASS = 4
    const val PASS_OP = 3

    const val GATE = 4031
    const val GO_THROUGH = 1
    const val LOOK_AT = 2

    const val CELL_DOOR = 2692
    /** The cell: x 3295 to 3297, z 3123 to 3125, its door on the east edge of 3297, 3124. */
    val CELL_X = 3295..3297
    val CELL_Z = 3123..3125
    const val CELL_INSIDE_X = 3296
    const val CELL_INSIDE_Z = 3124

    const val BANK_CHEST = 2693
    const val USE = 1

    const val PASS = 1854
    const val PRICE = 5
    const val FINE = 5

    /** Port Sarim's prison: 2004Scape's arrival tile, and the cell door's tile; its door opens south. */
    const val PRISON_X = 3017
    const val PRISON_Z = 3182
    const val PRISON_DOOR = 9565
    const val PICK_LOCK = 2

    /** [Player.charges] names: talked to Shantay before, jailed in his cell, Pick-lock attempts in a row. */
    const val TALKED = "shantay_talked"
    const val JAILED = "shantay_jailed"
    const val PICK_ATTEMPTS = "port_sarim_pick_attempts"

    fun inCell(player: Player) = player.x in CELL_X && player.z in CELL_Z && player.level == 0

    /** Shantay's talk; [actions] act on the world. */
    internal suspend fun Dialogue.shantay(actions: ShantayActions) = with(ShantayLines) {
        if (player.charges[TALKED] == 0) {
            player.charges[TALKED] = 1
            chatNpc(ChatAnim.NEUTRAL, HELLO)
            chatNpc(ChatAnim.NEUTRAL, SEE_YOU)
        } else {
            chatNpc(ChatAnim.NEUTRAL, HELLO_AGAIN)
        }
        when (choice(WHAT_IS, SELL, MUST_GO, BUY_PASS)) {
            1 -> whatIsThisPlace(actions)
            2 -> sell(actions)
            3 -> mustGo()
            else -> buyPass(actions)
        }
    }

    private suspend fun Dialogue.sell(actions: ShantayActions) = with(ShantayLines) {
        chatPlayer(ChatAnim.NEUTRAL, SELL)
        chatNpc(ChatAnim.HAPPY, ABSOLUTELY)
        actions.openShop()
    }

    private suspend fun Dialogue.mustGo() = with(ShantayLines) {
        chatPlayer(ChatAnim.NEUTRAL, MUST_GO)
        chatNpc(ChatAnim.NEUTRAL, SO_LONG)
    }

    internal suspend fun Dialogue.buyPass(actions: ShantayActions) = with(ShantayLines) {
        chatPlayer(ChatAnim.NEUTRAL, BUY_PASS)
        when {
            player.inventory.count(ShopScreen.COINS) < PRICE -> chatNpc(ChatAnim.NEUTRAL, NO_COINS)
            player.inventory.freeSlots == 0 && player.inventory.count(ShopScreen.COINS) != PRICE.toLong() &&
                player.inventory.count(PASS) == 0L -> chatNpc(ChatAnim.NEUTRAL, NO_ROOM)
            else -> {
                AlKharidTraders.pay(player, PRICE)
                actions.give(PASS)
                objbox(PASS, BOUGHT)
            }
        }
    }

    private suspend fun Dialogue.whatIsThisPlace(actions: ShantayActions) = with(ShantayLines) {
        chatPlayer(ChatAnim.QUIZ, WHAT_IS)
        chatNpc(ChatAnim.NEUTRAL, THIS_IS)
        chatNpc(ChatAnim.QUIZ, MY_MEN)
        when (choice(OUTLAW, NOVICE, ADVENTURER)) {
            1 -> {
                chatPlayer(ChatAnim.ANGRY, OUTLAW)
                chatNpc(ChatAnim.LAUGH, VERY_FUNNY)
                chatNpc(ChatAnim.ANGRY, ARREST)
                actions.jail()
                mesbox(ARRESTED)
                chatNpc(ChatAnim.NEUTRAL, PAY_NOW)
                fine(actions)
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, NOVICE)
                chatNpc(ChatAnim.NEUTRAL, NOVICE_1)
                chatNpc(ChatAnim.NEUTRAL, NOVICE_2)
                chatNpc(ChatAnim.NEUTRAL, NOVICE_3)
                afterAdvice(actions)
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, ADVENTURER)
                chatNpc(ChatAnim.HAPPY, ADVENTURER_1)
                chatNpc(ChatAnim.NEUTRAL, ADVENTURER_2)
                chatNpc(ChatAnim.NEUTRAL, ADVENTURER_3)
                afterAdvice(actions)
            }
        }
    }

    private suspend fun Dialogue.afterAdvice(actions: ShantayActions) = with(ShantayLines) {
        when (choice(SELL, MUST_GO, WHY)) {
            1 -> sell(actions)
            2 -> mustGo()
            else -> {
                chatPlayer(ChatAnim.QUIZ, WHY)
                mesbox(GESTURES)
                chatNpc(ChatAnim.SAD, INSULT)
                chatNpc(ChatAnim.NEUTRAL, REPAIR)
                if (choice(SELL, MUST_GO) == 1) sell(actions) else mustGo()
            }
        }
    }

    /** Who says Shantay's lines: Shantay himself, or Shantay come over to the cell door. */
    internal fun interface Say {
        suspend fun Dialogue.say(anim: ChatAnim, line: Line)
    }

    private val TALKING = Say { anim, line -> chatNpc(anim, line) }

    private suspend fun Dialogue.say(by: Say, anim: ChatAnim, line: Line) = with(by) { say(anim, line) }

    /** The fine menu: pay five coins, or refuse. */
    internal suspend fun Dialogue.fine(actions: ShantayActions, by: Say = TALKING) = with(ShantayLines) {
        if (choice(YES_OKAY, NOT_HAVING) == 1) pay(actions, by) else refuse(actions, by)
    }

    private suspend fun Dialogue.pay(actions: ShantayActions, by: Say) = with(ShantayLines) {
        chatPlayer(ChatAnim.NEUTRAL, YES_OKAY)
        say(by, ChatAnim.HAPPY, SENSES)
        if (AlKharidTraders.pay(player, FINE)) {
            actions.release()
            mesbox(HANDS_OVER)
            say(by, ChatAnim.HAPPY, KEEP_PEACE)
            mesbox(UNLOCKS)
            return@with
        }
        say(by, ChatAnim.NEUTRAL, NO_CASH)
        if (player.bank.count(ShopScreen.COINS) < FINE) return@with toPrison(actions, by)
        say(by, ChatAnim.QUIZ, YOUR_BANK)
        say(by, ChatAnim.QUIZ, WHICH)
        if (choice(PAY_FINE, NOT_HAVING) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, PAY_FINE)
            say(by, ChatAnim.NEUTRAL, NEED_BANK)
            actions.openBank()
        } else {
            chatPlayer(ChatAnim.ANGRY, NOT_HAVING)
            say(by, ChatAnim.NEUTRAL, VERY_WELL)
            actions.toPrison()
            mesbox(PRISON)
        }
    }

    private suspend fun Dialogue.refuse(actions: ShantayActions, by: Say) = with(ShantayLines) {
        chatPlayer(ChatAnim.ANGRY, NOT_HAVING)
        say(by, ChatAnim.NEUTRAL, CHOICE)
        say(by, ChatAnim.QUIZ, WILL_YOU)
        if (choice(YES_OKAY, DO_YOUR_WORST) == 1) return@with pay(actions, by)
        chatPlayer(ChatAnim.ANGRY, DO_YOUR_WORST)
        toPrison(actions, by)
    }

    private suspend fun Dialogue.toPrison(actions: ShantayActions, by: Say) = with(ShantayLines) {
        say(by, ChatAnim.ANGRY, TRANSPORTED)
        say(by, ChatAnim.NEUTRAL, LESSON)
        actions.toPrison()
        mesbox(PRISON)
    }

    /** The cell door while jailed: Shantay comes over and offers release, then the fine menu with him speaking. */
    internal suspend fun Dialogue.cellDoor(shantay: NpcType, actions: ShantayActions) = with(ShantayLines) {
        val by = Say { anim, line -> chatNpc(shantay, shantay.name, anim, line) }
        mesbox(LOCKED)
        mesbox(WALKS_OVER)
        say(by, ChatAnim.NEUTRAL, JAIL_PAY)
        fine(actions, by)
    }

    internal suspend fun Dialogue.guard() = chatNpc(ChatAnim.ANGRY, ShantayGuardLines.GO_TALK)

    /** The gate guard's talk; [gate] runs the gate as Go-through does when the player asks to go into the desert. */
    internal suspend fun Dialogue.gateGuard(gate: suspend Dialogue.() -> Unit) = with(ShantayGuardLines) {
        chatNpc(ChatAnim.NEUTRAL, HELLO)
        if (choice(DESERT, NOTHING) == 2) {
            chatPlayer(ChatAnim.NEUTRAL, NOTHING)
            chatNpc(ChatAnim.NEUTRAL, NICE_DAY)
            return@with
        }
        chatPlayer(ChatAnim.NEUTRAL, DESERT)
        gate()
    }

    /** Going south through the gate: the guard wants a pass; with one, the desert isn't open yet. */
    internal suspend fun Dialogue.gateSouth(guard: NpcType) {
        if (player.inventory.count(PASS) == 0L) {
            chatNpc(guard, guard.name, ChatAnim.NEUTRAL, ShantayGuardLines.NEED_PASS)
            return
        }
        mesbox(ShantayGateLines.DESERT_CLOSED)
    }

    internal suspend fun Dialogue.lookAt() = with(ShantayGateLines) {
        mesbox(LOOK_1)
        mesbox(LOOK_2)
        mesbox(LOOK_3)
    }

    /**
     * One Pick-lock try at Thieving [level], the [attempt]th in a row (from 1): its chance in hundredths. From 0 to 82
     * at level 1 and 39 to 127 at 99 over attempts 1 to 4, between by level (guessed reading of the Wiki).
     */
    fun pickChance(level: Int, attempt: Int): Int {
        val at = (minOf(attempt, 4) - 1) / 3.0
        val low = 0 + (39 - 0) * (level - 1) / 98.0
        val high = 82 + (127 - 82) * (level - 1) / 98.0
        return (low + (high - low) * at).toInt()
    }
}

/** What Shantay's talk does to the world. */
interface ShantayActions {
    fun openShop()
    fun give(obj: Int)
    fun jail()
    fun release()
    fun toPrison()
    fun openBank()
}

private class WorldShantayActions(private val world: World, private val context: ScriptContext) : ShantayActions {
    private val player get() = context.player

    override fun openShop() {
        world.shopTypes.ofNpc(ShantayPass.SHANTAY)?.let { world.shopScreen.open(player, it) }
    }

    override fun give(obj: Int) {
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) }
    }

    override fun jail() {
        player.charges[ShantayPass.JAILED] = 1
        world.teleport(player, ShantayPass.CELL_INSIDE_X, ShantayPass.CELL_INSIDE_Z, 0)
    }

    override fun release() {
        player.charges[ShantayPass.JAILED] = 0
    }

    override fun toPrison() {
        player.charges[ShantayPass.JAILED] = 0
        world.teleport(player, ShantayPass.PRISON_X, ShantayPass.PRISON_Z, 0)
    }

    override fun openBank() = world.bankScreen.open(player)
}

fun registerShantayPass(world: World) {
    with(ShantayPass) {
        world.handlers.onNpc(SHANTAY, TALK_TO) { op ->
            val actions = WorldShantayActions(world, this)
            world.dialogues.start(this, op.npc) { shantay(actions) }
        }
        world.handlers.onNpc(SHANTAY, BUY_PASS) { op ->
            val actions = WorldShantayActions(world, this)
            world.dialogues.start(this, op.npc) { buyPass(actions) }
        }
        world.handlers.onNpc(GUARD, TALK_TO) { op -> world.dialogues.start(this, op.npc) { guard() } }
        world.handlers.onNpc(IRENA, TALK_TO) { op -> world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.HAPPY, IrenaLines.THANKS) } }
        world.handlers.onNpc(GATE_GUARD, TALK_TO) { op ->
            val guard = world.npcTypes[GATE_GUARD] ?: return@onNpc
            world.dialogues.start(this, op.npc) { gateGuard { gateSouth(guard) } }
        }
        world.handlers.onNpc(GATE_GUARD, PASS_OP) { op ->
            val guard = world.npcTypes[GATE_GUARD] ?: return@onNpc
            world.dialogues.start(this, op.npc) { gateSouth(guard) }
        }
        world.handlers.onLoc(GATE, GO_THROUGH) { op ->
            arriveDelay()
            // From the desert side the gate always lets the player north (2004Scape).
            if (player.z <= op.loc!!.z) {
                world.teleport(player, player.x, player.z + 3, player.level)
                return@onLoc
            }
            val guard = world.npcTypes[GATE_GUARD] ?: return@onLoc
            world.dialogues.start(this) { gateSouth(guard) }
        }
        world.handlers.onLoc(GATE, LOOK_AT) { world.dialogues.start(this) { lookAt() } }
        world.handlers.onLoc(BANK_CHEST, USE) { world.bankScreen.open(player) }
        world.handlers.onLoc(CELL_DOOR, 1) { op ->
            if (player.charges[JAILED] != 0 && inCell(player)) {
                val shantay = world.npcTypes[SHANTAY] ?: return@onLoc
                val actions = WorldShantayActions(world, this)
                world.dialogues.start(this) { cellDoor(shantay, actions) }
                return@onLoc
            }
            // Guess (the Wiki doesn't say how a released player leaves): the door never opens for everyone, since an
            // open door would let a later prisoner walk out; Open steps the player through it and it stays shut.
            val door = op.loc!!
            if (player.x <= door.x) world.teleport(player, door.x + 1, door.z, door.level)
            else world.teleport(player, door.x, door.z, door.level)
        }
        world.handlers.onLoc(PRISON_DOOR, 1) { world.message(player, PortSarimJailLines.LOCKED) }
        world.handlers.onLoc(PRISON_DOOR, PICK_LOCK) { op -> pickLock(world, op.loc!!.x, op.loc.z) }
    }
}

/** One Pick-lock try on a Port Sarim cell door at [doorX], [doorZ] (its south edge): out on success. */
private fun ScriptContext.pickLock(world: World, doorX: Int, doorZ: Int) {
    with(ShantayPass) {
        // Only from inside the cell, north of the door.
        if (player.z < doorZ) return world.message(player, PortSarimJailLines.LOCKED)
        world.message(player, PortSarimJailLines.PICK)
        val attempt = player.charges[PICK_ATTEMPTS] + 1
        val chance = pickChance(player.skills.baseLevel(Skill.THIEVING), attempt)
        if (world.random.nextInt(100) < chance) {
            player.charges[PICK_ATTEMPTS] = 0
            player.skills.addTenths(Skill.THIEVING, 40)
            world.message(player, PortSarimJailLines.PICK_DONE)
            world.teleport(player, doorX, doorZ - 1, player.level)
        } else {
            player.charges[PICK_ATTEMPTS] = attempt
            player.skills.addTenths(Skill.THIEVING, 5)
            world.message(player, PortSarimJailLines.PICK_FAIL)
        }
    }
}
