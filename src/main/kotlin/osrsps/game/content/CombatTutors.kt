package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameFrame
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/**
 * Harlan's lines; the words are in the dialogue file, from the Wiki's Transcript:Melee combat tutor, read 2026-10-28.
 * One key per page of the transcript, so a line that reads the same in two branches still has a key in each.
 */
object HarlanLines : Lines("harlan", "Transcript:Melee combat tutor") {
    val GREETING = line("greeting", "Standard dialogue: the tutor's first line")
    val MELEE = line("melee", "first menu, option 1: melee combat, which the player then says")
    val WEAPON_TYPES = line("weapon_types", "first menu, option 2: weapon types, which the player then says")
    val SKILLCAPES = line("skillcapes", "first menu, option 3: skillcapes, which the player then says")
    val TRAINING_KIT = line("training_kit", "first menu, option 4: a training sword and shield, which the player then says")
    val GOODBYE = line("goodbye", "first menu, option 5: goodbye, which the player then says (end of dialogue)")
    val HUMAN = line("human", "menu after melee combat: fighting something more human, which the player then says")

    val MELEE_1 = line("melee_1", "melee combat: first line, a sword and shield for your level")
    val MELEE_2 = line("melee_2", "melee combat: second line, after the Worn equipment tab opens: equipping them")
    val MELEE_3 = line("melee_3", "melee combat: third line, after the Combat options tab opens: the options with a sword")
    val MELEE_4 = line("melee_4", "melee combat: fourth line, the four styles")
    val STYLES = line("styles", "melee combat: the player asking what each style does")
    val ACCURATE = line("accurate", "melee combat: accurate")
    val AGGRESSIVE = line("aggressive", "melee combat: aggressive")
    val DEFENSIVE = line("defensive", "melee combat: defensive")
    val CONTROLLED = line("controlled", "melee combat: controlled")
    val ATTACK_TYPES_1 = line("attack_types_1", "melee combat: the training sword's attack types, first line")
    val ATTACK_TYPES_2 = line("attack_types_2", "melee combat: the training sword's attack types, second line")
    val HOVER = line("hover", "melee combat: hover over a style's button")
    val MELEE_ANYTHING_ELSE = line("melee_anything_else", "melee combat: the tutor asking if there's anything else")

    val HUMAN_1 = line("human_1", "more human: first line, places to fight")
    val HUMAN_2 = line("human_2", "more human: second line, Castle Wars")
    val HUMAN_TEAMS = line("human_teams", "more human: third line, how the two teams play Castle Wars")
    val CAPTURE_THE_FLAG = line("capture_the_flag", "more human: the player's remark after Castle Wars")
    val HUMAN_3 = line("human_3", "more human: third line, Clan Wars (the Grouping tab opens after it)")
    val HUMAN_4 = line("human_4", "more human: fourth line, safe minigames")
    val HUMAN_5 = line("human_5", "more human: fifth line, where the Wilderness is")
    val HUMAN_6 = line("human_6", "more human: sixth line, losing items to other players")
    val HUMAN_7 = line("human_7", "more human: seventh line, looting")
    val HUMAN_8 = line("human_8", "more human: eighth line, the Wilderness level")
    val HUMAN_9 = line("human_9", "more human: ninth line, combat levels you can attack")
    val HUMAN_ANYTHING_ELSE = line("human_anything_else", "more human: the tutor asking if there's anything else")

    val WEAPONS_1 = line("weapons_1", "weapon types: first line, the types")
    val WEAPONS_2 = line("weapons_2", "weapon types: second line, experimenting")
    val WEAPONS_3 = line("weapons_3", "weapon types: third line, the tutor's own sword")
    val WEAPONS_4 = line("weapons_4", "weapon types: fourth line, accuracy against each monster")
    val WEAPONS_ANYTHING_ELSE = line("weapons_anything_else", "weapon types: the tutor asking if there's anything else")

    val CAPES_1 = line("capes_1", "skillcapes: first line")
    val CAPES_2 = line("capes_2", "skillcapes: second line, the Defence cape's effect")
    val CAPES_ANYTHING_ELSE = line("capes_anything_else", "skillcapes: the tutor asking if there's anything else")

