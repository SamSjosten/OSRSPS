package osrsps.game.skill

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Bob Barter's words, in the dialogue file, from the Wiki's Transcript:Bob Barter (herbs), read 2026-11-10. */
object BobBarterLines : Lines("bob_barter", "Transcript:Bob Barter (herbs)") {
    val HELLO = line("hello", "Talk-to: his opening line, the designer jewellery")
    val NO_THANKS = line("no_thanks", "Talk-to: the player's answer")
    val WHAT_CAN_I_DO = line("what_can_i_do", "Talk-to, on a members world: the prices or decanting")
    val WHO_ARE_YOU = line("who_are_you", "menu option 1, which the player then says")
    val IM_BOB = line("im_bob", "who he is: his answer")
    val WHAT_TO_SELL = line("what_to_sell", "who he is: the player asks what he sells")
    val NOT_MUCH = line("not_much", "who he is: not much at the moment")
    val NOT_SUCCESSFUL = line("not_successful", "who he is: the player says he doesn't look successful")
    val PLONKA = line("plonka", "who he is: it's all a show")
    val EVERYTHING = line("everything", "who he is: the player says he has a response for everything")
    val ARDOUGNE_WAY = line("ardougne_way", "who he is: the Ardougne way")
    val PRICES = line("prices", "menu option 2, which the player then says: herb and potion prices")
    val DECANT = line("decant", "menu option 3, which the player then says: decanting")
    val LEAVE = line("leave", "menu option 4: the player leaves him to it")
    val LETS_SEE = line("lets_see", "decanting to 1 to 3 doses: one of his two random lines before the result")
    val TWO_PLUS_TWO = line("two_plus_two", "decanting to 1 to 3 doses: the other random line")
    val NO_SPACE = line("no_space", "decanting: the player can't fit the spare vials")
    val SHORT = line("short", "decanting: the player has no spare vials and too little cash for any", "vial", "cup")
    val DONE_WHAT_I_CAN = line("done_what_i_can", "decanting: some decanted, then out of vials and cash", "vial", "cup")
    val ALL_DONE = line("all_done", "decanting: everything decanted")
    val NOTHING = line("nothing", "decanting: nothing he can decant")
}

/**
 * How [Decanting.decant] turned out: [inventory] is the backpack after it (unchanged unless something was decanted),
 * [bought] the vials bought.
 */
data class DecantResult(val outcome: DecantOutcome, val inventory: List<InvObj?>, val bought: Int = 0)

enum class DecantOutcome { NOTHING, NO_SPACE, SHORT, DONE_WHAT_I_CAN, ALL_DONE }

/**
 * A potion by its doses: [doses] 1 to 4 (index 0 is the 1-dose), and the note of each, -1 for none.
 */
data class PotionFamily(val name: String, val doses: List<Int>, val notes: List<Int>)

/**
 * Bob Barter (herbs) at the Grand Exchange (NPC 5449, ge_expert_herbs; Talk-to, Prices, Decant) and his decanting.
 *
 * Decant (or "Can you decant things for me?" in his Talk-to) opens the decant interface (582, `decant`) in the chatbox
 * with its four dose buttons (components 3 to 6, pause buttons: the osrs-dumps' decant.if3 and decant_init). Every
 * potion in the backpack, noted or not, is then decanted into potions of that many doses, one of fewer for what is left
 * over. The potions' own vials hold the result; spare empty vials come back, and missing ones are bought from him at
 * the Grand Exchange guide price of a vial plus 2 coins each (Wiki's Bob Barter page). With any input noted, or when the
 * result won't fit unnoted, the potions and vials come back as notes (Wiki). His lines are the Wiki transcript's.
 *
 * Sources: OSRS Wiki, Bob Barter (herbs) and Decanting, Transcript:Bob Barter (herbs), read 2026-11-10; the cache for his
 * options and the interface. Guessed: a potion is any item with a Drink option named "Name(n)" for each n from 1 to 4
 * (that leaves out cups, barbarian mixes, Guthix rests, oils and moth mixes, which the Wiki says he also decants); the
 * lowest id of each name is the one made; out of cash part way, potions are decanted in backpack order while the vials
 * last; "short of inventory space" when even notes won't fit; each chathead's expression. Out: Prices and the herb and
 * potion price guide (the Talk-to option for it ends the talk), the free-to-play lines (every world here is members),
 * cups of tea.
 */
class Decanting(private val world: World) {
    /** Every potion family in the cache, by each dose's obj and its note. */
    val families: Map<Int, Pair<PotionFamily, Int>> by lazy { families(world.objTypes) }

    fun register() {
        world.handlers.onNpc(ID, TALK_TO) { op -> world.dialogues.start(this, op.npc) { talk() } }
        world.handlers.onNpc(ID, DECANT) { op -> world.dialogues.start(this, op.npc) { decantAsked() } }
    }

