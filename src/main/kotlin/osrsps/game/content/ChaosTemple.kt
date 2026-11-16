package osrsps.game.content

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** The Chaos Temple's altar and its Elder Chaos druid (the Wiki's Chaos Temple (church) and Transcript:Elder Chaos druid (NPC)). */
object ChaosTempleLines : Lines("chaos_temple", "Chaos Temple (church); Transcript:Elder Chaos druid (NPC)") {
    val SPARED = line("spared", "a bone offered on the altar and not used up (the Wiki's words)")
    val WELCOME = line("welcome", "the druid's greeting: the Dark Lord's temple and the altar")
    val EXCHANGE = line("exchange", "his next box: he exchanges banknotes for bones")
    val FEE = line("fee", "his next box: 50 coins a banknote")
    val YES = line("yes", "the option to have notes exchanged")
    val WHO = line("who", "the option asking who he is")
    val NO = line("no", "the option to refuse, which the player then says")
    val HAND_ME = line("hand_me", "his answer to Yes please: hand him the banknotes")
    val NOT_IMPORTANT = line("not_important", "his answer to Who are you?")
    val NOT_A_NOTE = line("not_a_note", "an unnoted item used on him")
    val NOT_SACRIFICE = line("not_sacrifice", "a noted item that can't be offered on the altar used on him")
    val NO_COINS = line("no_coins", "noted bones used on him with fewer than 50 coins")
    val TOO_FULL = line("too_full", "noted bones used on him with a full backpack")
}

/**
 * The Chaos Temple in level 38 Wilderness (Wilderness step W3.4c), as the Wiki's Chaos Temple (church) page has it:
 *
 * - **The chaos altar** (`chaosaltar` 411 at 2947,3820): bones used on it are offered for 3.5 times their burying XP,
 *   each with a 50% chance to be spared (its own message, the Wiki says) and offered
 *   again; offerings keep going, one every [OFFER_TICKS], while the player holds that kind of bone and doesn't move.
 *   The bone taken is the first of that kind from the top left of the backpack (the 22 February 2023 change). Demonic
 *   ashes don't work (the Wiki). Pray-at on any chaos altar recharges prayer points as other altars do.
 * - **The Elder Chaos druid** (`wild_chaos_uncerter` 7995) outside the south wall talks as the Wiki's transcript has
 *   him and unnotes bones that can be offered for 50 coins each.
 *
 * Inferred: the offering's animation (`human_bone_sacrifice` 3705, the gilded altar's), no message on an offering
 * that isn't spared (the Wiki quotes only the spared one), the XP rounded down to a tenth. Guessed: [OFFER_TICKS], the
 * offerings going on until the player moves (a level-up box stops them too), the druid exchanging as many as the
 * coins and free slots allow with no count menu, the chathead faces. Left out: the altar's graphic on an offering, the confirmation asked before burying
 * bones in the temple (its words aren't sourced), Zealot's robes, the wine of Zamorak (Telekinetic Grab isn't built).
 */
object ChaosTemple {
    const val ALTAR = 411
    const val ALTAR_X = 2947
    const val ALTAR_Z = 3820
    const val PRAY_AT = 1

    const val DRUID = 7995
    const val TALK_TO = 1
    const val FEE = 50
    const val COINS = 995

    /** human_bone_sacrifice (inferred from the gilded altar). */
    const val ANIM_OFFER = 3705

    /** Ticks between offerings (guessed). */
    const val OFFER_TICKS = 3

    /** 3.5 times the burying XP, in tenths of the multiplier. */
    const val XP_MULTIPLIER_TENTHS = 35

    const val QUEUE = "chaos_altar_offer"

    fun isTempleAltar(loc: ZoneLoc) = loc.level == 0 && loc.x == ALTAR_X && loc.z == ALTAR_Z

    /** Whether [x], [z], [level] is on or beside [altar], [width] by [length] tiles before its rotation. */
    fun besideAltar(altar: ZoneLoc, width: Int, length: Int, x: Int, z: Int, level: Int): Boolean {
        val (w, l) = if (altar.angle % 2 == 1) length to width else width to length
        return level == altar.level && x in altar.x - 1..altar.x + w && z in altar.z - 1..altar.z + l
    }

    /** The altar's XP for bones [id], in tenths, or null for anything that can't be offered. */
    fun offerXp(id: Int): Int? = Bones.XP_TENTHS[id]?.let { it * XP_MULTIPLIER_TENTHS / 10 }

    /**
     * How many banknotes the druid exchanges for a player holding [notes] of them, [coins] and [freeSlots] free slots:
     * no more than the coins pay for or the free slots hold (guessed: no count menu).
     */
    fun exchangeable(notes: Long, coins: Long, freeSlots: Int): Int = minOf(notes, coins / FEE, freeSlots.toLong()).toInt().coerceAtLeast(0)
}

