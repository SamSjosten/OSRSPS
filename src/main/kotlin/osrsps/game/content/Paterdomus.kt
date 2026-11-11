package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

private val logger = KotlinLogging.logger {}

/** Drezel's lines after every quest; from the Wiki's Transcript:Drezel. */
object DrezelLines : Lines("drezel", "Transcript:Drezel") {
    val LOST_DAGGER = line("lost_dagger", "the player, owning no wolfbane dagger")
    val NO_SPACE = line("no_space", "his reply with a full backpack: he can't hand it back")
    val WASHED_UP = line("washed_up", "his reply as he hands it back: it washed up on the Salve's banks")
    val THANKS = line("thanks", "the player's thanks for the dagger or the key")
    val LOST_KEY = line("lost_key", "the player, owning the dagger but no temple library key")
    val ANOTHER_KEY = line("another_key", "his reply as he hands the key over")
    val HOW_ARE_THINGS = line("how_are_things", "after The Blood Moon Rises, his greeting by the player's name", "name")
    val NOT_BAD = line("not_bad", "the player's reply")
    val FRIENDS = line("friends", "his unease at being friends with the vampyres")
    val THAT_OR = line("that_or", "the player: that or wiping out a whole race")
    val GOOD_POINT = line("good_point", "his reply: the King, his advisor and the Salve")
    val HAPPIER_NEWS = line("happier_news", "his news of Ivan's studies")
    val PICKY = line("picky", "the player: can't be too picky")
    val SUCCESSOR = line("successor", "his reply: Ivan will be a worthy successor")
    val UNTIL_NEXT = line("until_next", "the player's last line")
}

/** Ivan Strom's lines after The Blood Moon Rises; from the Wiki's Transcript:Ivan Strom. */
object IvanStromLines : Lines("ivan_strom", "Transcript:Ivan Strom") {
    val HEY = line("hey", "his greeting by the player's name", "name")
    val GOOD_TO_SEE = line("good_to_see", "the player's greeting")
    val HOW_ARE_THINGS = line("how_are_things", "the menu's first option, which the player then says")
    val ANOTHER_MEDALLION = line("another_medallion", "the menu's second option, which the player then says")
    val UNTIL_NEXT_TIME = line("until_next_time", "the menu's third option, which the player then says")
    val TWICE_AS_HARD = line("twice_as_hard", "option 1: Drezel works him twice as hard")
    val NOT_THAT_OLD = line("not_that_old", "option 1: Drezel isn't that old")
    val COLUMBARIUM = line("columbarium", "option 1: trips into Morytania and the old columbarium")
    val COLUMBARIUM_Q = line("columbarium_q", "option 1, the player: Columbarium?")
    val BURIAL_CHAMBER = line("burial_chamber", "option 1: the burial chamber in one of the library's books")
    val INTERESTING = line("interesting", "option 1, the player: trips into Morytania?")
    val BURGH = line("burgh", "option 1: Veliaf's new pub in Burgh de Rott")
    val SAFALAAN = line("safalaan", "option 1: visiting Safalaan")
    val EFARITAY = line("efaritay", "option 1, the player: he has Efaritay now")
    val HOMELAND = line("homeland", "option 1: Efaritay back in her old homeland")
    val HALLOWVALE = line("hallowvale", "option 1, the player: the old Hallowvale")
    val SWAMPS = line("swamps", "option 1: he'll miss the swamps (as the transcript has it)")
    val ELLIPSIS = line("ellipsis", "option 1, the player: ...")
    val GHASTS = line("ghasts", "option 1: not a fan of the ghasts?")
    val SIGH = line("sigh", "option 1, the player: see you later")
    val UNTIL_NEXT = line("until_next", "option 1: his last line, by the player's name", "name")
    val SURE = line("sure", "option 2: his reply")
    val GIVES_MEDALLION = line("gives_medallion", "option 2: the item box as he hands a medallion over")
    val SEE_YOU = line("see_you", "option 3: his last line, by the player's name", "name")
}

