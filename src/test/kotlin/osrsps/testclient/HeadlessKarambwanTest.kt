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
import osrsps.game.World
import osrsps.game.combat.Foods
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
 * Fishing and Cooking 2 step FC3 through the headless client, on Karamja as npc-spawns/fishing-spots.json has it: a
 * small net nets karambwanji several at a time, and still does with the backpack full; a karambwan vessel baited from
 * them catches karambwan from 65, losing the bait on every roll; a raw karambwan cooks thoroughly or poorly from the
 * Make-X box; a poison karambwan hurts. The rl-client script test-runner/examples/karamja-karambwan.json does the same
 * in the real client against the real cache.
 */
class HeadlessKarambwanTest {
    private val karambwanjiSpot = NpcType(Fishing.KARAMBWANJI_SPOT, "Fishing spot", ops = listOf("Net", null, null, null, null))
    private val karambwanSpot = NpcType(Fishing.KARAMBWAN_SPOT, "Fishing spot", ops = listOf("Fish", null, null, null, null))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 20) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Fishing.BAITED_VESSEL + 1)
        objs[Fishing.NET] = ObjType(Fishing.NET, "Small fishing net")
        objs[Fishing.HARPOON] = ObjType(Fishing.HARPOON, "Harpoon")
        objs[Fishing.SHRIMPS.obj] = ObjType(Fishing.SHRIMPS.obj, "Raw shrimps")
        objs[Fishing.RAW_KARAMBWANJI] = ObjType(Fishing.RAW_KARAMBWANJI, "Raw karambwanji", stackable = true)
        objs[Fishing.KARAMBWAN_VESSEL] = ObjType(Fishing.KARAMBWAN_VESSEL, "Karambwan vessel")
        objs[Fishing.BAITED_VESSEL] = ObjType(Fishing.BAITED_VESSEL, "Karambwan vessel")
        objs[Cooking.KARAMBWAN.raw] = ObjType(Cooking.KARAMBWAN.raw, "Raw karambwan")
        objs[Cooking.KARAMBWAN.cooked] = food(Cooking.KARAMBWAN.cooked, "tbwt_cooked_karambwan", "Cooked karambwan")
        objs[Cooking.POISON_KARAMBWAN.cooked] = food(Cooking.POISON_KARAMBWAN.cooked, "tbwt_poorly_cooked_karambwan", "Poison karambwan")
        objs[Cooking.KARAMBWAN.burnt] = ObjType(Cooking.KARAMBWAN.burnt, "Burnt karambwan")
        val objTypes = ObjTypes(objs)
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(KARAMBWANJI_X shr 6, KARAMBWANJI_Z shr 6)
                markLoaded(KARAMBWAN_X shr 6, KARAMBWAN_Z shr 6)
            },
            objTypes = objTypes, npcTypes = NpcTypes.of(karambwanjiSpot, karambwanSpot), random = GameRandom(17),
            npcSpawns = listOf(
                NpcSpawn(karambwanjiSpot, KARAMBWANJI_X, KARAMBWANJI_Z, source = "test"),
                NpcSpawn(karambwanSpot, KARAMBWAN_X, KARAMBWAN_Z, source = "test"),
            ),
            locTypes = LocTypes(
                mapOf(Cooking.RANGE to LocType(Cooking.RANGE, "Range", listOf("Cook", null, null, null, null), LocCollisionType(width = 1, length = 1))),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Cooking.RANGE, LocShape.CENTREPIECE_STRAIGHT, 0, RANGE_X, RANGE_Z, 0)) },
            foodList = Foods.load(objTypes),
            cycleMillis = millis,
        )
    }

    private fun food(id: Int, internal: String, name: String) = ObjType(id, name, iops = listOf("Eat", null, null, null, "Drop"), internalName = internal)

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
    fun `a small net nets karambwanji level over 5 plus one at a time, and with the backpack full still nets them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.teleport(KARAMBWANJI_X, KARAMBWANJI_Z - 1)
                val spot = client.spot(Fishing.KARAMBWANJI_SPOT)
                client.give(Fishing.NET)
                // At 20: five at a time, for 5 XP a catch.
                client.level("fishing", FISHING, 20)
                val before = client.stat(FISHING)!!.xp
                client.npcOp(spot.index, NET_OP)
                client.seen("[fishing.cast_net]")
                client.seen("[fishing.catch_karambwanji]")
                val netted = client.await("karambwanji") { client.count(Fishing.RAW_KARAMBWANJI).takeIf { it > 0 } }
                assertEquals(0, netted % 5, "five a catch at 20, got $netted")
                val xp = client.await("Fishing XP") { client.stat(FISHING)!!.xp.takeIf { it > before } } - before
                assertEquals(0, xp % 5, "5 XP a catch, got $xp")

                // The backpack full but for the stack: only karambwanji come up, onto it. Walking off stops the net.
                client.teleport(KARAMBWANJI_X, KARAMBWANJI_Z - 8)
                client.awaitTicks(2)
                client.give(Fishing.HARPOON, client.inventory(BACKPACK).count { it == null })
                val shrimps = client.count(Fishing.SHRIMPS.obj)
                val stack = client.count(Fishing.RAW_KARAMBWANJI)
                client.level("fishing", FISHING, 99)
                client.teleport(KARAMBWANJI_X, KARAMBWANJI_Z - 1)
                client.npcOp(spot.index, NET_OP)
                client.await("more karambwanji", timeoutMillis = 60_000) { if (client.count(Fishing.RAW_KARAMBWANJI) >= stack + 20) Unit else null }
                assertEquals(shrimps, client.count(Fishing.SHRIMPS.obj))
                assertEquals(0, (client.count(Fishing.RAW_KARAMBWANJI) - stack) % 20, "twenty a catch at 99")
            }
        }
    }

    @Test
    fun `a vessel baited from the backpack catches karambwan from 65, losing the bait on every roll`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.teleport(KARAMBWAN_X, KARAMBWAN_Z - 1)
                val spot = client.spot(Fishing.KARAMBWAN_SPOT)
                // The level is checked first, then the vessel, then the bait.
                client.level("fishing", FISHING, 64)
                client.npcOp(spot.index, FISH_OP)
                client.seen("[fishing.level_karambwan level=65]")
                client.level("fishing", FISHING, 65)
                client.npcOp(spot.index, FISH_OP)
                client.seen("[fishing.no_vessel]")
                client.give(Fishing.KARAMBWAN_VESSEL)
                client.npcOp(spot.index, FISH_OP)
                client.seen("[fishing.no_karambwanji]")

                client.level("fishing", FISHING, 99)
                client.give(Fishing.RAW_KARAMBWANJI, 6)
                client.npcOp(spot.index, FISH_OP)
                client.seen("[fishing.cast_vessel]")
                // Baited at once from the backpack's karambwanji.
                client.await("the baited vessel") { if (client.count(Fishing.BAITED_VESSEL) == 1) Unit else null }
                // Six pieces of bait make six rolls, each a karambwan or a snatch, then it stops with the vessel empty.
                client.await("the bait running out", timeoutMillis = 60_000) {
                    if (client.messages().count { it.text == "[fishing.no_karambwanji]" } == 2) Unit else null
                }
                val karambwan = client.count(Cooking.KARAMBWAN.raw)
                val snatched = client.messages().count { it.text == "[fishing.snatched]" }
                assertEquals(6, karambwan + snatched, "$karambwan caught, $snatched snatched")
                assertTrue(karambwan > 0, "about 63% a roll at 99")
                assertEquals(karambwan, client.messages().count { it.text == "[fishing.catch_karambwan]" })
                assertEquals(0, client.count(Fishing.RAW_KARAMBWANJI))
                assertEquals(1, client.count(Fishing.KARAMBWAN_VESSEL))
                assertEquals(0, client.count(Fishing.BAITED_VESSEL))
            }
        }
    }

    @Test
    fun `a raw karambwan cooks thoroughly from 30 or poorly, as the box picks, and a poison karambwan hurts`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.teleport(RANGE_X - 1, RANGE_Z)
                client.give(Cooking.KARAMBWAN.raw)
                // Even one asks: the box shows the cooked karambwan first, then the poison one.
                client.level("cooking", COOKING, 29)
                client.locTarget(Cooking.RANGE, RANGE_X, RANGE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slot(Cooking.KARAMBWAN.raw), Cooking.KARAMBWAN.raw)
                val setup = client.await("the Make-X box") { client.clientScripts().firstOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }
                val shown = setup.args.filterIsInstance<Int>()
                assertTrue(shown.indexOf(Cooking.KARAMBWAN.cooked) in 0 until shown.indexOf(Cooking.POISON_KARAMBWAN.cooked), "${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.seen("[cooking.level level=30]")

                // At 99 neither way burns: the box's first item cooks it thoroughly, the second poorly.
                client.level("cooking", COOKING, 99)
                val before = client.stat(COOKING)!!.xp
                val boxes = client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }
                client.locTarget(Cooking.RANGE, RANGE_X, RANGE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slot(Cooking.KARAMBWAN.raw), Cooking.KARAMBWAN.raw)
                client.await("the Make-X box") { if (client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } > boxes) Unit else null }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.seen("[cooking.cooked_karambwan]")
                client.await("the cooked karambwan") { if (client.count(Cooking.KARAMBWAN.cooked) == 1) Unit else null }
                assertEquals(190, client.await("Cooking XP") { client.stat(COOKING)!!.xp.takeIf { it > before } } - before)

                client.give(Cooking.KARAMBWAN.raw)
                val scripts = client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }
                client.locOp(Cooking.RANGE, RANGE_X, RANGE_Z, 1)
                client.await("the Make-X box again") { if (client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } > scripts) Unit else null }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 1, 1)
                client.seen("[cooking.cooked_poison_karambwan]")
                client.await("the poison karambwan") { if (client.count(Cooking.POISON_KARAMBWAN.cooked) == 1) Unit else null }
                assertEquals(0, client.count(Cooking.KARAMBWAN.burnt))

                // Eaten, it hits for 5 and heals nothing.
                client.commandReply("setlevel hitpoints 99") { it.contains("set to level 99") }
                client.commandReply("hit 49 me") { it == "Hit yourself for 49: 50/99 hp" }
                val poison = client.slot(Cooking.POISON_KARAMBWAN.cooked)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, EAT_BUTTON_OP, poison, Cooking.POISON_KARAMBWAN.cooked)
                client.seen("[food.eat_karambwan food=poison karambwan]")
                client.seen("[food.painful]")
                client.await("45 hitpoints") { client.stat(HITPOINTS)?.takeIf { it.level == 45 } }
            }
        }
    }

    private companion object {
        /** A karambwanji spot in the Holy Lake and a karambwan spot by DKP, where mejrs's live list has them. */
        const val KARAMBWANJI_X = 2791
        const val KARAMBWANJI_Z = 3019
        const val KARAMBWAN_X = 2899
        const val KARAMBWAN_Z = 3119
        const val RANGE_X = 2896
        const val RANGE_Z = 3110

        const val NET_OP = 1
        const val FISH_OP = 1

        const val BACKPACK = 93
        const val HITPOINTS = 3
        const val COOKING = 7
        const val FISHING = 10

        /** Eat is iop1, which the inventory sends as button op 2. */
        const val EAT_BUTTON_OP = 2
    }
}
