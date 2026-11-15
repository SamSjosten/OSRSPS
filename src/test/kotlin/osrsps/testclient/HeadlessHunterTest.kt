package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.hunter.BirdSnares
import osrsps.game.skill.hunter.Traps
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Hunter step 1 through the headless client: laying a bird snare, a bird walking into it, checking it, the trap count,
 * someone else's trap, dismantling, the minute after which a trap collapses, a bird too high for the player's level
 * slipping out, and Reset. The players log in at 3222, 3218, so a snare laid there stands with a bird two tiles east.
 */
class HeadlessHunterTest {
    private val swift = NpcType(BirdSnares.CRIMSON_SWIFT, "Crimson swift", internalName = "hunting_bird_jungle")
    private val wagtail = NpcType(BirdSnares.TROPICAL_WAGTAIL, "Tropical wagtail", internalName = "multicoloured_bird")

    // Fast ticks: a trap stands 100 of them before it collapses.
    private fun server(dir: Path, bird: NpcType? = swift) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(11_000)
        objs[BirdSnares.SNARE] = ObjType(
            BirdSnares.SNARE, "Bird snare", stackable = true, tradeable = true,
            ops = listOf(null, null, "Take", "Lay", null), iops = listOf("Lay", null, null, null, "Drop"),
        )
        for ((id, name, stacks) in listOf(Triple(BONES, "Bones", false), Triple(MEAT, "Raw bird meat", false), Triple(RED_FEATHER, "Red feather", true))) {
            objs[id] = ObjType(id, name, stackable = stacks, tradeable = true)
        }
        val snareLocs = listOf(BirdSnares.SET, BirdSnares.FAILING, BirdSnares.BROKEN) + BirdSnares.BIRDS.flatMap { listOf(it.catching, it.full) }
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(7),
            locTypes = LocTypes(snareLocs.associateWith { LocType(it, "Bird snare", ops = opsOf(it), collision = LocCollisionType(interactType = 0, blocksProjectile = false)) }),
            npcTypes = NpcTypes.of(swift, wagtail),
            npcSpawns = listOfNotNull(bird?.let { NpcSpawn(it, 3224, 3218, source = "test") }),
            cycleMillis = millis,
        )
    }

    /** The cache's ops on each snare loc. */
    private fun opsOf(loc: Int): List<String?> = when (loc) {
        BirdSnares.SET -> listOf("Dismantle", "Investigate", null, null, null)
        BirdSnares.BROKEN -> listOf("Dismantle", "Reset", null, null, null)
        in BirdSnares.BIRDS.map { it.full } -> listOf("Check", "Reset", null, null, null)
        else -> List(5) { null }
    }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.awaitLoc(id: Int, x: Int, z: Int, timeoutMillis: Long = 10_000) =
        await("loc $id at $x, $z", timeoutMillis) { if (locAt(x, z) == id) Unit else null }

    private fun HeadlessClient.lay() = ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, LAY_BUTTON_OP, slotOf(BirdSnares.SNARE), BirdSnares.SNARE)

    @Test
    fun `a snare laid beside a crimson swift stands, the swift walks in, and Check gives its drops and XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val at = client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 99")
                client.command("item ${BirdSnares.SNARE} 2")
                client.slotOf(BirdSnares.SNARE)
                val before = client.await("Hunter XP") { client.stat(HUNTER)?.takeIf { it.level == 99 } }.xp
                client.lay()
                client.seen("[hunter.laying]")
                // The snare goes down at once, and stands as the trap on the skill clock's fourth tick; the player steps west.
                client.await("the snare on the ground") { client.groundObjs().firstOrNull { it.id == BirdSnares.SNARE && it.coord == at } }
                client.awaitLoc(BirdSnares.SET, at.x, at.z)
                client.await("a step west") { client.position?.takeIf { it.x == at.x - 1 && it.z == at.z } }
                assertEquals(1, client.count(BirdSnares.SNARE))
                // At 99 the catch is sure (164%): the swift two tiles east walks in, struggles, and the snare holds it.
                client.awaitLoc(9349, at.x, at.z, timeoutMillis = 20_000)
                client.awaitLoc(9373, at.x, at.z)
                // The minute runs from the catch, not from the struggle's end (Codex review on #690).
                val trap = server.world.hunter.traps.all.single()
                assertEquals((Traps.TRAP_TICKS - Traps.CATCHING_TICKS).toLong(), trap.collapseTick - trap.stateTick)
                client.locOp(9373, at.x, at.z, Traps.CHECK)
                client.seen("[hunter.caught creature=crimson swift]")
                client.await("34 XP") { if (client.stat(HUNTER)!!.xp - before == 34) Unit else null }
                client.await("the snare back") { if (client.count(BirdSnares.SNARE) == 2) Unit else null }
                assertEquals(1, client.count(BONES))
                assertEquals(1, client.count(MEAT))
                assertTrue(client.count(RED_FEATHER) in 5..10, "${client.count(RED_FEATHER)} feathers")
                assertTrue(server.world.hunter.traps.all.isEmpty())
            }
        }
    }

    @Test
    fun `two snares at level 1, none on a trap, and only the owner can take one down`(@TempDir dir: Path) {
        server(dir, bird = null).use { server ->
            server.login("bob").use { bob ->
                val at = bob.await("the login rebuild") { bob.position }
                bob.command("item ${BirdSnares.SNARE} 3")
                bob.lay()
                bob.awaitLoc(BirdSnares.SET, at.x, at.z)
                // Back onto the trap: nothing more goes there.
                bob.command("tele ${at.x} ${at.z}")
                bob.await("back on the trap") { bob.position?.takeIf { it == at } }
                bob.lay()
                bob.seen("[hunter.cant_here]")
                bob.command("tele ${at.x} ${at.z + 2}")
                bob.await("north of it") { bob.position?.takeIf { it.z == at.z + 2 } }
                bob.lay()
                bob.awaitLoc(BirdSnares.SET, at.x, at.z + 2)
                bob.lay()
                bob.seen("[hunter.too_many count=2]")
                assertEquals(1, bob.count(BirdSnares.SNARE))

                server.login("carol").use { carol ->
                    carol.await("the login rebuild") { carol.position }
                    carol.locOp(BirdSnares.SET, at.x, at.z, Traps.DISMANTLE)
                    carol.seen("[hunter.not_yours]")
                }
                bob.locOp(BirdSnares.SET, at.x, at.z, Traps.DISMANTLE)
                bob.seen("[hunter.dismantled]")
                bob.await("the snare back") { if (bob.count(BirdSnares.SNARE) == 2) Unit else null }
                bob.await("the trap gone") { if (bob.locAt(at.x, at.z) == -1 || bob.locAt(at.x, at.z) == null) Unit else null }
                assertEquals(1, server.world.hunter.traps.all.size)

                // The other one, left alone, collapses a minute after it stood, leaving its snare on its tile.
                bob.await("the trap collapsed", timeoutMillis = 20_000) {
                    bob.groundObjs().firstOrNull { it.id == BirdSnares.SNARE && it.coord.x == at.x && it.coord.z == at.z + 2 }
                }
                assertTrue(server.world.hunter.traps.all.isEmpty())
            }
        }
    }

    @Test
    fun `a bird above the player's level always slips out, and Reset lays the snare again`(@TempDir dir: Path) {
        server(dir, bird = wagtail).use { server ->
            server.login("dave").use { client ->
                val at = client.await("the login rebuild") { client.position }
                client.command("item ${BirdSnares.SNARE} 1")
                client.lay()
                client.awaitLoc(BirdSnares.SET, at.x, at.z)
                // Level 1 against the wagtail's 19: it walks in and the snare fails.
                client.awaitLoc(BirdSnares.FAILING, at.x, at.z, timeoutMillis = 20_000)
                client.awaitLoc(BirdSnares.BROKEN, at.x, at.z)
                assertEquals(0, client.count(BirdSnares.SNARE))
                client.locOp(BirdSnares.BROKEN, at.x, at.z, Traps.RESET)
                client.seen("[hunter.dismantled]")
                client.awaitLoc(BirdSnares.SET, at.x, at.z)
                assertEquals(0, client.count(BirdSnares.SNARE))
                assertEquals(1, server.world.hunter.traps.all.size)
            }
            // Its owner gone, the trap collapses and its snare is left for anyone.
            server.login("erin").use { erin ->
                erin.await("the snare left behind") { erin.groundObjs().firstOrNull { it.id == BirdSnares.SNARE } }
                assertTrue(server.world.hunter.traps.all.isEmpty())
            }
        }
    }

    @Test
    fun `a player who dies while a Reset waits loses no snare`(@TempDir dir: Path) {
        server(dir, bird = wagtail).use { server ->
            server.login("fred").use { client ->
                val at = client.await("the login rebuild") { client.position }
                client.command("item ${BirdSnares.SNARE} 1")
                client.lay()
                client.awaitLoc(BirdSnares.BROKEN, at.x, at.z, timeoutMillis = 20_000)
                client.locOp(BirdSnares.BROKEN, at.x, at.z, Traps.RESET)
                client.seen("[hunter.dismantled]")
                // Killed mid-Reset: the death cuts the script off, and the snare is on its tile, laid again, or held.
                client.command("hit 99 me")
                client.await("one snare, somewhere", timeoutMillis = 20_000) {
                    val onTile = server.world.zones.objsAt(at.x, at.z, 0).filter { it.id == BirdSnares.SNARE }.sumOf { it.count }
                    val laid = server.world.hunter.traps.all.size
                    if (onTile + laid + client.count(BirdSnares.SNARE) == 1) Unit else null
                }
            }
        }
    }

    @Test
    fun `no trap goes down in an instance`(@TempDir dir: Path) {
        server(dir, bird = null).use { server ->
            server.login("gina").use { client ->
                val at = client.await("the login rebuild") { client.position }
                client.command("item ${BirdSnares.SNARE} 1")
                client.command("instance 0")
                client.await("inside the instance") { client.position?.takeIf { it != at } }
                client.lay()
                client.seen("[hunter.cant_here]")
                assertEquals(1, client.count(BirdSnares.SNARE))
                assertTrue(server.world.hunter.traps.all.isEmpty())
            }
        }
    }

    private companion object {
        const val BONES = 526
        const val MEAT = 9978
        const val RED_FEATHER = 10088
        const val BACKPACK = 93
        const val HUNTER = 21

        /** iop1 (Lay) is button op 2 (see InventoryTab.click). */
        const val LAY_BUTTON_OP = 2
    }
}
