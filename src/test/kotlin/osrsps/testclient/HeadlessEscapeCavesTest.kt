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
import osrsps.game.combat.Prayers
import osrsps.game.content.EscapeCaves
import osrsps.game.content.WildernessLairFee
import osrsps.game.content.WildernessLairFeeLines
import osrsps.game.content.WildernessLairLines
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
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

    private val bear = NpcType(11989, "Grizzly bear", size = 2, internalName = "wbr_bear", stats = NpcStats(attack = 72, strength = 72, defence = 72, hitpoints = 75))
    private val spider = NpcType(11990, "Poison spider", internalName = "wbr_cave_poisonspider", stats = NpcStats(defence = 35, hitpoints = 73, ranged = 45))

    // The two protection prayers' objs, shaped like the cache's (HeadlessFightCaveTest).
    private val prayerObjs = listOf(
        ObjType(
            PROTECT_FROM_MISSILES_OBJ, "Null", internalName = "xmas16_marionette_blue",
            params = mapOf(630 to 13, 1751 to (Prayers.PRAYERBOOK shl 16 or PROTECT_FROM_MISSILES), 1752 to "Protect from Missiles", 1753 to 40, 1755 to 2677),
        ),
        ObjType(
            PROTECT_FROM_MELEE_OBJ, "Null", internalName = "placeholder_xmas16_marionette_blue",
            params = mapOf(630 to 14, 1751 to (Prayers.PRAYERBOOK shl 16 or PROTECT_FROM_MELEE), 1752 to "Protect from Melee", 1753 to 43, 1755 to 2676),
        ),
    )

    /** One of the caves' monsters beside the south way out's arrival tile, with the protection prayers loaded. */
    private fun fightServer(dir: Path, monster: NpcType) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val types = NpcTypes.of(bear, spider)
        val objTypes = ObjTypes.of(*prayerObjs.toTypedArray())
        World(
            crcs, CollisionMap().apply { markLoaded(52, 160) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(1), varTypes = varTypes,
            npcCombat = NpcCombat.load(types, SeqNames.NONE), prayerList = Prayers.load(objTypes),
            npcSpawns = listOf(NpcSpawn(monster, 3361, 10248, source = "test")),
            cycleMillis = millis,
        )
    }

    /** [monster]'s attacks land through the protection prayer [prayer] that would block their style. */
    private fun hitsThrough(dir: Path, monster: NpcType, prayer: String) {
        fightServer(dir, monster).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                val cy = server.world.playerNamed("cy")!!
                for (skill in listOf("prayer", "hitpoints")) client.commandReply("setlevel $skill 99") { true }
                client.commandReply("setlevel defence 1") { true }
                client.await("the levels") { Unit.takeIf { cy.skills.currentLevel(Skill.HITPOINTS) == 99 && cy.skills.currentLevel(Skill.PRAYER) == 99 } }
                client.command("tele 3359 10246 0")
                client.await("in the caves") { client.position?.takeIf { it == Coord(3359, 10246, 0) } }
                // Immune to the spider's poison, which comes through a prayer anyway: only a hit takes hitpoints.
                server.world.poison.cure(cy, 1_000)
                val protection = server.world.prayers.find(prayer)!!
                server.world.prayers.activate(cy, protection)
                client.await("$prayer in effect") { Unit.takeIf { server.world.prayers.inEffect(cy, protection) } }
                client.await("a hit through $prayer", timeoutMillis = 20_000) {
                    Unit.takeIf { cy.skills.currentLevel(Skill.HITPOINTS) < 99 }
                }
                assertTrue(server.world.prayers.isActive(cy, protection), "$prayer still on when the hit landed")
            }
        }
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
    fun `relogging in the caves keeps the visit (its debuff and linger count)`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("eve").use { client ->
                client.await("the login rebuild") { client.position }
                val eve = server.world.playerNamed("eve")!!
                client.command("tele 3283 3773 0")
                client.await("outside the cave") { client.position?.takeIf { it == Coord(3283, 3773, 0) } }
                client.command("item ${WildernessLairFee.COINS} 50000")
                client.await("the coins") { Unit.takeIf { eve.inventory.count(WildernessLairFee.COINS) == 50_000L } }
                client.locOp(EscapeCaves.SURFACE_CAVE, 3282, 3774, EscapeCaves.ENTER)
                client.awaitDialogue("the fee's menu") { it is DialogueView.Menu && WildernessLairFeeLines.TITLE.key in it.title }
                client.chooseOption(1)
                client.await("in the caves", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(3359, 10246, 0) } }
                assertTrue(server.world.escapeCaves.debuffed(eve))
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            val until = System.currentTimeMillis() + 10_000
            while (server.world.playerNamed("eve") != null) {
                check(System.currentTimeMillis() < until) { "eve never logged out" }
                Thread.sleep(20)
            }
            // A new Player for the same account (exploit review round 48, R48B-1).
            server.login("eve").use { client ->
                client.await("back in the caves") { client.position?.takeIf { it == Coord(3359, 10246, 0) } }
                val eve = client.await("eve again") { server.world.playerNamed("eve") }
                assertTrue(server.world.escapeCaves.debuffed(eve), "the debuff kept through a relog")
            }
            // Logged out there for good: forgotten after a while, so such accounts don't pile up (Codex review on #861).
            server.login("fay").use { fay ->
                fay.await("the login rebuild") { fay.position }
                fay.await("eve gone") { Unit.takeIf { server.world.playerNamed("eve") == null } }
                assertEquals(1, server.world.escapeCaves.remembered)
                val left = server.world.tick
                // A command every few seconds, so the server's 15-second read timeout doesn't close fay's connection.
                val until = System.currentTimeMillis() + 40_000
                while (server.world.escapeCaves.remembered != 0) {
                    check(System.currentTimeMillis() < until) { "eve's visit never forgotten" }
                    fay.commandReply("pos") { true }
                    fay.awaitTicks(40)
                }
                assertTrue(server.world.tick - left >= EscapeCaves.FORGET_TICKS - 1, "not before ${EscapeCaves.FORGET_TICKS} ticks")
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
    fun `a bear's swings land through Protect from Melee`(@TempDir dir: Path) = hitsThrough(dir, bear, "Protect from Melee")

    @Test
    fun `a spider's shots land through Protect from Missiles`(@TempDir dir: Path) = hitsThrough(dir, spider, "Protect from Missiles")

    @Test
    fun `the caves' bears and spiders are typeless`() {
        val combat = NpcCombat.load(NpcTypes.of(bear, spider), SeqNames.NONE)
        assertTrue(combat[bear]!!.typeless)
        assertTrue(combat[spider]!!.typeless)
    }

    private companion object {
        const val WBR_PERM_INT_1 = 3755
        const val PROTECT_FROM_MISSILES = 22
        const val PROTECT_FROM_MISSILES_OBJ = 20816
        const val PROTECT_FROM_MELEE = 23
        const val PROTECT_FROM_MELEE_OBJ = 20817
    }
}
