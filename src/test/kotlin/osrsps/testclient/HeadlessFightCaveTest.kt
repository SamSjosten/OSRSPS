package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.tzhaar.FightCaveMonster
import osrsps.game.tzhaar.FightCaveWaves
import osrsps.game.tzhaar.FightCaves
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Fight Caves step FC1 through the headless client: the cave entrance into a cave of one's own, the waves coming in at
 * the rotation's spots one after another, the exit and a death paying Tokkul for the waves done, a logout clicked in a
 * wave pausing the run at its end and the next login starting the next wave, a second click starting the same wave
 * again, and TzTok-Jad's death handing over the fire cape. The rl-client script test-runner/examples/fight-cave.json
 * goes in on the real map.
 */
class HeadlessFightCaveTest {
    private fun monster(id: Int, name: String, size: Int, level: Int, internal: String, hitpoints: Int) = NpcType(
        id, name, size = size, ops = listOf("Attack", null, null, null, null), combatLevel = level, internalName = internal,
        stats = NpcStats(attack = 20, strength = 30, defence = 15, hitpoints = hitpoints),
    )

    private val types = NpcTypes.of(
        monster(3116, "Tz-Kih", 1, 22, "tzhaar_fightcave_swarm_1a", 10),
        monster(3117, "Tz-Kih", 1, 22, "tzhaar_fightcave_swarm_1b", 10),
        monster(3118, "Tz-Kek", 2, 45, "tzhaar_fightcave_swarm_2a", 20),
        monster(3119, "Tz-Kek", 2, 45, "tzhaar_fightcave_swarm_2b", 20),
        monster(3121, "Tok-Xil", 3, 90, "tzhaar_fightcave_swarm_3a", 40),
        monster(3122, "Tok-Xil", 3, 90, "tzhaar_fightcave_swarm_3b", 40),
        monster(3123, "Yt-MejKot", 4, 180, "tzhaar_fightcave_swarm_4a", 80),
        monster(3124, "Yt-MejKot", 4, 180, "tzhaar_fightcave_swarm_4b", 80),
        monster(3125, "Ket-Zek", 5, 360, "tzhaar_fightcave_swarm_5a", 160),
        monster(3126, "Ket-Zek", 5, 360, "tzhaar_fightcave_swarm_5b", 160),
        monster(3127, "TzTok-Jad", 5, 702, "tzhaar_fightcave_swarm_boss", 250),
        NpcType(FightCaves.MEJ_JAL, "TzHaar-Mej-Jal", ops = listOf("Talk-to", null, "Exchange fire cape", null, null), internalName = "tzhaar_fightcave_master"),
    )

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val objTypes = ObjTypes.of(
            ObjType(FightCaves.TOKKUL, "Tokkul", stackable = true),
            ObjType(FightCaves.FIRE_CAPE, "Fire cape"),
            ObjType(995, "Coins", stackable = true),
        )
        val cave = { id: Int, name: String, ops: List<String?> -> LocType(id, name, ops, LocCollisionType(width = 3, length = 2)) }
        World(
            crcs, CollisionMap().apply { markLoaded(37, 79); markLoaded(38, 80) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(1), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            locTypes = LocTypes(
                listOf(
                    cave(FightCaves.ENTRANCE, "Cave entrance", listOf("Enter", null, null, null, null)),
                    cave(FightCaves.EXIT, "Cave entrance", listOf("Leave", "Escape", null, null, null)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(FightCaves.ENTRANCE, CENTREPIECE, 0, 2437, 5166, 0))
                add(ZoneLoc(FightCaves.EXIT, CENTREPIECE, 0, 2412, 5118, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun TestServer.run(name: String): FightCaves.Run? = world.playerNamed(name)?.let { world.fightCaves.runOf(it) }

    /** The run's living monsters once its wave [wave] has come in. */
    private fun HeadlessClient.waveIn(server: TestServer, name: String, wave: Int) = await("wave $wave", timeoutMillis = 20_000) {
        server.run(name)?.takeIf { it.wave == wave && it.nextWaveTick < 0 && it.monsters.isNotEmpty() }?.monsters?.toList()
    }

    private fun HeadlessClient.tokkul(): Int = inventory(INV).filterNotNull().filter { it.id == FightCaves.TOKKUL }.sumOf { it.count }

    @Test
    fun `the entrance makes a cave of one's own and the waves come in at the rotation's spots, one after another`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ash").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(2438, 5168)
                client.locOp(FightCaves.ENTRANCE, 2437, 5166, 1)
                val run = client.await("the run") { server.run("ash") }
                val arrival = run.at(FightCaves.ARRIVAL)
                client.await("in the cave") { client.position?.takeIf { it == Coord(arrival.x, arrival.z, 0) } }
                assertTrue(server.world.instances.at(arrival.x, arrival.z) === run.instance, "a cave of their own")
                assertTrue(run.rotation in 1..FightCaveWaves.ROTATIONS)

                val first = client.waveIn(server, "ash", 1).single()
                assertEquals(FightCaveMonster.TZ_KIH.first, first.type.id)
                val spot = run.at(FightCaveWaves.spots(1, run.rotation).single().tile)
                assertEquals(spot.x to spot.z, first.spawn.x to first.spawn.z)
                client.await("wave 1's line") { client.messages().firstOrNull { "fight_cave.wave" in it.text } }
                assertTrue(server.world.multiCombat.at(0, arrival.x, arrival.z), "the cave is multi-way")

                client.commandReply("kill ${first.index}") { it.startsWith("Killed") }
                val second = client.waveIn(server, "ash", 2)
                assertEquals(listOf(FightCaveMonster.TZ_KIH.first, FightCaveMonster.TZ_KIH.second), second.map { it.type.id })
                val spots = FightCaveWaves.spots(2, run.rotation).map { run.at(it.tile) }
                assertEquals(spots.map { it.x to it.z }, second.map { it.spawn.x to it.spawn.z })
                assertTrue(second.all { it.target === server.world.playerNamed("ash") }, "they go for the player")

                // Out by the exit, one wave done: 2 Tokkul, and the cave goes.
                val exit = run.at(osrsps.map.Tile(2412, 5118))
                client.locOp(FightCaves.EXIT, exit.x, exit.z, 1)
                client.await("outside") { client.position?.takeIf { it == Coord(FightCaves.OUTSIDE.x, FightCaves.OUTSIDE.z, 0) } }
                assertEquals(2, client.await("the Tokkul") { client.tokkul().takeIf { it > 0 } })
                assertNull(server.run("ash"))
                client.await("the cave gone") { server.world.instances.all.isEmpty().takeIf { it } }
                assertTrue(second.none { server.world.npcs[it.index] === it }, "its monsters went with it")
                assertEquals(0, server.world.playerNamed("ash")!!.vars[server.world.customVars.fightCaveVarp])
            }
        }
    }

    @Test
    fun `leaving the cave drops the shots its monsters have on their way`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cyd").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                // Wave 7 is a Tok-Xil alone.
                client.commandReply("fightcave 7 1") { it.startsWith("Fight cave") }
                client.waveIn(server, "cyd", 7)
                client.await("a shot on its way", timeoutMillis = 20_000) { server.world.meleeCombat.shotsInFlight.takeIf { it > 0 } }
                client.commandReply("fightcave leave") { true }
                client.await("the run over") { true.takeIf { server.run("cyd") == null } }
                assertEquals(0, server.world.meleeCombat.shotsInFlight)
            }
        }
    }