    val HAS_BOTH = line("has_both", "training sword and shield, already owning both: the tutor's refusal")
    val GIVES_BOTH = line("gives_both", "training sword and shield, owning neither: the box with both items")
    val GIVES_SWORD = line("gives_sword", "training sword and shield, owning the shield: the box with the sword")
    val GIVES_SHIELD = line("gives_shield", "training sword and shield, owning the sword: the box with the shield")
    val THERE_YOU_GO = line("there_you_go", "training sword and shield, after an item box: the tutor's line")
    val KIT_ANYTHING_ELSE = line("kit_anything_else", "training sword and shield: the tutor asking if there's anything else")

    val MASTER_1 = line("master_1", "99 Defence: the tutor's first line")
    val MASTER_2 = line("master_2", "99 Defence: second line, the skillcape on offer")
    val BUY_CAPE = line("buy_cape", "99 Defence menu, option 1: buying the skillcape")
    val BUY_CAPE_ASK = line("buy_cape_ask", "buying the skillcape: what the player says after choosing it")
    val MASTER_WEAPON_TYPES = line("master_weapon_types", "99 Defence menu, option 2: weapon types, which the player then says")
    val PRICE = line("price", "buying the skillcape: the tutor's price")
    val TOO_EXPENSIVE = line("too_expensive", "price menu, option 1: much too expensive, which the player then says")
    val TOO_EXPENSIVE_REPLY = line("too_expensive_reply", "much too expensive: the tutor's answer")
    val HAVE_MONEY = line("have_money", "price menu, option 2: the money is here, which the player then says")
    val NO_MONEY = line("no_money", "the money is here, under 99,000 coins: the player's admission")
    val NO_MONEY_REPLY = line("no_money_reply", "the money is here, under 99,000 coins: the tutor's answer")
    val NO_SPACE = line("no_space", "the money is here, no room for the hood: the tutor's line")
    val SOLD = line("sold", "the money is here, sold: the tutor's line")

    val RETURN_SWORD = line("return_sword", "Training sword used on the tutor: the player's line")
    val RETURN_SWORD_REPLY = line("return_sword_reply", "Training sword used on the tutor: the tutor's answer")
    val RETURNED_SWORD = line("returned_sword", "Training sword used on the tutor: the box with the sword")
    val RETURN_SHIELD = line("return_shield", "Training shield used on the tutor: the player's line")
    val RETURN_SHIELD_REPLY = line("return_shield_reply", "Training shield used on the tutor: the tutor's answer")
    val RETURNED_SHIELD = line("returned_shield", "Training shield used on the tutor: the box with the shield")
    val NO_USE = line("no_use", "any other item used on the tutor: the tutor's line")
}

/**
 * The combat tutors on the hill north of Lumbridge Castle: Harlan (melee, NPC 3216), Nemarti (ranged, 3217) and Mikasi
 * (magic, 3218). Only Harlan talks for now; the other two come with ranged and magic.
 *
 * From the Wiki's transcript: every branch, its lines and its order; a training sword and shield for a player who owns
 * neither, or the one they lack; at 99 Defence the skillcape for 99,000 coins, trimmed with another 99, and the hood
 * free. Guessed: every chathead expression; that "owns" means carried or worn (there is no bank yet); that after the
 * melee talk the "more human" option takes melee's place in the first menu (six options don't fit), and the first menu
 * comes back after every other branch, without the greeting; that the Grouping tab isn't opened (the transcript opens
 * it after Clan Wars; the tab isn't built); that the Adventure paths line never shows (there are no Adventure paths);
 * that the cape and hood need two free slots, counting the coins' slot when they are all spent; that a sword or shield
 * used on him is taken back; and that with a full inventory the training kit lands at the player's feet, as a drop
 * does. Deadman worlds' dialogue is left out.
 */
object Harlan {
    const val ID = 3216
    const val TALK_TO = 1

    /** aide_shortsword and aide_shield. */
    const val TRAINING_SWORD = 9703
    const val TRAINING_SHIELD = 9704

    /** skillcape_defence, skillcape_defence_trimmed and skillcape_defence_hood. */
    const val DEFENCE_CAPE = 9753
    const val DEFENCE_CAPE_TRIMMED = 9754
    const val DEFENCE_HOOD = 9755

    const val COINS = 995
    const val CAPE_PRICE = 99_000
    const val MAX_LEVEL = 99

