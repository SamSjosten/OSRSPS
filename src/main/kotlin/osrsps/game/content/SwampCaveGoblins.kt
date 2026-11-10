package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.skill.LightSources

/** The swamp caves' cave goblins' talk; the words are in the dialogue file, from the Wiki's Transcript:Cave goblin (monster). */
object CaveGoblinLines : Lines("cave_goblin", "Transcript:Cave goblin (monster), read 2026-11-10") {
    val DONT_HURT = line("dont_hurt", "the goblin's first line, the first time")
    val NOT_GOING_TO = line("not_going_to", "option 1, which the player then says")
    val SLAY_YOU_ALL = line("slay_you_all", "option 2, which the player then says")
    val FLEE_1 = line("flee_1", "after option 2: the first of the four overheads nearby goblins say")
    val FLEE_2 = line("flee_2", "after option 2: the second overhead")
    val FLEE_3 = line("flee_3", "after option 2: the third overhead")
    val FLEE_4 = line("flee_4", "after option 2: the fourth overhead")

    val FEET = line("feet", "random talk 1: the goblin")
    val FEET_REPLY = line("feet_reply", "random talk 1: the player")
    val GAS = line("gas", "random talk 2: the goblin")
    val GAS_REPLY = line("gas_reply", "random talk 2: the player")
    val HOW_ARE_YOU = line("how_are_you", "random talk 3: the player")
    val WORRIED = line("worried", "random talk 3: the goblin, first")
    val PRESENT_COMPANY = line("present_company", "random talk 3: the goblin, second")
    val WEATHER = line("weather", "random talk 4: the goblin, first")
    val WEATHER_REPLY = line("weather_reply", "random talk 4: the player")
    val ALWAYS_NICE = line("always_nice", "random talk 4: the goblin, second")
    val WHERE_FROM = line("where_from", "random talk 5: the goblin, first")
    val ABOVE_GROUND = line("above_ground", "random talk 5: the player, first")
    val WHERE_IS_THAT = line("where_is_that", "random talk 5: the goblin, second")
    val OPEN_AIR = line("open_air", "random talk 5: the player, second")
    val ICK = line("ick", "random talk 5: the goblin, third")
    val PEACE = line("peace", "random talk 6, after The Lost Tribe: the goblin, first")
    val PEACE_REPLY = line("peace_reply", "random talk 6: the player")
    val THANK_YOU = line("thank_you", "random talk 6: the goblin, second")
    val NAKED_FLAME = line("naked_flame", "random talk 7, carrying a lit naked flame: the goblin")
    val EYES = line("eyes", "random talk 8, carrying lights of brightness 2 or more: the goblin")
    val NO_LAMP = line("no_lamp", "random talk 9, carrying no light: the goblin, first")
    val SPARE_TORCH = line("spare_torch", "random talk 9: the goblin, second, as it hands over a lit torch")
    val CAREFUL = line("careful", "random talk 9: the goblin, third")
    val THANKS_A_LOT = line("thanks_a_lot", "random talk 9: the player")
    val WELCOME = line("welcome", "random talk 9: the goblin, last")
    val NO_TINDERBOX = line("no_tinderbox", "random talk 10, carrying no tinderbox: the goblin, first")
    val SPARE = line("spare", "random talk 10: the goblin, second, as it hands over a tinderbox")
    val THANKS = line("thanks", "random talk 10: the player")
}

/**
 * The Lumbridge Swamp Caves' cave goblins' Talk-to (Misthalin piece 7 step 3a), from the Wiki's transcript. The first
 * time they beg not to be hurt: promise not to, and later talks are one of their random lines; threaten them, and the
 * goblins nearby cry out. The random lines that depend on what the player carries come up only then: a lit torch for
 * a player with no light (9), a tinderbox for one with none (10), a warning about a naked flame (7) or a bright light
 * (8). The Lost Tribe counts as done, as every quest does here, so line 6 is always among them.
 *
 * Killing one breaks the promise (the Wiki: "First time talking or after killing one of them"): the next talk begs
 * again. Guessed: that the promise is remembered until logout rather than in a saved variable; that the killer is
 * whoever fought it last; the even chances among the lines that can come up; the expressions; that a gift is skipped
 * with a full backpack; that the overheads come from goblins within [CRY_RADIUS] tiles. Out: the threatened goblins
 * running away.
 */
class SwampCaveGoblins(private val world: World) {
    companion object {
        val GOBLINS = listOf(6434, 6435, 6436, 6437)
        const val TALK_TO = 1
        const val CRY_RADIUS = 8
    }

    /** Players who promised not to hurt them, until they log out. */
    private val befriended = HashSet<String>()

    internal fun befriended(player: Player) = player.username in befriended

    /** Forgets [player]'s promise as they leave the world. */
    fun forget(player: Player) {
        befriended -= player.username
    }

