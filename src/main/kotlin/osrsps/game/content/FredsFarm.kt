package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.script.ScriptContext

/** Fred the Farmer's lines; the words are in the dialogue file, from the Wiki's Transcript:Fred the Farmer (after Sheep Shearer), read 2026-10-29. */
object FredLines : Lines("fred_the_farmer", "Transcript:Fred the Farmer") {
    val MY_LAND = line("my_land", "Fred's first line, asking what the player is doing on his land")
    val KILL = line("kill", "option 1, which the player then says")
    val LIVESTOCK = line("livestock", "option 1: Fred's answer, leave his livestock alone")
    val LOST = line("lost", "option 2, which the player then says")
    val ROAD = line("road", "option 2: Fred's answer, the road to Lumbridge")
}

/**
 * Fred's farm, north-west of Lumbridge Castle: Fred the Farmer (732) and his sheep to shear. Its chickens and rams only
 * fight and drop (npc-combat.json, npc-drops.json); its eggs and shears are item spawns (obj-spawns/lumbridge.tsv), and
 * the onion patch by his house is one of the [Crops].
 *
 * Shearing is RSMod's sheep script: shears in the inventory, the human_shearing animation, two ticks, then the sheep
 * shows as its sheared type for 50 ticks (the OSRS Wiki, Sheep: 30 seconds to regrow) and the player gets a wool,
 * dropped at their feet when the inventory is full. Since 27 October 2019 it never fails (the Wiki). The messages are
 * RSMod's and the 2004Scape project's, not checked against live. Guessed: Fred's chathead expressions. Left out: Fred's
 * third option, about The Thing of Cold War, which isn't here, and the sheep's overhead bleat.
 */
object FredsFarm {
    const val FRED = 732
    const val TALK_TO = 1

    const val SHEAR = 1
    const val SHEARS = 1735
    const val WOOL = 1737

    /** human_shearing. */
    const val ANIM_SHEAR = 893

    /** How long a sheared sheep takes to grow its wool back (OSRS Wiki, Sheep). */
    const val REGROW_TICKS = 50

    /** Each sheep with wool, by NPC type id, and the type it shows as once sheared (RSMod's SheepNpcEditor). */
    val SHEARED: Map<Int, Int> = mapOf(
        2693 to 1178, // sheepunsheered
        2696 to 1301, // sheepunsheered2
        2699 to 1304, // sheepunsheered3
        2694 to 1299, // sheepunsheeredg
        2697 to 1302, // sheepunsheered2g
        2786 to 1308, // sheepunsheered3g
        2695 to 1300, // sheepunsheeredw
        2698 to 1303, // sheepunsheered2w
        2787 to 1309, // sheepunsheered3w
        2788 to 2691, // sheepunsheeredshaggy
        2789 to 2692, // sheepunsheeredshaggy2
    )

    const val NO_SHEARS = "You need a set of shears to do this."
    const val GOT_WOOL = "You get some wool."
    const val ALREADY_SHORN = "This sheep has already been shorn."

    internal suspend fun Dialogue.fred() = with(FredLines) {
        chatNpc(ChatAnim.ANGRY, MY_LAND)
        when (choice(KILL, LOST)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, KILL)
                chatNpc(ChatAnim.ANGRY, LIVESTOCK)
            }
            else -> {
                chatPlayer(ChatAnim.SAD, LOST)
                chatNpc(ChatAnim.CONFUSED, ROAD)
            }
        }
    }
}

/** Fred's Talk-to, and Shear and shears on the sheep. */
fun registerFredsFarm(world: World) {
    world.handlers.onNpc(FredsFarm.FRED, FredsFarm.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(FredsFarm) { fred() } }
    }
    for ((sheep, sheared) in FredsFarm.SHEARED) {
        world.handlers.onNpc(sheep, FredsFarm.SHEAR) { op -> shear(world, op.npc!!, sheared) }
        world.handlers.onObjOnNpc(sheep, FredsFarm.SHEARS) { op -> shear(world, op.npc!!, sheared) }
        // A sheared sheep has no Shear option; shears used on one say so.
        world.handlers.onObjOnNpc(sheared, FredsFarm.SHEARS) { player.message(FredsFarm.ALREADY_SHORN) }
    }
}

private suspend fun ScriptContext.shear(world: World, sheep: Npc, sheared: Int) {
    if (player.inventory.count(FredsFarm.SHEARS) == 0L) {
        player.message(FredsFarm.NO_SHEARS)
        return
    }
    player.anim(FredsFarm.ANIM_SHEAR)
    sheep.face(player)
    delay(2)
    // Another player may have sheared it meanwhile, or it may have gone.
    if (sheep.dead || sheep.transformedTo != null || world.npcs[sheep.index] !== sheep) return
    val type = world.npcTypes[sheared] ?: return
    world.npcs.transform(sheep, type, FredsFarm.REGROW_TICKS)
    player.message(FredsFarm.GOT_WOOL)
    world.objTypes[FredsFarm.WOOL]?.let { world.groundObjs.give(player, it, 1) }
}