    internal suspend fun Dialogue.talk(tutor: Tutor) = with(HarlanLines) {
        if (player.skills.baseLevel(Skill.DEFENCE) >= MAX_LEVEL) {
            chatNpc(ChatAnim.HAPPY, MASTER_1)
            chatNpc(ChatAnim.HAPPY, MASTER_2)
            if (choice(BUY_CAPE, MASTER_WEAPON_TYPES) == 1) {
                buyCape(tutor)
                return@with
            }
            chatPlayer(ChatAnim.QUIZ, MASTER_WEAPON_TYPES)
            weaponTypes()
        } else {
            chatNpc(ChatAnim.HAPPY, GREETING)
        }
        var first = MELEE
        while (true) {
            when (val picked = listOf(first, WEAPON_TYPES, SKILLCAPES, TRAINING_KIT, GOODBYE).let { it[choice(*it.toTypedArray()) - 1] }) {
                MELEE -> {
                    chatPlayer(ChatAnim.QUIZ, MELEE)
                    melee(tutor)
                    first = HUMAN
                }
                HUMAN -> {
                    chatPlayer(ChatAnim.QUIZ, HUMAN)
                    human()
                    first = MELEE
                }
                WEAPON_TYPES -> {
                    chatPlayer(ChatAnim.QUIZ, picked)
                    weaponTypes()
                }
                SKILLCAPES -> {
                    chatPlayer(ChatAnim.QUIZ, SKILLCAPES)
                    chatNpc(ChatAnim.NEUTRAL, CAPES_1)
                    chatNpc(ChatAnim.NEUTRAL, CAPES_2)
                    chatNpc(ChatAnim.HAPPY, CAPES_ANYTHING_ELSE)
                }
                TRAINING_KIT -> {
                    chatPlayer(ChatAnim.QUIZ, TRAINING_KIT)
                    trainingKit(tutor)
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, GOODBYE)
                    return@with
                }
            }
        }
    }

    private suspend fun Dialogue.melee(tutor: Tutor) = with(HarlanLines) {
        chatNpc(ChatAnim.NEUTRAL, MELEE_1)
        tutor.sideTab(player, GameFrame.SIDE_TAB_WORN)
        chatNpc(ChatAnim.NEUTRAL, MELEE_2)
        tutor.sideTab(player, GameFrame.SIDE_TAB_COMBAT)
        chatNpc(ChatAnim.NEUTRAL, MELEE_3)
        chatNpc(ChatAnim.NEUTRAL, MELEE_4)
        chatPlayer(ChatAnim.QUIZ, STYLES)
        for (line in listOf(ACCURATE, AGGRESSIVE, DEFENSIVE, CONTROLLED, ATTACK_TYPES_1, ATTACK_TYPES_2, HOVER)) chatNpc(ChatAnim.NEUTRAL, line)
        chatNpc(ChatAnim.HAPPY, MELEE_ANYTHING_ELSE)
    }

    private suspend fun Dialogue.human() = with(HarlanLines) {
        chatNpc(ChatAnim.NEUTRAL, HUMAN_1)
        chatNpc(ChatAnim.NEUTRAL, HUMAN_2)
        chatNpc(ChatAnim.NEUTRAL, HUMAN_TEAMS)
        chatPlayer(ChatAnim.HAPPY, CAPTURE_THE_FLAG)
        for (line in listOf(HUMAN_3, HUMAN_4, HUMAN_5, HUMAN_6, HUMAN_7, HUMAN_8, HUMAN_9)) chatNpc(ChatAnim.NEUTRAL, line)
        chatNpc(ChatAnim.HAPPY, HUMAN_ANYTHING_ELSE)
    }

    private suspend fun Dialogue.weaponTypes() = with(HarlanLines) {
        for (line in listOf(WEAPONS_1, WEAPONS_2, WEAPONS_3, WEAPONS_4)) chatNpc(ChatAnim.NEUTRAL, line)
        chatNpc(ChatAnim.HAPPY, WEAPONS_ANYTHING_ELSE)
    }

    private suspend fun Dialogue.trainingKit(tutor: Tutor) = with(HarlanLines) {
        val sword = owns(player, TRAINING_SWORD)
        val shield = owns(player, TRAINING_SHIELD)
        if (sword && shield) {
            chatNpc(ChatAnim.NEUTRAL, HAS_BOTH)
        } else {
            if (!sword) tutor.give(player, TRAINING_SWORD)
            if (!shield) tutor.give(player, TRAINING_SHIELD)
            when {
                !sword && !shield -> doubleobjbox(TRAINING_SWORD, TRAINING_SHIELD, text(GIVES_BOTH))
                !sword -> objbox(TRAINING_SWORD, GIVES_SWORD)
                else -> objbox(TRAINING_SHIELD, GIVES_SHIELD)
            }
            chatNpc(ChatAnim.HAPPY, THERE_YOU_GO)
        }
        chatNpc(ChatAnim.HAPPY, KIT_ANYTHING_ELSE)
    }

    private suspend fun Dialogue.buyCape(tutor: Tutor) = with(HarlanLines) {
        chatPlayer(ChatAnim.QUIZ, BUY_CAPE_ASK)
        chatNpc(ChatAnim.NEUTRAL, PRICE)
        if (choice(TOO_EXPENSIVE, HAVE_MONEY) == 1) {
            chatPlayer(ChatAnim.SHOCKED, TOO_EXPENSIVE)
            chatNpc(ChatAnim.NEUTRAL, TOO_EXPENSIVE_REPLY)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, HAVE_MONEY)
        val coins = player.inventory.count(COINS)
        when {
            coins < CAPE_PRICE -> {
                chatPlayer(ChatAnim.SAD, NO_MONEY)
                chatNpc(ChatAnim.NEUTRAL, NO_MONEY_REPLY)
            }
            player.inventory.freeSlots + (if (coins == CAPE_PRICE.toLong()) 1 else 0) < 2 -> chatNpc(ChatAnim.NEUTRAL, NO_SPACE)
            else -> {
                removeCount(player.inventory, COINS, CAPE_PRICE)
                tutor.give(player, if (otherMaxedSkill(player)) DEFENCE_CAPE_TRIMMED else DEFENCE_CAPE)
                tutor.give(player, DEFENCE_HOOD)
                // The Wiki's transcript has the cape and hood handed over with no game message.
                chatNpc(ChatAnim.HAPPY, SOLD)
            }
        }
    }

    /** A training sword or shield used on Harlan: he takes it back. */
    internal suspend fun Dialogue.giveBack(obj: Int, slot: Int) = with(HarlanLines) {
        val sword = obj == TRAINING_SWORD
        chatPlayer(ChatAnim.QUIZ, if (sword) RETURN_SWORD else RETURN_SHIELD)
        chatNpc(ChatAnim.HAPPY, if (sword) RETURN_SWORD_REPLY else RETURN_SHIELD_REPLY)
        // The item could have moved while the lines were read; take it from wherever it is now.
        if (slot in 0 until player.inventory.capacity && player.inventory[slot]?.id == obj) player.inventory.remove(slot, 1)
        else removeCount(player.inventory, obj, 1)
        objbox(obj, if (sword) RETURNED_SWORD else RETURNED_SHIELD)
    }

    fun owns(player: Player, obj: Int): Boolean = player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0

    /** Whether a skill other than Defence is at 99, which makes the cape trimmed. */
    fun otherMaxedSkill(player: Player): Boolean =
        Skill.entries.any { it != Skill.DEFENCE && player.skills.baseLevel(it) >= MAX_LEVEL }

    /** What the talk does outside the chatbox: hand over one of an item (at the player's feet if they have no room) and show a side tab. */
    class Tutor(val give: (Player, Int) -> Unit, val sideTab: (Player, Int) -> Unit) {
        constructor(world: World) : this(
            give = { player, obj ->
                world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
            },
            sideTab = { player, tab -> world.interfaces.switchSideTab(player, tab) },
        )
    }

    /** Takes [count] of [obj] from [inventory], from its first slots on. */
    private fun removeCount(inventory: Inventory, obj: Int, count: Int) {
        var left = count
        for ((slot, held) in inventory.contents()) {
            if (left == 0) break
            if (held.id == obj) left -= inventory.remove(slot, left)
        }
    }
}

/** Harlan's Talk-to and items used on him. */
fun registerCombatTutors(world: World) {
    val tutor = Harlan.Tutor(world)
    world.handlers.onNpc(Harlan.ID, Harlan.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Harlan) { talk(tutor) } }
    }
    for (obj in listOf(Harlan.TRAINING_SWORD, Harlan.TRAINING_SHIELD)) {
        world.handlers.onObjOnNpc(Harlan.ID, obj) { op ->
            world.dialogues.start(this, op.npc) { with(Harlan) { giveBack(op.obj, op.sub) } }
        }
    }
    world.handlers.onObjOnNpc(Harlan.ID) { op ->
        world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.NEUTRAL, HarlanLines.NO_USE) }
    }
}
