package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/**
 * Captain Harlan's lines; the words are in the dialogue file, from the Wiki's Transcript:Captain Harlan, read 2026-11-08.
 * Keys marked "missing" have no transcript (the page says so); their words are guessed or left empty.
 */
object HarlanLines : Lines("captain_harlan", "Transcript:Captain Harlan") {
    val GREETING_FIRST = line("greeting_first", "Standard dialogue: first time, his greeting")
    val GREETING = line("greeting", "Standard dialogue: later times, his greeting")

    val GUARDS = line("guards", "first menu: where the other guards are, which the player then says")
    val GUARDS_1 = line("guards_1", "the other guards: his answer, sent to Varrock")
    val GUARDS_ASK = line("guards_ask", "the other guards: the player asking if that causes problems")
    val GUARDS_2 = line("guards_2", "the other guards: his answer, problems now and then")

    val ADVICE = line("advice", "first menu: combat advice, which the player then says")
    val ADVICE_1 = line("advice_1", "combat advice: three types, melee and ranged")
    val ADVICE_2 = line("advice_2", "combat advice: magic and staves")
    val TRIANGLE = line("triangle", "combat advice: the player asking about the combat triangle")
    val ADVICE_3 = line("advice_3", "combat advice: the triangle")
    val ADVICE_4 = line("advice_4", "combat advice: the triangle isn't an absolute rule")
    val WEAPON_KINDS = line("weapon_kinds", "combat advice: the player asking about weapon types")
    val ADVICE_5 = line("advice_5", "combat advice: crush, slash and stab")
    val ADVICE_6 = line("advice_6", "combat advice: the right weapon for the job")
    val ADVICE_7 = line("advice_7", "combat advice: maces and warhammers, daggers and shortswords")
    val HINT_EQUIPMENT = line("hint_equipment", "combat advice: the Worn Equipment box")
    val ADVICE_8 = line("advice_8", "combat advice: a weapon's preferred style, the scimitar")
    val HINT_STYLES = line("hint_styles", "combat advice: the first Combat Options box, attack options")
    val HINT_XP = line("hint_xp", "combat advice: the second Combat Options box, XP per option")
    val ADVICE_9 = line("advice_9", "combat advice: his spare weapons, the blacksmith")
    val ADVICE_10 = line("advice_10", "combat advice: food")
    val ADVICE_11 = line("advice_11", "combat advice: buying or cooking food, the castle's cook")
    val ADVICE_12 = line("advice_12", "combat advice: that covers the basics")
    val HINT_GUIDES = line("hint_guides", "combat advice: the Skills panel box")
    val ADVICE_THANKS = line("advice_thanks", "combat advice: the player's thanks")

    val WEAPONS = line("weapons", "first menu: weapons he could give, which the player then says")
    val TOO_SOON_ALL = line("too_soon_all", "weapons, given some in the last 30 minutes, every option open: his refusal ({minutes})", "minutes")
    val OFFER_ALL = line("offer_all", "weapons, mage outlaw killed: his offer of all three")
    val ASK_MELEE = line("ask_melee", "weapons menu: a shortsword and a shield, which the player then says")
    val ASK_RANGED = line("ask_ranged", "weapons menu: a shortbow and some arrows, which the player then says")
    val ASK_MAGIC = line("ask_magic", "weapons menu: a staff and some runes, which the player then says")
    val SOMETHING_ELSE = line("something_else", "weapons menu: talk about something else, which the player then says")

    val GIVES_MELEE = line("gives_melee", "shortsword and shield, owning neither: the box with both")
    val NO_SPACE_MELEE = line("no_space_melee", "shortsword and shield, owning neither, no room: his line")
    val GIVES_SHIELD = line("gives_shield", "shortsword and shield, owning the shortsword: the box with the shield (missing)")
    val NO_SPACE_SHIELD = line("no_space_shield", "shortsword and shield, owning the shortsword, no room: his line (missing)")
    val GIVES_SHORTSWORD = line("gives_shortsword", "shortsword and shield, owning the shield: the box with the shortsword")
    val NO_SPACE_SHORTSWORD = line("no_space_shortsword", "shortsword and shield, owning the shield, no room: his line (missing)")
    val HAS_MELEE = line("has_melee", "shortsword and shield, owning both: his refusal")

