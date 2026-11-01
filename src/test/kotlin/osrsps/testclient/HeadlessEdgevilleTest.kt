package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.Edgeville
import osrsps.game.content.EdgevilleLines
import osrsps.game.content.HaystackLines
import osrsps.game.content.Haystacks
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Edgeville piece step 1 through the headless client: the grange's double doors, the haystack's three outcomes, the
 * mausoleum's coffin, the east house's wardrobe and the Wilderness Ditch's refusal, placed as the 241 cache has them
 * (types, sizes, angles and options), on open ground. The rl-client script test-runner/examples/edgeville-town.json
 * does the same on the real map.
 */
class HeadlessEdgevilleTest {
    private fun type(id: Int, name: String, width: Int, length: Int, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = width, length = length))

    private val types = LocTypes(
        listOf(
            type(Edgeville.LARGE_DOOR_LEFT, "Large door", 1, 1, "Open"),
            type(Edgeville.LARGE_DOOR_LEFT_OPEN, "Large door", 1, 1, "Close"),
            type(Edgeville.LARGE_DOOR_RIGHT, "Large door", 1, 1, "Open"),
            type(Edgeville.LARGE_DOOR_RIGHT_OPEN, "Large door", 1, 1, "Close"),
            type(Edgeville.COFFIN, "Coffin", 1, 2, "Open"),
            type(Edgeville.COFFIN_OPEN, "Coffin", 1, 2, "Close"),
            type(Edgeville.WARDROBE, "Wardrobe", 1, 1, "Open"),
            type(Edgeville.WARDROBE_OPEN, "Wardrobe", 1, 1, null, "Search", "Close"),
            type(HAYSTACK, "Haystack", 1, 1, "Search"),
            type(Edgeville.DITCH, "Wilderness Ditch", 1, 1, "Cross"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(Edgeville.LARGE_DOOR_LEFT, LocShape.WALL_STRAIGHT, 0, 3101, 3509, 0),
        ZoneLoc(Edgeville.LARGE_DOOR_RIGHT, LocShape.WALL_STRAIGHT, 0, 3101, 3510, 0),
        ZoneLoc(Edgeville.COFFIN, LocShape.CENTREPIECE_STRAIGHT, 2, 3090, 3476, 0),
        ZoneLoc(Edgeville.WARDROBE, LocShape.CENTREPIECE_STRAIGHT, 3, 3120, 3481, 0),
        ZoneLoc(HAYSTACK, LocShape.CENTREPIECE_STRAIGHT, 1, 3093, 3509, 0),
        ZoneLoc(Edgeville.DITCH, LocShape.CENTREPIECE_STRAIGHT, 0, 3090, 3521, 0),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Haystacks.NEEDLE + 1)
        objs[Haystacks.NEEDLE] = ObjType(Haystacks.NEEDLE, "Needle", iops = listOf(null, null, null, null, "Drop"))
        val map = CollisionMap().apply {
            markLoaded(3101 shr 6, 3509 shr 6)
            markLoaded(3090 shr 6, 3521 shr 6)
        }
        World(crcs, map, objTypes = ObjTypes(objs), locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) }, cycleMillis = millis)
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    /** Does [action] and waits for [text], counting the messages from before the action so a quick reply isn't missed. */
    private fun HeadlessClient.expectMessage(text: String, action: () -> Unit) {
        val before = messages().count { it.text == text }
        action()
        await("the message '$text'") { if (messages().count { it.text == text } > before) Unit else null }
    }

    private fun World.openTypeNear(id: Int, x: Int, z: Int): ZoneLoc? =
        (x - 1..x + 1).flatMap { dx -> (z - 1..z + 1).mapNotNull { dz -> locs.find(id, dx, dz, 0) } }.firstOrNull()

    @Test
    fun `the grange's large doors open both leaves together and close them again`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3100, 3509, 0)
                client.locOp(Edgeville.LARGE_DOOR_LEFT, 3101, 3509, 1)
                val world = server.world
                val leftOpen = client.await("both leaves open") {
                    val left = world.openTypeNear(Edgeville.LARGE_DOOR_LEFT_OPEN, 3101, 3509)
                    val right = world.openTypeNear(Edgeville.LARGE_DOOR_RIGHT_OPEN, 3101, 3510)
                    if (left != null && right != null) left else null
                }
                assertNull(world.locs.find(Edgeville.LARGE_DOOR_LEFT, 3101, 3509, 0))
                assertNull(world.locs.find(Edgeville.LARGE_DOOR_RIGHT, 3101, 3510, 0))
                client.locOp(Edgeville.LARGE_DOOR_LEFT_OPEN, leftOpen.x, leftOpen.z, 1)
                client.await("both leaves shut") {
                    if (world.locs.find(Edgeville.LARGE_DOOR_LEFT, 3101, 3509, 0) != null &&
                        world.locs.find(Edgeville.LARGE_DOOR_RIGHT, 3101, 3510, 0) != null
                    ) Unit else null
                }
            }
        }
    }

    @Test
    fun `the haystack finds nothing, a needle, or pricks for a hitpoint, by the roll`(@TempDir dir: Path) {
        // Seeds whose first roll lands in each band, so each search's outcome is known before it is made.
        fun seedFor(band: IntRange): Long = (1L..10_000L).first { GameRandom(it).nextInt(Haystacks.ROLL) in band }
        val prick = seedFor(0 until Haystacks.PRICK)
        val needle = seedFor(Haystacks.PRICK until Haystacks.PRICK + Haystacks.FIND)
        val nothing = seedFor(Haystacks.PRICK + Haystacks.FIND until Haystacks.ROLL)
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3093, 3508, 0)
                val hitpoints = client.await("hitpoints") { client.stat(Skill.HITPOINTS.ordinal)?.level }

                server.world.random.reseed(nothing)
                client.expectMessage(DialogueText.NONE[HaystackLines.NOTHING]) { client.locOp(HAYSTACK, 3093, 3509, Haystacks.SEARCH) }

                server.world.random.reseed(needle)
                client.expectMessage(DialogueText.NONE[HaystackLines.NEEDLE]) { client.locOp(HAYSTACK, 3093, 3509, Haystacks.SEARCH) }
                client.await("a needle") { if (client.inventory(BACKPACK).count { it?.id == Haystacks.NEEDLE } == 1) Unit else null }

                server.world.random.reseed(prick)
                client.expectMessage(DialogueText.NONE[HaystackLines.PRICKED]) { client.locOp(HAYSTACK, 3093, 3509, Haystacks.SEARCH) }
                assertEquals(hitpoints - 1, client.await("a hitpoint lost") {
                    client.stat(Skill.HITPOINTS.ordinal)?.level?.takeIf { it < hitpoints }
                })
                assertEquals(1, client.inventory(BACKPACK).count { it?.id == Haystacks.NEEDLE }, "a prick finds no needle")
            }
        }
    }

    @Test
    fun `the coffin and the wardrobe open and close, and the ditch can't be crossed`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val world = server.world
                client.tele(3091, 3476, 0)
                client.locOp(Edgeville.COFFIN, 3090, 3476, 1)
                client.await("the coffin open") { world.locs.find(Edgeville.COFFIN_OPEN, 3090, 3476, 0) }
                client.locOp(Edgeville.COFFIN_OPEN, 3090, 3476, 1)
                client.await("the coffin shut") { world.locs.find(Edgeville.COFFIN, 3090, 3476, 0) }

                client.tele(3121, 3481, 0)
                client.locOp(Edgeville.WARDROBE, 3120, 3481, 1)
                client.await("the wardrobe open") { world.locs.find(Edgeville.WARDROBE_OPEN, 3120, 3481, 0) }
                client.expectMessage(DialogueText.NONE[EdgevilleLines.WARDROBE_EMPTY]) { client.locOp(Edgeville.WARDROBE_OPEN, 3120, 3481, 2) }

                client.tele(3090, 3520, 0)
                client.expectMessage(DialogueText.NONE[EdgevilleLines.DITCH_CLOSED]) { client.locOp(Edgeville.DITCH, 3090, 3521, Edgeville.CROSS) }
                client.awaitTicks(2)
                assertTrue(client.position!!.z < 3521, "still south of the ditch")
            }
        }
    }

    private companion object {
        /** haystack3, the grange's haystack. */
        const val HAYSTACK = 300

        const val BACKPACK = 93
    }
}
