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
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.trail.ClueStep
import osrsps.game.trail.StashUnits
import osrsps.game.trail.StepType
import osrsps.game.trail.TrailData
import osrsps.game.trail.TrailOutfit
import osrsps.game.trail.TrailTarget
import osrsps.game.trail.TrailTier
import osrsps.game.trail.TrailTile
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Treasure Trails step TT5 through the headless client: Build on an easy STASH unit wants the level (a crystal saw adds
 * three), the tools and the materials, then builds it (the varbit set, the planks and nails gone, the experience given); Search stashes the
 * clue's full outfit from the backpack, refuses a part of it, and gives it all back. A made-up step and unit: the real
 * ones come from the cache.
 */
class HeadlessTreasureTrailsStashTest {
    private val outfit = TrailOutfit("a made-up hat and boots", items = mapOf(0 to setOf(HAT), 10 to setOf(BOOTS, OTHER_BOOTS)))
    private val step = ClueStep(
        StepType.EMOTE, 1, TrailTier.EASY, "Bow in a made-up hat.", listOf(TrailTarget.Dig(TrailTile(3222, 3222, 0))),
        items = listOf(CLUE), emotes = listOf(0), outfits = listOf(outfit), stash = STASH,
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(26_000)
        for ((id, name) in listOf(
            HAT to "Made-up hat", BOOTS to "Made-up boots", OTHER_BOOTS to "Other boots", 2347 to "Hammer", 8794 to "Saw", StashUnits.CRYSTAL_SAW to "Crystal saw", StashUnits.PLANK to "Plank",
        )) objs[id] = ObjType(id, name)
        objs[4819] = ObjType(4819, "Bronze nails", stackable = true)
        objs[CLUE] = ObjType(CLUE, "Clue scroll (easy)", iops = listOf("Read", "Check steps", null, null, "Drop"))
        val unit = LocType(
            STASH, "null", ops = listOf("Build", "Search", null, null, null), collision = LocCollisionType(width = 1, length = 1),
            multiloc = true, multiVarbit = VARBIT, multiLocs = listOf(UNBUILT, BUILT, -1),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            locTypes = LocTypes(mapOf(STASH to unit)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(STASH, LocShape.CENTREPIECE_STRAIGHT, 0, X, Z, 0)) },
            varTypes = VarTypes(6_000, mapOf(VARBIT to VarbitType(VARP, 0, 0)), 21_000),
            trailData = TrailData(listOf(step)), cycleMillis = millis,
        )
    }

    /** Option [option] on the unit, then its message [key] (with any values). */
    private fun HeadlessClient.unit(option: Int, key: String) {
        val from = messages().size
        locOp(STASH, X, Z, option)
        await(key) { messages().drop(from).firstOrNull { it.text == "[$key]" || it.text.startsWith("[$key ") } }
    }

    @Test
    fun `Build wants the level, the tools and the materials, then Search stashes the outfit and gives it back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("jo").use { client ->
                client.await("the login rebuild") { client.position }
                val player = server.world.playerNamed("jo")!!
                assertEquals(setOf(STASH), server.world.trails.stashes.units.keys)

                client.unit(StashUnits.BUILD, "stash.level")
                // Level 24 is three short of easy; a crystal saw in the backpack makes it up.
                client.commandReply("setlevel construction 24") { it.contains("level 24") }
                client.unit(StashUnits.BUILD, "stash.level")
                player.inventory[1] = InvObj(StashUnits.CRYSTAL_SAW, 1)
                client.unit(StashUnits.BUILD, "stash.tools")
                player.inventory[0] = InvObj(2347, 1)
                player.inventory[2] = InvObj(StashUnits.PLANK, 1)
                player.inventory[3] = InvObj(4819, 25)
                client.unit(StashUnits.BUILD, "stash.materials")
                player.inventory[4] = InvObj(StashUnits.PLANK, 1)
                val xp = player.skills.xp(Skill.CONSTRUCTION)
                client.unit(StashUnits.BUILD, "stash.built")
                assertTrue(server.world.trails.stashes.built(player, STASH))
                assertEquals(0L, player.inventory.count(StashUnits.PLANK))
                assertEquals(15L, player.inventory.count(4819))
                assertEquals(150, player.skills.xp(Skill.CONSTRUCTION) - xp)

                // Half the outfit isn't stashed; the whole of it is, with either of the boots.
                player.inventory[5] = InvObj(HAT, 1)
                client.unit(StashUnits.SEARCH, "stash.missing")
                player.inventory[6] = InvObj(OTHER_BOOTS, 1)
                client.unit(StashUnits.SEARCH, "stash.stored")
                assertEquals(listOf(HAT, OTHER_BOOTS), player.trails.stashes[STASH])
                assertEquals(0L, player.inventory.count(HAT) + player.inventory.count(OTHER_BOOTS))

                client.unit(StashUnits.SEARCH, "stash.taken")
                assertEquals(1L, player.inventory.count(HAT))
                assertEquals(1L, player.inventory.count(OTHER_BOOTS))
                assertEquals(null, player.trails.stashes[STASH])
            }
        }
    }

    private companion object {
        const val STASH = 34_740
        const val UNBUILT = 34_741
        const val BUILT = 34_742
        const val VARBIT = 8_258
        const val VARP = 5_990
        const val HAT = 1_017
        const val BOOTS = 1_061
        const val OTHER_BOOTS = 1_837
        const val CLUE = 2_677
        const val X = 3_224
        const val Z = 3_222
    }
}
