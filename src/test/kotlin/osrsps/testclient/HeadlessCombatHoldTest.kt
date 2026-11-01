package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Who an NPC belongs to in single-way combat, from the exploit review's round 7: an aggressive monster doesn't join a
 * fight the player is in, and one hit doesn't hold an NPC for good. ::pause, ::tele and ::god only make the timing exact.
 */
class HeadlessCombatHoldTest {
    private val spider = NpcType(3017, "Giant spider", ops = listOf(null, "Attack", null, null, null), combatLevel = 2, internalName = "giantspider1", stats = NpcStats(hitpoints = 5))
    // Stands in for any strong non-aggressive single-way monster; lots of hitpoints so the fight lasts the whole test.
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 99))

    private fun server(dir: Path, spiderX: Int, spiderZ: Int) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(spider, man)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(
                NpcSpawn(spider, spiderX, spiderZ, source = "test"),
                NpcSpawn(man, MAN_X, MAN_Z, source = "test"),
            ),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    private fun World.man(): Npc = npcs.all.first { it.type.id == man.id }

    private fun HeadlessClient.setUp() {
        await("the login rebuild") { position }
        commandReply("god") { it.startsWith("You can") }
        commandReply("pause") { it.startsWith("Paused after tick") }
    }

    @Test
    fun `an aggressive spider beside a player fighting a man leaves them alone, and the man hits back`(@TempDir dir: Path) {
        // The spider just north of the attacking tile, the man just east of it.
        server(dir, spiderX = MAN_X - 1, spiderZ = MAN_Z + 1).use { server ->
            server.login("alice").use { alice ->
                alice.setUp()
                val world = server.world
                val npc = world.man()
                val player = assertNotNull(world.playerNamed("alice"))
                alice.commandReply("tele ${MAN_X - 1} $MAN_Z") { it.startsWith("Teleported") }
                alice.npcOp(npc.index, 2)
                var manHits = 0
                repeat(60) {
                    alice.stepTicks(1)
                    if (player.attackedBy === npc) manHits++
                }
                val spiderNpc = world.npcs.all.first { it.type.id == spider.id }
                assertNull(spiderNpc.target, "the spider leaves a player fighting the man alone")
                assertTrue(npc.hitpoints < man.stats.hitpoints, "the player's hits land: ${npc.hitpoints}")
                assertSame(player, npc.target, "the man fights the player hitting him")
                assertTrue(manHits > 0, "the man hits back")
            }
        }
    }

    /** With the spider far away, the same fight has the man hitting back within a few ticks. */
    @Test
    fun `without the spider the man hits back within six ticks`(@TempDir dir: Path) {
        server(dir, spiderX = MAN_X + 30, spiderZ = MAN_Z + 30).use { server ->
            server.login("alice").use { alice ->
                alice.setUp()
                val world = server.world
                val npc = world.man()
                val player = assertNotNull(world.playerNamed("alice"))
                alice.commandReply("tele ${MAN_X - 1} $MAN_Z") { it.startsWith("Teleported") }
                alice.npcOp(npc.index, 2)
                alice.stepTicks(6)
                assertSame(npc, player.attackedBy, "the man fights back")
            }
        }
    }

    /**
     * One hit, then standing 8 to 18 tiles from the man's spawn: past the 7 tiles he follows, inside the 18 he chases.
     * He never reaches her, so once neither has fought for [MeleeCombat.IN_COMBAT_TICKS] ticks he is no longer hers.
     */
    @Test
    fun `one hit and standing past the man's leash doesn't keep him hers`(@TempDir dir: Path) {
        server(dir, spiderX = MAN_X + 30, spiderZ = MAN_Z + 30).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.setUp()
                    bob.await("the login rebuild") { bob.position }
                    val world = server.world
                    val npc = world.man()
                    val attacker = assertNotNull(world.playerNamed("alice"))
                    val other = assertNotNull(world.playerNamed("bob"))
                    alice.commandReply("tele ${MAN_X - 1} $MAN_Z") { it.startsWith("Teleported") }
                    alice.npcOp(npc.index, 2)
                    alice.stepTicks(2)
                    assertSame(attacker, npc.target, "the hit landed and the man turned on her")
                    // She walks off (teleported here for speed) to 12 tiles east of his spawn: past his 7-tile leash,
                    // inside the 18-tile chase range.
                    alice.commandReply("tele ${MAN_X + 12} $MAN_Z") { it.startsWith("Teleported") }
                    alice.stepTicks(40)
                    assertNull(attacker.attackedBy, "he never reached her to hit her")
                    assertTrue(npc.x <= MAN_X + 7, "held on his leash at ${npc.x}")
                    assertTrue(world.tick - npc.attackedTick > MeleeCombat.IN_COMBAT_TICKS, "her last hit is long past the hold")
                    // Bob comes up beside the man where he stands now.
                    bob.commandReply("tele ${npc.x} ${npc.z - 1}") { it.startsWith("Teleported") }
                    alice.stepTicks(1)
                    bob.await("the man in view") { bob.npcs().firstOrNull { it.index == npc.index } }
                    val from = bob.messages().size
                    bob.npcOp(npc.index, 2)
                    alice.stepTicks(1)
                    assertFalse(world.meleeCombat.foughtByAnother(other, npc), "he is free for others")
                    alice.stepTicks(3)
                    val said = bob.messages().drop(from).map { it.text }
                    assertFalse(said.any { "someone_else" in it }, "bob isn't refused: $said")
                    assertSame(other, npc.attacker, "bob hit him")
                }
            }
        }
    }

    private companion object {
        const val MAN_X = 3231
        const val MAN_Z = 3218
    }
}
