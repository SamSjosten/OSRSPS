package osrsps.game.skill.magic

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.map.CollisionMap
import osrsps.testclient.TestServer

/**
 * The charged jewellery of Magic utility step U6b, on a world with only its items: the uses are the player's (a second
 * ring carries on where the first stopped), the last one crumbles the piece and the next starts full; clay comes out
 * soft; an expeditious bracelet counts some kills twice and a bracelet of slaughter some not at all, a quarter each;
 * an amulet of chemistry makes about one 3-dose potion in 20 a 4-dose one; an amulet of bounty saves seeds about a
 * quarter of the time, and only from three. The furnace, rock, task and patch they hook into are tested through
 * test-runner/examples/magic-jewellery-skilling.json on the live cache.
 */
class JewelleryEffectsTest {
    private val drink = listOf("Drink", null, null, null, "Drop")

    /**
     * Runs [block] on a logged-in player of a world with only these items, paused so the test thread can change the
     * player safely (messages need the session a login gives).
     */
    private fun withPlayer(dir: Path, block: (JewelleryEffects, Player) -> Unit) {
        TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(11), cycleMillis = millis,
                objTypes = ObjTypes.of(
                    ObjType(FORGING, "Ring of forging", internalName = "ring_of_forging"),
                    ObjType(CLAY_BRACELET, "Bracelet of clay", internalName = "jewl_bracelet_of_clay"),
                    ObjType(CHEMISTRY, "Amulet of chemistry", internalName = "amulet_of_chemistry"),
                    ObjType(BOUNTY, "Amulet of bounty", internalName = "amulet_of_bounty"),
                    ObjType(EXPEDITIOUS, "Expeditious bracelet", internalName = "expeditious_bracelet"),
                    ObjType(SLAUGHTER, "Bracelet of slaughter", internalName = "bracelet_of_slaughter"),
                    *ATTACK.mapIndexed { i, id -> ObjType(id, "Attack potion(${i + 1})", iops = drink, tradeable = true) }.toTypedArray(),
                ),
            )
        }.use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("pause") { it.startsWith("Paused") }
                block(server.world.jewelleryEffects, server.world.playerNamed("alice")!!)
            }
        }
    }

    @Test
    fun `a ring of forging's 140 uses are the player's, and the last crumbles it`(@TempDir dir: Path) = withPlayer(dir) { effects, player ->
        player.equipment[WearPos.RING] = InvObj(FORGING, 1)
        repeat(100) { assertTrue(effects.use(player, effects.forging)) }
        // Another ring carries on from 100.
        player.equipment[WearPos.RING] = InvObj(FORGING, 1)
        repeat(39) { assertTrue(effects.use(player, effects.forging)) }
        assertEquals(FORGING, player.equipment[WearPos.RING]?.id)
        assertTrue(effects.use(player, effects.forging), "the 140th")
        assertNull(player.equipment[WearPos.RING], "crumbled")
        assertEquals(0, player.charges[effects.forging!!.charges], "the next starts full")
        assertTrue(!effects.use(player, effects.forging), "nothing worn")
    }

    @Test
    fun `clay comes out soft with a bracelet of clay, 28 times`(@TempDir dir: Path) = withPlayer(dir) { effects, player ->
        assertEquals(JewelleryEffects.CLAY, effects.minedOre(player, JewelleryEffects.CLAY))
        player.equipment[WearPos.HANDS] = InvObj(CLAY_BRACELET, 1)
        assertEquals(IRON_ORE, effects.minedOre(player, IRON_ORE), "only clay")
        repeat(28) { assertEquals(JewelleryEffects.SOFT_CLAY, effects.minedOre(player, JewelleryEffects.CLAY)) }
        assertNull(player.equipment[WearPos.HANDS])
    }

    @Test
    fun `the Slayer bracelets change a quarter of the kills each`(@TempDir dir: Path) = withPlayer(dir) { effects, player ->
        assertEquals(1, effects.taskKills(player))
        player.equipment[WearPos.HANDS] = InvObj(EXPEDITIOUS, 1)
        val counts = List(28) { effects.taskKills(player) }
        assertTrue(counts.all { it == 1 || it == 2 } && counts.count { it == 2 } in 2..14, counts.toString())
        assertEquals(counts.count { it == 2 }, player.charges[effects.expeditious!!.charges], "a use for each double kill")
        player.equipment[WearPos.HANDS] = InvObj(SLAUGHTER, 1)
        val slaughtered = List(28) { effects.taskKills(player) }
        assertTrue(slaughtered.all { it == 0 || it == 1 } && slaughtered.count { it == 0 } in 2..14, slaughtered.toString())
    }

    @Test
    fun `an amulet of chemistry makes about one 3-dose potion in 20 a 4-dose one`(@TempDir dir: Path) = withPlayer(dir) { effects, player ->
        assertEquals(ATTACK[2], effects.potion(player, ATTACK[2]))
        player.equipment[WearPos.NECK] = InvObj(CHEMISTRY, 1)
        repeat(100) { assertEquals(ATTACK[3], effects.potion(player, ATTACK[3]), "nothing past 4 doses") }
        var made = 0
        var tries = 0
        while (made < 5 && tries < 1000) {
            tries++
            if (effects.potion(player, ATTACK[2]) == ATTACK[3]) made++
        }
        assertEquals(5, made)
        assertTrue(tries in 30..400, "about 1 in 20: $tries tries")
        assertNull(player.equipment[WearPos.NECK], "five uses")
    }

    @Test
    fun `an amulet of chemistry rolls on stamina and extended antifire by the dose, not on sanfew serum's steps`() {
        val data = osrsps.game.skill.HerbloreData.load()
        assertTrue("stamina_3" in data.chemistryPotions && "extended_antifire_1" in data.chemistryPotions)
        assertTrue(data.oneAtATime.isNotEmpty() && data.oneAtATime.none { it in data.chemistryPotions }, "sanfew")
        assertTrue(data.potions.values.all { it.key in data.chemistryPotions })
    }

    @Test
    fun `an amulet of bounty saves two of three allotment seeds about a quarter of the time`(@TempDir dir: Path) = withPlayer(dir) { effects, player ->
        player.equipment[WearPos.NECK] = InvObj(BOUNTY, 1)
        assertEquals(1, effects.allotmentSeeds(player, 1), "not from fewer than three")
        val used = List(30) { effects.allotmentSeeds(player, 3) }
        assertTrue(used.all { it == 1 || it == 3 } && used.count { it == 1 } in 2..10, used.toString())
    }

    companion object {
        const val FORGING = 2568
        const val CLAY_BRACELET = 11074
        const val CHEMISTRY = 21163
        const val BOUNTY = 21160
        const val EXPEDITIOUS = 21177
        const val SLAUGHTER = 21183
        const val IRON_ORE = 440

        /** Attack potion(1) to (4). */
        val ATTACK = listOf(125, 123, 121, 2428)
    }
}
