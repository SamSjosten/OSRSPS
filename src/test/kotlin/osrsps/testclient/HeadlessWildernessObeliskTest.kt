package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.content.WildernessObelisks
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.script.QueueType
import osrsps.game.trade.Trades
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W3.1 through the headless client: the six obelisks at their Wiki levels, Activate lighting the four
 * stones, a seeded run of teleports reaching every obelisk (its own included), two players in the 3x3 going together
 * and keeping their places, and a Tele Blocked one left behind. The stones are placed as the 242 map has them.
 */
class HeadlessWildernessObeliskTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 50) { crcs, millis ->
        val ops = listOf("Activate", "Teleport to Destination", "Set Destination", null, null)
        val types = WildernessObelisks.ALL.map { LocType(it.stone, "Obelisk", ops, LocCollisionType(width = 1, length = 1)) } +
            LocType(WildernessObelisks.GLOWING, "Obelisk", List(5) { null }, LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { for (o in WildernessObelisks.ALL) markLoaded(o.x shr 6, o.z shr 6) },
            random = GameRandom(11), locTypes = LocTypes(types.associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                for (o in WildernessObelisks.ALL) for ((x, z) in o.corners) add(ZoneLoc(o.stone, LocShape.CENTREPIECE_STRAIGHT, 0, x, z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /**
     * Activate on [from]'s stone at its centre's [dx], [dz] corner (each 2 tiles off), then, once the stones are lit, a
     * step back into the 3x3: a corner stone is reached from outside the centre, as on live.
     */
    private fun HeadlessClient.activate(world: World, from: WildernessObelisks.Obelisk, dx: Int, dz: Int, back: Coord) {
        locOp(from.stone, from.x + dx, from.z + dz, WildernessObelisks.ACTIVATE)
        await("the stones lit") { world.locs.find(WildernessObelisks.GLOWING, from.x + dx, from.z + dz, 0) }
        walk(back.x, back.z)
        await("back in the 3x3 at $back") { position?.takeIf { it == back } }
    }

    /** Which obelisk's 3x3 [at] is in. */
    private fun obeliskAt(at: Coord) = WildernessObelisks.ALL.firstOrNull { it.inCentre(at.x, at.z, at.level) }

    @Test
    fun `the six obelisks stand at the Wiki's levels`() {
        val levels = WildernessObelisks.ALL.map { Wilderness.level(it.x, it.z) }
        assertEquals(listOf(13, 19, 27, 35, 44, 50), levels)
    }

    @Test
    fun `Activate lights the stones, and seeded teleports reach every obelisk, their own included`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("otto").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                val from = WildernessObelisks.LEVEL_13
                val seen = HashMap<WildernessObelisks.Obelisk, Int>()
                repeat(24) { round ->
                    client.tele(from.x + 1, from.z - 1)
                    val before = client.messages().count { it.text == "[obelisk.teleported]" }
                    client.activate(server.world, from, 2, -2, Coord(from.x + 1, from.z - 1, 0))
                    if (round == 0) {
                        client.await("the stones lit") {
                            client.locChanges().firstOrNull { it.id == WildernessObelisks.GLOWING && it.coord == Coord(from.x + 2, from.z - 2, 0) }
                        }
                    }
                    client.await("the teleport message", timeoutMillis = 20_000) {
                        Unit.takeIf { client.messages().count { it.text == "[obelisk.teleported]" } > before }
                    }
                    val at = client.position!!
                    val to = obeliskAt(at) ?: error("landed at $at, in no obelisk")
                    // The same place in the far 3x3: one east, one south of its centre.
                    assertEquals(Coord(to.x + 1, to.z - 1, 0), at)
                    seen.merge(to, 1, Int::plus)
                }
                assertEquals(WildernessObelisks.ALL.toSet(), seen.keys, "every obelisk in 24 rolls: $seen")
            }
        }
    }

    @Test
    fun `two players in the 3x3 go together, and a Tele Blocked one stays`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("pia").use { a ->
                server.login("quin").use { b ->
                    for (client in listOf(a, b)) {
                        client.await("the login rebuild") { client.position }
                        client.commandReply("keys on") { it == "Dialogue shows keys" }
                    }
                    val from = WildernessObelisks.LEVEL_35
                    a.tele(from.x, from.z)
                    b.tele(from.x - 1, from.z + 1)
                    // Counted, not awaited as new: b's message can come in while a's is being waited for.
                    fun HeadlessClient.arrivals() = messages().count { it.text == "[obelisk.teleported]" }
                    a.activate(server.world, from, -2, 2, Coord(from.x, from.z, 0))
                    a.await("a's arrival", timeoutMillis = 20_000) { Unit.takeIf { a.arrivals() == 1 } }
                    b.await("b's arrival", timeoutMillis = 20_000) { Unit.takeIf { b.arrivals() == 1 } }
                    val to = obeliskAt(a.position!!)!!
                    assertEquals(Coord(to.x, to.z, 0), a.position)
                    b.await("b beside a") { b.position?.takeIf { it == Coord(to.x - 1, to.z + 1, 0) } }

                    // Back to the same obelisk with b Tele Blocked: a goes, b stays.
                    a.tele(from.x, from.z)
                    b.tele(from.x - 1, from.z + 1)
                    server.world.playerNamed("quin")!!.teleblockTicks = 400
                    val bSeen = b.messages().count { it.text == "[obelisk.teleported]" }
                    val aSeen = a.messages().count { it.text == "[obelisk.teleported]" }
                    a.activate(server.world, from, -2, 2, Coord(from.x, from.z, 0))
                    a.await("a's second arrival", timeoutMillis = 20_000) {
                        Unit.takeIf { a.messages().count { it.text == "[obelisk.teleported]" } > aSeen }
                    }
                    assertEquals(Coord(from.x - 1, from.z + 1, 0), b.position)
                    assertEquals(bSeen, b.messages().count { it.text == "[obelisk.teleported]" })
                    assertTrue(obeliskAt(a.position!!) != null)
                }
            }
        }
    }

    @Test
    fun `two obelisks going on one tick, one to the other, each take only their own riders`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("rhea").use { a ->
                server.login("saul").use { b ->
                    for (client in listOf(a, b)) {
                        client.await("the login rebuild") { client.position }
                        client.commandReply("keys on") { it == "Dialogue shows keys" }
                    }
                    a.tele(WildernessObelisks.LEVEL_13.x, WildernessObelisks.LEVEL_13.z)
                    b.tele(WildernessObelisks.LEVEL_19.x + 1, WildernessObelisks.LEVEL_19.z)
                    a.commandReply("pause") { it.startsWith("Paused after tick") }
                    // Set going together while the world is paused: 13 to 19, and 19 to 27.
                    assertTrue(server.world.obelisks.activate(WildernessObelisks.LEVEL_13, WildernessObelisks.LEVEL_19))
                    assertTrue(server.world.obelisks.activate(WildernessObelisks.LEVEL_19, WildernessObelisks.LEVEL_27))
                    a.stepTicks(WildernessObelisks.TELEPORT_TICKS + 1)
                    a.commandReply("resume") { it.startsWith("Resumed") }
                    val to19 = WildernessObelisks.LEVEL_19
                    val to27 = WildernessObelisks.LEVEL_27
                    a.await("a at level 19's centre") { a.position?.takeIf { it == Coord(to19.x, to19.z, 0) } }
                    b.await("b beside level 27's centre") { b.position?.takeIf { it == Coord(to27.x + 1, to27.z, 0) } }
                    assertEquals(1, a.messages().count { it.text == "[obelisk.teleported]" })
                    assertEquals(1, b.messages().count { it.text == "[obelisk.teleported]" })
                }
            }
        }
    }

    @Test
    fun `a rider's trade with a player outside the 3x3 ends, and their weak scripts drop, before the teleport`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("tess").use { a ->
                server.login("ugo").use { b ->
                    for (client in listOf(a, b)) {
                        client.await("the login rebuild") { client.position }
                        client.commandReply("keys on") { it == "Dialogue shows keys" }
                    }
                    val from = WildernessObelisks.LEVEL_35
                    a.tele(from.x, from.z + 1)
                    b.tele(from.x, from.z + 2)
                    a.playerOp(b.index, 4)
                    b.await("the request") { b.messages().firstOrNull { it.type == Trades.CHATTYPE_TRADEREQ } }
                    b.playerOp(a.index, 4)
                    a.await("trading") { a.openInterfaces().takeIf { Trades.MAIN in it } }
                    b.await("trading") { b.openInterfaces().takeIf { Trades.MAIN in it } }
                    a.commandReply("pause") { it.startsWith("Paused after tick") }
                    // A weak script under way (as a Home Teleport's cast is) doesn't carry on at the far end.
                    var dropped = false
                    server.world.scripts.queue(server.world.playerNamed("tess")!!, "obelisk_test_cast", QueueType.WEAK, 50, onDrop = { dropped = true }) {}
                    assertTrue(server.world.obelisks.activate(from, WildernessObelisks.LEVEL_50))
                    a.stepTicks(WildernessObelisks.TELEPORT_TICKS + 1)
                    a.commandReply("resume") { it.startsWith("Resumed") }
                    val to = WildernessObelisks.LEVEL_50
                    a.await("a at level 50") { a.position?.takeIf { it == Coord(to.x, to.z + 1, 0) } }
                    assertNull(server.world.playerNamed("tess")!!.trade)
                    assertTrue(dropped, "the weak script dropped by the move")
                    assertNull(server.world.playerNamed("ugo")!!.trade)
                    b.await("b's trade screen shut") { Unit.takeIf { Trades.MAIN !in b.openInterfaces() } }
                    assertEquals(Coord(from.x, from.z + 2, 0), b.position)
                }
            }
        }
    }
}
