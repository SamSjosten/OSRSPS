package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
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
 * Fishing and Cooking 2 step FC1 through the headless client, on Catherby's beach as npc-spawns/fishing-spots.json has
 * it: a harpoon catches a tuna from 35 and the tuna cooks on a range; a lobster pot cages a lobster from 40; a wielded
 * dragon harpoon fishes only from 61; the Big Net/Harpoon spot harpoons sharks from 76 and big nets mackerel, junk and
 * cod (step FC2). The rl-client script
 * test-runner/examples/catherby-harpoon-cage.json does the same in the real client against the real cache.
 */
class HeadlessHarpoonFishingTest {
    private val cageSpot = NpcType(Fishing.CATHERBY_CAGE_SPOT, "Fishing spot", ops = listOf("Cage", null, "Harpoon", null, null))
    private val sharkSpot = NpcType(Fishing.CATHERBY_SHARK_SPOT, "Fishing spot", ops = listOf("Big Net", null, "Harpoon", null, null))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 20) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Fishing.DRAGON_HARPOON + 1)
        objs[Fishing.HARPOON] = ObjType(Fishing.HARPOON, "Harpoon")
        objs[Fishing.LOBSTER_POT] = ObjType(Fishing.LOBSTER_POT, "Lobster pot")
        objs[Fishing.DRAGON_HARPOON] = ObjType(
            Fishing.DRAGON_HARPOON, "Dragon harpoon", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON,
        )
        objs[Fishing.BIG_NET] = ObjType(Fishing.BIG_NET, "Big fishing net")
        for (fish in Fishing.BIG_NET_FISHING.fish) objs[fish.obj] = ObjType(fish.obj, "Catch ${fish.obj}")
        for (food in listOf(Cooking.LOBSTER, Cooking.TUNA, Cooking.SWORDFISH, Cooking.SHARK, Cooking.MACKEREL, Cooking.COD, Cooking.BASS)) {
            for (id in listOf(food.raw, food.cooked, food.burnt)) objs[id] = ObjType(id, "Food $id")
        }
        World(
            crcs,
            CollisionMap().apply { markLoaded(CAGE_X shr 6, CAGE_Z shr 6) },
            objTypes = ObjTypes(objs), npcTypes = NpcTypes.of(cageSpot, sharkSpot), random = GameRandom(11),
            npcSpawns = listOf(NpcSpawn(cageSpot, CAGE_X, CAGE_Z, source = "test"), NpcSpawn(sharkSpot, SHARK_X, SHARK_Z, source = "test")),
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

    private fun HeadlessClient.give(id: Int) {
        val before = count(id)
        command("item $id")
        await("item $id") { if (count(id) == before + 1) Unit else null }
    }

    private fun HeadlessClient.level(skill: String, id: Int, level: Int) {
        command("setlevel $skill $level")
        await("$skill $level") { stat(id)?.takeIf { it.level == level } }
    }

    private fun HeadlessClient.spot(id: Int) = await("spot $id in view") { npcs().firstOrNull { it.id == id } }

    private fun HeadlessClient.xp(skill: Int) = stat(skill)?.xp ?: 0

    @Test
    fun `a harpoon catches a tuna from 35 and the tuna cooks on a range`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele $CAGE_X ${CAGE_Z + 1}")
                client.await("the teleport") { client.position?.takeIf { it.x == CAGE_X && it.z == CAGE_Z + 1 } }
                val spot = client.spot(Fishing.CATHERBY_CAGE_SPOT)
                // The level is checked before the tool.
                client.level("fishing", FISHING, 40)
                client.npcOp(spot.index, HARPOON_OP)
                client.seen("[fishing.no_harpoon]")
                client.give(Fishing.HARPOON)
                client.level("fishing", FISHING, 34)
                client.npcOp(spot.index, HARPOON_OP)
                client.seen("[fishing.level_harpoon level=35]")

                // At 40 the spot gives only tuna: swordfish need 50.
                client.level("fishing", FISHING, 40)
                val before = client.xp(FISHING)
                client.npcOp(spot.index, HARPOON_OP)
                client.seen("[fishing.cast_harpoon]")
                client.seen("[fishing.catch_tuna]")
                client.await("the tuna") { if (client.count(Cooking.TUNA.raw) >= 1) Unit else null }
                assertEquals(0, client.count(Cooking.SWORDFISH.raw))
                // Fishing goes on while the test reads, so more tuna can come in: 80 XP each.
                val gained = client.await("Fishing XP") { client.xp(FISHING).takeIf { it > before } } - before
                assertTrue(gained % 80 == 0, "80 XP a tuna, got $gained")

                client.level("cooking", COOKING, 99)
                val cookingBefore = client.xp(COOKING)
                client.locTarget(Cooking.RANGE, RANGE_X, RANGE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slot(Cooking.TUNA.raw), Cooking.TUNA.raw)
                // A second tuna may have come in on the way: then the Make-X box asks, and one is cooked.
                client.await("the tuna cooking or the Make-X box") {
                    if (client.clientScripts().any { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }) {
                        client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                        Unit
                    } else {
                        client.messages().firstOrNull { it.text == "[cooking.cooked_tuna]" }
                    }
                }
                client.seen("[cooking.cooked_tuna]")
                client.await("the cooked tuna") { if (client.count(Cooking.TUNA.cooked) == 1) Unit else null }
                assertEquals(100, client.await("Cooking XP") { client.xp(COOKING).takeIf { it > cookingBefore } } - cookingBefore)
            }
        }
    }

    @Test
    fun `a lobster pot cages lobsters from 40, and a wielded dragon harpoon fishes only from 61`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele $CAGE_X ${CAGE_Z + 1}")
                client.await("the teleport") { client.position?.takeIf { it.x == CAGE_X && it.z == CAGE_Z + 1 } }
                val spot = client.spot(Fishing.CATHERBY_CAGE_SPOT)
                client.level("fishing", FISHING, 40)
                client.npcOp(spot.index, CAGE_OP)
                client.seen("[fishing.no_cage]")
                client.give(Fishing.LOBSTER_POT)
                client.level("fishing", FISHING, 39)
                client.npcOp(spot.index, CAGE_OP)
                client.seen("[fishing.level_cage level=40]")
                client.level("fishing", FISHING, 40)
                client.npcOp(spot.index, CAGE_OP)
                client.seen("[fishing.cast_cage]")
                client.seen("[fishing.catch_lobster]")
                client.await("the lobster") { if (client.count(Cooking.LOBSTER.raw) >= 1) Unit else null }

                client.give(Fishing.DRAGON_HARPOON)
                client.level("attack", ATTACK, 60)
                client.ifButton(INVENTORY, INVENTORY_ITEMS, WIELD_BUTTON_OP, client.slot(Fishing.DRAGON_HARPOON), Fishing.DRAGON_HARPOON)
                client.await("the dragon harpoon wielded") { client.inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == Fishing.DRAGON_HARPOON } }
                client.level("fishing", FISHING, 60)
                val refused = client.messages().count { it.text == "[fishing.no_harpoon]" }
                client.npcOp(spot.index, HARPOON_OP)
                client.await("the dragon harpoon refused below 61") { if (client.messages().count { it.text == "[fishing.no_harpoon]" } > refused) Unit else null }
                client.level("fishing", FISHING, 61)
                val catches = client.messages().count { it.text == "[fishing.catch_tuna]" || it.text == "[fishing.catch_swordfish]" }
                client.npcOp(spot.index, HARPOON_OP)
                client.await("a catch with the dragon harpoon", timeoutMillis = 30_000) {
                    if (client.messages().count { it.text == "[fishing.catch_tuna]" || it.text == "[fishing.catch_swordfish]" } > catches) Unit else null
                }
                assertEquals(Fishing.DRAGON_HARPOON, client.inventory(WORN).getOrNull(WearPos.WEAPON)?.id)
            }
        }
    }

    @Test
    fun `the Big Net and Harpoon spot harpoons sharks from 76`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele $SHARK_X ${SHARK_Z + 1}")
                client.await("the teleport") { client.position?.takeIf { it.x == SHARK_X && it.z == SHARK_Z + 1 } }
                val spot = client.spot(Fishing.CATHERBY_SHARK_SPOT)
                client.give(Fishing.HARPOON)
                client.level("fishing", FISHING, 75)
                client.npcOp(spot.index, HARPOON_OP)
                client.seen("[fishing.level_harpoon level=76]")
                client.level("fishing", FISHING, 99)
                val before = client.xp(FISHING)
                client.npcOp(spot.index, HARPOON_OP)
                client.seen("[fishing.catch_shark]", timeoutMillis = 60_000)
                client.await("the shark") { if (client.count(Cooking.SHARK.raw) >= 1) Unit else null }
                assertTrue((client.xp(FISHING) - before) % 110 == 0, "110 XP a shark")
            }
        }
    }

    @Test
    fun `the big net nets mackerel and junk from 16, cod only from 23`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele $SHARK_X ${SHARK_Z + 1}")
                client.await("the teleport") { client.position?.takeIf { it.x == SHARK_X && it.z == SHARK_Z + 1 } }
                val spot = client.spot(Fishing.CATHERBY_SHARK_SPOT)
                client.level("fishing", FISHING, 16)
                client.npcOp(spot.index, BIG_NET_OP)
                client.seen("[fishing.no_big_net]")
                client.give(Fishing.BIG_NET)
                client.level("fishing", FISHING, 15)
                client.npcOp(spot.index, BIG_NET_OP)
                client.seen("[fishing.level_big_net level=16]")

                // At 22: four catches, none of them cod (23) or bass (46).
                client.level("fishing", FISHING, 22)
                client.npcOp(spot.index, BIG_NET_OP)
                client.seen("[fishing.cast_net]")
                val catches = client.await("four catches", timeoutMillis = 60_000) {
                    client.messages().filter { it.text.startsWith("[fishing.catch_") }.takeIf { it.size >= 4 }
                }
                assertTrue(catches.none { it.text == "[fishing.catch_cod]" || it.text == "[fishing.catch_bass]" }, "${catches.map { it.text }}")
                assertEquals(0, client.count(Cooking.COD.raw) + client.count(Cooking.BASS.raw))

                client.level("fishing", FISHING, 99)
                client.npcOp(spot.index, BIG_NET_OP)
                client.seen("[fishing.catch_cod]", timeoutMillis = 60_000)
                client.await("the cod") { if (client.count(Cooking.COD.raw) >= 1) Unit else null }
            }
        }
    }

    @Test
    fun `the range cooks the big net's mackerel, cod and bass`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele $CAGE_X ${CAGE_Z + 1}")
                client.await("the teleport") { client.position?.takeIf { it.x == CAGE_X && it.z == CAGE_Z + 1 } }
                // At 99 none of them burn on a range: mackerel stop at 45, cod at 49, bass at 79.
                client.level("cooking", COOKING, 99)
                for ((food, xp) in listOf(Cooking.MACKEREL to 60, Cooking.COD to 75, Cooking.BASS to 130)) {
                    client.give(food.raw)
                    val before = client.xp(COOKING)
                    client.locTarget(Cooking.RANGE, RANGE_X, RANGE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slot(food.raw), food.raw)
                    client.await("${food.raw} cooked") { if (client.count(food.cooked) == 1 && client.count(food.raw) == 0) Unit else null }
                    assertEquals(0, client.count(food.burnt))
                    assertEquals(xp, client.await("Cooking XP") { client.xp(COOKING).takeIf { it > before } } - before)
                }
                client.seen("[cooking.cooked_mackerel]")
                client.seen("[cooking.cooked_cod]")
                client.seen("[cooking.cooked_bass]")
            }
        }
    }

    private companion object {
        /** A Cage/Harpoon and a Big Net/Harpoon spot on Catherby's beach, where mejrs's live list has them. */
        const val CAGE_X = 2844
        const val CAGE_Z = 3429
        const val SHARK_X = 2855
        const val SHARK_Z = 3423
        const val RANGE_X = 2846
        const val RANGE_Z = 3432

        const val CAGE_OP = 1
        const val BIG_NET_OP = 1
        const val HARPOON_OP = 3

        const val BACKPACK = 93
        const val WORN = 94
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0

        /** The backpack's Wield, its second op: IF_BUTTON op 3. */
        const val WIELD_BUTTON_OP = 3

        const val ATTACK = 0
        const val COOKING = 7
        const val FISHING = 10
    }
}