    @Test
    fun `a shot from the cave doesn't land on a player who teleported out before it arrived`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dot").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { true }
                client.commandReply("fightcave 7 1") { it.startsWith("Fight cave") }
                client.waveIn(server, "dot", 7)
                val player = server.world.playerNamed("dot")!!
                // Wait for a shot to leave, then teleport out with it on its way (a ::tele, which lands at once).
                client.await("a shot on its way", timeoutMillis = 20_000) { server.world.meleeCombat.shotsInFlight.takeIf { it > 0 } }
                val hp = player.hitpoints
                client.tele(2438, 5170)
                client.await("the shot gone") { true.takeIf { server.world.meleeCombat.shotsInFlight == 0 } }
                assertEquals(hp, player.hitpoints, "no hit outside")
            }
        }
    }

    @Test
    fun `a death in the cave is safe and pays for the waves done`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bea").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("item 995 7") { true }
                client.commandReply("fightcave 4 2") { it.startsWith("Fight cave") }
                client.waveIn(server, "bea", 4)
                client.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                client.await("outside after the death", timeoutMillis = 20_000) {
                    client.position?.takeIf { it == Coord(FightCaves.OUTSIDE.x, FightCaves.OUTSIDE.z, 0) }
                }
                // Three waves done: 2 + 4 + 6.
                assertEquals(12, client.await("the Tokkul") { client.tokkul().takeIf { it > 0 } })
                assertEquals(7, client.inventory(INV).filterNotNull().single { it.id == 995 }.count, "nothing lost")
                assertTrue(server.world.deaths.all.isEmpty(), "no grave")
                assertNull(server.run("bea"))
            }
        }
    }

    @Test
    fun `logout in a wave pauses the run at its end, and the next login starts the next wave in the middle of the cave`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cal").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("fightcave 2 1") { it.startsWith("Fight cave") }
                val wave = client.waveIn(server, "cal", 2)
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the pause and its warning") {
                    client.messages().map { it.text }.takeIf { m -> m.any { "fight_cave.logout_pause" in it } && m.any { "fight_cave.logout_repeat" in it } }
                }
                assertTrue(client.packets().none { it.prot.name == "LOGOUT" }, "not out before the wave ends")
                for (npc in wave) client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                client.await("the logout at the wave's end") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.login("cal").use { client ->
                client.await("the login rebuild") { client.position }
                val run = client.await("the run back") { server.run("cal") }
                assertEquals(3, run.wave)
                assertEquals(1, run.rotation)
                val middle = run.at(FightCaves.MIDDLE)
                client.await("in the middle") { client.position?.takeIf { it == Coord(middle.x, middle.z, 0) } }
                val next = client.waveIn(server, "cal", 3)
                assertEquals(listOf(FightCaveMonster.TZ_KEK.first), next.map { it.type.id })
            }
        }
    }

    @Test
    fun `logging out again before the wave ends starts that wave over at the next login`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dee").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("fightcave 1 1") { it.startsWith("Fight cave") }
                client.waveIn(server, "dee", 1)
                // The Tz-Kih comes in across the cave, so nothing has hit them yet and the second click logs them out.
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the pause") { client.messages().firstOrNull { "fight_cave.logout_pause" in it.text } }
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.login("dee").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(1, client.await("the run back") { server.run("dee") }.wave)
                assertEquals(FightCaveMonster.TZ_KIH.first, client.waveIn(server, "dee", 1).single().type.id)
            }
        }
    }

    @Test
    fun `TzTok-Jad's death ends the run with the fire cape and every wave's Tokkul`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("eve").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("fightcave 63 5") { it.startsWith("Fight cave") }
                val jad = client.waveIn(server, "eve", 63).single()
                assertEquals(FightCaveMonster.TZTOK_JAD.first, jad.type.id)
                val run = server.run("eve")!!
                val spot = run.at(FightCaveWaves.spots(3, 5).single().tile)
                assertEquals(spot.x to spot.z, jad.spawn.x to jad.spawn.z, "where wave 3's monster comes in")
                client.commandReply("kill ${jad.index}") { it.startsWith("Killed") }
                client.await("outside", timeoutMillis = 20_000) { client.position?.takeIf { it == Coord(FightCaves.OUTSIDE.x, FightCaves.OUTSIDE.z, 0) } }
                assertEquals(8_032, client.await("the Tokkul") { client.tokkul().takeIf { it > 0 } })
                assertNotNull(client.inventory(INV).firstOrNull { it?.id == FightCaves.FIRE_CAPE })
                client.awaitDialogue("TzHaar-Mej-Jal") { it is DialogueView.Chat && "fight_cave.victorious" in it.text }
            }
        }
    }

    @Test
    fun `the Fight Cave scenarios put the player in at their wave`() {
        val scenarios = Scenarios.load()
        val jad = scenarios["fight-cave-jad"]!!.minigame!!
        assertEquals(Scenario.ScenarioMinigame.FIGHT_CAVE, jad.name)
        assertEquals(63 to 1, jad.wave to jad.rotation)
        assertEquals(1 to null, scenarios["fight-cave"]!!.minigame!!.let { it.wave to it.rotation })
        assertEquals(31, scenarios["fight-cave-wave31"]!!.minigame!!.wave)
    }

    private companion object {
        const val CENTREPIECE = 10
        const val INV = 93
    }
}