    val GIVES_RANGED = line("gives_ranged", "shortbow and arrows, no shortbow: the box with the bow and 40 arrows")
    val NO_SPACE_RANGED = line("no_space_ranged", "shortbow and arrows, no shortbow, no room: his line")
    val GIVES_ARROWS = line("gives_arrows", "shortbow and arrows, owning a shortbow: the box with 40 arrows")
    val NO_SPACE_ARROWS = line("no_space_arrows", "shortbow and arrows, owning a shortbow, no room: his line")

    val GIVES_MAGIC = line("gives_magic", "staff and runes, no staff: the box with the staff and runes")
    val NO_SPACE_MAGIC = line("no_space_magic", "staff and runes, no staff, no room: his line")
    val GIVES_RUNES = line("gives_runes", "staff and runes, owning a staff: the box with the runes")
    val NO_SPACE_RUNES = line("no_space_runes", "staff and runes, owning a staff, no room: his line")

    val ANYTHING_ELSE = line("anything_else", "weapons: his line after each answer, anything else you need")

    val CAPE = line("cape", "first menu: the cape he's wearing, which the player then says")
    val CAPE_WHAT = line("cape_what", "the cape: his question")
    val BUY_CAPE = line("buy_cape", "cape menu: buying one, which the player then says")
    val PRICE = line("price", "buying a cape: his price")
    val ACCEPT = line("accept", "price menu: sounds good, which the player then says")
    val TOO_MUCH = line("too_much", "price menu: too much, which the player then says")
    val TOO_MUCH_REPLY = line("too_much_reply", "too much: his answer (missing)")
    val NOT_MASTER = line("not_master", "sounds good, under 99 Defence: his refusal (missing)")
    val NO_MONEY = line("no_money", "sounds good, under 99,000 coins: the player's admission (missing)")
    val NO_MONEY_REPLY = line("no_money_reply", "sounds good, under 99,000 coins: his answer (missing)")
    val NO_SPACE_CAPE = line("no_space_cape", "sounds good, no room for the cape and hood: his line (missing)")
    val SOLD = line("sold", "sounds good, sold: his line (missing)")
    val HOOD = line("hood", "cape menu: another hood, which the player then says")
    val HOOD_NO_CAPE = line("hood_no_cape", "another hood, owning no Defence cape: his line (missing)")
    val HOOD_NO_SPACE = line("hood_no_space", "another hood, no room: his line (missing)")
    val HOOD_GIVEN = line("hood_given", "another hood, given: his line (missing)")

    val LEAVE = line("leave", "first menu: better be off, which the player then says")
    val LEAVE_REPLY = line("leave_reply", "better be off: his farewell (end of dialogue)")

    val CLAIM_MELEE = line("claim_melee", "Claim-weapons menu: a shortsword and a shield")
    val CLAIM_RANGED = line("claim_ranged", "Claim-weapons menu: a shortbow and some arrows")
    val CLAIM_MAGIC = line("claim_magic", "Claim-weapons menu: a staff and some runes")
    val CANCEL = line("cancel", "Claim-weapons menu: cancel")
}

