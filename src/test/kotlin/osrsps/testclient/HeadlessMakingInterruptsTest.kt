package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.HerbloreData
import osrsps.game.skill.Smithing
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * One artisan action at a time: making something (the Make-X box answered) ends a repeating loc action such as smelting.
 * Found by the fifth exploit review, where a scripted client ran both skills at once.
 */
class HeadlessMakingInterruptsTest {
    private val herblore = HerbloreData.load()

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val ids = Smithing.BARS.flatMap { bar -> bar.ores.map { it.first } + bar.obj } +
            herblore.potions.values.flatMap { r -> r.inputs.map { it.obj } + r.product }
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, "Obj $id", tradeable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(7),
            locTypes = LocTypes(
                mapOf(Smithing.FURNACE to LocType(Smithing.FURNACE, "Furnace", listOf(null, "Smelt", null, null, null), LocCollisionType(width = 1, length = 1))),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Smithing.FURNACE, LocShape.CENTREPIECE_STRAIGHT, 0, FURNACE_X, FURNACE_Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.said(prefix: String) = messages().filter { it.text.startsWith(prefix) }

    /**
     * Smelting stops only when it finds a box open, and a scripted client can send the use (which opens the Make-X box)
     * and its answer in one tick, so no box is open when smelting next runs. Making ends it all the same: no bar
     * after the potions start.
     */
    @Test
    fun `answering the Make-X box on the tick it opens still ends smelting`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("mallory").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel herblore 3")
                client.await("level 3 herblore") { client.stat(HERBLORE)?.takeIf { it.level >= 3 } }
                client.give(COPPER, 4)
                client.give(TIN, 4)
                client.give(GUAM_UNF, 4)
                client.give(EYE_OF_NEWT, 4)

                client.locOp(Smithing.FURNACE, FURNACE_X, FURNACE_Z, 2)
                client.await("the smelting box") { client.clientScripts().firstOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + Smithing.BARS.indexOf(Smithing.BRONZE), 4)
                client.await("the first bar", timeoutMillis = 20_000) { client.said("[smithing.smelted").firstOrNull() }

                // The use and the box's answer land in one tick.
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                val bars = client.said("[smithing.smelted").size
                val newt = client.slotOf(EYE_OF_NEWT)
                val unf = client.slotOf(GUAM_UNF)
                client.ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, newt, EYE_OF_NEWT, InventoryTab.INTERFACE, InventoryTab.ITEMS, unf, GUAM_UNF)
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 4)
                client.stepTicks(30)
                client.commandReply("resume") { it.startsWith("Resumed") }

                client.await("four potions", timeoutMillis = 20_000) { client.said("[herblore.mix").takeIf { it.size >= 4 } }
                assertTrue(bars < 4, "smelting finished before the use, $bars bars")
                assertEquals(bars, client.said("[smithing.smelted").size)
                assertEquals(bars, client.count(Smithing.BRONZE.obj))
                assertEquals(4, client.count(ATTACK_POTION))
            }
        }
    }

    private companion object {
        const val FURNACE_X = 3224
        const val FURNACE_Z = 3218
        const val BACKPACK = 93
        const val HERBLORE = 15
        const val COPPER = 436
        const val TIN = 438
        const val GUAM_UNF = 91
        const val EYE_OF_NEWT = 221
        const val ATTACK_POTION = 121
    }
}