/** Our own lines at Paterdomus. */
object PaterdomusLines : Lines("paterdomus", "Paterdomus Temple") {
    val MORYTANIA_LATER = line("morytania_later", "the Holy barrier and the ornate railing: our own line, as Morytania waits for its own piece")
    val MEDALLION_OWNED = line("medallion_owned", "Ivan's option 2 with a medallion already owned: our own line (guessed: live opens a reclaim box)")
}

/**
 * Misthalin piece 7 step 8: Paterdomus, the temple on the River Salve, after The Blood Moon Rises. Sources: the OSRS
 * Wiki's Paterdomus Temple, Drezel, Transcript:Drezel, Ivan Strom, Transcript:Ivan Strom, Temple Guardian, Holy barrier,
 * Ornate railing, Temple library key, Trapdoor (Paterdomus Library), Bookcase (Paterdomus Library) and Bucket pages and
 * The Blood Moon Rises' page, read 2026-11-11, mejrs's NPC list and the 242 cache. The NPCs are in paterdomus.json, the
 * top floor's bucket in obj-spawns/paterdomus.tsv; the ground floor's altar is the church's ([Church]).
 *
 * - The monks of Zamorak are gone (The Blood Moon Rises clears them for good), and so is the Temple Guardian (it never
 *   comes back once Priest in Peril has it killed; its multi-NPC shows nothing after the quest's early stages).
 * - Drezel (`priestperiltrappedmonk2` 9805, showing 9636) stands in the mausoleum below. Talk-to hands back a lost
 *   wolfbane dagger (with room for it), then a lost temple library key, as the transcript has it; otherwise his talk
 *   after The Blood Moon Rises.
 * - Ivan Strom (`myq6_ivan_postquest` 15853, showing 15857) lives here after the quest. Where he stands isn't on the
 *   Wiki or in mejrs's list (older than the quest): beside Drezel is guessed. His talk is the transcript's; option 2
 *   hands over Drakan's medallion if none is owned (guessed: live opens a reclaim box).
 * - The temple's large front doors (3489, 3490) and the mausoleum's two gates (3444, 3445) have no open types in the
 *   cache: Open moves the player through to the other side (guessed).
 * - The spiral stairs up from the ground floor (61189, 61190) and down from the first (16673, 62173); the ladders to the
 *   second floor are the castle's ([LumbridgeCastle]).
 * - The trapdoor north of the temple (`trapdoor` 1579, [EdgevilleDungeon.LANDINGS]) climbs down to the mausoleum, and
 *   its ladder (17385) back up. The library's trapdoor (12762, open after In Aid of the Myreque) climbs down to the
 *   secret library, and its ladder (12764) back up.
 * - The Holy barrier into Morytania (3443) and the 65 Agility ornate railing (16552, 17000) refuse with
 *   [PaterdomusLines.MORYTANIA_LATER]: Morytania waits for the Wilderness (Sam, 2 Nov).
 *
 * Guessed: every chathead expression (the transcripts give the words, not the expressions).
 *
 * Left out (12a's sweep): the monuments, the well, the stained glass, the plinths and bookcases upstairs, the coffin,
 * the cell gate, the organ, the keyhole and the library's bookcases (no post-quest words on the Wiki, or a quest's
 * books); the east trapdoor (3432); the Temple Trekking sign.
 */
object Paterdomus {
    const val DREZEL = 9636
    const val IVAN = 15857
    const val TALK_TO = 1

    const val WOLFBANE = 2952
    const val LIBRARY_KEY = 7632
    const val MEDALLION = 22400

    /** The front doors on the temple's west wall, x 3408, z 3488 and 3489; the player goes from 3407 to 3409. */
    const val DOOR_LEFT = 3489
    const val DOOR_RIGHT = 3490

    /** The mausoleum's gates: 3444 on the south edge of 3405,9895, 3445 on the east edge of 3431,9897. */
    const val GATE_WEST = 3444
    const val GATE_EAST = 3445
    const val OPEN = 1

