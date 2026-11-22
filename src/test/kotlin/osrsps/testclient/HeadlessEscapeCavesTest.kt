package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.EscapeCaves
import osrsps.game.content.WildernessLairFee
import osrsps.game.content.WildernessLairFeeLines
import osrsps.game.content.WildernessLairLines
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.Tile

/**
 * Wilderness step W4.11 through the headless client: a surface cave into the Escape Caves taking the lairs' fee and
 * putting the player by its way out below, the debuff draining their prayer there, and that way out leading back up;
 * players leaving the same lair within 10 seconds coming out of the same cave mouth; and the caves' bears and spiders
 * typeless. The rl-client script test-runner/examples/escape-caves.json does the way in and out on the real map.
 */
class HeadlessEscapeCavesTest {
    private val varTypes = VarTypes(
        6_000,
        mapOf(WildernessLairFee.VARBIT_PAID to VarbitType(WBR_PERM_INT_1, 0, 0), WildernessLairFee.VARBIT_DISCOUNT to VarbitType(WBR_PERM_INT_1, 1, 3)),
        21_000,
    )

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val cave = LocType(EscapeCaves.SURFACE_CAVE, "Cave", listOf("Enter", "Check-Fee", null, null, null), LocCollisionType(width = 3, length = 3))
        val exit = LocType(EscapeCaves.EXIT_SOUTH, "Cave", listOf("Exit", null, null, null, null), LocCollisionType(width = 2, length = 3))
        World(
            crcs, CollisionMap().apply { markLoaded(52, 160); markLoaded(51, 58) },
            objTypes = ObjTypes.of(ObjType(WildernessLairFee.COINS, "Coins", stackable = true)),
            random = GameRandom(1), varTypes = varTypes,
            locTypes = LocTypes(mapOf(EscapeCaves.SURFACE_CAVE to cave, EscapeCaves.EXIT_SOUTH to exit)),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(EscapeCaves.SURFACE_CAVE, 10, 0, 3282, 3774, 0))
                add(ZoneLoc(EscapeCaves.EXIT_SOUTH, 10, 1, 3358, 10244, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `a surface cave takes the fee, the debuff drains prayer below, and the way out leads back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                val abe = server.world.playerNamed("abe")!!
                client.command("tele 3283 3773 0")
                client.await("outside the cave") { client.position?.takeIf { it == Coord(3283, 3773, 0) } }
                client.locOp(EscapeCaves.SURFACE_CAVE, 3282, 3774, EscapeCaves.CHECK_FEE)
                client.seen("[${WildernessLairLines.FEE_DUE.key} fee=50000]")
                client.commandReply("setlevel prayer 50") { true }
                client.command("item ${WildernessLairFee.COINS} 50000")
                client.await("the coins") { Unit.takeIf { abe.inventory.count(WildernessLairFee.COINS) == 50_000L } }
                client.locOp(EscapeCaves.SURFACE_CAVE, 3282, 3774, EscapeCaves.ENTER)
                client.awaitDialogue("the fee's menu") { it is DialogueView.Menu && WildernessLairFeeLines.TITLE.key in it.title }
                client.chooseOption(1)
                client.await("in the caves", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(3359, 10246, 0) } }
                assertTrue(server.world.escapeCaves.debuffed(abe))
                client.await("prayer drained", timeoutMillis = 15_000) { Unit.takeIf { abe.skills.currentLevel(Skill.PRAYER) < 50 } }

                client.locOp(EscapeCaves.EXIT_SOUTH, 3358, 10244, 1)
                client.await("back up", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(3283, 3773, 0) } }
                client.await("the debuff gone") { Unit.takeIf { !server.world.escapeCaves.debuffed(abe) } }
            }
        }
    }

    @Test
    fun `lingering past 50 ticks drains prayer and run energy, and stops once out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                val bo = server.world.playerNamed("bo")!!
                client.commandReply("setlevel prayer 50") { true }
                client.command("tele 3359 10246 0")
                client.await("in the caves") { client.position?.takeIf { it == Coord(3359, 10246, 0) } }
                bo.runEnergy = 10_000
                val arrived = server.world.tick
                assertFalse(server.world.escapeCaves.debuffed(bo), "no debuff without the surface cave")
                client.await("prayer drained", timeoutMillis = 15_000) { Unit.takeIf { bo.skills.currentLevel(Skill.PRAYER) < 50 } }
                assertTrue(server.world.tick - arrived >= EscapeCaves.LINGER_AFTER, "not before about 30 seconds")
                assertTrue(bo.runEnergy <= 10_000 - EscapeCaves.LINGER_ENERGY, "run energy with it")
                client.await("drained again", timeoutMillis = 15_000) { Unit.takeIf { bo.skills.currentLevel(Skill.PRAYER) < 49 } }

                client.command("tele 3283 3773 0")
                client.await("out") { client.position?.takeIf { it == Coord(3283, 3773, 0) } }
                val prayer = bo.skills.currentLevel(Skill.PRAYER)
                val left = server.world.tick
                client.await("a while out") { Unit.takeIf { server.world.tick - left > 2 * EscapeCaves.LINGER_TICKS } }
                assertEquals(prayer, bo.skills.currentLevel(Skill.PRAYER), "no drain once out")
            }
        }
    }

    @Test
    fun `players leaving the same lair within 10 seconds come out together`() {
        val caves = EscapeCaves()
        val first = caves.lairExit("callisto", 100) { 0 }
        assertEquals(EscapeCaves.LAIR_MOUTHS[0], first)
        assertEquals(first, caves.lairExit("callisto", 100 + EscapeCaves.SHARED_TICKS) { 2 }, "within 10 seconds of the last")
        assertEquals(EscapeCaves.LAIR_MOUTHS[1], caves.lairExit("vetion", 120) { 1 }, "another lair's own")
        assertEquals(EscapeCaves.LAIR_MOUTHS[2], caves.lairExit("callisto", 100 + 3 * EscapeCaves.SHARED_TICKS) { 2 }, "long after")
        for (mouth in EscapeCaves.LAIR_MOUTHS) assertTrue(EscapeCaves.inCaves(mouth.x, mouth.z))
        for (pair in EscapeCaves.PAIRS) assertTrue(EscapeCaves.inCaves(pair.inside.x, pair.inside.z))
        assertFalse(EscapeCaves.inCaves(3283, 3773))
        assertEquals(Tile(3359, 10246), EscapeCaves.PAIRS.first().inside)
    }

    @Test
    fun `the caves' bears and spiders are typeless`() {
        val bear = NpcType(11989, "Grizzly bear", size = 2, internalName = "wbr_bear", stats = NpcStats(attack = 72, strength = 72, defence = 72, hitpoints = 75))
        val spider = NpcType(11990, "Poison spider", internalName = "wbr_cave_poisonspider", stats = NpcStats(defence = 35, hitpoints = 73, ranged = 45))
        val combat = NpcCombat.load(NpcTypes.of(bear, spider), SeqNames.NONE)
        assertTrue(combat[bear]!!.typeless)
        assertTrue(combat[spider]!!.typeless)
    }

    private companion object {
        const val WBR_PERM_INT_1 = 3755
    }
}
