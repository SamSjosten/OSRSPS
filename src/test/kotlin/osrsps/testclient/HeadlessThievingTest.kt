package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.DraynorMarket
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Thieving
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Draynor piece step 7 through the headless client: stealing from the seed and wine stalls, the checks before a theft,
 * and a Market Guard who sees it. The rl-client script test-runner/examples/draynor-stalls.json steals in the real
 * market.
 */
class HeadlessThievingTest {
    private val guard = NpcType(
        Thieving.MARKET_GUARD, "Market Guard", ops = listOf(null, "Attack", null, null, null), combatLevel = 20,
        internalName = "farming_market_guard", stats = NpcStats(hitpoints = 22),
    )
    private val olivia = NpcType(Thieving.OLIVIA, "Olivia", ops = listOf("Talk-to", null, "Trade", null, null), internalName = "seed_merchant")

    private fun stall(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops.toList() + List(5 - ops.size) { null }, LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path, spawns: List<NpcSpawn> = emptyList()) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(8_000)
        for (stall in Thieving.STALLS.values) for (loot in stall.loot) objs[loot.obj] = ObjType(loot.obj, "Item ${loot.obj}")
        objs[POT] = ObjType(POT, "Pot")
        val npcTypes = NpcTypes.of(guard, olivia)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(7),
            npcTypes = npcTypes, npcSpawns = spawns, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE),
            locTypes = LocTypes(
                listOf(
                    stall(SEED_STALL, "Seed Stall", null, "Steal from"),
                    stall(Thieving.STALLS.getValue(SEED_STALL).empty, "Market stall"),
                    stall(WINE_STALL, "Market stall", null, "Steal-From"),
                    stall(Thieving.STALLS.getValue(WINE_STALL).empty, "Market stall"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(SEED_STALL, LocShape.CENTREPIECE_STRAIGHT, 0, 3224, 3218, 0))
                add(ZoneLoc(WINE_STALL, LocShape.CENTREPIECE_STRAIGHT, 0, 3220, 3218, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.items() = inventory(BACKPACK).filterNotNull().filter { it.id != POT }

    @Test
    fun `a seed stall gives one seed and 10 XP, empties and comes back 4 ticks later`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 27")
                client.await("level 27") { client.stat(THIEVING)?.takeIf { it.level == 27 && it.xp == 9_730 } }
                client.locOp(SEED_STALL, 3224, 3218, 2)
                val seed = client.await("a seed") { client.items().singleOrNull() }
                assertTrue(Thieving.SEED_LOOT.any { it.obj == seed.id })
                client.seen("[thieving.stolen item=Item ${seed.id}]")
                // 9,730 XP at level 27, then the theft's 10.
                assertEquals(9_740, client.await("Thieving XP") { client.stat(THIEVING)?.takeIf { it.xp > 9_730 } }.xp)
                client.await("the empty stall") { if (client.locAt(3224, 3218) == Thieving.STALLS.getValue(SEED_STALL).empty) Unit else null }
                client.await("the stall back") { if (client.locAt(3224, 3218) == SEED_STALL) Unit else null }
            }
        }
    }

    @Test
    fun `the wine stall needs level 22 and a free slot`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(WINE_STALL, 3220, 3218, 2)
                client.awaitDialogue("the level box") { it is DialogueView.Message && it.text == "[thieving.level level=22 stall=market stall]" }
                client.continueDialogue()
                client.command("setlevel thieving 22")
                client.command("item $POT 28")
                client.await("a full backpack") { if (client.inventory(BACKPACK).count { it != null } == 28) Unit else null }
                client.locOp(WINE_STALL, 3220, 3218, 2)
                client.awaitDialogue("the full box") { it is DialogueView.Message && it.text == "[thieving.inventory_full]" }
                assertEquals(emptyList(), client.items())
            }
        }
    }

    @Test
    fun `a Market Guard in sight stops the theft and attacks, and Olivia won't trade after one`(@TempDir dir: Path) {
        val spawns = listOf(NpcSpawn(guard, 3226, 3221, source = "test"), NpcSpawn(olivia, 3222, 3211, source = "test"))
        server(dir, spawns).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 27")
                client.await("level 27") { client.stat(THIEVING)?.takeIf { it.level == 27 } }
                client.locOp(SEED_STALL, 3224, 3218, 2)
                // The shout goes with the guard turning to face the player, a block the headless client's NPC reader skips
                // (it reads a say block alone), so it isn't checked here; the rl-client script sees it.
                val npc = server.world.npcs.all.single { it.spawn.type.id == Thieving.MARKET_GUARD }
                val player = server.world.onlinePlayers.single()
                client.await("the guard attacking") { if (npc.target === player) Unit else null }
                assertSame(player, npc.target)
                assertEquals(emptyList(), client.items())
                assertNull(client.locAt(3224, 3218))

                // With the guard gone (and Olivia out of sight, 7 tiles off), the theft goes through, and Olivia won't trade for
                // 30 minutes.
                server.world.npcs.remove(npc)
                client.awaitTicks(2)
                client.locOp(SEED_STALL, 3224, 3218, 2)
                client.await("a seed") { client.items().singleOrNull() }
                val oliviaNpc = server.world.npcs.all.single { it.spawn.type.id == Thieving.OLIVIA }
                assertTrue(server.world.thieving.refuses(player, oliviaNpc, server.world.tick))
                assertEquals(false, server.world.thieving.refuses(player, oliviaNpc, server.world.tick + Thieving.OLIVIA_REFUSE_TICKS + 1))
            }
        }
    }

    @Test
    fun `of two guards Olivia calls, only one fights in single-way combat`(@TempDir dir: Path) {
        // Olivia 7 tiles off, out of sight of the theft.
        server(dir, listOf(NpcSpawn(olivia, 3222, 3211, source = "test"))).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("god on")
                client.command("varp 172 1")
                client.command("setlevel thieving 27")
                client.await("level 27") { client.stat(THIEVING)?.takeIf { it.level == 27 } }
                client.locOp(SEED_STALL, 3224, 3218, 2)
                client.await("a seed") { client.items().singleOrNull() }
                val player = server.world.onlinePlayers.single()
                val oliviaNpc = server.world.npcs.all.single { it.spawn.type.id == Thieving.OLIVIA }
                // Two guards by her, in sight of the player, when the player next deals with her.
                val guards = listOf(3221, 3223).map { x -> server.world.npcs.add(NpcSpawn(guard, x, 3213, source = "test"))!! }
                client.awaitTicks(1)
                client.npcOp(oliviaNpc.index, DraynorMarket.TALK_TO)
                // Her two lines, the second a tick after the first. Read on the server: the second goes with her turning
                // back, a block the headless client's NPC reader skips.
                val said = mutableMapOf<String, Long>()
                val deadline = System.nanoTime() + 10_000_000_000L
                while (said.size < 2) {
                    check(System.nanoTime() < deadline) { "Timed out waiting for her two lines: $said" }
                    val tick = server.world.tick
                    val say = oliviaNpc.effects.say
                    if (say != null && say !in said && server.world.tick == tick) said[say] = tick
                    // A tight spin: a line is only there between the NPC phase and the end of its tick.
                    Thread.onSpinWait()
                }
                assertEquals(said.getValue("[thieving.owner_angry]") + 1, said.getValue("[thieving.guards]"))
                val first = client.await("a guard's hit") { player.attackedBy }
                // Within the 8 ticks a player's own hit would keep both on them, only the first guard does.
                client.awaitTicks(2)
                assertEquals(listOf(first), guards.filter { it.target === player })
                val hitters = mutableSetOf(first)
                val end = server.world.tick + 8
                while (server.world.tick < end) {
                    player.attackedBy?.let { hitters += it }
                    Thread.sleep(5)
                }
                assertEquals(setOf(first), hitters)
            }
        }
    }

    @Test
    fun `a guard fighting another player stays on them when a theft is seen`(@TempDir dir: Path) {
        server(dir, listOf(NpcSpawn(guard, 3226, 3221, source = "test"))).use { server ->
            server.login("frank").use { fighter ->
                fighter.await("the login rebuild") { fighter.position }
                fighter.command("god on")
                val npc = server.world.npcs.all.single { it.spawn.type.id == Thieving.MARKET_GUARD }
                val frank = server.world.onlinePlayers.single()
                fighter.npcOp(npc.index, 2)
                fighter.await("frank fighting the guard") { if (npc.target === frank && npc.attacker === frank) Unit else null }
                server.login("gina").use { thief ->
                    thief.await("the login rebuild") { thief.position }
                    thief.command("setlevel thieving 27")
                    thief.await("level 27") { thief.stat(THIEVING)?.takeIf { it.level == 27 } }
                    // The guard still sees it and stops it, but keeps on Frank.
                    thief.locOp(SEED_STALL, 3224, 3218, 2)
                    thief.await("the guard's shout") { thief.npcSaid().firstOrNull { it.index == npc.index && it.text == "[thieving.caught]" } }
                    thief.awaitTicks(2)
                    assertSame(frank, npc.target)
                    assertEquals(emptyList(), thief.items())
                }
            }
        }
    }

    @Test
    fun `an owner's ban ends at logout`(@TempDir dir: Path) {
        server(dir, listOf(NpcSpawn(olivia, 3222, 3211, source = "test"))).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 27")
                client.await("level 27") { client.stat(THIEVING)?.takeIf { it.level == 27 } }
                client.locOp(SEED_STALL, 3224, 3218, 2)
                client.await("a seed") { client.items().singleOrNull() }
                val oliviaNpc = server.world.npcs.all.single { it.spawn.type.id == Thieving.OLIVIA }
                assertTrue(server.world.thieving.refuses(server.world.onlinePlayers.single(), oliviaNpc, server.world.tick))
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            val deadline = System.nanoTime() + 10_000_000_000L
            while (server.world.playerCount != 0) {
                check(System.nanoTime() < deadline) { "Timed out waiting for the logout" }
                Thread.sleep(10)
            }
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                val oliviaNpc = server.world.npcs.all.single { it.spawn.type.id == Thieving.OLIVIA }
                assertEquals(false, server.world.thieving.refuses(server.world.onlinePlayers.single(), oliviaNpc, server.world.tick))
            }
        }
    }

    private companion object {
        const val SEED_STALL = 7053
        const val WINE_STALL = 14011
        const val POT = 1931
        const val BACKPACK = 93
        const val THIEVING = 17
    }
}
