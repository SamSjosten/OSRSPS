package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** The Odd Old Man's lines after Rag and Bone Man II; from the Wiki's Transcript:Odd Old Man. */
object OddOldManLines : Lines("odd_old_man", "Transcript:Odd Old Man") {
    val REPLACE_HELM = line("replace_helm", "the player, owning no ram skull helm: asks for a replacement helm")
    val REPLACE_BONESACK = line("replace_bonesack", "the player, owning the helm but no bonesack: asks for a replacement bonesack")
    val PLENTY = line("plenty", "his reply to either: he has plenty")
    val THANKS = line("thanks", "the player's thanks, as the helm or bonesack is handed over")
    val SORTING = line("sorting", "the player, owning both: asks how the sorting is going")
    val GOING_WELL = line("going_well", "his reply: the 'sorting' is going very well")
    val GLAD = line("glad", "the player: glad to hear it")
    val MUMBLING = line("mumbling", "the player asks where the mumbling was coming from")
    val ERRR = line("errr", "his reply")
    val SACK_MUMBLE = line("sack_mumble", "the sack on his back mumbles")
    val WHAT_MUMBLING = line("what_mumbling", "his denial")
    val LIE_DOWN = line("lie_down", "his last line: the player is hearing things")
    val SACK_NAME = line("sack_name", "the name over the sack's chathead (the cache names the NPC Bones; the transcript calls it Sack)")
}

/** The wish-list beside the Odd Old Man (Wiki, Wish-list), and our own line for Zemouregal's base. */
object SilvareaLines : Lines("silvarea", "Wish-list") {
    val WISH_LIST = line("wish_list", "Read on the Odd Old Man's wish-list after Rag and Bone Man II: the one wish left")
    val BASE_LATER = line("base_later", "Open or Enter on the trapdoor to Zemouregal's base: our own line, as the base isn't built yet")
}

/**
 * Misthalin piece 7 step 7: Silvarea, the valley between Varrock's east fence and the River Salve. Sources: the OSRS
 * Wiki's Silvarea, Bat, Limestone rock, Odd Old Man, Transcript:Odd Old Man, Wish-list and Zemouregal's base pages,
 * read 2026-11-11, mejrs's NPC list and the 242 cache. The NPCs are in silvarea.json; the bats fight from
 * npc-combat.json and npc-drops.json; the limestone rocks are [osrsps.game.skill.Mining]'s.
 *
 * - The Odd Old Man (`rag_odd_old_man` 1259) by his pot-boiler: Rag and Bone Man II is done on every account, and live
 *   lets a player who chose one reward get the other as well, so every account is taken to have both (inferred). Talk-to
 *   hands back a lost ram skull helm, then a lost bonesack, one a talk; owning both (carried, worn or banked), he talks
 *   about his "sorting" and the sack on his back mumbles, as the transcript has it.
 * - The wish-list (14010) reads its one wish left after the quest, in a message box (the box guessed).
 * - The trapdoor to Zemouregal's base (`dov_base_entry` 50689, a multiloc showing Enter after Defender of Varrock)
 *   refuses with [SilvareaLines.BASE_LATER] until the base and its armoured zombies are built.
 * - The members' gate in Varrock's east fence (11766 and 11767) opens both leaves together.
 *
 * Left out: the Giantsoul amulet trade-in (Odd Old Man), the easy STASH, Elias White (at the Jolly Boar Inn or Uzer
 * once his quests are done, Wiki), the hunting trail plants and the pot-boiler (no options once the quests are done).
 */
object Silvarea {
    const val ODD_OLD_MAN = 1259
    const val TALKING_SACK = 1266
    const val TALK_TO = 1

    const val RAM_SKULL_HELM = 7917
    const val BONESACK = 7918

    const val WISH_LIST = 14010
    const val READ = 1

    /** `dov_base_entry`: the map's multiloc, which shows `dov_base_entry_open_op` (Enter) once the quest is done. */
    const val BASE_ENTRY = 50689
    const val BASE_OP = 1

    /** `fai_varrock_member_gatel` and `_gater`, and their open types. */
    const val GATE_LEFT = 11766
    const val GATE_LEFT_OPEN = 11770
    const val GATE_RIGHT = 11767
    const val GATE_RIGHT_OPEN = 11771

    /** Carried, worn or banked. */
    fun owns(player: Player, obj: Int): Boolean =
        player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0 || player.bank.count(obj) > 0

    /** The reward [player] has lost and is owed again: the helm first, then the bonesack; null when they own both. */
    fun owed(player: Player): Int? = when {
        !owns(player, RAM_SKULL_HELM) -> RAM_SKULL_HELM
        !owns(player, BONESACK) -> BONESACK
        else -> null
    }

    // Guessed: every chathead expression here (the transcript gives the words, not the expressions).
    internal suspend fun Dialogue.oddOldMan(world: World, give: (Player, Int) -> Unit) = with(OddOldManLines) {
        val owed = owed(player)
        if (owed != null) {
            chatPlayer(ChatAnim.QUIZ, if (owed == RAM_SKULL_HELM) REPLACE_HELM else REPLACE_BONESACK)
            chatNpc(ChatAnim.HAPPY, PLENTY)
            give(player, owed)
            chatPlayer(ChatAnim.HAPPY, THANKS)
            return
        }
        chatPlayer(ChatAnim.QUIZ, SORTING)
        chatNpc(ChatAnim.HAPPY, GOING_WELL)
        chatPlayer(ChatAnim.HAPPY, GLAD)
        chatPlayer(ChatAnim.QUIZ, MUMBLING)
        chatNpc(ChatAnim.NEUTRAL, ERRR)
        val sack = world.npcTypes[TALKING_SACK]
        if (sack != null) chatNpc(sack, text(SACK_NAME), ChatAnim.NEUTRAL, SACK_MUMBLE) else chatNpc(ChatAnim.NEUTRAL, SACK_MUMBLE)
        chatNpc(ChatAnim.NEUTRAL, WHAT_MUMBLING)
        chatNpc(ChatAnim.NEUTRAL, LIE_DOWN)
    }
}

/** The Odd Old Man, his wish-list, the trapdoor to Zemouregal's base and the members' gate. */
fun registerSilvarea(world: World) {
    with(Silvarea) {
        val give: (Player, Int) -> Unit = { player, obj ->
            world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
        }
        world.handlers.onNpc(ODD_OLD_MAN, TALK_TO) { op -> world.dialogues.start(this, op.npc) { oddOldMan(world, give) } }
        world.handlers.onLoc(WISH_LIST, READ) { world.dialogues.start(this) { mesbox(SilvareaLines.WISH_LIST) } }
        world.handlers.onLoc(BASE_ENTRY, BASE_OP) { world.message(player, SilvareaLines.BASE_LATER) }
        world.doors.registerDouble(GATE_LEFT, GATE_LEFT_OPEN, GATE_RIGHT, GATE_RIGHT_OPEN)
    }
}
