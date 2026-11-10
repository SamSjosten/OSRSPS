package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Cooking
import osrsps.game.skill.Fishing
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Fishing and Cooking 2 step FC4 through the headless client, as npc-spawns/fishing-spots.json has the spots: a small
 * net catches monkfish from 62 at the Piscatoris Fishing Colony, and they cook on a range; a rod with sandworms catches
 * anglerfish from 82 at Port Piscarilius, a sandworm a catch. The rl-client script
 * test-runner/examples/piscatoris-monkfish-anglerfish.json does the same in the real client against the real cache.
 */
class HeadlessMonkfishAnglerfishTest {
    private val monkfishSpot = NpcType(Fishing.PISCATORIS_SPOT, "Fishing spot", ops = listOf("Net", null, "Harpoon", null, null))
    private val anglerfishSpot = NpcType(Fishing.PISCARILIUS_SPOT, "Rod Fishing spot", ops = listOf("Bait", null, null, null, null))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 20) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Cooking.ANGLERFISH.burnt + 1)
        objs[Fishing.NET] = ObjType(Fishing.NET, "Small fishing net")
        objs[Fishing.FISHING_ROD] = ObjType(Fishing.FISHING_ROD, "Fishing rod")
        objs[Fishing.SANDWORMS] = ObjType(Fishing.SANDWORMS, "Sandworms", stackable = true)
        for (food in listOf(Cooking.MONKFISH, Cooking.ANGLERFISH)) {
            for (id in listOf(food.raw, food.cooked, food.burnt)) objs[id] = ObjType(id, "Food $id")
        }
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(MONKFISH_X shr 6, MONKFISH_Z shr 6)
                markLoaded(ANGLERFISH_X shr 6, ANGLERFISH_Z shr 6)
            },
            objTypes = ObjTypes(objs), npcTypes = NpcTypes.of(monkfishSpot, anglerfishSpot), random = GameRandom(23),
            npcSpawns = listOf(
                NpcSpawn(monkfishSpot, MONKFISH_X, MONKFISH_Z, source = "test"),
                NpcSpawn(anglerfishSpot, ANGLERFISH_X, ANGLERFISH_Z, source = "test"),
            ),
            locTypes = LocTypes(
                mapOf(Cooking.RANGE to LocType(Cooking.RANGE, "Range", listOf("Cook", null, null, null, null), LocCollisionType(width = 1, length = 1))),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Cooking.RANGE, LocShape.CENTREPIECE_STRAIGHT, 0, RANGE_X, RANGE_Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slot(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

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

    private fun HeadlessClient.spot(id: Int) = await("spot $id in view") { npcs().firstOrNull { it.id == id } }

    private fun HeadlessClient.teleport(x: Int, z: Int) {
        command("tele $x $z")
        await("the teleport") { position?.takeIf { it.x == x && it.z == z } }
    }

    @Test
    fun `a small net catches monkfish from 62 at Piscatoris, and a range cooks them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.teleport(MONKFISH_X, MONKFISH_Z - 1)
                val spot = client.spot(Fishing.PISCATORIS_SPOT)
                client.give(Fishing.NET)
                client.level("fishing", FISHING, 61)
                client.npcOp(spot.index, NET_OP)
                client.seen("[fishing.level_monkfish level=62]")
                client.level("fishing", FISHING, 62)
                val before = client.stat(FISHING)!!.xp
                client.npcOp(spot.index, NET_OP)
                client.seen("[fishing.cast_net]")
                client.seen("[fishing.catch_monkfish]", timeoutMillis = 60_000)
                client.await("the monkfish") { if (client.count(Cooking.MONKFISH.raw) >= 1) Unit else null }
                val gained = client.await("Fishing XP") { client.stat(FISHING)!!.xp.takeIf { it > before } } - before
                assertEquals(0, gained % 120, "120 XP a monkfish, got $gained")

                // Walk off to stop, then cook one at 99 on a range, where monkfish no longer burn (from 90).
                client.teleport(RANGE_X - 1, RANGE_Z)
                client.awaitTicks(2)
                client.command("empty")
                client.await("the backpack emptied") { if (client.inventory(BACKPACK).all { it == null }) Unit else null }
                client.give(Cooking.MONKFISH.raw)
                client.level("cooking", COOKING, 99)
                val cookingBefore = client.stat(COOKING)!!.xp
                client.locTarget(Cooking.RANGE, RANGE_X, RANGE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slot(Cooking.MONKFISH.raw), Cooking.MONKFISH.raw)
                client.seen("[cooking.cooked_monkfish]")
                client.await("the cooked monkfish") { if (client.count(Cooking.MONKFISH.cooked) == 1) Unit else null }
                assertEquals(150, client.await("Cooking XP") { client.stat(COOKING)!!.xp.takeIf { it > cookingBefore } } - cookingBefore)
            }
        }
    }

    @Test
    fun `a rod baited with sandworms catches anglerfish from 82 at Port Piscarilius, a sandworm a catch`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.teleport(ANGLERFISH_X, ANGLERFISH_Z - 1)
                val spot = client.spot(Fishing.PISCARILIUS_SPOT)
                client.give(Fishing.FISHING_ROD)
                // The level first, then the bait.
                client.level("fishing", FISHING, 81)
                client.npcOp(spot.index, BAIT_OP)
                client.seen("[fishing.level_bait level=82]")
                client.level("fishing", FISHING, 99)
                client.npcOp(spot.index, BAIT_OP)
                client.seen("[fishing.no_sandworms]")

                client.give(Fishing.SANDWORMS, 3)
                val before = client.stat(FISHING)!!.xp
                client.npcOp(spot.index, BAIT_OP)
                client.seen("[fishing.cast_line]")
                // About 14% a roll at 99: three sandworms make three catches, then it stops for want of bait.
                client.await("the sandworms used up", timeoutMillis = 120_000) {
                    if (client.messages().count { it.text == "[fishing.no_sandworms]" } == 2) Unit else null
                }
                assertEquals(3, client.count(Cooking.ANGLERFISH.raw))
                assertEquals(3, client.messages().count { it.text == "[fishing.catch_anglerfish]" })
                assertEquals(0, client.count(Fishing.SANDWORMS))
                val gained = client.await("Fishing XP") { (client.stat(FISHING)!!.xp - before).takeIf { it >= 3 * 120 } }
                assertEquals(3 * 120, gained, "120 XP an anglerfish")
            }
        }
    }

    private companion object {
        /** A Piscatoris Net/Harpoon spot and a Port Piscarilius anglerfish spot, where mejrs's live list has them. */
        const val MONKFISH_X = 2326
        const val MONKFISH_Z = 3700
        const val ANGLERFISH_X = 1827
        const val ANGLERFISH_Z = 3770
        const val RANGE_X = 2330
        const val RANGE_Z = 3696

        const val NET_OP = 1
        const val BAIT_OP = 1

        const val BACKPACK = 93
        const val COOKING = 7
        const val FISHING = 10
    }
}