    const val STAIRS_UP = 61189
    const val STAIRS_UP_M = 61190
    const val STAIRS_DOWN = 16673
    const val STAIRS_DOWN_M = 62173

    /** `trapdoor` north of the temple and where climbing down puts the player, south of the ladder below (guessed). */
    val TRAPDOOR_TILE = Tile(3405, 3507)
    val TRAPDOOR_LANDING = Tile(3405, 9906)

    /** `burgh_temple_trapdoor_multiloc` and the library's ladder up. */
    const val LIBRARY_TRAPDOOR = 12762
    const val LIBRARY_LADDER = 12764
    val LIBRARY_LANDING = Tile(3361, 9903)
    val MAUSOLEUM_LANDING = Tile(3440, 9899)

    const val HOLY_BARRIER = 3443
    val RAILINGS = listOf(16552, 17000)

    /** Carried, worn or banked. */
    fun owns(player: Player, obj: Int): Boolean =
        player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0 || player.bank.count(obj) > 0

    /**
     * Where Open on a door or gate with no open type takes a player standing at [x], [z]: across [door]'s wall, to the
     * tile on its other side.
     */
    fun across(door: ZoneLoc, x: Int, z: Int): Tile = when (door.angle and 3) {
        0 -> if (x < door.x) Tile(door.x, z) else Tile(door.x - 1, z)
        1 -> if (z > door.z) Tile(x, door.z) else Tile(x, door.z + 1)
        2 -> if (x > door.x) Tile(door.x, z) else Tile(door.x + 1, z)
        else -> if (z < door.z) Tile(x, door.z) else Tile(x, door.z - 1)
    }

    internal suspend fun Dialogue.drezel(give: (Player, Int) -> Unit) = with(DrezelLines) {
        if (!owns(player, WOLFBANE)) {
            chatPlayer(ChatAnim.SAD, LOST_DAGGER)
            if (player.inventory.freeSlots == 0) return chatNpc(ChatAnim.NEUTRAL, NO_SPACE)
            give(player, WOLFBANE)
            chatNpc(ChatAnim.HAPPY, WASHED_UP)
            chatPlayer(ChatAnim.HAPPY, THANKS)
            return
        }
        if (!owns(player, LIBRARY_KEY)) {
            chatPlayer(ChatAnim.SAD, LOST_KEY)
            give(player, LIBRARY_KEY)
            chatNpc(ChatAnim.HAPPY, ANOTHER_KEY)
            chatPlayer(ChatAnim.HAPPY, THANKS)
            return
        }
        chatNpc(ChatAnim.HAPPY, HOW_ARE_THINGS, "name" to player.username)
        chatPlayer(ChatAnim.HAPPY, NOT_BAD)
        chatNpc(ChatAnim.NEUTRAL, FRIENDS)
        chatPlayer(ChatAnim.NEUTRAL, THAT_OR)
        chatNpc(ChatAnim.NEUTRAL, GOOD_POINT)
        chatNpc(ChatAnim.HAPPY, HAPPIER_NEWS)
        chatPlayer(ChatAnim.NEUTRAL, PICKY)
        chatNpc(ChatAnim.HAPPY, SUCCESSOR)
        chatPlayer(ChatAnim.HAPPY, UNTIL_NEXT)
    }

