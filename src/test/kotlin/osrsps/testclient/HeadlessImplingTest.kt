package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.hunter.Butterflies
import osrsps.game.skill.hunter.Implings
import osrsps.map.CollisionMap

/**
 * Hunter step 6b through the headless client: a baby impling (sure at 99 with the magic insect net) caught into an
 * impling jar, and Loot on the jar emptying it; a second caught with no jar, its loot taken at once; then the checks:
 * Loot with a full backpack, below the level with a net, and no net below the barehanded level. The player logs in at
 * 3222, 3218; the implings stand still two tiles east and west. What the loot is isn't checked: the baby impling's
 * table can give nothing, and implings the spawn cycle puts elsewhere draw on the same random as they roam.
 */
class HeadlessImplingTest {
    private val baby = NpcType(Implings.BABY.npcs.single(), "Baby impling", ops = listOf("Catch", null, null, null, null), internalName = "ii_impling_type_1")

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(30_000)
        for (type in lootItems()) objs[type.id] = type
        objs[Implings.IMPLING_JAR] = ObjType(Implings.IMPLING_JAR, "Impling jar", stackable = false, tradeable = true)
        objs[JARRED] = ObjType(JARRED, "Baby impling jar", stackable = false, tradeable = true, iops = listOf(null, null, "Loot", null, "Destroy"))
        objs[Butterflies.MAGIC_INSECT_NET] = ObjType(Butterflies.MAGIC_INSECT_NET, "Magic insect net", stackable = false, tradeable = false)
        objs[FILLER] = ObjType(FILLER, "Filler", stackable = false, tradeable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(7),
            npcTypes = NpcTypes.of(baby),
            npcSpawns = listOf(
                NpcSpawn(baby, 3224, 3218, wander = 0, source = "test"),
                NpcSpawn(baby, 3220, 3218, wander = 0, source = "test"),
            ),
            cycleMillis = millis,
        )
    }

    /** The loot file's items, all stackable so a loot takes one slot. */
    private fun lootItems(): List<ObjType> {
        val text = Implings::class.java.getResource(Implings.RESOURCE)!!.readText()
        val items = HashMap<Int, ObjType>()
        fun collect(obj: JsonObject) {
            obj["item"]?.let { id -> items.getOrPut(id.jsonPrimitive.int) { ObjType(id.jsonPrimitive.int, obj["name"]!!.jsonPrimitive.content, stackable = true, tradeable = true) } }
            for ((_, value) in obj) {
                runCatching { value.jsonObject }.getOrNull()?.let(::collect)
                runCatching { value.jsonArray }.getOrNull()?.forEach { runCatching { it.jsonObject }.getOrNull()?.let(::collect) }
            }
        }
        collect(Json.parseToJsonElement(text).jsonObject)
        return items.values.toList()
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.used() = inventory(BACKPACK).count { it != null }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.babyAt(x: Int) = await("the baby impling at $x", 15_000) { npcs().firstOrNull { it.id == baby.id && it.coord.x == x } }

    @Test
    fun `a baby impling caught into a jar is looted from it, and caught with no jar its loot comes at once`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("di").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 99")
                client.command("item ${Implings.IMPLING_JAR} 1")
                client.slotOf(Implings.IMPLING_JAR)
                val before = client.await("Hunter XP") { client.stat(HUNTER)?.takeIf { it.level == 99 } }.xp
                server.world.playerNamed("di")!!.equipment[WearPos.WEAPON] = InvObj(Butterflies.MAGIC_INSECT_NET, 1)
                client.npcOp(client.babyAt(3224).index, Implings.CATCH)
                client.seen("[hunter.imp_jarred]")
                client.await("the jarred impling") { if (client.count(JARRED) == 1) Unit else null }
                assertEquals(0, client.count(Implings.IMPLING_JAR))
                client.await("20 XP") { if (client.stat(HUNTER)!!.xp - before == 20) Unit else null }
                // The catch holds the player for its whole 3 ticks.
                client.awaitTicks(Butterflies.CATCH_TICKS)
                // Loot: the jar comes back or breaks, and the table gives one thing or nothing.
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, LOOT_BUTTON_OP, client.slotOf(JARRED), JARRED)
                client.await("the jar looted") { if (client.count(JARRED) == 0) Unit else null }
                assertTrue(client.used() - client.count(Implings.IMPLING_JAR) <= 1)
                // No jar now: the other one's loot comes straight to the backpack.
                client.command("empty")
                client.await("an empty backpack") { if (client.used() == 0) Unit else null }
                client.npcOp(client.babyAt(3220).index, Implings.CATCH)
                client.seen("[hunter.imp_looted]", timeoutMillis = 15_000)
                client.await("40 XP") { if (client.stat(HUNTER)!!.xp - before == 40) Unit else null }
                assertTrue(client.used() <= 1)
                assertEquals(0, client.count(JARRED))
                assertEquals(0, server.world.npcs.all.count { it.spawn.source == "test" })
            }
        }
    }

    @Test
    fun `Loot needs a free slot, a net is needed below the barehanded level, and the level with one`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("el").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $JARRED 1")
                client.command("item $FILLER 27")
                client.await("a full backpack") { if (client.used() == 28) Unit else null }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, LOOT_BUTTON_OP, client.slotOf(JARRED), JARRED)
                client.seen("[hunter.loot_space]")
                assertEquals(1, client.count(JARRED))
                client.command("setlevel hunter 20")
                client.await("level 20") { client.stat(HUNTER)?.takeIf { it.level == 20 } }
                client.npcOp(client.babyAt(3224).index, Implings.CATCH)
                client.seen("[hunter.imp_need_net]")
                client.command("setlevel hunter 10")
                client.await("level 10") { client.stat(HUNTER)?.takeIf { it.level == 10 } }
                server.world.playerNamed("el")!!.equipment[WearPos.WEAPON] = InvObj(Butterflies.MAGIC_INSECT_NET, 1)
                client.npcOp(client.babyAt(3224).index, Implings.CATCH)
                client.seen("[hunter.imp_level level=17]")
                assertEquals(2, server.world.npcs.all.count { it.spawn.source == "test" })
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val HUNTER = 21
        const val JARRED = 11238
        const val FILLER = 1511

        /** iop3 (Loot) is button op 4 (see InventoryTab.click). */
        const val LOOT_BUTTON_OP = 4
    }
}
