package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.CairnIsle
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.magic.TeleportObjs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Karamja step K4.1 through the headless client: Cairn Isle's rock slide refusing below 15 Agility and climbed across
 * to the promontory, and the bridge's balance check on stepping onto it, which ends across with the balance message or
 * back on the shore with the fall's. test-runner/examples/cairn-isle.json does the same on the real map.
 */
class HeadlessCairnIsleTest {
    /** The rock slide's six tiles, as the 242 map has them. */
    private val locs = listOf(2978, 2979, 2980).flatMap { z -> listOf(ZoneLoc(ROCKS, 22, 2, 2792, z, 0), ZoneLoc(ROCKS, 22, 0, 2794, z, 0)) }

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            // Lumbridge too, where a ring of life sends its wearer.
            CollisionMap().apply { markLoaded(2794 shr 6, 2979 shr 6); markLoaded(2770 shr 6, 2979 shr 6); markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = TeleportObjs.objTypes(ObjType(RING_OF_LIFE, "Ring of life", wearpos1 = WearPos.RING, iops = listOf(null, "Wear", null, null, "Drop"), internalName = "ring_of_life")),
            locTypes = LocTypes(mapOf(ROCKS to LocType(ROCKS, "Rocks", listOf("Climb", null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0)))),
            staticLocs = StaticLocs().apply { locs.forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
    }

    @Test
    fun `the rock slide wants 15 Agility and climbs across to the promontory`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.ready()
                client.tele(2795, 2979)
                client.locOp(ROCKS, 2794, 2979, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[agility.cairn_rocks_level level=15]" } }
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                val xp = client.stat(AGILITY)!!.xp
                // 254/256 at 99 (the Wiki's Jagex chart): a fall is tried again.
                climb(client, 2794, Coord(2791, 2979, 0))
                client.await("1 XP") { client.stat(AGILITY)?.takeIf { it.xp >= xp + 1 } }
                climb(client, 2792, Coord(2795, 2979, 0))
            }
        }
    }

    private fun climb(client: HeadlessClient, x: Int, to: Coord) {
        for (attempt in 1..5) {
            val from = client.messages().size
            client.locOp(ROCKS, x, 2979, 1)
            val fell = client.await("across or a fall") {
                when {
                    client.position == to -> false
                    client.messages().drop(from).any { it.text == "[agility.cairn_rocks_fall]" } -> true
                    else -> null
                }
            }
            if (!fell) return
            client.awaitTicks(2)
        }
        throw AssertionError("five falls in a row at 99")
    }

    @Test
    fun `stepping onto the bridge checks the player's balance, across or washed back to the shore`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ben").use { client ->
                client.ready()
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                client.tele(CairnIsle.EAST_END + 1, CairnIsle.Z)
                client.walk(CairnIsle.EAST_END - 1, CairnIsle.Z)
                val text = client.await("the bridge's end") {
                    client.messages().firstOrNull { it.text == "[cairn_isle.balance]" || it.text == "[cairn_isle.fell]" }?.text
                }
                val expected = if (text == "[cairn_isle.balance]") Coord(CairnIsle.WEST_END - 1, CairnIsle.Z, 0) else Coord(CairnIsle.SHORE.x, CairnIsle.SHORE.z, 0)
                assertEquals(expected, client.await("where it ends") { client.position?.takeIf { it == expected } })
            }
        }
    }

    @Test
    fun `a run onto the bridge stops on its end, where the check starts`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cal").use { client ->
                client.ready()
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                client.tele(CairnIsle.EAST_END + 1, CairnIsle.Z)
                client.awaitTicks(2)
                val player = server.world.playerNamed("cal")!!
                player.runEnabled = true
                client.commandReply("pause") { it.contains("aused after tick") }
                client.walk(CairnIsle.EAST_END - 3, CairnIsle.Z)
                var tick = 0
                while (player.x == CairnIsle.EAST_END + 1 && tick++ < 5) client.stepTicks(1)
                assertEquals(CairnIsle.EAST_END, player.x, "the run's first tile is the bridge's end, and it stops there")
                client.commandReply("resume") { it.startsWith("Resumed") }
                client.await("the bridge's end") { client.messages().firstOrNull { it.text == "[cairn_isle.balance]" || it.text == "[cairn_isle.fell]" } }
            }
        }
    }

    @Test
    fun `a ring of life that saves a falling player keeps them in Lumbridge, not washed up on the shore`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dee").use { client ->
                client.ready()
                client.commandReply("setlevel hitpoints 99") { true }
                client.await("99 HP") { client.stat(HITPOINTS)?.takeIf { it.level == 99 } }
                // Level 1 Agility: the bridge fails about seven times in ten. A crossing is tried again.
                var saved = false
                for (attempt in 1..20) {
                    if (client.inventory(WORN).getOrNull(WearPos.RING)?.id != RING_OF_LIFE) {
                        client.command("item $RING_OF_LIFE 1")
                        val slot = client.await("the ring") { client.inventory(BACKPACK).indexOfFirst { it?.id == RING_OF_LIFE }.takeIf { it >= 0 } }
                        client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, WEAR_BUTTON_OP, slot, RING_OF_LIFE)
                        client.await("the ring worn") { client.inventory(WORN).getOrNull(WearPos.RING)?.takeIf { it.id == RING_OF_LIFE } }
                    }
                    // 12 of 99: the fall's three hits of 12 / 11 = 1 leave 9, which is 10% or less on the third.
                    client.commandReply("setlevel hitpoints 99") { true }
                    client.commandReply("hit 87 me") { it.startsWith("Hit yourself") }
                    client.tele(CairnIsle.EAST_END + 1, CairnIsle.Z)
                    // Only this crossing's messages: an earlier one's balance stays in the history.
                    val from = client.messages().size
                    client.walk(CairnIsle.EAST_END - 1, CairnIsle.Z)
                    val text = client.await("across or saved") {
                        client.messages().drop(from).firstOrNull { it.text == "[cairn_isle.balance]" || it.text == "[jewellery_effect.ring_of_life]" }?.text
                    }
                    if (text == "[cairn_isle.balance]") continue
                    val lumbridge = client.await("in Lumbridge") { client.position?.takeIf { it.x in 3219..3223 && it.z in 3217..3221 } }
                    client.awaitTicks(3)
                    assertEquals(lumbridge, client.position, "left in Lumbridge, not put on the shore")
                    assertNull(client.inventory(WORN).getOrNull(WearPos.RING))
                    saved = true
                    break
                }
                assertTrue(saved, "no fall in 20 crossings at level 1")
            }
        }
    }

    private companion object {
        const val RING_OF_LIFE = 2570
        const val BACKPACK = 93
        const val WORN = 94
        const val WEAR_BUTTON_OP = 3
        const val HITPOINTS = 3
        const val ROCKS = 2231
        const val AGILITY = 16
    }
}
