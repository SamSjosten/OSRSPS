package osrsps.game.zone

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZoneFullFollows
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialEnclosed
import net.rsprot.protocol.game.outgoing.zone.payload.LocAddChangeV2
import net.rsprot.protocol.game.outgoing.zone.payload.LocDel
import net.rsprot.protocol.game.outgoing.zone.payload.MapAnim
import net.rsprot.protocol.game.outgoing.zone.payload.ObjAdd
import net.rsprot.protocol.game.outgoing.zone.payload.ObjCount
import net.rsprot.protocol.game.outgoing.zone.payload.ObjDel
import net.rsprot.protocol.message.OutgoingGameMessage
import net.rsprot.protocol.message.ZoneProt
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

    /** One cycle's zone updates for each of [players], worked out but not encoded. */
    private fun cycle(vararg players: Player): List<List<ZoneUpdate>> {
        val result = players.map(zones::collect)
        zones.endCycle()
        return result
    }

    /** One cycle's packets for each of [players]. */
    private fun send(vararg players: Player): List<List<OutgoingGameMessage>> {
        val result = players.map { player -> mutableListOf<OutgoingGameMessage>().also { zones.update(player, it::add) } }
        result.forEach(sent::addAll)
        zones.endCycle()
        return result
    }

    private fun List<ZoneUpdate>.prots(): List<ZoneProt> = flatMap { it.shared + it.own }

    @Test
    fun `a new player gets every zone in a 7x7 block wiped, centred on their own`() {
        val updates = cycle(alice).single()
        assertEquals(49, updates.size)
        assertTrue(updates.all { it.full && it.zone.level == 0 })
        assertTrue(updates.prots().isEmpty(), "nothing in the zones yet")
        // Zone (402, 402) is 6 zones into the build area, 48 tiles; the block runs 3 zones either side.
        assertEquals((24..72 step 8).toSet(), updates.map { it.zoneX }.toSet())
        assertEquals((24..72 step 8).toSet(), updates.map { it.zoneZ }.toSet())
        assertEquals(49, alice.zoneView.zones.size)
    }

    @Test
    fun `standing still sends nothing when nothing happens`() {
        cycle(alice)
        assertEquals(listOf(emptyList()), cycle(alice))
    }

    @Test
    fun `a graphic reaches everyone watching its zone`() {
        cycle(alice, bob)
        zones.mapAnim(3222, 3218, 0, id = 85)
        for (updates in cycle(alice, bob)) {
            val update = updates.single()
            assertEquals(listOf(48, 48, false), listOf(update.zoneX, update.zoneZ, update.full))
            assertEquals(85, (update.shared.single() as MapAnim).id)
        }
        assertEquals(listOf(emptyList(), emptyList()), cycle(alice, bob), "gone after its cycle")
    }

    @Test
    fun `a graphic is sent in one payload, encoded once for everyone`() {
        send(alice, bob)
        zones.mapAnim(3222, 3218, 0, id = 85)
        val (toAlice, toBob) = send(alice, bob)
        val a = toAlice.single() as UpdateZonePartialEnclosed
        val b = toBob.single() as UpdateZonePartialEnclosed
        assertEquals(listOf(48, 48, 0), listOf(a.zoneX, a.zoneZ, a.level))
        assertSame(a.payload, b.payload)
        // The first byte is the zone prot's index, which is 0 for MAP_ANIM.
        assertEquals(0, a.payload.getByte(a.payload.readerIndex()).toInt())
    }

    @Test
    fun `a zone whose shared updates fail to encode is skipped for everyone watching, not thrown at each`() {
        val failing = Zones { error("can't encode") }
        failing.update(alice) {}
        failing.update(bob) {}
        failing.endCycle()
        failing.mapAnim(3222, 3218, 0, id = 85)
        val toAlice = mutableListOf<OutgoingGameMessage>().also { failing.update(alice, it::add) }
        val toBob = mutableListOf<OutgoingGameMessage>().also { failing.update(bob, it::add) }
        failing.endCycle()
        assertTrue(toAlice.isEmpty() && toBob.isEmpty(), "nothing sent, and nobody thrown at")
    }

    @Test
    fun `objs, locs and projectiles only ever go out inside a zone header`() {
        // RSProt throws on ObjAdd, ObjDel and projectiles queued on their own, and the client drops area sounds.
        zones.addObj(ZoneObj(1351, 1, 3222 + 32, 3218, 0))
        zones.addObj(ZoneObj(995, 5, 3222 + 32, 3218, 0, owner = "alice"))
        zones.addLoc(ZoneLoc(1530, shape = 0, angle = 1, 3222 + 32, 3218, 0))
        send(alice)
        zones.addObj(ZoneObj(1351, 1, 3222, 3218, 0, owner = "alice"))
        zones.projectile(Projectile(91, 3222, 3218, 0, 3222, 3223, 172, 124, 30, 65, 16, 64))
        zones.soundArea(3222, 3218, 0, id = 2727)
        val packets = send(alice).single().toMutableList()
        alice.x += 8
        packets += send(alice).single()
        assertTrue(packets.isNotEmpty())
        assertTrue(packets.all { it is UpdateZoneFullFollows || it is UpdateZonePartialEnclosed }, packets.toString())
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
        val updates = cycle(alice).single()
        assertEquals(7, updates.size)
        assertTrue(updates.all { it.full && it.zoneX == 80 }, "the column 4 zones east of the old centre")
    }

    @Test
    fun `objs on the ground are sent to a player when their zone comes into view`() {
        zones.addObj(ZoneObj(1351, 1, 3222 + 32, 3218, 0))
        cycle(alice)
        alice.x += 8
        val updates = cycle(alice).single()
        val zone = updates.single { it.zoneX == 80 && it.zoneZ == 48 }
        val add = zone.own.single() as ObjAdd
        assertEquals(listOf(1351, 1, 6, 2), listOf(add.id, add.quantity, add.xInZone, add.zInZone))
        assertEquals(1, updates.prots().size)
    }

    @Test
    fun `an obj added in a zone that just came into view is sent once`() {
        zones.addObj(ZoneObj(995, 100, 3222, 3218, 0))
        val prots = cycle(alice).single().prots()
        assertEquals(1, prots.count { it is ObjAdd })
        assertEquals(1, prots.size)
    }

    @Test
    fun `a private obj goes to its owner alone`() {
        cycle(alice, bob)
        zones.addObj(ZoneObj(1351, 1, 3222, 3218, 0, owner = "alice"))
        val (toAlice, toBob) = cycle(alice, bob)
        val update = toAlice.single()
        assertTrue(update.shared.isEmpty())
        assertTrue(update.own.single() is ObjAdd)
        assertEquals(emptyList(), toBob)

        alice.zoneView.reset()
        bob.zoneView.reset()
        val (fullAlice, fullBob) = cycle(alice, bob)
        assertEquals(1, fullAlice.prots().count { it is ObjAdd })
        assertEquals(0, fullBob.prots().count { it is ObjAdd })
    }

    @Test
    fun `a removed obj is not sent again`() {
        val obj = ZoneObj(1351, 1, 3222, 3218, 0)
        zones.addObj(obj)
        cycle(alice, bob)
        assertTrue(zones.removeObj(obj))
        assertFalse(zones.removeObj(obj), "already gone")
        assertTrue(zones.objsAt(3222, 3218, 0).isEmpty())
        assertTrue(cycle(bob).single().single().shared.single() is ObjDel)
        alice.zoneView.reset()
        assertEquals(0, cycle(alice).single().prots().count { it is ObjAdd })
    }

    @Test
    fun `loc changes are kept and sent with the zone, one per layer`() {
        zones.addLoc(ZoneLoc(1530, shape = 0, angle = 1, 3222, 3218, 0))
        zones.addLoc(ZoneLoc(1531, shape = 0, angle = 2, 3222, 3218, 0))
        zones.removeLoc(3223, 3218, 0, shape = 10, angle = 0)
        zones.endCycle()
        val prots = cycle(alice).single().prots()
        val add = prots.filterIsInstance<LocAddChangeV2>().single()
        assertEquals(listOf(1531, 0, 2), listOf(add.id, add.shape, add.rotation))
        val del = prots.filterIsInstance<LocDel>().single()
        assertEquals(listOf(7, 2, 10), listOf(del.xInZone, del.zInZone, del.shape))
    }

    @Test
    fun `a map rebuild sends every zone in view again`() {
        cycle(alice)
        alice.zoneView.reset()
        assertEquals(49, cycle(alice).single().count { it.full })
    }

    @Test
    fun `changing level wipes the zones on the new level`() {
        cycle(alice)
        alice.level = 1
        val updates = cycle(alice).single()
        assertEquals(49, updates.size)
        assertTrue(updates.all { it.full && it.zone.level == 1 })
    }

    @Test
    fun `zones past the edge of the build area are left out`() {
        // 2 zones from the build area's west edge, where a rebuild would be due: one column of the block falls outside.
        alice.x = 3168 + 16
        val updates = cycle(alice).single()
        assertEquals(42, updates.size)
        assertTrue(updates.all { it.zoneX >= 0 })
    }

    @Test
    fun `a public obj someone dropped tells each client whose it is`() {
        cycle(alice, bob)
        zones.addObj(ZoneObj(1351, 1, 3222, 3218, 0, droppedBy = "alice", ticksUntilDespawn = 50))
        val (toAlice, toBob) = cycle(alice, bob).map { updates -> updates.single().own.single() as ObjAdd }
        assertEquals(listOf(Zones.OWNERSHIP_SELF, 50), listOf(toAlice.ownershipType, toAlice.timeUntilDespawn))
        assertEquals(listOf(Zones.OWNERSHIP_OTHER, 50), listOf(toBob.ownershipType, toBob.timeUntilDespawn))
    }

    @Test
    fun `revealing a private obj tells everyone but its owner`() {
        val obj = ZoneObj(995, 5, 3222, 3218, 0, owner = "alice", ticksUntilPublic = 3)
        zones.addObj(obj)
        cycle(alice, bob)
        zones.revealObj(obj)
        val (toAlice, toBob) = cycle(alice, bob)
        assertEquals(emptyList(), toAlice)
        val add = toBob.single().own.single() as ObjAdd
        assertEquals(listOf(995, 5, Zones.OWNERSHIP_OTHER, 0), listOf(add.id, add.quantity, add.ownershipType, add.timeUntilPublic))
        assertTrue(obj.visibleTo("bob"))
    }

    @Test
    fun `a stack's new count goes to whoever can see it`() {
        val private = ZoneObj(995, 5, 3222, 3218, 0, owner = "alice")
        val public = ZoneObj(995, 7, 3223, 3218, 0)
        zones.addObj(private)
        zones.addObj(public)
        cycle(alice, bob)
        zones.setObjCount(private, 8)
        zones.setObjCount(public, 9)
        val (toAlice, toBob) = cycle(alice, bob)
        assertEquals(listOf(5 to 8), toAlice.single().own.map { (it as ObjCount).oldQuantity to it.newQuantity })
        assertEquals(listOf(7 to 9), toBob.single().shared.map { (it as ObjCount).oldQuantity to it.newQuantity })
        assertTrue(toBob.single().own.isEmpty())
    }
}
