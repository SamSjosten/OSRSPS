package osrsps.game.content

import osrsps.game.InvObj
import osrsps.game.NpcType
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** Gillie Groats' lines; the words are in the dialogue file, from the Wiki's Transcript:Gillie Groats, read 2026-10-29. */
object GillieLines : Lines("gillie_groats", "Transcript:Gillie Groats") {
    val HELLO = line("hello", "Gillie's first line, asking what she can do")
    val WHO = line("who", "option 1")
    val NAME = line("name", "option 1: Gillie says who she is and what she does")
    val SPARE = line("spare", "option 1: the player asks for milk")
    val NOT_SPARE = line("not_spare", "option 1: Gillie has none to spare")
    val THANKS = line("thanks", "option 1: the player's last line")
    val HOW = line("how", "option 2")
    val HOW_GET = line("how_get", "option 2: the player asks how milk is got")
    val BUCKET = line("bucket", "option 2: Gillie, first you need a bucket")
    val DAIRY = line("dairy", "option 2: Gillie, then find a dairy cow")
    val FIND = line("find", "option 2: the player asks how to find one")
    val SPOT = line("spot", "option 2: Gillie, how they look")
    val NEAR = line("near", "option 2: Gillie, a couple in this field")
    val MILK_IT = line("milk_it", "option 2: Gillie's last line")
    val FINE = line("fine", "option 3, which the player then says")
    val TEE_HEE = line("tee_hee", "milking a dairy cow with no bucket: Gillie's first line")
    val HOW_TELL = line("how_tell", "milking with no bucket: the player")
    val SPILLING = line("spilling", "milking with no bucket: Gillie, the milk is spilling")
    val GUESSED = line("guessed", "milking with no bucket: the player")
    val CITY = line("city", "milking with no bucket: Gillie, try again with a bucket")
    val DO_THAT = line("do_that", "milking with no bucket: the player's last line")
}

/** Milking's messages, RSMod's, not checked against live. */
object DairyCowLines : Lines("dairy_cow", "Dairy cow") {
    val MILKED = line("milked", "each bucket filled")
    val NO_BUCKET = line("no_bucket", "Milk with no bucket, on a cow outside Gillie's pen")
    val NOT_WANTED = line("not_wanted", "anything but a bucket used on a dairy cow")
}

/**
 * Groats' farm, north of Lumbridge east of the River Lum: Gillie Groats the milkmaid (4628) and the two dairy cows in
 * her pen. The farm's beef cows are in npc-spawns/lumbridge.json.
 *
 * Milking is RSMod's dairy cow script: an empty bucket, then two ticks later a bucket of milk, the milkit animation and
 * a message, again every eight ticks while buckets are left, until the player does something else. With no
 * bucket, a dairy cow in Gillie's pen gets her talk about it and any other dairy cow a message. Guessed: Gillie's
 * chathead expressions outside RSMod's no-bucket talk. Left out: Steal-cowbell (Thieving, and Cold War's cowbells) and
 * Gillie's talk about a lost cowbell amulet, both Cold War's.
 */
object GroatsFarm {
    const val GILLIE = 4628
    const val TALK_TO = 1

    /** The name her lines show under when she speaks up about milking with no bucket (Transcript:Gillie Groats). */
    const val GILLIE_MILKMAID = "Gillie Groats the Milkmaid"

    /** fat_cow and fat_cow_fawest: the 241 cache's dairy cows, which are locs. */
    val DAIRY_COWS = listOf(8689, 60788)
    const val MILK = 1

    const val BUCKET = 1925
    const val BUCKET_OF_MILK = 1927

    /** milkit. */
    const val ANIM_MILK = 2305

    /** Ticks from the click to the first bucket, and between buckets after it (RSMod). */
    const val FIRST_MILK_TICKS = 2
    const val MILK_TICKS = 8

    /** The dairy cows in Gillie's pen (the 241 map). */
    val GILLIES_COWS = setOf(3252 to 3275, 3253 to 3271)