fun registerChaosTemple(world: World) {
    with(ChaosTemple) {
        world.handlers.onLoc(ALTAR, PRAY_AT) { prayAt(world) }
        world.handlers.onObjOnLoc(ALTAR) { op ->
            val loc = op.loc!!
            if (!isTempleAltar(loc) || offerXp(op.obj) == null) return@onObjOnLoc world.message(player, AltarLines.WRATH)
            // A player already beside it offers in this tick's movement phase, after the script phase that first counts
            // a queued delay down; one who stepped there waits a tick and offers in the script phase (as fill's fromMovement).
            val fromMovement = player.lastMoveTick != tick
            arriveDelay()
            // Using bones again doesn't offer any sooner than the repeat would (round 37: re-clicking each tick tripled
            // the rate): an offering still [OFFER_TICKS] off from the last one waits for it. INFERRED from the Wiki's
            // steady rate; nothing found says live lets clicks hurry it.
            val wait = (player.nextAltarOfferTick - tick).toInt()
            if (wait > 0) {
                val delay = if (fromMovement) wait - 1 else wait
                queue(QUEUE, QueueType.WEAK, delay = delay) { offer(world, loc, op.obj) }
            } else {
                offer(world, loc, op.obj, fromMovement)
            }
        }
        world.handlers.onNpc(DRUID, TALK_TO) { op -> world.dialogues.start(this, op.npc) { druid() } }
        world.handlers.onObjOnNpc(DRUID) { op -> world.dialogues.start(this, op.npc) { exchange(world, op.obj) } }
    }
}

/**
 * One offering of bones [id], then the next queued [ChaosTemple.OFFER_TICKS] later ([fromMovement] when this runs in the
 * movement phase), until the player moves or runs out.
 */
private fun ScriptContext.offer(world: World, altar: ZoneLoc, id: Int, fromMovement: Boolean = false) {
    val xp = ChaosTemple.offerXp(id) ?: return
    // The repeat stops once the player isn't beside the altar any more, however they left (a teleport too).
    val type = world.locs.types[altar.id]
    if (!ChaosTemple.besideAltar(altar, type?.width ?: 1, type?.length ?: 1, player.x, player.z, player.level)) return
    val slot = player.inventory.contents().firstOrNull { (_, obj) -> obj.id == id }?.first ?: return
    player.anim(ChaosTemple.ANIM_OFFER)
    player.nextAltarOfferTick = tick + ChaosTemple.OFFER_TICKS
    if (world.random.chance(1, 2)) world.message(player, ChaosTempleLines.SPARED) else player.inventory.remove(slot, 1)
    player.skills.addTenths(Skill.PRAYER, xp)
    if (player.inventory.count(id) == 0L) return
    val delay = if (fromMovement) ChaosTemple.OFFER_TICKS - 1 else ChaosTemple.OFFER_TICKS
    queue(ChaosTemple.QUEUE, QueueType.WEAK, delay = delay) { offer(world, altar, id) }
}

private suspend fun Dialogue.druid(): Unit = with(ChaosTempleLines) {
    chatNpc(ChatAnim.NEUTRAL, WELCOME)
    chatNpc(ChatAnim.NEUTRAL, EXCHANGE)
    chatNpc(ChatAnim.NEUTRAL, FEE)
    when (choice(YES, WHO, NO)) {
        1 -> chatNpc(ChatAnim.NEUTRAL, HAND_ME)
        2 -> {
            chatNpc(ChatAnim.NEUTRAL, NOT_IMPORTANT)
            if (choice(YES, NO) == 1) chatNpc(ChatAnim.NEUTRAL, HAND_ME) else chatPlayer(ChatAnim.NEUTRAL, NO)
        }
        else -> chatPlayer(ChatAnim.NEUTRAL, NO)
    }
}

/** Banknotes [noteId] used on the druid: refused as his transcript has it, or exchanged for bones at 50 coins each. */
private suspend fun Dialogue.exchange(world: World, noteId: Int): Unit = with(ChaosTempleLines) {
    val note = world.objTypes[noteId]
    if (note == null || !note.isCert) return chatNpc(ChatAnim.NEUTRAL, NOT_A_NOTE)
    val bones = world.objTypes[note.certlink]
    if (bones == null || ChaosTemple.offerXp(bones.id) == null) return chatNpc(ChatAnim.NEUTRAL, NOT_SACRIFICE)
    val coins = player.inventory.count(ChaosTemple.COINS)
    if (coins < ChaosTemple.FEE) return chatNpc(ChatAnim.NEUTRAL, NO_COINS)
    if (player.inventory.freeSlots == 0) return chatNpc(ChatAnim.NEUTRAL, TOO_FULL)
    val count = ChaosTemple.exchangeable(player.inventory.count(noteId), coins, player.inventory.freeSlots)
    if (count <= 0) return
    Ned.take(player, ChaosTemple.COINS, count * ChaosTemple.FEE)
    Ned.take(player, noteId, count)
    player.inventory.add(bones, count)
}
