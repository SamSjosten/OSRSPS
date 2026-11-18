package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.InfernalEels
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Fishing
import osrsps.game.skill.Thieving
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Karamja step K2.4 through the headless client: infernal eels need ice gloves worn and come up from 80 with an oily
 * rod and bait, a hammer cracks every eel held into Tokkul, onyx bolt tips or lava scale shards, and TzHaar-Hur-Rin's
 * gem counter gives a gem from 75 and stands empty after. test-runner/examples/mor-ul-rek-eels.json does the same on the
 * real map.
 */
class HeadlessInfernalEelsTest {
    private val spot = NpcType(Fishing.INFERNAL_EEL_SPOT, "Rod Fishing spot", ops = listOf("Bait", null, null, null, null))

    private fun counter(id: Int, vararg ops: String?) =
        LocType(id, "Shop Counter", ops.toList() + List(5 - ops.size) { null }, LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 20) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(30_000)
        for (id in listOf(Fishing.OILY_FISHING_ROD, Fishing.ICE_GLOVES, InfernalEels.HAMMER)) objs[id] = ObjType(id, "Item $id")
        objs[Fishing.FISHING_BAIT] = ObjType(Fishing.FISHING_BAIT, "Fishing bait", stackable = true)
        objs[InfernalEels.EEL] = ObjType(InfernalEels.EEL, "Infernal eel", iops = listOf(null, null, null, "Eat", "Drop"))
        for (id in listOf(InfernalEels.TOKKUL, InfernalEels.ONYX_BOLT_TIPS, InfernalEels.LAVA_SCALE_SHARD)) objs[id] = ObjType(id, "Loot $id", stackable = true)
        for (stall in Thieving.MOR_UL_REK_STALLS) for (loot in stall.loot) objs[loot.obj] = ObjType(loot.obj, "Gem ${loot.obj}")
        World(
            crcs, CollisionMap().apply { markLoaded(2446 shr 6, 5104 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(11),
            npcTypes = NpcTypes.of(spot), npcSpawns = listOf(NpcSpawn(spot, 2446, 5104, source = "test")),
            locTypes = LocTypes(
                listOf(counter(Thieving.TZHAAR_GEM_COUNTER, null, "Steal-from"), counter(Thieving.TZHAAR_COUNTER_EMPTY)).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Thieving.TZHAAR_GEM_COUNTER, LocShape.CENTREPIECE_STRAIGHT, 0, 2450, 5100, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slot(id: Int) = inventory(BACKPACK).indexOfFirst { it?.id == id }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("item $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.level(skill: String, id: Int, level: Int) {
        command("setlevel $skill $level")
        await("$skill $level") { stat(id)?.takeIf { it.level == level } }
    }

    @Test
    fun `infernal eels need ice gloves, and a hammer cracks them all open`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("tele 2447 5104")
                client.give(Fishing.OILY_FISHING_ROD)
                client.give(Fishing.FISHING_BAIT, 50)
                client.give(InfernalEels.HAMMER)
                client.level("fishing", FISHING, 79)
                val npc = client.await("the spot") { client.npcs().firstOrNull { it.id == Fishing.INFERNAL_EEL_SPOT } }
                client.npcOp(npc.index, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[fishing.level_infernal_eel level=80]" } }
                client.level("fishing", FISHING, 99)
                client.npcOp(npc.index, 1)
                client.await("the gloves message") { client.messages().firstOrNull { it.text == "[fishing.no_ice_gloves]" } }
                server.world.playerNamed("alice")!!.equipment[WearPos.HANDS] = InvObj(Fishing.ICE_GLOVES, 1)
                client.npcOp(npc.index, 1)
                client.await("two eels", 60_000) { if (client.count(InfernalEels.EEL) >= 2) Unit else null }
                // Walk off to stop fishing, then crack them.
                client.command("tele 2448 5106")
                client.await("still") { if (client.position?.z == 5106) Unit else null }
                val eels = client.count(InfernalEels.EEL)
                val hammer = client.slot(InfernalEels.HAMMER)
                val eel = client.slot(InfernalEels.EEL)
                client.ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, hammer, InfernalEels.HAMMER, InventoryTab.INTERFACE, InventoryTab.ITEMS, eel, InfernalEels.EEL)
                client.await("every eel cracked") { if (client.count(InfernalEels.EEL) == 0) Unit else null }
                val loot = listOf(InfernalEels.TOKKUL, InfernalEels.ONYX_BOLT_TIPS, InfernalEels.LAVA_SCALE_SHARD)
                assertTrue(loot.any { client.count(it) > 0 }, "loot from $eels eels")
                assertEquals(eels, client.messages().count { it.text.startsWith("[infernal_eel.cracked item=") })
            }
        }
    }

    @Test
    fun `the gem counter needs 75 and gives a gem`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 2449 5100")
                client.level("thieving", THIEVING, 75)
                client.locOp(Thieving.TZHAAR_GEM_COUNTER, 2450, 5100, 2)
                val gems = Thieving.TZHAAR_GEM_LOOT.map { it.obj }
                client.await("a gem") { if (gems.any { client.count(it) > 0 }) Unit else null }
                client.await("the empty counter") { client.locChanges().lastOrNull { it.coord.x == 2450 && it.coord.z == 5100 }?.takeIf { it.id == Thieving.TZHAAR_COUNTER_EMPTY } }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val FISHING = 10
        const val THIEVING = 17
    }
}