    private suspend fun Dialogue.talk() = with(BobBarterLines) {
        chatNpc(ChatAnim.HAPPY, HELLO)
        chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
        chatNpc(ChatAnim.NEUTRAL, WHAT_CAN_I_DO)
        while (true) {
            when (choice(WHO_ARE_YOU, PRICES, DECANT, LEAVE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHO_ARE_YOU)
                    chatNpc(ChatAnim.HAPPY, IM_BOB)
                    chatPlayer(ChatAnim.QUIZ, WHAT_TO_SELL)
                    chatNpc(ChatAnim.NEUTRAL, NOT_MUCH)
                    chatPlayer(ChatAnim.NEUTRAL, NOT_SUCCESSFUL)
                    chatNpc(ChatAnim.ANGRY, PLONKA)
                    chatPlayer(ChatAnim.NEUTRAL, EVERYTHING)
                    chatNpc(ChatAnim.HAPPY, ARDOUGNE_WAY)
                }
                2 -> {
                    // The price guide isn't built: the talk ends here.
                    chatPlayer(ChatAnim.QUIZ, PRICES)
                    return@with
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, DECANT)
                    decantAsked()
                    return@with
                }
                else -> return@with
            }
        }
    }

    /** The decant interface, then the decanting and his line for how it went. */
    private suspend fun Dialogue.decantAsked() {
        val player = player
        world.interfaces.openChatModal(player, INTERFACE, Chatbox.UNCLAMP_FIXED)
        for (button in BUTTONS) player.session.queue(IfSetEventsV2(INTERFACE, button, -1, -1, Chatbox.EVENT_PAUSE_BUTTON, 0))
        val (button, _) = context.pauseButtons(INTERFACE, BUTTONS, -1..-1)
        val dose = button - BUTTONS.first() + 1
        val price = vialPrice()
        val result = decant(List(player.inventory.capacity) { player.inventory[it] }, dose, price)
        if (result.outcome == DecantOutcome.ALL_DONE || result.outcome == DecantOutcome.DONE_WHAT_I_CAN) {
            for (slot in 0 until player.inventory.capacity) {
                if (player.inventory[slot] != result.inventory[slot]) player.inventory[slot] = result.inventory[slot]
            }
        }
        with(BobBarterLines) {
            if (result.outcome == DecantOutcome.NOTHING) return chatNpc(ChatAnim.NEUTRAL, NOTHING)
            if (dose < 4) chatNpc(ChatAnim.NEUTRAL, if (world.random.nextInt(2) == 0) LETS_SEE else TWO_PLUS_TWO)
            val costs = arrayOf("vial" to price, "cup" to price)
            when (result.outcome) {
                DecantOutcome.NO_SPACE -> chatNpc(ChatAnim.NEUTRAL, NO_SPACE)
                DecantOutcome.SHORT -> chatNpc(ChatAnim.NEUTRAL, SHORT, *costs)
                DecantOutcome.DONE_WHAT_I_CAN -> chatNpc(ChatAnim.NEUTRAL, DONE_WHAT_I_CAN, *costs)
                else -> chatNpc(ChatAnim.HAPPY, ALL_DONE)
            }
        }
    }

    /** What he charges for an empty vial: its guide price plus 2, or 3 with no price known (guessed). */
    fun vialPrice(): Int {
        val vial = world.objTypes[VIAL] ?: return 3
        return ((world.itemPrices.price(vial) ?: 1L) + 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Decants every potion in [slots] (the backpack, null for empty) into [dose]-dose potions, buying missing vials at
     * [price] coins each with the coins held. Pure: the result says what the backpack becomes.
     */
    fun decant(slots: List<InvObj?>, dose: Int, price: Int): DecantResult {
        require(dose in 1..4)
        val types = world.objTypes
        // Each family held, in backpack order: its doses, the vials its potions fill, whether any came noted.
        class Held(val family: PotionFamily, var doses: Long = 0, var vials: Long = 0, var noted: Boolean = false)
        val held = LinkedHashMap<String, Held>()
        var spareVials = 0L
        var coins = 0L
        for (obj in slots) {
            if (obj == null) continue
            if (obj.id == VIAL || obj.id == VIAL_NOTE) { spareVials += obj.count; continue }
            if (obj.id == COINS) { coins += obj.count; continue }
            val (family, index) = families[obj.id] ?: continue
            val h = held.getOrPut(family.name) { Held(family) }
            h.doses += obj.count.toLong() * (index + 1)
            h.vials += obj.count
            if (family.doses[index] != obj.id) h.noted = true
        }
        val original = slots.toList()
        if (held.isEmpty()) return DecantResult(DecantOutcome.NOTHING, original)
        // Families whose doses need more vials than their own potions take the spare vials, then bought ones.
        val need = held.values.associateWith { (it.doses + dose - 1) / dose }
        val shortBy = (need.values.sum() - held.values.sumOf { it.vials } - spareVials).coerceAtLeast(0)
        val affordable = if (price <= 0) shortBy else minOf(shortBy, coins / price)
        var pool = spareVials + affordable
        val decanted = ArrayList<Held>()
        for (h in held.values) {
            val extra = need.getValue(h) - h.vials
            if (extra > pool) continue
            pool -= extra
            decanted += h
        }
        if (decanted.isEmpty()) return DecantResult(DecantOutcome.SHORT, original)
        // The vials the decanted families take beyond their own (fewer doses a potion), or free (4 doses).
        val taken = decanted.sumOf { need.getValue(it) - it.vials }
        val bought = (taken - spareVials).coerceIn(0, affordable)
        val vialsBack = spareVials + bought - taken
        val anyNoted = decanted.any { it.noted } || original.any { it?.id == VIAL_NOTE }
        val decantedNames = decanted.map { it.family.name }.toSet()
        val kept = original.map { obj ->
            when {
                obj == null -> null
                obj.id == VIAL || obj.id == VIAL_NOTE -> null
                obj.id == COINS -> (obj.count - bought * price).let { if (it > 0) InvObj(COINS, it.toInt()) else null }
                families[obj.id]?.first?.name in decantedNames -> null
                else -> obj
            }
        }
        val outputs = ArrayList<Pair<Int, Long>>()
        for (h in decanted) {
            val full = h.doses / dose
            val rest = (h.doses % dose).toInt()
            if (full > 0) outputs += h.family.doses[dose - 1] to full
            if (rest > 0) outputs += h.family.doses[rest - 1] to 1L
        }
        if (vialsBack > 0) outputs += VIAL to vialsBack
        val unnoted = if (anyNoted) null else place(kept, outputs, types)
        val result = unnoted ?: place(kept, outputs.map { (id, count) -> (if (id == VIAL) VIAL_NOTE else noteOf(id)) to count }, types)
            ?: return DecantResult(DecantOutcome.NO_SPACE, original)
        val outcome = if (decanted.size == held.size) DecantOutcome.ALL_DONE else DecantOutcome.DONE_WHAT_I_CAN
        return DecantResult(outcome, result, bought.toInt())
    }

    private fun noteOf(id: Int): Int = families[id]?.let { (family, index) -> family.notes[index].takeIf { it >= 0 } } ?: id

    /** [kept] with [outputs] added, or null when they won't all fit. */
    private fun place(kept: List<InvObj?>, outputs: List<Pair<Int, Long>>, types: ObjTypes): List<InvObj?>? {
        val inventory = Inventory(BACKPACK, kept.size)
        for ((slot, obj) in kept.withIndex()) inventory[slot] = obj
        for ((id, count) in outputs) {
            if (count > Int.MAX_VALUE) return null
            val type = types[id] ?: return null
            if (inventory.add(type, count.toInt()) < count) return null
        }
        return List(kept.size) { inventory[it] }
    }

    companion object {
        const val ID = 5449
        const val TALK_TO = 1
        const val DECANT = 4

        /** decant (582): its four dose buttons, 1 to 4 doses. */
        const val INTERFACE = 582
        val BUTTONS = listOf(3, 4, 5, 6)

        const val VIAL = 229
        const val VIAL_NOTE = 230
        const val COINS = 995
        private const val BACKPACK = 93

        private val DOSED = Regex("""^(.+)\(([1-4])\)$""")

        /** Every family of potions in [types]: items with a Drink option named "Name(n)" for each n from 1 to 4. */
        fun families(types: ObjTypes): Map<Int, Pair<PotionFamily, Int>> {
            val byName = LinkedHashMap<String, Array<ObjType?>>()
            for (id in 0 until types.size) {
                val type = types[id] ?: continue
                if (type.isCert || type.isPlaceholder || type.iops.none { it == "Drink" }) continue
                val match = DOSED.matchEntire(type.name) ?: continue
                val doses = byName.getOrPut(match.groupValues[1].lowercase()) { arrayOfNulls(4) }
                val index = match.groupValues[2].toInt() - 1
                if (doses[index] == null) doses[index] = type
            }
            val families = HashMap<Int, Pair<PotionFamily, Int>>()
            for ((name, doses) in byName) {
                if (doses.any { it == null }) continue
                val family = PotionFamily(name, doses.map { it!!.id }, doses.map { type -> type!!.certlink.takeIf { it >= 0 && types[it]?.isCert == true } ?: -1 })
                for (i in 0..3) {
                    families[family.doses[i]] = family to i
                    if (family.notes[i] >= 0) families[family.notes[i]] = family to i
                }
            }
            // Other items of the same names (a second id for a potion) decant into the family too.
            for (id in 0 until types.size) {
                val type = types[id] ?: continue
                if (id in families || type.iops.none { it == "Drink" } && !type.isCert) continue
                val base = if (type.isCert) types[type.certlink] ?: continue else type
                val match = DOSED.matchEntire(base.name) ?: continue
                val family = byName[match.groupValues[1].lowercase()]?.takeIf { d -> d.none { it == null } } ?: continue
                val first = families[family[0]!!.id]?.first ?: continue
                families[id] = first to match.groupValues[2].toInt() - 1
            }
            return families
        }
    }
}
