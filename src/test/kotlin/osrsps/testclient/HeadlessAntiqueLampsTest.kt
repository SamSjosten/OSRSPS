package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.AntiqueLamps
import osrsps.game.content.DestroyItem
import osrsps.map.CollisionMap

/**
 * The diary lamps through the headless client: Rub sets the screen's level and skill vars before opening it, a skill
 * confirmed gives the lamp's XP exactly and takes the lamp, a skill below the lamp's level (a forged slot) gives
 * nothing and keeps it, and Destroy asks first, with No keeping the lamp and Yes taking it.
 */
class HeadlessAntiqueLampsTest {
    private val lampIops = listOf("Rub", null, null, null, "Destroy")
    private val objs = ObjTypes.of(
        *AntiqueLamps.Lamp.entries.map { ObjType(it.obj, "Antique lamp", iops = lampIops) }.toTypedArray(),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, cycleMillis = millis)
    }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).count { it?.id == id }

    private fun HeadlessClient.rub(lamp: AntiqueLamps.Lamp) {
        ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, RUB_OP, slotOf(lamp.obj), lamp.obj)
        await("the skill screen") { Unit.takeIf { AntiqueLamps.SCREEN in openInterfaces() } }
    }

    private fun HeadlessClient.xp(skill: Skill) = stat(skill.id)?.xp ?: 0

    @Test
    fun `a confirmed skill gets the lamp's xp and the lamp goes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.command("setlevel mining 30")
                client.command("item ${AntiqueLamps.Lamp.EASY.obj} 1")
                client.slotOf(AntiqueLamps.Lamp.EASY.obj)
                client.rub(AntiqueLamps.Lamp.EASY)
                assertEquals(30, client.varp(AntiqueLamps.VARP_LEVEL))
                assertEquals(AntiqueLamps.ALL_SKILLS_MASK, client.varp(AntiqueLamps.VARP_SKILLS))
                val before = client.xp(Skill.MINING)
                client.resumePause(AntiqueLamps.SCREEN, AntiqueLamps.SCREEN_UNIVERSE, AntiqueLamps.SLOT_SKILLS.indexOf(Skill.MINING))
                client.await("the XP") { Unit.takeIf { client.xp(Skill.MINING) == before + 2_500 } }
                client.await("the lamp gone") { Unit.takeIf { client.count(AntiqueLamps.Lamp.EASY.obj) == 0 } }
                assertTrue(client.messages().any { it.text.startsWith("[antique_lamp.granted") })
                assertTrue(AntiqueLamps.SCREEN !in client.openInterfaces())
            }
        }
    }

    @Test
    fun `a skill below the lamp's level gets nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("item ${AntiqueLamps.Lamp.ELITE.obj} 1")
                client.slotOf(AntiqueLamps.Lamp.ELITE.obj)
                client.rub(AntiqueLamps.Lamp.ELITE)
                val before = client.xp(Skill.ATTACK)
                client.resumePause(AntiqueLamps.SCREEN, AntiqueLamps.SCREEN_UNIVERSE, AntiqueLamps.SLOT_SKILLS.indexOf(Skill.ATTACK))
                client.await("the refusal") { Unit.takeIf { client.messages().any { it.text.startsWith("[antique_lamp.cant") } } }
                assertEquals(before, client.xp(Skill.ATTACK))
                assertEquals(1, client.count(AntiqueLamps.Lamp.ELITE.obj))
            }
        }
    }

    @Test
    fun `destroy asks first`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                val lamp = AntiqueLamps.Lamp.HARD
                client.command("item ${lamp.obj} 1")
                fun destroy() {
                    client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, DESTROY_OP, client.slotOf(lamp.obj), lamp.obj)
                    client.await("the destroy box") { Unit.takeIf { client.clientScripts().count { it.id == DestroyItem.INIT_SCRIPT } > 0 } }
                    client.await("its buttons") { Unit.takeIf { DestroyItem.BOX in client.openInterfaces() } }
                }
                destroy()
                client.resumePause(DestroyItem.BOX, DestroyItem.BOX_UNIVERSE, DestroyItem.NO)
                client.await("the box closed") { Unit.takeIf { DestroyItem.BOX !in client.openInterfaces() } }
                assertEquals(1, client.count(lamp.obj))
                client.forgetPackets()
                destroy()
                client.resumePause(DestroyItem.BOX, DestroyItem.BOX_UNIVERSE, DestroyItem.YES)
                client.await("the lamp gone") { Unit.takeIf { client.count(lamp.obj) == 0 } }
            }
        }
    }

    private companion object {
        /** The inventory's button ops for iop1 (Rub) and iop5 (Destroy) ([InventoryTab]). */
        const val RUB_OP = 2
        const val DESTROY_OP = 7
    }
}
