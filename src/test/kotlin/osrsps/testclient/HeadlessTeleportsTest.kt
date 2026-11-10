package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.skill.magic.Teleports
import osrsps.game.skill.magic.TeleportObjs
import osrsps.map.CollisionMap

/**
 * The standard teleports and their tablets through the headless client, with the real teleports file and objs shaped
 * as the cache has them ([TeleportObjs]): Varrock Teleport takes its runes, gives 35 XP and lands in Varrock's square
 * [Teleports.CAST_TICKS] ticks after the click; too low a level or too few runes casts nothing; a staff of air stands
 * in for the air runes; Ape Atoll Teleport takes its banana; a Lumbridge tablet lands in Lumbridge's plus with no XP
 * and is used up; and above level 20 Wilderness neither a spell nor a tablet works, and nothing is taken. The rl-client
 * script test-runner/examples/magic-teleports.json casts and breaks them in the real client.
 */
class HeadlessTeleportsTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val collision = CollisionMap().apply {
            markLoaded(3222 shr 6, 3218 shr 6)
            markLoaded(3213 shr 6, 3424 shr 6)
            markLoaded(WILDERNESS.x shr 6, WILDERNESS.z shr 6)
        }
        val objTypes = TeleportObjs.objTypes()
        World(crcs, collision, random = GameRandom(7), objTypes = objTypes, spells = Spells.load(objTypes, osrsps.game.SeqNames.NONE, osrsps.game.SpotanimNames.NONE), cycleMillis = millis)
    }

    private fun HeadlessClient.cast(component: Int) = ifButton(Spells.SPELLBOOK, component, Teleports.CAST)

    private fun HeadlessClient.count(id: Int): Int = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.teleTo(at: Coord) {
        commandReply("tele ${at.x} ${at.z}") { it.startsWith("Teleported") }
        await("the teleport") { position?.takeIf { it == at } }
    }

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = count(id)
        command("item $id $count")
        await("$count of item $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.magicXp(): Int = stat(MAGIC)?.xp ?: 0

    /** Sets Magic to [level] and waits for the client's stat to show it, so the XP read next is after the change. */
    private fun HeadlessClient.magicLevel(level: Int) {
        commandReply("setlevel magic $level") { it.contains("set to level $level") }
        await("Magic $level") { stat(MAGIC)?.takeIf { it.level == level } }
    }

    @Test
    fun `Varrock Teleport takes its runes, gives its XP and lands in the square 3 ticks on, and refuses without the level or runes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.teleTo(START)
                with(TeleportObjs) {
                    client.give(LAW, 1)
                    client.give(AIR, 3)
                    client.give(FIRE, 1)

                    // Level 24: one short.
                    client.magicLevel(24)
                    client.cast(VARROCK_COMPONENT)
                    client.awaitMessage("the level refusal") { it == "[combat.magic_level]" }
                    client.awaitTicks(4)
                    assertEquals(START, client.position)
                    assertEquals(1, client.count(LAW))

                    client.magicLevel(25)
                    val xpBefore = client.magicXp()
                    val animsBefore = client.animationTicks().size
                    val ticksAtCast = client.ticksSeen
                    client.cast(VARROCK_COMPONENT)
                    val landed = client.await("the landing in Varrock") { client.position?.takeIf { it.x in 3211..3215 && it.z in 3422..3426 } }
                    val after = client.ticksSeen - ticksAtCast
                    // The click is read in the next tick's input; the landing shows CAST_TICKS ticks after that.
                    check(after in Teleports.CAST_TICKS..Teleports.CAST_TICKS + 2) { "landed $after ticks after the click" }
                    assertEquals(0, landed.level)
                    assertTrue(client.animationTicks().size > animsBefore, "the cast animates")
                    assertEquals(listOf(0, 0, 0), listOf(client.count(LAW), client.count(AIR), client.count(FIRE)), "the runes are gone")
                    assertEquals(xpBefore + 35, client.magicXp())

                    // No runes left: nothing happens but the message.
                    client.cast(VARROCK_COMPONENT)
                    client.awaitMessage("the runes refusal") { it.startsWith("[combat.not_enough_runes") }
                    client.awaitTicks(4)
                    assertEquals(landed, client.position)
                }
            }
        }
    }

    @Test
    fun `a staff of air stands in for air runes, and Ape Atoll Teleport takes its banana`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.magicLevel(99)
                client.teleTo(START)
                with(TeleportObjs) {
                    client.give(STAFF_OF_AIR, 1)
                    client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, WIELD_BUTTON_OP, client.slotOf(STAFF_OF_AIR), STAFF_OF_AIR)
                    client.await("the staff wielded") { if (client.count(STAFF_OF_AIR) == 0) Unit else null }
                    client.give(LAW, 3)
                    // Camelot's map isn't loaded here, so the landing is the area's centre, the Wiki's tile.
                    client.cast(CAMELOT_COMPONENT)
                    client.await("the landing at Camelot") { client.position?.takeIf { it == Coord(2757, 3479) } }
                    assertEquals(2, client.count(LAW), "one law rune, and the staff's air")

                    client.teleTo(START)
                    client.give(WATER, 2)
                    client.give(FIRE, 2)
                    client.cast(APE_ATOLL_COMPONENT)
                    client.awaitMessage("the banana refusal") { it.startsWith("[combat.not_enough_runes") }
                    client.awaitTicks(4)
                    assertEquals(START, client.position)
                    client.give(BANANA, 1)
                    client.cast(APE_ATOLL_COMPONENT)
                    client.await("the landing on Ape Atoll") { client.position?.takeIf { it == Coord(2798, 2799) } }
                    assertEquals(listOf(0, 0, 0, 0), listOf(client.count(LAW), client.count(WATER), client.count(FIRE), client.count(BANANA)))
                }
            }
        }
    }

    @Test
    fun `a tablet lands in Lumbridge's plus with no XP and is used up, and nothing works above level 20 Wilderness`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                with(TeleportObjs) {
                    client.teleTo(WILDERNESS)
                    client.give(LUMBRIDGE_TABLET, 2)
                    client.give(LAW, 1)
                    client.give(AIR, 3)
                    client.give(FIRE, 1)
                    client.magicLevel(25)
                    val messagesBefore = client.messages().size
                    client.cast(VARROCK_COMPONENT)
                    client.await("the spell's two Wilderness lines") {
                        client.messages().drop(messagesBefore).map { it.text }.takeIf { "[home_teleport.wilderness]" in it && "[home_teleport.wilderness_2]" in it }
                    }
                    client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, BREAK_BUTTON_OP, client.slotOf(LUMBRIDGE_TABLET), LUMBRIDGE_TABLET)
                    client.awaitTicks(6)
                    assertEquals(WILDERNESS, client.position)
                    assertEquals(listOf(2, 1, 3, 1), listOf(client.count(LUMBRIDGE_TABLET), client.count(LAW), client.count(AIR), client.count(FIRE)), "nothing taken")
                    assertEquals(2, client.messages().count { it.text == "[home_teleport.wilderness]" }, "the tablet refused too")

                    // Level 20 is still allowed: the tablet works from its top row.
                    client.teleTo(Coord(WILDERNESS.x, LEVEL_20_TOP))
                    val xpBefore = client.magicXp()
                    val ticksAtBreak = client.ticksSeen
                    client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, BREAK_BUTTON_OP, client.slotOf(LUMBRIDGE_TABLET), LUMBRIDGE_TABLET)
                    val landed = client.await("the landing in Lumbridge") { client.position?.takeIf { it.z < 3300 } }
                    val after = client.ticksSeen - ticksAtBreak
                    check(after in Teleports.TABLET_TICKS..Teleports.TABLET_TICKS + 2) { "landed $after ticks after the click" }
                    assertTrue((landed.x in 3219..3223 && landed.z in 3218..3219) || (landed.x in 3221..3222 && landed.z in 3216..3220), "in the plus: $landed")
                    assertEquals(1, client.count(LUMBRIDGE_TABLET))
                    assertEquals(xpBefore, client.magicXp(), "no XP from a tablet")
                }
            }
        }
    }

    private companion object {
        val START = Coord(3222, 3218)

        /** Level 23 Wilderness, north of Edgeville: (3700 - 3520) / 8 + 1. */
        val WILDERNESS = Coord(3100, 3700)

        /** The last row of level 20: z 3520 + 20 * 8 - 1. */
        const val LEVEL_20_TOP = 3679

        const val BACKPACK = 93
        const val MAGIC = 6

        /** Break is iop1 and Wield iop2, which the inventory sends as button ops 2 and 3. */
        const val BREAK_BUTTON_OP = 2
        const val WIELD_BUTTON_OP = 3
    }
}