/**
 * Captain Harlan (NPC 16821, captain_harlan), head of the Lumbridge Guard on the hill north of Lumbridge Castle. Since
 * revision 242.1 (3 November 2026) he is the only combat tutor: the melee, ranged and magic tutors (3216 to 3218) are
 * gone from the cache.
 *
 * From the Wiki's transcript, after Mudfoot's Big Adventure (every account has every quest done): the first menu's
 * options and lines; the free weapons once every 30 minutes, with all three kinds open (the mage outlaw is dead); each
 * kind's checks of what the player already has and their room; Claim-weapons straight to the same handling; the cape
 * talk's first lines and price; and the Defence cape, trimmed with another 99, for 99,000 coins (the Wiki's stock).
 *
 * Guessed: every chathead expression; that the quest's options (help, the thieves) are gone after it (the post-quest
 * dialogue is missing from the page); the first-time greeting, as finishing the quest leaves captain_harlan_intro set
 * ([osrsps.game.Quests.AFTER_QUESTS]); that the Wiki's icon boxes are plain message boxes (we have no sprite box);
 * that the 30 minutes are real time kept in a var of ours ([CustomVars.harlanClaimVarp]), counted to the second from the
 * last time he gave anything, rounded up to whole minutes when he says how long is left, and checked again on every pick, so one talk gives
 * one kind; that "previous" after a kind is the weapons menu and "other" the first menu; that "has" means carried or
 * worn or banked (as the old Melee combat tutor's "inventory or bank"); which slots each kind needs (a stack already carried needs none); what happens with the shortsword and no
 * shield (missing, so the shield is given as the shortsword is); the bonus giveaways (captain_harlan_bonus_*) are left
 * out. The cape purchase after the price, the hood and "too much" are missing from the page: they follow the old
 * Melee combat tutor's cape sale (99 Defence, coins, room for cape and hood).
 */
object Harlan {
    const val ID = 16821
    const val TALK_TO = 1
    const val CLAIM_WEAPONS = 3

    /** Varbit captain_harlan_intro: 1 once he has greeted the player. */
    const val VARBIT_INTRO = 16780

    const val BRONZE_SHORTSWORD = 1277
    const val WOODEN_SHIELD = 1171
    const val SHORTBOW = 841
    const val BRONZE_ARROW = 882
    const val STAFF = 1379
    const val AIR_RUNE = 556
    const val MIND_RUNE = 558
    const val AMMO = 40

    /** skillcape_defence, skillcape_defence_trimmed and skillcape_defence_hood. */
    const val DEFENCE_CAPE = 9753
    const val DEFENCE_CAPE_TRIMMED = 9754
    const val DEFENCE_HOOD = 9755

    const val COINS = 995
    const val CAPE_PRICE = 99_000
    const val MAX_LEVEL = 99

    /** Minutes between two handouts. */
    const val CLAIM_MINUTES = 30

    internal suspend fun Dialogue.talk(tutor: Tutor) = with(HarlanLines) {
        // A cache without the varbit (tests) counts as met.
        if (player.vars.types.varbit(VARBIT_INTRO) != null && player.vars.getVarbit(VARBIT_INTRO) == 0) {
            player.vars.setVarbit(VARBIT_INTRO, 1)
            chatNpc(ChatAnim.HAPPY, GREETING_FIRST)
        } else {
            chatNpc(ChatAnim.HAPPY, GREETING)
        }
        mainMenu(tutor)
    }

