package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.Brutus
import osrsps.game.content.BrutusRecords
import osrsps.game.content.BrutusScoreboard
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Brutus step 3b through the headless client: a kill after an attack counts on the scoreboard (the player's kill
 * count and fastest kill, the server's too), the scoreboard takes the plant's place, and Read shows both columns in
 * `cowboss_scoreboard`. A death in a field counts too.
 */
class HeadlessBrutusScoreboardTest {
    private val types = LocTypes(
        listOf(
            LocType(Brutus.GATE_LEFT, "Gate", ops = listOf("Release", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)),
            LocType(BrutusScoreboard.SCOREBOARD, "Scoreboard", ops = listOf("Read", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)),
            // The map's loc, which the client sends whichever form it shows.
            LocType(BrutusScoreboard.SCOREBOARD_MULTI, "null", collision = LocCollisionType(width = 1, length = 1), multiloc = true),
        ).associateBy { it.id },
    )

    private val brutus = NpcType(
        Brutus.BOSS, "Brutus", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 30, internalName = "cowboss",
        stats = NpcStats(attack = 12, defence = 10, strength = 25, hitpoints = 58, ranged = 1, magic = 8),
    )

    private val varTypes = VarTypes(6_000, mapOf(BrutusScoreboard.SCOREBOARDS_VARBIT to VarbitType(5405, 0, 2)))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply { for (x in (3240 shr 6)..(3271 shr 6)) for (z in (3256 shr 6)..(3303 shr 6)) markLoaded(x, z) }
        val npcTypes = NpcTypes.of(brutus)
        World(
            crcs, map, random = GameRandom(13), locTypes = types,
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Brutus.GATE_LEFT, LocShape.WALL_STRAIGHT, 1, 3262, 3294, 0))
                add(ZoneLoc(BrutusScoreboard.SCOREBOARD_MULTI, LocShape.CENTREPIECE_STRAIGHT, 1, 3258, 3298, 0))
            },
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), varTypes = varTypes, cycleMillis = millis,
        )
    }

    @Test
    fun `a kill after an attack goes on the scoreboard, which Read shows`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("tele ${Brutus.OUTSIDE.x} ${Brutus.OUTSIDE.z} 0")
                client.await("outside the gate") { client.position?.takeIf { it == Coord(Brutus.OUTSIDE.x, Brutus.OUTSIDE.z, 0) } }
                client.locOp(Brutus.GATE_LEFT, 3262, 3294, Brutus.RELEASE)
                client.awaitDialogue("Release's menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                val alice = server.world.playerNamed("alice")!!
                val run = client.await("in a field") { server.world.brutusPens.runOf(alice) }
                val boss = run.boss!!
                client.await("Brutus seen") { client.npcs().firstOrNull { it.index == boss.index } }
                client.npcOp(boss.index, 2)
                client.await("her first attack") { run.fightStart.takeIf { it >= 0 } }
                client.awaitTicks(3)
                client.commandReply("kill ${boss.index}") { it.startsWith("Killed") }
                client.await("on the board") { if (server.world.brutusRecords.kills == 1L) Unit else null }
                val best = alice.vars[server.world.customVars.brutusBestVarp]
                assertTrue(best >= 3, "her fastest kill, from her first attack: $best ticks")
                assertEquals(best.toLong(), server.world.brutusRecords.bestTicks)
                assertEquals(1, alice.vars.getVarbit(BrutusScoreboard.SCOREBOARDS_VARBIT), "the scoreboard in the plant's place")

                // Out, and Read.
                client.command("tele 3258 3297 0")
                client.await("by the board") { client.position?.takeIf { it == Coord(3258, 3297, 0) } }
                client.locOp(BrutusScoreboard.SCOREBOARD_MULTI, 3258, 3298, BrutusScoreboard.READ)
                val texts = client.await("the scoreboard") { client.texts(BrutusScoreboard.INTERFACE).takeIf { it.size >= 6 } }
                assertTrue(BrutusScoreboard.INTERFACE in client.openInterfaces())
                assertEquals("1", texts[BrutusScoreboard.PERSONAL_KILLS])
                assertEquals("0", texts[BrutusScoreboard.PERSONAL_DEATHS])
                assertEquals(BrutusScoreboard.time(best.toLong()), texts[BrutusScoreboard.PERSONAL_BEST])
                assertEquals("1", texts[BrutusScoreboard.GLOBAL_KILLS])
                assertEquals(BrutusScoreboard.time(best.toLong()), texts[BrutusScoreboard.GLOBAL_BEST])
            }
        }
    }

    @Test
    fun `times show as minutes, seconds and hundredths, and the server's records keep in their file`(@TempDir dir: Path) {
        assertEquals("0:01.20", BrutusScoreboard.time(2))
        assertEquals("1:23.40", BrutusScoreboard.time(139))
        assertEquals("-", BrutusScoreboard.time(0))
        val file = dir.resolve(BrutusRecords.FILE_NAME)
        BrutusRecords(file).apply {
            killed(50)
            killed(40)
            killed(null)
            died()
        }
        val again = BrutusRecords(file)
        assertEquals(3L to 1L, again.kills to again.deaths)
        assertEquals(40L, again.bestTicks)
    }
}
