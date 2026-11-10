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
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Pickpocketing
import osrsps.game.skill.Thieving
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Thieving 2 step T3 through the headless client: East Ardougne's gem stall (75, 408 XP, a gem, 100 ticks empty), the
 * bakery stall seen by the second baker, and the gem stall seen by a paladin, who attacks. The rl-client script
 * test-runner/examples/thieving-t3.json steals in the real market.
 */
class HeadlessArdougneStallsTest {
    private val baker2 = NpcType(Thieving.ARDOUGNE_BAKER_2, "Baker", ops = listOf("Talk-to", null, "Trade", null, null), internalName = "baker_merchant_ardougne2")
    private val paladin = NpcType(
        Pickpocketing.PALADINS.first(), "Paladin", ops = listOf(null, "Attack", "Pickpocket", null, null), combatLevel = 62,
        internalName = "paladin", stats = NpcStats(hitpoints = 66),
    )

    private fun stall(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops.toList() + List(5 - ops.size) { null }, LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path, spawns: List<NpcSpawn> = emptyList()) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(8_000)
        for (stall in Thieving.ARDOUGNE_STALLS) for (loot in stall.loot) objs[loot.obj] = ObjType(loot.obj, "Item ${loot.obj}")
        val npcTypes = NpcTypes.of(baker2, paladin)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(7),
            npcTypes = npcTypes, npcSpawns = spawns, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE),
            locTypes = LocTypes(
                listOf(
                    stall(Thieving.GEM_STALL, "Gem stall", null, "Steal-from"),
                    stall(Thieving.BAKERY_STALL, "Baker's stall", null, "Steal-from"),
                    stall(Thieving.EMPTY_STALL, "Market stall"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Thieving.GEM_STALL, LocShape.CENTREPIECE_STRAIGHT, 0, 3224, 3218, 0))
                add(ZoneLoc(Thieving.BAKERY_STALL, LocShape.CENTREPIECE_STRAIGHT, 0, 3220, 3218, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.items() = inventory(BACKPACK).filterNotNull()

    @Test
    fun `the gem stall needs 75, gives a gem and 408 XP, and stays empty 100 ticks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 74")
                client.await("level 74") { client.stat(THIEVING)?.takeIf { it.level == 74 } }
                client.locOp(Thieving.GEM_STALL, 3224, 3218, 2)
                client.awaitDialogue("the level box") { it is DialogueView.Message && it.text == "[thieving.level level=75 stall=gem stall]" }
                client.continueDialogue()
                client.command("setlevel thieving 75")
                val xp = client.await("level 75") { client.stat(THIEVING)?.takeIf { it.level == 75 } }.xp
                client.locOp(Thieving.GEM_STALL, 3224, 3218, 2)
                val gem = client.await("a gem") { client.items().singleOrNull() }
                assertTrue(Thieving.GEM_LOOT.any { it.obj == gem.id })
                assertEquals(xp + 408, client.await("Thieving XP") { client.stat(THIEVING)?.takeIf { it.xp > xp } }.xp)
                client.await("the empty stall") { if (client.locAt(3224, 3218) == Thieving.EMPTY_STALL) Unit else null }
                val emptied = server.world.tick
                client.await("the stall back", timeoutMillis = 20_000) { if (client.locAt(3224, 3218) == Thieving.GEM_STALL) Unit else null }
                assertTrue(server.world.tick - emptied >= 99, "empty for 100 ticks")
            }
        }
    }

    @Test
    fun `East Ardougne's second baker sees a theft from the bakery stall, and won't deal after one`(@TempDir dir: Path) {
        server(dir, listOf(NpcSpawn(baker2, 3220, 3221, wander = 0, source = "test"))).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 5")
                client.await("level 5") { client.stat(THIEVING)?.takeIf { it.level == 5 } }
                client.locOp(Thieving.BAKERY_STALL, 3220, 3218, 2)
                client.awaitTicks(6)
                assertEquals(emptyList(), client.items(), "the baker saw it")
                val baker = server.world.npcs.all.single { it.spawn.type.id == Thieving.ARDOUGNE_BAKER_2 }
                server.world.npcs.remove(baker)
                client.awaitTicks(2)
                client.locOp(Thieving.BAKERY_STALL, 3220, 3218, 2)
                val bread = client.await("a cake, bread or chocolate slice") { client.items().singleOrNull() }
                assertTrue(Thieving.BAKERY_LOOT.any { it.obj == bread.id })
                val player = server.world.onlinePlayers.single()
                // Both bakers keep the stall, so the theft bans the player with each.
                assertTrue(server.world.thieving.refuses(player, baker, server.world.tick))
            }
        }
    }

    @Test
    fun `a paladin in sight of the gem stall stops the theft and attacks`(@TempDir dir: Path) {
        server(dir, listOf(NpcSpawn(paladin, 3226, 3221, wander = 0, source = "test"))).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 99")
                client.await("level 99") { client.stat(THIEVING)?.takeIf { it.level == 99 } }
                client.locOp(Thieving.GEM_STALL, 3224, 3218, 2)
                val npc = server.world.npcs.all.single { it.spawn.type.id == paladin.id }
                val player = server.world.onlinePlayers.single()
                client.await("the paladin attacking") { if (npc.target === player) Unit else null }
                assertEquals(emptyList(), client.items())
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val THIEVING = 17
    }
}