    internal suspend fun Dialogue.ivan(give: (Player, Int) -> Unit) = with(IvanStromLines) {
        val name = "name" to player.username
        chatNpc(ChatAnim.HAPPY, HEY, name)
        chatPlayer(ChatAnim.HAPPY, GOOD_TO_SEE)
        when (choice(HOW_ARE_THINGS, ANOTHER_MEDALLION, UNTIL_NEXT_TIME)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, HOW_ARE_THINGS)
                chatNpc(ChatAnim.NEUTRAL, TWICE_AS_HARD)
                chatNpc(ChatAnim.HAPPY, NOT_THAT_OLD)
                chatNpc(ChatAnim.NEUTRAL, COLUMBARIUM)
                chatPlayer(ChatAnim.QUIZ, COLUMBARIUM_Q)
                chatNpc(ChatAnim.HAPPY, BURIAL_CHAMBER)
                chatPlayer(ChatAnim.QUIZ, INTERESTING)
                chatNpc(ChatAnim.HAPPY, BURGH)
                chatNpc(ChatAnim.SAD, SAFALAAN)
                chatPlayer(ChatAnim.NEUTRAL, EFARITAY)
                chatNpc(ChatAnim.NEUTRAL, HOMELAND)
                chatPlayer(ChatAnim.HAPPY, HALLOWVALE)
                chatNpc(ChatAnim.SAD, SWAMPS)
                chatPlayer(ChatAnim.NEUTRAL, ELLIPSIS)
                chatNpc(ChatAnim.QUIZ, GHASTS)
                chatPlayer(ChatAnim.NEUTRAL, SIGH)
                chatNpc(ChatAnim.HAPPY, UNTIL_NEXT, name)
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, ANOTHER_MEDALLION)
                chatNpc(ChatAnim.HAPPY, SURE)
                if (owns(player, Paterdomus.MEDALLION)) return chatNpc(ChatAnim.NEUTRAL, PaterdomusLines.MEDALLION_OWNED)
                give(player, Paterdomus.MEDALLION)
                objbox(Paterdomus.MEDALLION, GIVES_MEDALLION)
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, UNTIL_NEXT_TIME)
                chatNpc(ChatAnim.HAPPY, SEE_YOU, name)
            }
        }
    }
}

/** A door or gate with no open type: the player goes through to its other side. */
private suspend fun ScriptContext.throughWall(world: World, door: ZoneLoc) {
    arriveDelay()
    val to = Paterdomus.across(door, player.x, player.z)
    world.teleport(player, to.x, to.z, door.level)
}

/** A trapdoor or ladder to [to] on level 0, with [anim]. */
private suspend fun ScriptContext.climbTo(world: World, to: Tile, anim: Int) {
    arriveDelay()
    player.anim(anim)
    delay(1)
    world.teleport(player, to.x, to.z, 0)
}

/** Drezel, Ivan, the doors and gates, the stairs, the trapdoors and ladders, and the ways into Morytania. */
fun registerPaterdomus(world: World) {
    with(Paterdomus) {
        val give: (Player, Int) -> Unit = { player, obj ->
            world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
        }
        world.handlers.onNpc(DREZEL, TALK_TO) { op -> world.dialogues.start(this, op.npc) { drezel(give) } }
        world.handlers.onNpc(IVAN, TALK_TO) { op -> world.dialogues.start(this, op.npc) { ivan(give) } }
        for (door in listOf(DOOR_LEFT, DOOR_RIGHT, GATE_WEST, GATE_EAST)) {
            world.handlers.onLoc(door, OPEN) { op -> throughWall(world, op.loc!!) }
        }
        for (stairs in listOf(STAIRS_UP, STAIRS_UP_M)) world.handlers.onLoc(stairs, 1) { op -> climbStairs(world, op.loc!!, 1) }
        for (stairs in listOf(STAIRS_DOWN, STAIRS_DOWN_M)) world.handlers.onLoc(stairs, 1) { op -> climbStairs(world, op.loc!!, -1) }
        world.handlers.onLoc(LIBRARY_TRAPDOOR, 1) { climbTo(world, LIBRARY_LANDING, Climbing.ANIM_PICKUP_FLOOR) }
        world.handlers.onLoc(LIBRARY_LADDER, 1) { climbTo(world, MAUSOLEUM_LANDING, Climbing.ANIM_REACH_FOR_LADDER) }
        for (way in listOf(HOLY_BARRIER) + RAILINGS) world.handlers.onLoc(way, 1) { world.message(player, PaterdomusLines.MORYTANIA_LATER) }
    }
}
