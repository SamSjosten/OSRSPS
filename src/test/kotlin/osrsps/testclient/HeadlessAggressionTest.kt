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
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.Aggression
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap
import osrsps.map.LocCollision
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Aggression through two headless players, with the real NPC combat file: a giant spider goes for the nearer of
 * them, and once that one has had their ten minutes, for the other; and it leaves a player behind a wall alone until
 * the wall goes; and one lured off past its range lets go, walks home and goes for the next player. The rl-client
 * scripts test-runner/examples/aggro.json, aggro-sight.json and aggro-leash.json do these in the real client.
 */
class HeadlessAggressionTest {
    private val spider = NpcType(3017, "Giant spider", ops = listOf(null, "Attack", null, null, null), combatLevel = 2, internalName = "giantspider1", stats = NpcStats(hitpoints = 5))

    private fun server(dir: Path, map: CollisionMap = CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(spider)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, map, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            // Far enough from the login tile that it leaves everyone alone until they are put beside it.
            npcSpawns = listOf(NpcSpawn(spider, 3245, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    /** The spider's line in `::npcs`. */
    private fun spiderLine(client: HeadlessClient): String {
        val from = client.messages().size
        client.command("npcs 30")
        return client.await("the spider's line") { client.messages().drop(from).firstOrNull { it.text.startsWith("#") && " Giant spider " in it.text } }.text
    }

    @Test
    fun `a giant spider goes for the nearer of two players, and for the other once the nearer is tolerated`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }
                    // Paused, so both stand by it before it looks.
                    alice.commandReply("pause") { it.startsWith("Paused after tick") }
                    alice.commandReply("tele 3248 3218") { it.startsWith("Teleported") }
                    bob.commandReply("tele 3246 3219") { it.startsWith("Teleported") }
                    alice.stepTicks(1)
                    assertTrue(spiderLine(alice).endsWith("; fighting bob"), "the nearer")
                    // Bob leaves, and after ten minutes by it he is tolerated anyway.
                    bob.commandReply("tele 3222 3218") { it.startsWith("Teleported") }
                    alice.stepTicks(1)
                    assertTrue(spiderLine(alice).endsWith("; fighting no one"), "it lets bob go once he is out of its range")
                    bob.commandReply("tele 3246 3219") { it.startsWith("Teleported") }
                    alice.stepTicks(1)
                    assertTrue(spiderLine(alice).endsWith("; fighting bob"), "bob is still the nearer")
                    bob.commandReply("tele 3222 3218") { it.startsWith("Teleported") }
                    alice.stepTicks(1)
                    bob.commandReply("tele 3246 3219") { it.startsWith("Teleported") }
                    assertEquals(
                        "${Aggression.TOLERANCE_TICKS} ticks in the areas around 3222, 3218 and 3246, 3219: aggressive monsters here are tolerant of you.",
                        bob.commandReply("aggro ${Aggression.TOLERANCE_TICKS}") { it.contains("ticks in the areas") },
                    )
                    alice.stepTicks(1)
                    assertTrue(spiderLine(alice).endsWith("; fighting alice"), "bob is tolerated, so alice")
                    alice.commandReply("resume") { it.startsWith("Resumed") }
                }
            }
        }
    }

    @Test
    fun `a giant spider leaves a player behind a wall alone, and goes for them once the wall is gone`(@TempDir dir: Path) {
        val map = CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }
        // A projectile-blocking loc between the spider's tile and where alice will stand, as a shut door would be.
        val wall = LocCollisionType(blocksProjectile = true)
        LocCollision(map).addLoc(wall, 3247, 3218, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
        server(dir, map).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("pause") { it.startsWith("Paused after tick") }
                // 4 tiles east of it, within its hunt range, behind the wall.
                alice.commandReply("tele 3249 3218") { it.startsWith("Teleported") }
                alice.stepTicks(3)
                assertTrue(spiderLine(alice).endsWith("; fighting no one"), "it can't see her")
                // The door opens.
                LocCollision(map).removeLoc(wall, 3247, 3218, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
                alice.stepTicks(1)
                assertTrue(spiderLine(alice).endsWith("; fighting alice"), "in sight now")
                alice.commandReply("resume") { it.startsWith("Resumed") }
            }
        }
    }

    /**
     * The exploit review's COMB-3, the other way round: a player who lures it out past the 7 tiles it follows to, and
     * stands there, no longer keeps it from going for anyone else.
     */
    @Test
    fun `a giant spider lured off past its range lets go, walks home and goes for the next player`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }
                    alice.commandReply("god") { it.startsWith("You can") }
                    bob.commandReply("god") { it.startsWith("You can") }
                    val world = server.world
                    val spiderNpc = world.npcs.all.first { it.type.id == spider.id }
                    val lurer = assertNotNull(world.playerNamed("alice"))
                    val victim = assertNotNull(world.playerNamed("bob"))
                    // Bob waits well out of its hunt range; alice steps up beside it and it goes for her.
                    bob.commandReply("tele ${SPIDER_X - 15} $SPIDER_Z") { it.startsWith("Teleported") }
                    alice.commandReply("tele ${SPIDER_X - 1} $SPIDER_Z") { it.startsWith("Teleported") }
                    alice.commandReply("pause") { it.startsWith("Paused after tick") }
                    var turned = false
                    repeat(10) { if (!turned) { alice.stepTicks(1); turned = spiderNpc.target === lurer } }
                    assertTrue(turned, "the spider went for alice")
                    // She walks off (teleported for speed) 12 tiles east of its spawn: past the 7 it follows to, within 18.
                    alice.commandReply("tele ${SPIDER_X + 12} $SPIDER_Z") { it.startsWith("Teleported") }
                    alice.stepTicks(1)
                    val leftAt = world.tick
                    alice.stepTicks(20)
                    assertTrue(spiderLine(alice).endsWith("; fighting no one"), "it let go of her")
                    assertEquals(SPIDER_X to SPIDER_Z, spiderNpc.x to spiderNpc.z, "and walked home")
                    assertTrue(lurer.attackedTick < leftAt, "it never reached her out there")
                    // Bob steps up beside it, and it goes for him.
                    bob.commandReply("tele $SPIDER_X ${SPIDER_Z - 1}") { it.startsWith("Teleported") }
                    assertNull(victim.attackedBy)
                    alice.stepTicks(3)
                    assertTrue(spiderLine(alice).endsWith("; fighting bob"), "it went for bob")
                    alice.commandReply("resume") { it.startsWith("Resumed") }
                }
            }
        }
    }

    private companion object {
        const val SPIDER_X = 3245
        const val SPIDER_Z = 3218
    }
}
