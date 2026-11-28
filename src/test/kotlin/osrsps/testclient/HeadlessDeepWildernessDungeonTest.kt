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
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.DeepWildernessDungeon
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.NpcSpawnFile
import osrsps.game.skill.AgilityData
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Wilderness step W6.1 through the headless client: the Deep Wilderness Dungeon's spawn file loads every NPC in it, in
 * levels 49 to 56; the broken house's stairs lead down and the dungeon's back up; the crevice at Agility 46 crosses
 * both ways and below it says so; and a shadow spider halves a player's prayer as it begins fighting them and again
 * after [DeepWildernessDungeon.DRAIN_TICKS].
 */
class HeadlessDeepWildernessDungeonTest {
    private val names = mapOf(
        2098 to "giant", 2099 to "giant2", 2100 to "giant3", 291 to "dwarf_chaos", 3016 to "shadow_spider",
        2078 to "firegiant", 2079 to "firegiant2", 2080 to "firegiant3", 8698 to "chaos_temple_monk_wilderness",
    )
    private val npcTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())

    @Test
    fun `the spawn file has the Wiki's monsters, all in the dungeon`() {
        val text = NpcSpawnFile::class.java.getResource("/npc-spawns/deep-wilderness-dungeon.json")!!.readText()
        val parsed = NpcSpawnFile.parse(text, npcTypes, "deep-wilderness-dungeon.json")
        assertEquals(emptyList(), parsed.unknown)
        assertTrue(parsed.spawns.all { DeepWildernessDungeon.inDungeon(it.x, it.z) && it.level == 0 && Wilderness.level(it.x, it.z) in 49..56 })
        val counts = parsed.spawns.groupingBy { it.type.name.removeSuffix("2").removeSuffix("3") }.eachCount()
        assertEquals(mapOf("giant" to 6, "dwarf_chaos" to 4, "shadow_spider" to 11, "firegiant" to 5, "chaos_temple_monk_wilderness" to 2), counts)
    }

    @Test
    fun `its monsters fight and drop as the Wiki has them`() {
        val combat = NpcCombat.load(npcTypes, SeqNames.NONE)
        assertEquals(11, combat[2078]!!.maxHit, "fire giant")
        assertEquals(5, combat[3016]!!.maxHit, "shadow spider")
        assertEquals(10, combat[8698]!!.maxHit, "the level 23 monk's magic")
        val drops = NpcDrops.load(npcTypes, DropTestItems.objTypes())
        assertEquals(listOf(532), drops[2078]!!.always.map { it.type.id }, "fire giants' big bones")
        assertEquals(listOf(526), drops[8698]!!.always.map { it.type.id }, "the monk's bones")
        assertTrue(drops[3016]!!.always.isEmpty(), "shadow spiders drop no bones")
    }

    private val agility = AgilityData::class.java.getResource(AgilityData.RESOURCE)!!.readText()

    private fun server(dir: Path, spawns: List<NpcSpawn> = emptyList(), types: NpcTypes = npcTypes) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val loc = { id: Int, name: String, op: String -> LocType(id, name, listOf(op, null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0)) }
        World(
            crcs, CollisionMap().apply { markLoaded(47, 61); markLoaded(47, 161) },
            npcTypes = types, random = GameRandom(3), npcSpawns = spawns, npcCombat = NpcCombat.load(types, SeqNames.NONE),
            locTypes = LocTypes(
                mapOf(
                    DeepWildernessDungeon.STAIRS_DOWN to loc(DeepWildernessDungeon.STAIRS_DOWN, "Staircase", "Climb-down"),
                    DeepWildernessDungeon.STAIRS_UP to loc(DeepWildernessDungeon.STAIRS_UP, "Staircase", "Climb-up"),
                    CREVICE to loc(CREVICE, "Crevice", "Use"),
                ),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(DeepWildernessDungeon.STAIRS_DOWN, 10, 0, 3044, 3924, 0))
                add(ZoneLoc(DeepWildernessDungeon.STAIRS_UP, 10, 0, 3044, 10324, 0))
                add(ZoneLoc(CREVICE, 0, 3, 3046, 10327, 0))
                add(ZoneLoc(CREVICE, 0, 1, 3048, 10335, 0))
            },
            agilityData = AgilityData.parse(agility), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.arrive(x: Int, z: Int, what: String) =
        await(what, timeoutMillis = 15_000) { position?.takeIf { it == Coord(x, z, 0) } }

    @Test
    fun `the stairs lead down and up, and the crevice crosses at Agility 46`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(3045, 3925)
                client.locOp(DeepWildernessDungeon.STAIRS_DOWN, 3044, 3924, 1)
                client.arrive(DeepWildernessDungeon.ARRIVAL.x, DeepWildernessDungeon.ARRIVAL.z, "down in the dungeon")
                client.awaitTicks(2)
                client.locOp(DeepWildernessDungeon.STAIRS_UP, 3044, 10324, 1)
                client.arrive(DeepWildernessDungeon.EXIT.x, DeepWildernessDungeon.EXIT.z, "back up in the broken house")

                client.command("setlevel agility 45")
                client.await("level 45") { client.stat(AGILITY)?.takeIf { it.level == 45 } }
                client.tele(3046, 10326)
                client.locOp(CREVICE, 3046, 10327, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[agility.level level=46]" } }
                assertEquals(Coord(3046, 10326, 0), client.position)
                client.command("setlevel agility 46")
                client.await("level 46") { client.stat(AGILITY)?.takeIf { it.level == 46 } }
                client.locOp(CREVICE, 3046, 10327, 1)
                client.arrive(3048, 10336, "by the fire giants")
                client.awaitTicks(2)
                client.locOp(CREVICE, 3048, 10335, 1)
                client.arrive(3046, 10326, "back by the stairs")
            }
        }
    }

    @Test
    fun `a shadow spider halves the player's prayer as it fights them`(@TempDir dir: Path) {
        val spider = NpcType(3016, "Shadow spider", ops = listOf(null, "Attack", null, null, null), combatLevel = 52, internalName = "shadow_spider", stats = NpcStats(attack = 44, strength = 42, defence = 44, hitpoints = 55))
        val types = NpcTypes.of(spider)
        server(dir, listOf(NpcSpawn(spider, 3020, 10322, source = "test")), types).use { server ->
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { true }
                client.command("setlevel prayer 40")
                client.await("prayer 40") { client.stat(PRAYER)?.takeIf { it.level == 40 } }
                client.tele(3021, 10322)
                client.keepAlive()
                client.await("half gone", timeoutMillis = 20_000) { client.stat(PRAYER)?.takeIf { it.level == 20 } }
                client.keepAlive()
                client.await("half again", timeoutMillis = 20_000) { client.stat(PRAYER)?.takeIf { it.level == 10 } }
            }
        }
    }

    private companion object {
        const val CREVICE = 19043
        const val AGILITY = 16
        const val PRAYER = 5
    }
}