    internal suspend fun Dialogue.gillie() = with(GillieLines) {
        chatNpc(ChatAnim.HAPPY, HELLO)
        when (choice(WHO, HOW, FINE)) {
            1 -> {
                chatNpc(ChatAnim.HAPPY, NAME)
                chatPlayer(ChatAnim.QUIZ, SPARE)
                chatNpc(ChatAnim.NEUTRAL, NOT_SPARE)
                chatPlayer(ChatAnim.NEUTRAL, THANKS)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, HOW_GET)
                chatNpc(ChatAnim.HAPPY, BUCKET)
                chatNpc(ChatAnim.HAPPY, DAIRY)
                chatPlayer(ChatAnim.QUIZ, FIND)
                chatNpc(ChatAnim.HAPPY, SPOT)
                chatNpc(ChatAnim.HAPPY, NEAR)
                chatNpc(ChatAnim.HAPPY, MILK_IT)
            }
            else -> chatPlayer(ChatAnim.NEUTRAL, FINE)
        }
    }

    /** Gillie, from wherever she is, tells off a player milking with no bucket: RSMod's expressions. */
    internal suspend fun Dialogue.noBucket(gillie: NpcType) = with(GillieLines) {
        chatNpc(gillie, GILLIE_MILKMAID, ChatAnim.LAUGH, TEE_HEE)
        chatPlayer(ChatAnim.QUIZ, HOW_TELL)
        chatNpc(gillie, GILLIE_MILKMAID, ChatAnim.LAUGH, SPILLING)
        chatPlayer(ChatAnim.NEUTRAL, GUESSED)
        chatNpc(gillie, GILLIE_MILKMAID, ChatAnim.LAUGH, CITY)
        chatPlayer(ChatAnim.NEUTRAL, DO_THAT)
    }
}

/** Gillie's Talk-to, and Milk and items used on the dairy cows. */
fun registerGroatsFarm(world: World) {
    world.handlers.onNpc(GroatsFarm.GILLIE, GroatsFarm.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(GroatsFarm) { gillie() } }
    }
    for (cow in GroatsFarm.DAIRY_COWS) {
        world.handlers.onLoc(cow, GroatsFarm.MILK) { op -> milk(world, op.loc!!) }
        world.handlers.onObjOnLoc(cow, GroatsFarm.BUCKET) { op -> milk(world, op.loc!!) }
        world.handlers.onObjOnLoc(cow) { world.message(player, DairyCowLines.NOT_WANTED) }
    }
}

private suspend fun ScriptContext.milk(world: World, cow: ZoneLoc) {
    arriveDelay()
    if (player.inventory.count(GroatsFarm.BUCKET) == 0L) {
        if (cow.x to cow.z in GroatsFarm.GILLIES_COWS) {
            val gillie = world.npcTypes[GroatsFarm.GILLIE] ?: return
            world.dialogues.start(this) { with(GroatsFarm) { noBucket(gillie) } }
        } else {
            world.message(player, DairyCowLines.NO_BUCKET)
        }
        return
    }
    queue(MILK_QUEUE, QueueType.WEAK, GroatsFarm.FIRST_MILK_TICKS) { milkOnce(world) }
}

/** One bucket filled, and the next queued: a weak queue, so walking off or clicking anything else stops it. */
private fun ScriptContext.milkOnce(world: World) {
    val slot = player.inventory.contents().firstOrNull { it.second.id == GroatsFarm.BUCKET }?.first ?: return
    player.inventory[slot] = InvObj(GroatsFarm.BUCKET_OF_MILK, 1)
    world.message(player, DairyCowLines.MILKED)
    player.anim(GroatsFarm.ANIM_MILK)
    queue(MILK_QUEUE, QueueType.WEAK, GroatsFarm.MILK_TICKS) { milkOnce(world) }
}

private const val MILK_QUEUE = "milk_cow"
