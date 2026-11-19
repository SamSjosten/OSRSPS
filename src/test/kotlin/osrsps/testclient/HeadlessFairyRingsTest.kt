package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.Destination
import osrsps.game.content.FairyRingCode
import osrsps.game.content.FairyRingCodes
import osrsps.game.content.FairyRingLines
import osrsps.game.content.FairyRings
import osrsps.game.content.LumbridgeSwamp
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * The fairy rings (travel networks step T1) through the headless client: the staff check, the dials (each click turns the
 * dial's varbit as the client does), Confirm to the code they spell, the Zanaris and Last-destination options, a code
 * that leads nowhere, and the creature that can come through; and the travel log beside the dials (step T2), with its
 * favourites and the rings' Favourites option. The codes are a few rows as the 242 cache's `fairyring`
 * table has them; the rl-client script test-runner/examples/fairy-rings.json does the same at Edgeville in the real
 * client, with the cache's table.
 */
class HeadlessFairyRingsTest {
    // The log's lines are stand-ins: the cache's are Jagex's words.
    private val dkr = FairyRingCode(122, 26, "DKR", Destination(3129, 3496, 0), description = "<br>dkr line", textComponent = 50, faveIconComponent = 51)
    private val bks = FairyRingCode(321, 57, "BKS", Destination(2412, 4434, 0), description = "<br>bks line", textComponent = 60, faveIconComponent = 61)
    private val dis = FairyRingCode(101, 17, "DIS", Destination(3108, 3149, 0), description = "<br>dis line", textComponent = 40, faveIconComponent = 41)
    private val dlr = FairyRingCode(112, 22, "DLR", Destination(2213, 3099, 0), apparition = BADGER, noStaffReturn = true)
    private val aip = FairyRingCode(0, 0, "AIP", null)
    private val codes = FairyRingCodes(listOf(dkr, bks, dis, dlr, aip))

    private fun ring(code: FairyRingCode, id: Int = FairyRings.MINOR_HUB) =
        ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, 0, code.destination!!.x, code.destination!!.z, 0)

    private fun server(dir: Path, seed: Long = 7) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val rings = listOf(ring(dkr), ring(bks, FairyRings.HOME_HUB), ring(dis), ring(dlr))
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                for (loc in rings) markLoaded(loc.x shr 6, loc.z shr 6)
                markLoaded(FairyRings.INVALID_LANDING.x shr 6, FairyRings.INVALID_LANDING.z shr 6)
            },
            random = GameRandom(seed),
            objTypes = ObjTypes.of(
                ObjType(LumbridgeSwamp.DRAMEN_STAFF, "Dramen staff", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, internalName = "dramen_staff"),
            ),
            npcTypes = NpcTypes.of(NpcType(BADGER, "Badger")),
            locTypes = LocTypes(
                listOf(
                    LocType(FairyRings.MINOR_HUB, "Fairy ring", listOf("Zanaris", "Configure", "Last-destination", "Favourites", null), LocCollisionType()),
                    LocType(FairyRings.HOME_HUB, "Fairy ring", listOf(null, "Configure", "Last-destination", "Favourites", null), LocCollisionType()),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { rings.forEach(::add) },
            varTypes = VarTypes(
                6_000,
                mapOf(
                    FairyRings.DIALS[0] to VarbitType(DIALS_VARP, 0, 1),
                    FairyRings.DIALS[1] to VarbitType(DIALS_VARP, 2, 3),
                    FairyRings.DIALS[2] to VarbitType(DIALS_VARP, 4, 5),
                    FairyRings.VARBIT_LASTLOC to VarbitType(HISTORY_VARP, 0, 5),
                    FairyRings.VARBIT_LASTLOC_SET to VarbitType(HISTORY_VARP, 6, 6),
                    FairyRings.VARBIT_FAVOURITE_OPVIS to VarbitType(DIALS_VARP, 6, 17),
                    FairyRings.VISITED.getValue(dis.id) to VarbitType(VISITED_VARP, 0, 0),
                    FairyRings.VISITED.getValue(dkr.id) to VarbitType(VISITED_VARP, 1, 1),
                    FairyRings.VISITED.getValue(bks.id) to VarbitType(VISITED_VARP, 2, 2),
                ) + FairyRings.FAVES.withIndex().associate { (slot, varbit) ->
                    varbit to VarbitType(FAVES_VARP + slot / 4, slot % 4 * 7, slot % 4 * 7 + 6)
                },
            ),
            fairyRingCodes = codes,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /** Waits for the landing at [to], then for the appearance to end: the player's clicks are ignored until it has. */
    private fun HeadlessClient.awaitAt(to: Destination) {
        await("at $to", timeoutMillis = 10_000) { position?.takeIf { it == Coord(to.x, to.z, to.level) } }
        awaitTicks(FairyRings.APPEAR_TICKS + 1)
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.wieldStaff() {
        command("item ${LumbridgeSwamp.DRAMEN_STAFF}")
        val slot = await("the staff in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == LumbridgeSwamp.DRAMEN_STAFF }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, WIELD_BUTTON, slot, LumbridgeSwamp.DRAMEN_STAFF)
        await("the staff worn") { inventory(WORN).firstOrNull { it?.id == LumbridgeSwamp.DRAMEN_STAFF } }
    }

    /** Beside [code]'s ring, one tile east: the ring itself blocks walking on live. */
    private fun HeadlessClient.besideRing(code: FairyRingCode) = tele(code.destination!!.x + 1, code.destination!!.z)

    private fun HeadlessClient.openDials(code: FairyRingCode) {
        locOp(FairyRings.MINOR_HUB.takeIf { code != bks } ?: FairyRings.HOME_HUB, code.destination!!.x, code.destination!!.z, FairyRings.CONFIGURE)
        await("the dials") { if (FairyRings.INTERFACE in openInterfaces()) Unit else null }
        await("Confirm clickable") { if (opEnabled(FairyRings.INTERFACE, FairyRings.CONFIRM, -1, 1)) Unit else null }
    }

    private fun HeadlessClient.dials(): Triple<Int, Int, Int> = varp(DIALS_VARP).let { Triple(it and 3, (it ushr 2) and 3, (it ushr 4) and 3) }

    /** Turns each dial clockwise until the three spell [target]'s id. */
    private fun HeadlessClient.dialTo(target: FairyRingCode) {
        val want = Triple(target.id / 100, target.id / 10 % 10, target.id % 10)
        repeat(3) { dial ->
            val wanted = want.toList()[dial]
            while (dials().toList()[dial] != wanted) {
                val before = dials().toList()[dial]
                ifButton(FairyRings.INTERFACE, FairyRings.CLOCKWISE[dial], 1)
                await("dial ${dial + 1} turned") { if (dials().toList()[dial] == (before + 1) % 4) Unit else null }
            }
        }
    }

    @Test
    fun `without a staff every option refuses, and the dials don't open`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.besideRing(dkr)
                client.locOp(FairyRings.MINOR_HUB, dkr.destination!!.x, dkr.destination!!.z, FairyRings.CONFIGURE)
                client.seen("[${FairyRingLines.NO_STAFF.key}]")
                assertFalse(FairyRings.INTERFACE in client.openInterfaces())
                val before = client.messages().size
                client.locOp(FairyRings.MINOR_HUB, dkr.destination!!.x, dkr.destination!!.z, FairyRings.ZANARIS)
                client.await("the second refusal") { client.messages().drop(before).firstOrNull { it.text == "[${FairyRingLines.NO_STAFF.key}]" } }
                assertEquals(Coord(dkr.destination!!.x + 1, dkr.destination!!.z, 0), client.position)
            }
        }
    }

    @Test
    fun `the dials spell DIS and Confirm takes the player to the Wizards' Tower's ring, which is then the last destination`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.wieldStaff()
                client.besideRing(dkr)
                client.openDials(dkr)
                // Anticlockwise adds three quarters, as fairyrings_op does: A to B, and back to A.
                client.ifButton(FairyRings.INTERFACE, FairyRings.ANTICLOCKWISE[0], 1)
                client.await("dial 1 at B") { if (client.dials().first == 3) Unit else null }
                client.ifButton(FairyRings.INTERFACE, FairyRings.CLOCKWISE[0], 1)
                client.await("dial 1 back at A") { if (client.dials().first == 0) Unit else null }
                client.dialTo(dis)
                val events = client.actorEvents().size
                client.ifButton(FairyRings.INTERFACE, FairyRings.CONFIRM, 1)
                client.awaitAt(dis.destination!!)
                val anims = client.actorEvents(events).filter { !it.npc && it.kind == ActorEventKind.ANIMATION }.map { it.id }
                assertTrue(anims.indexOf(FairyRings.VANISH_ANIM) in 0 until anims.indexOf(FairyRings.APPEAR_ANIM), "$anims")
                assertTrue(client.actorEvents(events).any { !it.npc && it.kind == ActorEventKind.SPOTANIM && it.id == FairyRings.RING_SPOTANIM })
                assertFalse(FairyRings.INTERFACE in client.openInterfaces())
                // Last-destination now names DIS: the state, with the shown bit above it.
                assertEquals(64 + dis.state, client.varp(HISTORY_VARP))
            }
        }
    }

    @Test
    fun `Last-destination goes straight to the code last dialled, and Zanaris goes to BKS without changing it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.wieldStaff()
                client.besideRing(dkr)
                // Hidden until a code has been dialled: a forged click does nothing.
                client.locOp(FairyRings.MINOR_HUB, dkr.destination!!.x, dkr.destination!!.z, FairyRings.LAST_DESTINATION)
                client.openDials(dkr)
                client.dialTo(dis)
                client.ifButton(FairyRings.INTERFACE, FairyRings.CONFIRM, 1)
                client.awaitAt(dis.destination!!)
                client.besideRing(dis)
                client.locOp(FairyRings.MINOR_HUB, dis.destination!!.x, dis.destination!!.z, FairyRings.ZANARIS)
                client.awaitAt(bks.destination!!)
                assertEquals(64 + dis.state, client.varp(HISTORY_VARP))
                // From the ring's own tile, where the trip landed.
                client.locOp(FairyRings.HOME_HUB, bks.destination!!.x, bks.destination!!.z, FairyRings.LAST_DESTINATION)
                client.awaitAt(dis.destination!!)
            }
        }
    }

    @Test
    fun `a code that leads nowhere lands beside Zanaris's ring, and a ring nothing else reaches sends a player home without a staff`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.wieldStaff()
                client.besideRing(dkr)
                client.openDials(dkr)
                client.dialTo(aip)
                client.ifButton(FairyRings.INTERFACE, FairyRings.CONFIRM, 1)
                client.awaitAt(FairyRings.INVALID_LANDING)
                assertEquals(64 + aip.state, client.varp(HISTORY_VARP))
            }
            // Someone with no staff at all, at DLR: its ring's Zanaris still works.
            server.login("dave2").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(dlr.destination!!.x + 1, dlr.destination!!.z)
                client.locOp(FairyRings.MINOR_HUB, dlr.destination!!.x, dlr.destination!!.z, FairyRings.ZANARIS)
                client.awaitAt(bks.destination!!)
            }
        }
    }

    @Test
    fun `Confirm does nothing once the player has gone from the ring`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.wieldStaff()
                client.besideRing(dkr)
                client.openDials(dkr)
                client.dialTo(dis)
                client.command("tele 3135 3496 0")
                client.await("away") { client.position?.takeIf { it.x == 3135 } }
                client.ifButton(FairyRings.INTERFACE, FairyRings.CONFIRM, 1)
                // A tick or two for the click to land; the player must not have moved.
                client.commandReply("pos") { it.contains("3135") }
                assertEquals(Coord(3135, 3496, 0), client.position)
            }
        }
    }

    @Test
    fun `leaving from DLR, a badger comes through one trip in six`(@TempDir dir: Path) {
        server(dir, seed = 11).use { server ->
            server.login("frank").use { client ->
                client.await("the login rebuild") { client.position }
                client.wieldStaff()
                var trips = 0
                while (server.world.npcs.all.none { it.type.id == BADGER } && trips < 40) {
                    client.besideRing(dlr)
                    client.locOp(FairyRings.MINOR_HUB, dlr.destination!!.x, dlr.destination!!.z, FairyRings.ZANARIS)
                    client.awaitAt(bks.destination!!)
                    trips++
                }
                val badger = server.world.npcs.all.firstOrNull { it.type.id == BADGER }
                assertTrue(badger != null, "no badger in $trips trips")
                assertTrue(maxOf(kotlin.math.abs(badger.x - bks.destination!!.x), kotlin.math.abs(badger.z - bks.destination!!.z)) == 1, "badger at ${badger.x}, ${badger.z}")
            }
        }
    }

    /** Travels from DKR to DIS by the dials, then opens the dials and the log at DIS. */
    private fun HeadlessClient.logAtDis() {
        await("the login rebuild") { position }
        wieldStaff()
        besideRing(dkr)
        openDials(dkr)
        dialTo(dis)
        ifButton(FairyRings.INTERFACE, FairyRings.CONFIRM, 1)
        awaitAt(dis.destination!!)
        besideRing(dis)
        openDials(dis)
        await("the log") { if (FairyRings.LOG in openInterfaces()) Unit else null }
        await("the log's lines clickable") { if (opEnabled(FairyRings.LOG, FairyRings.FAVE_TEXT[0], -1, 2)) Unit else null }
    }

    private fun HeadlessClient.favourites(): List<Int> =
        FairyRings.FAVES.indices.map { slot -> (varp(FAVES_VARP + slot / 4) ushr (slot % 4 * 7)) and 0x7F }

    @Test
    fun `Configure opens the travel log beside the dials, listing only the codes the player has landed on`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gina").use { client ->
                val scripts = client.clientScripts().size
                client.logAtDis()
                // DIS was landed on; DKR, where the trip started, and BKS weren't.
                client.await("DIS's line") { client.text(FairyRings.LOG, dis.textComponent)?.takeIf { it == dis.description } }
                assertEquals(1, client.varp(VISITED_VARP))
                assertTrue(client.text(FairyRings.LOG, dkr.textComponent).isNullOrEmpty())
                assertTrue(client.text(FairyRings.LOG, bks.textComponent).isNullOrEmpty())
                // The empty favourites written, then the client asked to lay the log out.
                assertEquals("", client.text(FairyRings.LOG, FairyRings.FAVE_TEXT[0]))
                client.await("the sort") { client.clientScripts().drop(scripts).firstOrNull { it.id == FairyRings.SORT_UPDATE } }
                // Zanaris's BKS goes in the log too.
                client.closeModal()
                client.locOp(FairyRings.MINOR_HUB, dis.destination!!.x, dis.destination!!.z, FairyRings.ZANARIS)
                client.awaitAt(bks.destination!!)
                assertEquals(0b101, client.varp(VISITED_VARP))
            }
        }
    }

    @Test
    fun `Use code turns the dials, Add Favourite fills a slot the rings' Favourites option then travels by, and Remove Favourite empties it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hank").use { client ->
                client.logAtDis()
                // The dials still spell DIS from the trip here: one turn away, then Use code turns them back.
                client.ifButton(FairyRings.INTERFACE, FairyRings.CLOCKWISE[0], 1)
                client.await("the dials at CIS") { if (client.dials() == Triple(2, 0, 1)) Unit else null }
                client.ifButton(FairyRings.LOG, dis.textComponent, 1)
                client.await("the dials at DIS") { if (client.dials() == Triple(1, 0, 1)) Unit else null }
                // DKR isn't in the log, so a forged Use code or Add Favourite on it does nothing.
                client.ifButton(FairyRings.LOG, dkr.textComponent, 1)
                client.ifButton(FairyRings.LOG, dkr.faveIconComponent, 1)
                client.commandReply("pos") { it.contains("3109") }
                assertEquals(Triple(1, 0, 1), client.dials())
                assertEquals(List(10) { 0 }, client.favourites())
                client.ifButton(FairyRings.LOG, dis.faveIconComponent, 1)
                client.await("DIS in slot 1") { if (client.favourites()[0] == 64 + dis.state) Unit else null }
                client.await("the favourite's line") { client.text(FairyRings.LOG, FairyRings.FAVE_TEXT[0])?.takeIf { it == dis.description } }
                assertEquals("D I S", client.text(FairyRings.LOG, FairyRings.FAVE_CODE[0]))
                assertEquals(1, (client.varp(DIALS_VARP) ushr 6) and 0xFFF, "Favourites shown")
                // Again, by the line's second option: already a favourite, so nothing changes.
                client.ifButton(FairyRings.LOG, dis.textComponent, 2)
                client.commandReply("pos") { it.contains("3109") }
                assertEquals(listOf(64 + dis.state) + List(9) { 0 }, client.favourites())
                // Back to DKR, and the Favourites option's first slot brings the player to DIS.
                client.dialTo(dkr)
                client.ifButton(FairyRings.INTERFACE, FairyRings.CONFIRM, 1)
                client.awaitAt(dkr.destination!!)
                client.locOp(FairyRings.MINOR_HUB, dkr.destination!!.x, dkr.destination!!.z, FairyRings.FAVOURITES, subop = 1)
                client.awaitAt(dis.destination!!)
                assertEquals(64 + dis.state, client.varp(HISTORY_VARP), "a favourite's trip is the last destination")
                // An empty slot's sub-option does nothing.
                client.locOp(FairyRings.MINOR_HUB, dis.destination!!.x, dis.destination!!.z, FairyRings.FAVOURITES, subop = 2)
                client.commandReply("pos") { it.contains("3108") }
                assertEquals(Coord(dis.destination!!.x, dis.destination!!.z, 0), client.position)
                // Remove Favourite, by the favourite's star.
                client.openDials(dis)
                client.await("the log") { if (FairyRings.LOG in client.openInterfaces()) Unit else null }
                client.ifButton(FairyRings.LOG, FairyRings.FAVE_ICON[0], 1)
                client.await("slot 1 empty") { if (client.favourites()[0] == 0) Unit else null }
                client.await("the favourite's line gone") { client.text(FairyRings.LOG, FairyRings.FAVE_TEXT[0])?.takeIf { it.isEmpty() } }
                assertEquals(0, (client.varp(DIALS_VARP) ushr 6) and 0xFFF, "Favourites hidden")
            }
        }
    }

    private companion object {
        const val BADGER = 1234
        const val DIALS_VARP = 816
        const val HISTORY_VARP = 1423
        const val VISITED_VARP = 1500
        const val FAVES_VARP = 1501

        // As HeadlessEquipmentTest has them.
        const val BACKPACK = 93
        const val WORN = 94
        const val WIELD_BUTTON = 3
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
    }
}
