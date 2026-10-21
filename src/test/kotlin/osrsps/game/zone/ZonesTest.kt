package osrsps.game.zone

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZoneFullFollows
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialEnclosed
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialFollows
import net.rsprot.protocol.game.outgoing.zone.payload.LocAddChangeV2
import net.rsprot.protocol.game.outgoing.zone.payload.LocDel
import net.rsprot.protocol.game.outgoing.zone.payload.ObjAdd
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.Player

class ZonesTest {
    private val zones = Zones()

    // Both in Lumbridge castle's courtyard zone (402, 402); build areas start at zone (396, 396), tile (3168, 3168).
    private val alice = Player(1, "alice", 3222, 3218, 0)
    private val bob = Player(2, "bob", 3220, 3221, 0)

    private val sent = mutableListOf<OutgoingGameMessage>()

    @AfterTest
    fun release() {
        sent.filterIsInstance<UpdateZonePartialEnclosed>().forEach { it.release() }
        zones.endCycle()
    }

    /** One cycle's updates for [player]. */
    private fun update(player: Player): List<OutgoingGameMessage> {
        val out = mutableListOf<OutgoingGameMessage>()
        zones.update(player, out::add)
        sent += out
        return out
    }

    private fun cycle(vararg players: Player): List<List<OutgoingGameMessage>> {
        val result = players.map(::update)
        zones.endCycle()
        return result
    }

    @Test
    fun `a new player gets every zone in a 7x7 block wiped, centred on their own`() {
        val out = update(alice)
        val headers = out.filterIsInstance<UpdateZoneFullFollows>()
        assertEquals(49, headers.size)
        assertEquals(out.size, headers.size, "nothing in the zones yet")
        // Zone (402, 402) is 6 zones into the build area, 48 tiles; the block runs 3 zones either side.
        assertEquals((24..72 step 8).toSet(), headers.map { it.zoneX }.toSet())
        assertEquals((24..72 step 8).toSet(), headers.map { it.zoneZ }.toSet())
        assertTrue(headers.all { it.level == 0 })
        assertEquals(49, alice.zoneView.zones.size)
    }

    @Test
    fun `standing still sends nothing when nothing happens`() {
        cycle(alice)
        assertEquals(listOf(emptyList()), cycle(alice))
    }

    @Test
    fun `a graphic reaches everyone watching its zone in one shared payload, once`() {
        cycle(alice, bob)
        zones.mapAnim(3222, 3218, 0, id = 85)
        val (toAlice, toBob) = cycle(alice, bob)
        val a = toAlice.single() as UpdateZonePartialEnclosed
        val b = toBob.single() as UpdateZonePartialEnclosed
        assertEquals(listOf(48, 48, 0), listOf(a.zoneX, a.zoneZ, a.level))
        assertSame(a.payload, b.payload, "encoded once for both")
        // The first byte is the zone prot's index, which is 0 for MAP_ANIM.
        assertEquals(0, a.payload.getByte(a.payload.readerIndex()).toInt())
        assertEquals(listOf(emptyList(), emptyList()), cycle(alice, bob), "gone after its cycle")
    }

    @Test
    fun `a graphic out of view is not sent`() {
        cycle(alice)
        zones.mapAnim(3222 + 32, 3218, 0, id = 85)
        assertEquals(listOf(emptyList()), cycle(alice))
    }

    @Test
    fun `walking into the next zone wipes only the row of zones that came into view`() {
        cycle(alice)
        alice.x += 8
        val headers = cycle(alice).single().filterIsInstance<UpdateZoneFullFollows>()
        assertEquals(7, headers.size)
        assertTrue(headers.all { it.zoneX == 80 }, "the column 4 zones east of the old centre")
    }

    @Test
    fun `objs on the ground are sent to a player when their zone comes into view`() {
        val obj = ZoneObj(1351, 1, 3222 + 32, 3218, 0)
        zones.addObj(obj)
        cycle(alice)
        alice.x += 8
        val out = cycle(alice).single()
        val header = out.indexOfFirst { it is UpdateZoneFullFollows && it.zoneX == 80 && it.zoneZ == 48 }
        val add = out[header + 1] as ObjAdd
        assertEquals(listOf(1351, 1, 6, 2), listOf(add.id, add.quantity, add.xInZone, add.zInZone))
        assertEquals(1, out.count { it is ObjAdd })
    }

    @Test
    fun `an obj added in a zone that just came into view is sent once`() {
        zones.addObj(ZoneObj(995, 100, 3222, 3218, 0))
        val out = cycle(alice).single()
        assertEquals(1, out.count { it is ObjAdd })
        assertTrue(out.none { it is UpdateZonePartialEnclosed })
    }

    @Test
    fun `a private obj goes to its owner alone`() {
        cycle(alice, bob)
        zones.addObj(ZoneObj(1351, 1, 3222, 3218, 0, owner = "alice"))
        val (toAlice, toBob) = cycle(alice, bob)
        assertEquals(2, toAlice.size)
        assertTrue(toAlice[0] is UpdateZonePartialFollows)
        assertTrue(toAlice[1] is ObjAdd)
        assertEquals(emptyList(), toBob)

        alice.zoneView.reset()
        bob.zoneView.reset()
        val (fullAlice, fullBob) = cycle(alice, bob)
        assertEquals(1, fullAlice.count { it is ObjAdd })
        assertEquals(0, fullBob.count { it is ObjAdd })
    }

    @Test
    fun `a removed obj is not sent again`() {
        val obj = ZoneObj(1351, 1, 3222, 3218, 0)
        zones.addObj(obj)
        cycle(alice)
        assertTrue(zones.removeObj(obj))
        assertTrue(!zones.removeObj(obj), "already gone")
        assertTrue(zones.objsAt(3222, 3218, 0).isEmpty())
        cycle(alice)
        alice.zoneView.reset()
        assertEquals(0, cycle(alice).single().count { it is ObjAdd })
    }

    @Test
    fun `loc changes are kept and sent with the zone, one per layer`() {
        zones.addLoc(ZoneLoc(1530, shape = 0, angle = 1, 3222, 3218, 0))
        zones.addLoc(ZoneLoc(1531, shape = 0, angle = 2, 3222, 3218, 0))
        zones.removeLoc(3223, 3218, 0, shape = 10, angle = 0)
        zones.endCycle()
        val out = cycle(alice).single()
        val add = out.filterIsInstance<LocAddChangeV2>().single()
        assertEquals(listOf(1531, 0, 2), listOf(add.id, add.shape, add.rotation))
        val del = out.filterIsInstance<LocDel>().single()
        assertEquals(listOf(7, 2, 10), listOf(del.xInZone, del.zInZone, del.shape))
    }

    @Test
    fun `a map rebuild sends every zone in view again`() {
        cycle(alice)
        alice.zoneView.reset()
        assertEquals(49, cycle(alice).single().count { it is UpdateZoneFullFollows })
    }

    @Test
    fun `changing level wipes the zones on the new level`() {
        cycle(alice)
        alice.level = 1
        val headers = cycle(alice).single().filterIsInstance<UpdateZoneFullFollows>()
        assertEquals(49, headers.size)
        assertTrue(headers.all { it.level == 1 })
    }

    @Test
    fun `zones past the edge of the build area are left out`() {
        // 2 zones from the build area's west edge, where a rebuild would be due: one column of the block falls outside.
        alice.x = 3168 + 16
        val headers = cycle(alice).single().filterIsInstance<UpdateZoneFullFollows>()
        assertEquals(42, headers.size)
        assertTrue(headers.all { it.zoneX >= 0 })
    }
}
