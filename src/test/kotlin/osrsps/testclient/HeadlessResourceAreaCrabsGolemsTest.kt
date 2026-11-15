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
import osrsps.game.content.ResourceArea
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Cooking
import osrsps.game.skill.Fishing
import osrsps.map.CollisionMap

/**
 * Wilderness step W3.6b through the headless client: the Resource Area's dark crab spot caging dark crabs from 85 with
 * a lobster pot and dark fishing bait, a bait a catch, for 130 XP; and a runite golem killed leaving runite rocks on its
 * tile, mined at 85 for one runite ore, after which the rock is gone; a second golem's rock gone by itself a minute
 * later. The rl-client script test-runner/examples/resource-area-golem.json does the same on the real map.
 */
class HeadlessResourceAreaCrabsGolemsTest {
    private val spot = NpcType(Fishing.RESOURCE_AREA_SPOT, "Fishing spot", ops = listOf("Cage", null, null, null, null), internalName = "49_61_crabs")
    private val golem = NpcType(
        ResourceArea.GOLEM, "Runite Golem", ops = listOf(null, "Attack", null, null, null), combatLevel = 165,
        internalName = "wilderness_rune_golem", stats = NpcStats(hitpoints = 120),
    )
    private val rock = NpcType(ResourceArea.GOLEM_ROCK, "Runite rocks", ops = listOf("Mine", null, null, null, null), internalName = "wilderness_golem_rune_rock")

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 20) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Fishing.DARK_FISHING_BAIT + 1)
        objs[Fishing.LOBSTER_POT] = ObjType(Fishing.LOBSTER_POT, "Lobster pot")
        objs[Fishing.DARK_FISHING_BAIT] = ObjType(Fishing.DARK_FISHING_BAIT, "Dark fishing bait", stackable = true)
        for (id in listOf(Cooking.DARK_CRAB.raw, Cooking.DARK_CRAB.cooked, Cooking.DARK_CRAB.burnt)) objs[id] = ObjType(id, "Dark crab $id")
        objs[RUNE_PICKAXE] = ObjType(RUNE_PICKAXE, "Rune pickaxe")
        objs[RUNITE_ORE] = ObjType(RUNITE_ORE, "Runite ore")
        val types = NpcTypes.of(spot, golem, rock)
        World(
            crcs, CollisionMap().apply { markLoaded(3184 shr 6, 3930 shr 6) }, objTypes = ObjTypes(objs), npcTypes = types,
            random = GameRandom(3),
            npcSpawns = listOf(
                NpcSpawn(spot, SPOT_X, SPOT_Z, source = "test"),
                NpcSpawn(golem, GOLEM_X, GOLEM_Z, source = "test"),
                NpcSpawn(golem, GOLEM_X + 6, GOLEM_Z, source = "test"),
            ),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count.toLong() }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 30_000) =
        await(text, timeoutMillis) { messages().lastOrNull { it.text == text } }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("item $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.level(skill: String, id: Int, level: Int) {
        command("setlevel $skill $level")
        await("$skill $level") { stat(id)?.takeIf { it.level == level } }
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `the dark crab spot cages dark crabs from 85, a dark fishing bait a catch`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("una").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(SPOT_X, SPOT_Z + 1)
                val index = server.world.npcs.all.first { it.type.id == Fishing.RESOURCE_AREA_SPOT }.index
                client.give(Fishing.LOBSTER_POT)
                client.give(Fishing.DARK_FISHING_BAIT, 20)
                client.level("fishing", FISHING, 84)
                client.npcOp(index, 1)
                client.seen("[fishing.level_dark_crab level=85]")
                client.level("fishing", FISHING, 99)
                val before = client.stat(FISHING)!!.xp
                client.npcOp(index, 1)
                client.seen("[fishing.cast_dark_crab]")
                client.seen("[fishing.catch_dark_crab]", timeoutMillis = 90_000)
                val crabs = client.await("a dark crab") { client.count(Cooking.DARK_CRAB.raw).takeIf { it >= 1 } }
                assertEquals(20 - crabs, client.count(Fishing.DARK_FISHING_BAIT), "a bait a catch")
                val gained = client.await("Fishing XP") { client.stat(FISHING)!!.xp.takeIf { it > before } } - before
                assertEquals(0, gained % 130, "130 XP a dark crab, got $gained")
            }
        }
    }

    @Test
    fun `a runite golem killed leaves runite rocks for one ore, gone by themselves a minute later`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("vic").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.give(RUNE_PICKAXE)
                client.level("mining", MINING, 85)
                client.tele(GOLEM_X, GOLEM_Z - 1)
                val first = server.world.npcs.all.first { it.type.id == ResourceArea.GOLEM && it.x == GOLEM_X }
                client.commandReply("kill ${first.index}") { it.startsWith("Killed") }
                val head = client.await("the golem's head") {
                    server.world.npcs.all.firstOrNull { it.type.id == ResourceArea.GOLEM_ROCK && it.x == GOLEM_X && it.z == GOLEM_Z }
                }
                client.npcOp(head.index, ResourceArea.MINE)
                client.seen("[mining.get_ore ore=runite]", timeoutMillis = 60_000)
                client.await("the ore") { Unit.takeIf { client.count(RUNITE_ORE) == 1L } }
                client.await("the rock gone after its ore") { Unit.takeIf { server.world.npcs[head.index] !== head } }

                val second = server.world.npcs.all.first { it.type.id == ResourceArea.GOLEM && it.x == GOLEM_X + 6 }
                client.commandReply("kill ${second.index}") { it.startsWith("Killed") }
                val other = client.await("the second head") {
                    server.world.npcs.all.firstOrNull { it.type.id == ResourceArea.GOLEM_ROCK && it.x == GOLEM_X + 6 }
                }
                val spawned = server.world.tick
                client.await("the second head gone", timeoutMillis = 30_000) { Unit.takeIf { server.world.npcs[other.index] !== other } }
                assertTrue(server.world.tick - spawned >= ResourceArea.ROCK_TICKS - 1, "a minute, not sooner")
            }
        }
    }

    private companion object {
        const val SPOT_X = 3183
        const val SPOT_Z = 3926
        const val GOLEM_X = 3178
        const val GOLEM_Z = 3938
        const val RUNE_PICKAXE = 1275
        const val RUNITE_ORE = 451
        const val FISHING = 10
        const val MINING = 14
        const val BACKPACK = 93
    }
}
