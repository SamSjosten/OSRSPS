package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerOps
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.map.CollisionMap

/**
 * Wilderness step W1.7 through two headless clients: a player killed by another keeps their three most valuable items
 * (none skulled), the tradeable rest drops where they fell for the killer alone, and the untradeable rest goes to the
 * grave at level 20 or below, while above it one that can't be worn is kept and one that can is gone.
 */
class HeadlessPvpDeathTest {
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val coins = ObjType(995, "Coins", cost = 1, stackable = true, tradeable = true)
    private val sword = ObjType(1277, "Bronze sword", cost = 26, tradeable = true)
    private val bones = ObjType(526, "Bones", cost = 1, tradeable = true)
    private val lobster = ObjType(379, "Lobster", cost = 150, tradeable = true)
    private val cape = ObjType(6570, "Fire cape", wearpos1 = WearPos.CAPE)
    private val book = ObjType(757, "Book")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3222 shr 6, 3218 shr 6)
            for (sz in (3456 shr 6)..(3967 shr 6)) markLoaded(3090 shr 6, sz)
        }
        World(crcs, map, objTypes = ObjTypes.of(coins, sword, bones, lobster, cape, book), npcTypes = NpcTypes.of(grave), cycleMillis = millis)
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /** [victim] carrying the test's items at level [z]'s Wilderness, on 1 hitpoint, killed by one sure punch from [killer]. */
    private fun TestServer.kill(killer: HeadlessClient, victim: HeadlessClient, victimName: String, z: Int, skulled: Boolean = false) {
        killer.await("the login rebuild") { killer.position }
        victim.await("the login rebuild") { victim.position }
        // Both at combat level 3, so even level 5 has them in range: one point of hitpoints left, and a sure punch.
        killer.commandReply("sure max") { it.startsWith("Your attacks") }
        victim.commandReply("hit 9 me") { it.startsWith("Hit yourself") }
        for (command in listOf("item 379", "item 1277", "item 995 10", "item 526 2", "item 6570", "item 757")) victim.command(command)
        victim.await("the bag filled") { victim.inventory(BACKPACK).takeIf { it.count { obj -> obj != null } == 7 } }
        victim.command("varp 172 1")
        if (skulled) world.playerNamed(victimName)!!.skullTicks = 500
        killer.tele(3090, z)
        victim.tele(3091, z)
        killer.await("Attack given") { Unit.takeIf { killer.playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
        killer.await("the victim in view") { killer.otherPlayers().takeIf { it.isNotEmpty() } }
        killer.playerOp(victim.index, PlayerOps.ATTACK)
        victim.await("[combat.dead]") { victim.messages().firstOrNull { it.text == "[combat.dead]" } }
        victim.await("the respawn", timeoutMillis = 10_000) { victim.position?.takeIf { it == Coord(3222, 3218) } }
    }

    private fun TestServer.groundFor(name: String, z: Int) =
        world.groundObjs.visibleAt(world.playerNamed(name)!!, 3091, z, 0).map { InvObj(it.id, it.count) }.sortedBy { it.id }

    @Test
    fun `above level 20 the killer gets the tradeables, an untradeable that can be worn is gone, one that can't is kept`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kai").use { killer ->
                server.login("lou").use { victim ->
                    server.kill(killer, victim, "lou", LEVEL_25)
                    victim.await("the kept items") { victim.inventory(BACKPACK).filterNotNull().takeIf { it.size == 4 } }
                    assertEquals(
                        listOf(InvObj(lobster.id, 1), InvObj(sword.id, 1), InvObj(coins.id, 1), InvObj(book.id, 1)),
                        victim.inventory(BACKPACK).filterNotNull(),
                        "the three most valuable, and the book, which can't be worn",
                    )
                    assertEquals(listOf(InvObj(bones.id, 1), InvObj(bones.id, 1), InvObj(coins.id, 9)), server.groundFor("kai", LEVEL_25))
                    assertEquals(emptyList(), server.groundFor("lou", LEVEL_25), "the killer's alone for now")
                    assertNull(server.world.deaths.graveOf(server.world.playerNamed("lou")!!), "nothing for a grave above 20")
                }
            }
        }
    }

    @Test
    fun `skulled, nothing is kept, and the skull goes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("max").use { killer ->
                server.login("ned").use { victim ->
                    server.kill(killer, victim, "ned", LEVEL_25, skulled = true)
                    victim.awaitTicks(2)
                    assertEquals(listOf(InvObj(book.id, 1)), victim.inventory(BACKPACK).filterNotNull())
                    assertTrue(InvObj(lobster.id, 1) in server.groundFor("max", LEVEL_25))
                    assertEquals(0, server.world.playerNamed("ned")!!.skullTicks)
                }
            }
        }
    }

    @Test
    fun `at level 20 or below the untradeables go to the grave`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("oli").use { killer ->
                server.login("pam").use { victim ->
                    server.kill(killer, victim, "pam", LEVEL_5)
                    val graveItems = server.world.deaths.graveOf(server.world.playerNamed("pam")!!)!!.items.contents().map { InvObj(it.second.id, it.second.count) }
                    assertEquals(setOf(InvObj(cape.id, 1), InvObj(book.id, 1)), graveItems.toSet())
                    assertEquals(listOf(InvObj(bones.id, 1), InvObj(bones.id, 1), InvObj(coins.id, 9)), server.groundFor("oli", LEVEL_5))
                }
            }
        }
    }

    private companion object {
        const val LEVEL_25 = 3520 + 24 * 8
        const val LEVEL_5 = 3520 + 4 * 8
        const val BACKPACK = 93
    }
}