    /** The random talks the player can get now, by their number in the transcript. */
    internal fun talks(player: Player): List<Int> = buildList {
        addAll(1..6)
        if (LightSources.nakedFlames(player).isNotEmpty()) add(7)
        if (LightSources.brightness(player) >= 2) add(8)
        val room = player.inventory.freeSlots > 0
        if (!LightSources.hasLight(player) && room && world.objTypes[LightSources.LIT_TORCH] != null) add(9)
        if (player.inventory.count(LightSources.TINDERBOX) < 1L && room && world.objTypes[LightSources.TINDERBOX] != null) add(10)
    }

    internal suspend fun Dialogue.talk(roll: (Int) -> Int) = with(CaveGoblinLines) {
        if (!befriended(player)) {
            chatNpc(ChatAnim.SCARED, DONT_HURT)
            if (choice(NOT_GOING_TO, SLAY_YOU_ALL) == 1) {
                chatPlayer(ChatAnim.NEUTRAL, NOT_GOING_TO)
                befriended += player.username
            } else {
                chatPlayer(ChatAnim.ANGRY, SLAY_YOU_ALL)
                val cries = listOf(FLEE_1, FLEE_2, FLEE_3, FLEE_4)
                for (goblin in world.npcs.near(player.x, player.z, player.level, CRY_RADIUS)) {
                    if (goblin.type.id in GOBLINS && !goblin.outOfPlay) goblin.effects.say(text(cries[roll(cries.size)]))
                }
            }
            return
        }
        val talks = talks(player)
        when (talks[roll(talks.size)]) {
            1 -> {
                chatNpc(ChatAnim.NEUTRAL, FEET)
                chatPlayer(ChatAnim.NEUTRAL, FEET_REPLY)
            }
            2 -> {
                chatNpc(ChatAnim.NEUTRAL, GAS)
                chatPlayer(ChatAnim.NEUTRAL, GAS_REPLY)
            }
            3 -> {
                chatPlayer(ChatAnim.NEUTRAL, HOW_ARE_YOU)
                chatNpc(ChatAnim.NEUTRAL, WORRIED)
                chatNpc(ChatAnim.NEUTRAL, PRESENT_COMPANY)
            }
            4 -> {
                chatNpc(ChatAnim.HAPPY, WEATHER)
                chatPlayer(ChatAnim.CONFUSED, WEATHER_REPLY)
                chatNpc(ChatAnim.HAPPY, ALWAYS_NICE)
            }
            5 -> {
                chatNpc(ChatAnim.NEUTRAL, WHERE_FROM)
                chatPlayer(ChatAnim.NEUTRAL, ABOVE_GROUND)
                chatNpc(ChatAnim.CONFUSED, WHERE_IS_THAT)
                chatPlayer(ChatAnim.NEUTRAL, OPEN_AIR)
                chatNpc(ChatAnim.NEUTRAL, ICK)
            }
            6 -> {
                chatNpc(ChatAnim.NEUTRAL, PEACE)
                chatPlayer(ChatAnim.NEUTRAL, PEACE_REPLY)
                chatNpc(ChatAnim.HAPPY, THANK_YOU)
            }
            7 -> chatNpc(ChatAnim.NEUTRAL, NAKED_FLAME)
            8 -> chatNpc(ChatAnim.ANGRY, EYES)
            9 -> {
                chatNpc(ChatAnim.NEUTRAL, NO_LAMP)
                give(LightSources.LIT_TORCH, SPARE_TORCH)
                chatNpc(ChatAnim.NEUTRAL, CAREFUL)
                chatPlayer(ChatAnim.NEUTRAL, THANKS_A_LOT)
                chatNpc(ChatAnim.NEUTRAL, WELCOME)
            }
            else -> {
                chatNpc(ChatAnim.NEUTRAL, NO_TINDERBOX)
                give(LightSources.TINDERBOX, SPARE)
                chatPlayer(ChatAnim.NEUTRAL, THANKS)
            }
        }
    }

    /** Hands over one [obj] as the goblin says [line], when there is still room. */
    private suspend fun Dialogue.give(obj: Int, line: Line) {
        val type = world.objTypes[obj] ?: return
        if (player.inventory.freeSlots > 0) player.inventory.add(type, 1)
        chatNpc(ChatAnim.NEUTRAL, line)
    }
}

fun registerSwampCaveGoblins(world: World) {
    // Whoever fought it last killed it, and their promise is broken.
    world.npcScripts.onDeath(SwampCaveGoblins.GOBLINS) { goblin -> goblin.lastAttacker?.let(world.caveGoblins::forget) }
    for (goblin in SwampCaveGoblins.GOBLINS) {
        world.handlers.onNpc(goblin, SwampCaveGoblins.TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { with(world.caveGoblins) { talk { world.random.nextInt(it) } } }
        }
    }
}
