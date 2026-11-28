package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.BarbarianAnvil
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * The barbarian anvil through the headless client: Smith without a hammer says so, with a bronze bar, logs and a hammer
 * it offers the bronze spear and hasta, and the hasta chosen comes for 25 XP; an iron bar and oak logs below level 20
 * give the level message. The rl-client script test-runner/examples/misthalin-fixes-10.json does the same on the live cache.
 */
class HeadlessBarbarianAnvilTest {
    private val bronze = BarbarianAnvil.METALS[0]
    private val iron = BarbarianAnvil.METALS[1]

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val ids = listOf(BarbarianAnvil.HAMMER, bronze.bar, bronze.logs, bronze.spear, bronze.hasta, iron.bar, iron.logs, iron.spear, iron.hasta)
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6) },
            objTypes = ObjTypes.of(*ids.map { ObjType(it, "item $it") }.toTypedArray()),
            locTypes = LocTypes(mapOf(BarbarianAnvil.ANVIL to LocType(BarbarianAnvil.ANVIL, "Barbarian anvil", listOf("Smith", null, null, null, null), LocCollisionType(width = 1, length = 1)))),
            staticLocs = StaticLocs().apply { add(ZoneLoc(BarbarianAnvil.ANVIL, LocShape.CENTREPIECE_STRAIGHT, 3, X, Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.give(id: Int) {
        val before = count(id)
        command("item $id 1")
        await("item $id") { if (count(id) > before) Unit else null }
    }

    private fun HeadlessClient.seen(text: String, from: Int) = await(text) { messages().drop(from).firstOrNull { it.text == text } }

    @Test
    fun `the anvil makes a spear or hasta from a bar and its logs with a hammer, at the bar's level`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${X + 1} $Z")
                client.await("by the anvil") { client.position?.takeIf { it.x == X + 1 && it.z == Z } }
                client.locOp(BarbarianAnvil.ANVIL, X, Z, BarbarianAnvil.SMITH)
                client.seen("[smithing.no_hammer]", 0)
                client.give(BarbarianAnvil.HAMMER)
                var from = client.messages().size
                client.locOp(BarbarianAnvil.ANVIL, X, Z, BarbarianAnvil.SMITH)
                client.seen("[smithing.no_bars]", from)

                client.give(bronze.bar)
                client.give(bronze.logs)
                client.give(iron.bar)
                client.give(iron.logs)
                client.command("setlevel smithing 10")
                client.await("level 10") { client.stat(Skill.SMITHING.ordinal)?.takeIf { it.level == 10 } }
                val xp = client.stat(Skill.SMITHING.ordinal)!!.xp
                client.locOp(BarbarianAnvil.ANVIL, X, Z, BarbarianAnvil.SMITH)
                val box = client.await("the Make-X box") { client.clientScripts().lastOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }
                for (id in listOf(bronze.spear, bronze.hasta, iron.spear, iron.hasta)) assertTrue(id in box.args, "the box shows $id: ${box.args}")
                from = client.messages().size
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 1, 1)
                client.seen("[barbarian_anvil.made item=item ${bronze.hasta}]", from)
                // The message can reach the client a moment before the inventory update of the same tick.
                assertEquals(1, client.await("the hasta") { client.count(bronze.hasta).takeIf { it > 0 } })
                assertEquals(0, client.count(bronze.bar) + client.count(bronze.logs))
                assertEquals(xp + 25, client.await("the XP") { client.stat(Skill.SMITHING.ordinal)?.takeIf { it.xp > xp } }.xp)

                from = client.messages().size
                val boxes = client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }
                client.locOp(BarbarianAnvil.ANVIL, X, Z, BarbarianAnvil.SMITH)
                client.await("the Make-X box again") { if (client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } > boxes) Unit else null }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.seen("[smithing.anvil_level level=20 item=item ${iron.spear}]", from)
                assertEquals(1, client.count(iron.bar))
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val X = 3082
        const val Z = 3411
    }
}