    private suspend fun Dialogue.mainMenu(tutor: Tutor) = with(HarlanLines) {
        while (true) {
            when (choice(GUARDS, ADVICE, WEAPONS, CAPE, LEAVE)) {
                1 -> guards()
                2 -> advice()
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, WEAPONS)
                    if (!weapons(tutor)) return@with
                }
                4 -> cape(tutor)
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, LEAVE)
                    chatNpc(ChatAnim.HAPPY, LEAVE_REPLY)
                    return@with
                }
            }
        }
    }

    private suspend fun Dialogue.guards() = with(HarlanLines) {
        chatPlayer(ChatAnim.QUIZ, GUARDS)
        chatNpc(ChatAnim.NEUTRAL, GUARDS_1)
        chatPlayer(ChatAnim.QUIZ, GUARDS_ASK)
        chatNpc(ChatAnim.NEUTRAL, GUARDS_2)
    }

    private suspend fun Dialogue.advice() = with(HarlanLines) {
        chatPlayer(ChatAnim.QUIZ, ADVICE)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_1)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_2)
        chatPlayer(ChatAnim.QUIZ, TRIANGLE)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_3)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_4)
        chatPlayer(ChatAnim.QUIZ, WEAPON_KINDS)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_5)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_6)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_7)
        mesbox(HINT_EQUIPMENT)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_8)
        mesbox(HINT_STYLES)
        mesbox(HINT_XP)
        for (line in listOf(ADVICE_9, ADVICE_10, ADVICE_11, ADVICE_12)) chatNpc(ChatAnim.NEUTRAL, line)
        mesbox(HINT_GUIDES)
        chatPlayer(ChatAnim.HAPPY, ADVICE_THANKS)
    }

    /** The weapons branch from the first menu. False when the talk ends there (too soon). */
    private suspend fun Dialogue.weapons(tutor: Tutor): Boolean {
        with(HarlanLines) {
            if (refusedTooSoon(tutor)) return false
            chatNpc(ChatAnim.HAPPY, OFFER_ALL)
            while (true) {
                val kind = when (choice(ASK_MELEE, ASK_RANGED, ASK_MAGIC, SOMETHING_ELSE)) {
                    1 -> Kind.MELEE
                    2 -> Kind.RANGED
                    3 -> Kind.MAGIC
                    else -> {
                        chatPlayer(ChatAnim.NEUTRAL, SOMETHING_ELSE)
                        return true
                    }
                }
                chatPlayer(ChatAnim.QUIZ, kind.ask)
                if (refusedTooSoon(tutor)) return false
                if (!give(tutor, kind)) return true
            }
        }
    }

    /** Claim-weapons: the kinds as a menu, straight to the handout; the talk ends after it (guessed). */
    internal suspend fun Dialogue.claim(tutor: Tutor) = with(HarlanLines) {
        val kind = when (choice(CLAIM_MELEE, CLAIM_RANGED, CLAIM_MAGIC, CANCEL)) {
            1 -> Kind.MELEE
            2 -> Kind.RANGED
            3 -> Kind.MAGIC
            else -> return@with
        }
        if (refusedTooSoon(tutor)) return@with
        give(tutor, kind)
    }

    /** His refusal while the last handout is under [CLAIM_MINUTES] old; true when he refused. */
    private suspend fun Dialogue.refusedTooSoon(tutor: Tutor): Boolean = with(HarlanLines) {
        val left = minutesLeft(player, tutor.nowSeconds())
        if (left <= 0) return@with false
        chatNpc(ChatAnim.NEUTRAL, TOO_SOON_ALL, "minutes" to left)
        true
    }

    /** Minutes until the next handout at [now] (seconds since the epoch), rounded up; 0 when one is due. */
    fun minutesLeft(player: Player, now: Long): Long {
        val varp = player.vars.types.namedVarps[CustomVars.HARLAN_CLAIMED] ?: return 0
        val last = player.vars[varp]
        if (last == 0) return 0
        val seconds = (last + CLAIM_MINUTES * 60L - now).coerceIn(0, CLAIM_MINUTES * 60L)
        return (seconds + 59) / 60
    }

    private fun claimed(player: Player, now: Long) {
        val varp = player.vars.types.namedVarps[CustomVars.HARLAN_CLAIMED] ?: return
        player.vars[varp] = now.toInt()
    }

    enum class Kind(val ask: osrsps.game.dialogue.Line) {
        MELEE(HarlanLines.ASK_MELEE), RANGED(HarlanLines.ASK_RANGED), MAGIC(HarlanLines.ASK_MAGIC)
    }

    /**
     * Hands out [kind] as far as the player's room allows, with its box or his no-room line, then his "anything else".
     * True when the weapons menu comes back after it ("previous"), false for the first menu ("other").
     */
    private suspend fun Dialogue.give(tutor: Tutor, kind: Kind): Boolean = with(HarlanLines) {
        val free = player.inventory.freeSlots
        val previous: Boolean
        when (kind) {
            Kind.MELEE -> {
                val sword = hasAnywhere(player, BRONZE_SHORTSWORD)
                val shield = hasAnywhere(player, WOODEN_SHIELD)
                when {
                    sword && shield -> {
                        chatNpc(ChatAnim.NEUTRAL, HAS_MELEE)
                        previous = false
                    }
                    !sword && !shield -> if (free >= 2) {
                        handOut(tutor, BRONZE_SHORTSWORD to 1, WOODEN_SHIELD to 1)
                        doubleobjbox(BRONZE_SHORTSWORD, WOODEN_SHIELD, text(GIVES_MELEE))
                        previous = true
                    } else {
                        chatNpc(ChatAnim.NEUTRAL, NO_SPACE_MELEE)
                        previous = false
                    }
                    else -> {
                        val missing = if (sword) WOODEN_SHIELD else BRONZE_SHORTSWORD
                        if (free >= 1) {
                            handOut(tutor, missing to 1)
                            objbox(missing, if (sword) GIVES_SHIELD else GIVES_SHORTSWORD)
                        } else {
                            chatNpc(ChatAnim.NEUTRAL, if (sword) NO_SPACE_SHIELD else NO_SPACE_SHORTSWORD)
                        }
                        previous = true
                    }
                }
            }
            Kind.RANGED -> if (!hasAnywhere(player, SHORTBOW)) {
                // The Wiki: one free slot is enough; with exactly one the arrows land on the ground.
                if (free >= 1) {
                    handOut(tutor, SHORTBOW to 1, BRONZE_ARROW to AMMO)
                    doubleobjbox(SHORTBOW, BRONZE_ARROW, text(GIVES_RANGED))
                } else {
                    chatNpc(ChatAnim.NEUTRAL, NO_SPACE_RANGED)
                }
                previous = true
            } else if (slotsFor(player, BRONZE_ARROW) <= free) {
                handOut(tutor, BRONZE_ARROW to AMMO)
                objbox(BRONZE_ARROW, GIVES_ARROWS)
                previous = true
            } else {
                chatNpc(ChatAnim.NEUTRAL, NO_SPACE_ARROWS)
                previous = false
            }
            Kind.MAGIC -> if (!hasAnywhere(player, STAFF)) {
                if (1 + slotsFor(player, MIND_RUNE, AIR_RUNE) <= free) {
                    handOut(tutor, STAFF to 1, MIND_RUNE to AMMO, AIR_RUNE to AMMO)
                    doubleobjbox(STAFF, MIND_RUNE, text(GIVES_MAGIC))
                    previous = true
                } else {
                    chatNpc(ChatAnim.NEUTRAL, NO_SPACE_MAGIC)
                    previous = false
                }
            } else if (slotsFor(player, MIND_RUNE, AIR_RUNE) <= free) {
                handOut(tutor, MIND_RUNE to AMMO, AIR_RUNE to AMMO)
                doubleobjbox(MIND_RUNE, AIR_RUNE, text(GIVES_RUNES))
                previous = true
            } else {
                chatNpc(ChatAnim.NEUTRAL, NO_SPACE_RUNES)
                previous = false
            }
        }
        chatNpc(ChatAnim.HAPPY, ANYTHING_ELSE)
        previous
    }

    private fun Dialogue.handOut(tutor: Tutor, vararg objs: Pair<Int, Int>) {
        for ((obj, count) in objs) tutor.give(player, obj, count)
        claimed(player, tutor.nowSeconds())
    }

    /** Slots [objs] (stackables) take: none for one already carried. */
    private fun slotsFor(player: Player, vararg objs: Int): Int = objs.count { player.inventory.count(it) == 0L }

    private suspend fun Dialogue.cape(tutor: Tutor) = with(HarlanLines) {
        chatPlayer(ChatAnim.QUIZ, CAPE)
        chatNpc(ChatAnim.NEUTRAL, CAPE_WHAT)
        if (choice(BUY_CAPE, HOOD) == 2) {
            chatPlayer(ChatAnim.QUIZ, HOOD)
            when {
                !owns(player, DEFENCE_CAPE) && !owns(player, DEFENCE_CAPE_TRIMMED) -> chatNpc(ChatAnim.NEUTRAL, HOOD_NO_CAPE)
                player.inventory.freeSlots < 1 -> chatNpc(ChatAnim.NEUTRAL, HOOD_NO_SPACE)
                else -> {
                    tutor.give(player, DEFENCE_HOOD, 1)
                    chatNpc(ChatAnim.HAPPY, HOOD_GIVEN)
                }
            }
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, BUY_CAPE)
        chatNpc(ChatAnim.HAPPY, PRICE)
        if (choice(ACCEPT, TOO_MUCH) == 2) {
            chatPlayer(ChatAnim.SHOCKED, TOO_MUCH)
            chatNpc(ChatAnim.NEUTRAL, TOO_MUCH_REPLY)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, ACCEPT)
        val coins = player.inventory.count(COINS)
        when {
            player.skills.baseLevel(Skill.DEFENCE) < MAX_LEVEL -> chatNpc(ChatAnim.NEUTRAL, NOT_MASTER)
            coins < CAPE_PRICE -> {
                chatPlayer(ChatAnim.SAD, NO_MONEY)
                chatNpc(ChatAnim.NEUTRAL, NO_MONEY_REPLY)
            }
            player.inventory.freeSlots + (if (coins == CAPE_PRICE.toLong()) 1 else 0) < 2 -> chatNpc(ChatAnim.NEUTRAL, NO_SPACE_CAPE)
            else -> {
                removeCount(player, COINS, CAPE_PRICE)
                tutor.give(player, if (otherMaxedSkill(player)) DEFENCE_CAPE_TRIMMED else DEFENCE_CAPE, 1)
                tutor.give(player, DEFENCE_HOOD, 1)
                chatNpc(ChatAnim.HAPPY, SOLD)
            }
        }
    }

    fun owns(player: Player, obj: Int): Boolean = player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0

    /** Whether the player carries, wears or banks [obj]: what the free weapons check. */
    fun hasAnywhere(player: Player, obj: Int): Boolean = owns(player, obj) || player.bank.count(obj) > 0

    /** Whether a skill other than Defence is at 99, which makes the cape trimmed. */
    fun otherMaxedSkill(player: Player): Boolean =
        Skill.entries.any { it != Skill.DEFENCE && player.skills.baseLevel(it) >= MAX_LEVEL }

    /** What the talk does outside the chatbox: hand over items (at the player's feet without room), and the clock. */
    class Tutor(val give: (Player, Int, Int) -> Unit, val nowSeconds: () -> Long = { System.currentTimeMillis() / 1_000 }) {
        constructor(world: World) : this(
            give = { player, obj, count ->
                world.objTypes[obj]?.let { world.groundObjs.give(player, it, count) } ?: logger.warn { "No item $obj in the cache" }
            },
        )
    }

    /** Takes [count] of [obj] from the inventory, from its first slots on. */
    private fun removeCount(player: Player, obj: Int, count: Int) {
        var left = count
        for ((slot, held) in player.inventory.contents()) {
            if (left == 0) break
            if (held.id == obj) left -= player.inventory.remove(slot, left)
        }
    }
}

/** Captain Harlan's Talk-to and Claim-weapons. */
fun registerCombatTutors(world: World) {
    val tutor = Harlan.Tutor(world)
    world.handlers.onNpc(Harlan.ID, Harlan.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Harlan) { talk(tutor) } }
    }
    world.handlers.onNpc(Harlan.ID, Harlan.CLAIM_WEAPONS) { op ->
        world.dialogues.start(this, op.npc) { with(Harlan) { claim(tutor) } }
    }
}
