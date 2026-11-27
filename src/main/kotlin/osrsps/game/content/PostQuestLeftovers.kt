package osrsps.game.content

import osrsps.game.InvObj
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.map.Tile
import osrsps.game.script.ScriptContext

/** Post-quest step 16's words: the Wiki's transcripts where it has them, read 2026-11-19. */
object PostQuestLeftoversLines : Lines("post_quest_leftovers", "Transcript:Broken cart (Rat Burgiss); the rest our own") {
    val CART = line("cart", "searching Rat Burgiss's broken cart (Transcript:Broken cart (Rat Burgiss))")
    val NO_BELLOWS = line("no_bellows", "sucking swamp bubbles with no ogre bellows to fill; our own line")
    val BELLOWS_FULL = line("bellows_full", "sucking swamp bubbles with every ogre bellows full; our own line")
    val BELLOWS_FILLED = line("bellows_filled", "the ogre bellows filled with swamp gas; our own line")
}

/**
 * Post-quest step 16, the leftovers sweep: what is left of the post-quest options in Misthalin once the earlier steps
 * are in, each checked on the Wiki (read 2026-11-19).
 *
 * - **Rat Burgiss's broken cart** (`surok_rat_cart` 23055, south of Varrock): Search shows the Wiki's one line (What
 *   Lies Below is over; the cart is as it was).
 * - **Swamp bubbles** (`swampbubbles` 684, Lumbridge Swamp): Suck fills every ogre bellows in the backpack to three
 *   loads of swamp gas, and using one ogre bellows on the bubbles fills that one (the Wiki's Swamp bubbles and Ogre
 *   bellows, 8 February 2018 change).
 * - **Lucien's door** (`ikov_luciendoor` 102, his house east of the Edgeville willows; Temple of Ikov is done on his side,
 *   see [EdgevillePeople]): Open lets the player through and the door shuts behind them, its open form having no
 *   Close (inferred from the cache's `ikov_luciendoor_open`, 11403).
 *
 * Guessed: the bubbles' messages, animation (`human_chompybird_ogrebellows`) and its tick, and how long Lucien's door
 * stands open. Left out, with no Wiki text to build from: the Varrock Census (Defender of Varrock's book: its 66
 * entries would need the book's screen), Varrock's study desk and shelves (2712 to 2714), the barrel 2530 and the
 * bookcases 9523, the mysterious ghosts (Curse of the Empty Lord), and Paterdomus's library (Morytania).
 */
object PostQuestLeftovers {
    const val CART = 23055
    const val SEARCH = 1
    const val SWAMP_BUBBLES = 684
    const val SUCK = 1
    const val LUCIEN_DOOR = 102
    const val LUCIEN_DOOR_OPEN = 11403
    const val OPEN = 1

    const val BELLOWS_EMPTY = 2871
    const val BELLOWS_3 = 2872
    const val BELLOWS_2 = 2873
    const val BELLOWS_1 = 2874
    val UNFILLED = listOf(BELLOWS_EMPTY, BELLOWS_1, BELLOWS_2)

    /** human_chompybird_ogrebellows. Guessed: the player's sucking animation. */
    const val SUCK_ANIM = 1026

    /** Guessed: how long Lucien's door stands open behind the player, as Draynor Manor's doors do. */
    const val DOOR_TICKS = DraynorManor.DOOR_TICKS

    /** Fills the ogre bellows in slot [only] (or every unfilled one) to three loads. */
    suspend fun ScriptContext.suck(world: World, only: Int? = null) {
        arriveDelay()
        val slots = player.inventory.contents().filter { (slot, held) -> held.id in UNFILLED && (only == null || slot == only) }.map { it.first }
        if (slots.isEmpty()) {
            val any = player.inventory.contents().any { (_, held) -> held.id == BELLOWS_3 }
            return world.message(player, if (any) PostQuestLeftoversLines.BELLOWS_FULL else PostQuestLeftoversLines.NO_BELLOWS)
        }
        player.anim(SUCK_ANIM)
        delay(1)
        for (slot in slots) {
            if (player.inventory[slot]?.id !in UNFILLED) continue
            player.inventory[slot] = InvObj(BELLOWS_3, 1)
        }
        world.message(player, PostQuestLeftoversLines.BELLOWS_FILLED)
    }
}

fun registerPostQuestLeftovers(world: World) = with(PostQuestLeftovers) {
    world.handlers.onLoc(CART, SEARCH) {
        arriveDelay()
        world.dialogues.start(this) { mesbox(PostQuestLeftoversLines.CART) }
    }
    world.handlers.onLoc(SWAMP_BUBBLES, SUCK) { suck(world) }
    for (bellows in UNFILLED + BELLOWS_3) world.handlers.onObjOnLoc(SWAMP_BUBBLES, bellows) { op -> suck(world, op.sub) }
    world.doors.addClosedType(LUCIEN_DOOR, LUCIEN_DOOR_OPEN)
    world.handlers.onLoc(LUCIEN_DOOR, OPEN) { op ->
        val door = op.loc!!
        world.doors.openAs(door, LUCIEN_DOOR_OPEN, shutAfter = DOOR_TICKS)
        val to: Tile = DraynorManor.otherSide(door, player.x, player.z)
        player.waypoints.clear()
        player.waypoints.add(to)
    }
}
