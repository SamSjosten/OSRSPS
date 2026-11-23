package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.skill.Pickpocketing
import osrsps.game.trail.SkillingClues
import osrsps.game.trail.TrailData
import osrsps.game.trail.TrailTier
import osrsps.map.CollisionMap

/**
 * Treasure Trails step TT9 through the headless client: skilling rolls a scroll box for each tier the player may still
 * take, and a clue scroll from a pickpocket, impling jar or chest comes as its scroll box, or not at all past the cap.
 * A base chance of 1 makes every roll hit; the real ones are the Wiki's.
 */
class HeadlessTreasureTrailsSourcesTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(25_000)
        for (tier in TrailTier.entries) objs[tier.scrollBox] = ObjType(tier.scrollBox, "Scroll box (${tier.key})", stackable = true)
        objs[Pickpocketing.CLUE_EASY] = ObjType(Pickpocketing.CLUE_EASY, "Clue scroll (easy)", iops = listOf("Read", null, null, null, "Drop"), params = mapOf(TrailTier.EASY.param to 1))
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), trailData = TrailData(emptyList()), cycleMillis = millis)
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    @Test
    fun `a skilling success gives a scroll box of each tier from beginner to elite that the player may still take`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("rue").use { client ->
                client.await("the login rebuild") { client.position }
                val player = server.world.playerNamed("rue")!!
                player.inventory[5] = InvObj(TrailTier.EASY.scrollBox, 2)
                server.world.trails.skilling.rolled(player, Skill.WOODCUTTING, 1)
                client.await("the boxes") { if (client.count(TrailTier.ELITE.scrollBox) == 1) Unit else null }
                for (tier in listOf(TrailTier.BEGINNER, TrailTier.MEDIUM, TrailTier.HARD)) assertEquals(1, client.count(tier.scrollBox), tier.key)
                assertEquals(2, client.count(TrailTier.EASY.scrollBox), "easy is at its cap")
                assertEquals(0, client.count(TrailTier.MASTER.scrollBox), "skilling gives no master")
                server.world.trails.skilling.rolled(player, Skill.WOODCUTTING, null)
                client.awaitTicks(2)
                assertEquals(1, client.count(TrailTier.BEGINNER.scrollBox), "no base chance, no roll")
            }
        }
    }

    @Test
    fun `a clue scroll from a source comes as its scroll box, and past the cap as a sneaking suspicion`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("sol").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { true }
                val player = server.world.playerNamed("sol")!!
                val clue = server.world.objTypes[Pickpocketing.CLUE_EASY]!!
                server.world.trails.give(player, clue, 1)
                client.await("one box") { if (client.count(TrailTier.EASY.scrollBox) == 1) Unit else null }
                assertEquals(0, client.count(Pickpocketing.CLUE_EASY), "no plain clue scroll")
                server.world.trails.give(player, clue, 1)
                client.await("two boxes") { if (client.count(TrailTier.EASY.scrollBox) == 2) Unit else null }
                val from = client.messages().size
                server.world.trails.give(player, clue, 1)
                client.await("the suspicion") { client.messages().drop(from).firstOrNull { it.text == "[trail.suspicion_easy]" } }
                assertEquals(2, client.count(TrailTier.EASY.scrollBox))
            }
        }
    }

    @Test
    fun `taking scroll boxes past the cap destroys the rest, and a second clue of a held tier stays on the ground`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ash").use { client ->
                val at = client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { true }
                val player = server.world.playerNamed("ash")!!
                player.inventory[5] = InvObj(TrailTier.EASY.scrollBox, 1)
                server.world.groundObjs.addPublic(server.world.objTypes[TrailTier.EASY.scrollBox]!!, 3, at.x, at.z, at.level)
                client.await("the boxes on the ground") { client.groundObjs().firstOrNull { it.id == TrailTier.EASY.scrollBox } }
                var from = client.messages().size
                client.objOp(TrailTier.EASY.scrollBox, at.x, at.z, TAKE)
                client.await("the destroyed line") { client.messages().drop(from).firstOrNull { it.text == "[trail.boxes_destroyed]" } }
                client.await("two boxes, the cap") { if (client.count(TrailTier.EASY.scrollBox) == 2) Unit else null }
                client.awaitTicks(1)
                assertTrue(client.groundObjs().none { it.id == TrailTier.EASY.scrollBox }, "the rest are destroyed")

                player.inventory[7] = InvObj(Pickpocketing.CLUE_EASY, 1)
                server.world.groundObjs.addPublic(server.world.objTypes[Pickpocketing.CLUE_EASY]!!, 1, at.x, at.z, at.level)
                client.await("the clue on the ground") { client.groundObjs().firstOrNull { it.id == Pickpocketing.CLUE_EASY } }
                from = client.messages().size
                client.objOp(Pickpocketing.CLUE_EASY, at.x, at.z, TAKE)
                client.await("the refusal") { client.messages().drop(from).firstOrNull { it.text == "[trail.already_clue]" } }
                assertEquals(1, client.count(Pickpocketing.CLUE_EASY))
                assertTrue(client.groundObjs().any { it.id == Pickpocketing.CLUE_EASY }, "the clue stays where it lies")
            }
        }
    }

    @Test
    fun `the rates are the Wiki's`() {
        // The Wiki's Clue nest formula at a normal tree (317,647) and 99 Woodcutting: 317,647 / 199 × 0.2, rounded down.
        assertEquals(319, SkillingClues.chance(317_647, 99, TrailTier.BEGINNER))
        assertEquals(15_962, SkillingClues.chance(317_647, 99, TrailTier.ELITE))
        // Mining takes 70% of the base: runite's 42,377 is 29,663 before the level.
        assertEquals(29_663 * 10 / 185, SkillingClues.chance((42_377 * SkillingClues.MINING_FACTOR).toInt(), 85, TrailTier.ELITE))
        assertTrue(Pickpocketing.HAM_LOOT.any { it.obj == Pickpocketing.CLUE_EASY }, "the H.A.M. members' easy clue")
    }

    private companion object {
        const val BACKPACK = 93
        const val TAKE = 3
    }
}
