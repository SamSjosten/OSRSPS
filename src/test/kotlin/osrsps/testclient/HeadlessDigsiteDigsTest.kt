package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.DigsiteDigs
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 9b through the headless client: digging at each site with the right and wrong tools (and the
 * workmen stopping the wrong ones), a specimen tray searched without and with a jar, panning and searching the muddy
 * tray, the specimen-jar sacks and a buried skeleton. The rl-client script test-runner/examples/digsite-digs.json does
 * the same on the real map.
 */
class HeadlessDigsiteDigsTest {
    private val workman = NpcType(DigsiteDigs.WORKMEN.first(), "Digsite workman", ops = listOf("Talk-to", null, "Steal-from", null, null), internalName = "digworkman1")

    private fun soil(x: Int, z: Int) = ZoneLoc(DigsiteDigs.SOIL_1, LocShape.GROUND_DECOR, 0, x, z, 0)

    /** A soil tile in each site: training, level 1, level 2 and level 3. */
    private val training = soil(3368, 3398)
    private val level1 = soil(3368, 3405)
    private val level2 = soil(3368, 3425)
    private val level3 = soil(3372, 3439)
    private val outside = soil(3383, 3418)
    private val tray = ZoneLoc(DigsiteDigs.SPECIMEN_TRAY, LocShape.CENTREPIECE_STRAIGHT, 0, 3366, 3410, 0)
    private val point = ZoneLoc(DigsiteDigs.PANNING_POINT, LocShape.CENTREPIECE_STRAIGHT, 0, 3376, 3410, 0)
    private val jarSacks = ZoneLoc(DigsiteDigs.JAR_SACKS, LocShape.CENTREPIECE_STRAIGHT, 0, 3366, 3402, 0)
    private val sacks = ZoneLoc(DigsiteDigs.SACKS, LocShape.CENTREPIECE_STRAIGHT, 0, 3366, 3400, 0)
    private val skeleton = ZoneLoc(DigsiteDigs.SKELETON, LocShape.CENTREPIECE_STRAIGHT, 0, 3360, 3398, 0)

    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1, interactType = if (id == DigsiteDigs.SOIL_1) 0 else 1))

    private fun server(dir: Path, workmen: List<Pair<Int, Int>> = listOf(3370 to 3410)) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(3000)
        for (find in DigsiteDigs.Site.entries.flatMap { it.finds } + DigsiteDigs.TRAY_FINDS + DigsiteDigs.PANNING_FINDS) {
            if (find.obj >= 0) objs[find.obj] = ObjType(find.obj, "Item ${find.obj}", stackable = find.obj in listOf(995, 680))
        }
        for (id in listOf(DigsiteDigs.TROWEL, DigsiteDigs.ROCK_PICK, DigsiteDigs.SPECIMEN_BRUSH, DigsiteDigs.SPECIMEN_JAR, DigsiteDigs.SPADE, DigsiteDigs.PANNING_TRAY)) {
            objs[id] = ObjType(id, "Tool $id")
        }
        objs[DigsiteDigs.PANNING_TRAY_MUD] = ObjType(DigsiteDigs.PANNING_TRAY_MUD, "Panning tray", iops = listOf(null, "Search", null, null, "Drop"))
        objs[DigsiteDigs.LEATHER_GLOVES] = ObjType(DigsiteDigs.LEATHER_GLOVES, "Leather gloves", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.HANDS)
        objs[DigsiteDigs.LEATHER_BOOTS] = ObjType(DigsiteDigs.LEATHER_BOOTS, "Leather boots", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.FEET)
        World(
            crcs,
            CollisionMap().apply { markLoaded(3368 shr 6, 3410 shr 6) },
            objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(workman),
            npcSpawns = workmen.map { (x, z) -> NpcSpawn(workman, x, z, source = "test") },
            locTypes = LocTypes(
                listOf(
                    type(DigsiteDigs.SOIL_1, "Soil", null),
                    type(DigsiteDigs.SPECIMEN_TRAY, "Specimen tray", null, "Search"),
                    type(DigsiteDigs.PANNING_POINT, "Panning point", "Pan"),
                    type(DigsiteDigs.JAR_SACKS, "Sacks", "Search"),
                    type(DigsiteDigs.SACKS, "Sacks", "Search"),
                    type(DigsiteDigs.SKELETON, "Buried skeleton", "Search"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(training, level1, level2, level3, outside, tray, point, jarSacks, sacks, skeleton).forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.chats(vararg keys: String) {
        for (key in keys) {
            awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
            continueDialogue()
        }
    }

    private fun HeadlessClient.awaitClosed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.give(vararg ids: Int) {
        for (id in ids) {
            command("item $id")
            slotOf(id)
        }
    }

    private fun HeadlessClient.wear(id: Int) {
        give(id)
        ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 3, slotOf(id), id)
        await("item $id worn") { inventory(WORN).firstOrNull { it?.id == id } }
    }

    private fun HeadlessClient.use(id: Int, loc: ZoneLoc) = locTarget(loc.id, loc.x, loc.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(id), id)

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.awaitSeen(text: String, times: Int = 1) = await("$text x$times") { if (seen(text) >= times) Unit else null }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.xp(skill: Int) = stat(skill)?.xp ?: 0

    private fun HeadlessClient.awaitXp(skill: Int, xp: Int) = await("$xp XP in skill $skill") { stat(skill)?.takeIf { it.xp == xp } }

    /** Finds and empty finds both: their keys start digsite_dig.find. */
    private fun HeadlessClient.finds() = messages().count { it.text.startsWith("[digsite_dig.find") }

    @Test
    fun `each site takes its own tools and the workman stops the wrong ones`(@TempDir dir: Path) {
        // A workman beside the training, level 1 and level 2 pits, so each wrong tool brings one.
        server(dir, workmen = listOf(3370 to 3399, 3370 to 3406, 3370 to 3424)).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.give(DigsiteDigs.TROWEL, DigsiteDigs.ROCK_PICK, DigsiteDigs.SPADE, DigsiteDigs.SPECIMEN_BRUSH)
                val start = client.await("stats") { client.stat(MINING) }.xp

                client.tele(training.x, training.z)
                client.use(DigsiteDigs.TROWEL, training)
                client.awaitSeen("[digsite_dig.dig]")
                client.awaitXp(MINING, start + 5)
                client.await("a find") { if (client.finds() == 1) Unit else null }

                client.use(DigsiteDigs.SPADE, training)
                client.chats("digsite_dig.spade")
                client.awaitClosed()
                client.use(DigsiteDigs.SPECIMEN_BRUSH, training)
                client.chats("digsite_dig.brush")
                client.awaitClosed()

                client.tele(level1.x, level1.z)
                client.use(DigsiteDigs.TROWEL, level1)
                client.chats("digsite_dig.gloves", "digsite_dig.no_gloves", "digsite_dig.get_gloves")
                client.awaitClosed()
                client.wear(DigsiteDigs.LEATHER_GLOVES)
                client.use(DigsiteDigs.TROWEL, level1)
                client.chats("digsite_dig.boots")
                client.awaitClosed()
                client.wear(DigsiteDigs.LEATHER_BOOTS)
                client.use(DigsiteDigs.TROWEL, level1)
                client.awaitXp(MINING, start + 11)
                client.use(DigsiteDigs.ROCK_PICK, level1)
                client.chats("digsite_dig.rock_pick_level_2")
                client.awaitClosed()

                client.tele(level2.x, level2.z)
                client.use(DigsiteDigs.TROWEL, level2)
                client.chats("digsite_dig.need_rock_pick")
                client.awaitClosed()
                client.use(DigsiteDigs.ROCK_PICK, level2)
                client.awaitXp(MINING, start + 18)

                client.tele(level3.x, level3.z)
                client.use(DigsiteDigs.TROWEL, level3)
                client.awaitSeen("[digsite_dig.level_3_kit]")
                client.give(DigsiteDigs.SPECIMEN_JAR)
                client.use(DigsiteDigs.TROWEL, level3)
                client.awaitXp(MINING, start + 26)
                client.await("four finds") { if (client.finds() == 4) Unit else null }

                client.tele(outside.x, outside.z)
                client.use(DigsiteDigs.TROWEL, outside)
                client.awaitSeen("[digsite_dig.not_a_site]")
                assertEquals(start + 26, client.xp(MINING))
            }
        }
    }

    @Test
    fun `with no workman near, a wrong tool's line shows as a message`(@TempDir dir: Path) {
        server(dir, workmen = emptyList()).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.give(DigsiteDigs.SPADE)
                client.tele(training.x, training.z)
                client.use(DigsiteDigs.SPADE, training)
                client.awaitSeen("[digsite_dig.spade]")
                assertEquals(null, client.dialogueView)
            }
        }
    }

    @Test
    fun `a specimen tray needs a jar, and a trowel in it brings the workman`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.tele(tray.x, tray.z - 1)
                client.locOp(tray.id, tray.x, tray.z, DigsiteDigs.TRAY_SEARCH)
                client.chats("digsite_dig.oi")
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[digsite_dig.errand]", "[digsite_dig.searching_tray]"), menu.options)
                client.chooseOption(2)
                client.chats("digsite_dig.searching_tray", "digsite_dig.wheres_your_jar", "digsite_dig.ah_dont_have", "digsite_dig.bring_it")
                client.awaitClosed()
                client.give(DigsiteDigs.TROWEL)
                client.use(DigsiteDigs.TROWEL, tray)
                client.chats("digsite_dig.no_digging_trays")
                client.awaitClosed()

                client.give(DigsiteDigs.SPECIMEN_JAR)
                client.locOp(tray.id, tray.x, tray.z, DigsiteDigs.TRAY_SEARCH)
                client.awaitSeen("[digsite_dig.search_tray]")
                client.await("a find") { if (client.finds() == 1) Unit else null }
            }
        }
    }

    @Test
    fun `panning fills the tray with mud for 2 Fishing and 2 Mining XP, and searching it empties it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.tele(point.x, point.z - 1)
                client.locOp(point.id, point.x, point.z, DigsiteDigs.PAN)
                client.awaitSeen("[digsite_dig.no_panning_tray]")
                client.give(DigsiteDigs.PANNING_TRAY)
                val fishing = client.await("stats") { client.stat(FISHING) }.xp
                val mining = client.xp(MINING)
                client.locOp(point.id, point.x, point.z, DigsiteDigs.PAN)
                client.awaitSeen("[digsite_dig.pan]")
                client.await("a muddy tray") { if (client.count(DigsiteDigs.PANNING_TRAY_MUD) == 1) Unit else null }
                client.awaitXp(FISHING, fishing + 2)
                client.awaitXp(MINING, mining + 2)

                client.locOp(point.id, point.x, point.z, DigsiteDigs.PAN)
                client.chats("digsite_dig.full_tray")
                client.awaitClosed()

                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 3, client.slotOf(DigsiteDigs.PANNING_TRAY_MUD), DigsiteDigs.PANNING_TRAY_MUD)
                client.await("an empty tray") { if (client.count(DigsiteDigs.PANNING_TRAY) == 1) Unit else null }
                client.awaitSeen("[digsite_dig.search_panning_tray]")
                client.await("a find or nothing") { if (client.finds() == 1) Unit else null }
            }
        }
    }

    @Test
    fun `the jar sacks give one jar, the other sacks and the skeleton nothing, and a trowel on the skeleton brings the workman`(@TempDir dir: Path) {
        server(dir, workmen = listOf(3362 to 3399)).use { server ->
            server.login("erin").use { client ->
                client.ready()
                client.tele(jarSacks.x, jarSacks.z - 1)
                client.locOp(jarSacks.id, jarSacks.x, jarSacks.z, DigsiteDigs.SEARCH)
                client.chats("digsite_dig.something_under")
                client.awaitDialogue("the jar box") { it is DialogueView.Other && it.interfaceId == Chatbox.OBJECTBOX }
                client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
                client.awaitClosed()
                assertEquals(1, client.count(DigsiteDigs.SPECIMEN_JAR))
                client.locOp(jarSacks.id, jarSacks.x, jarSacks.z, DigsiteDigs.SEARCH)
                client.awaitSeen("[digsite_dig.sacks_nothing]")
                assertEquals(1, client.count(DigsiteDigs.SPECIMEN_JAR))
                client.locOp(sacks.id, sacks.x, sacks.z, DigsiteDigs.SEARCH)
                client.awaitSeen("[digsite_dig.sacks_nothing]", 2)
                assertEquals(3, client.seen("[digsite_dig.search_sacks]"))

                client.locOp(skeleton.id, skeleton.x, skeleton.z, DigsiteDigs.SEARCH)
                client.awaitSeen("[digsite_dig.skeleton_nothing]")
                assertTrue(client.seen("[digsite_dig.search_skeleton]") == 1)
                client.give(DigsiteDigs.TROWEL)
                client.use(DigsiteDigs.TROWEL, skeleton)
                client.chats("digsite_dig.skeleton_fragile", "digsite_dig.skeleton_sorry")
                client.awaitClosed()
            }
        }
    }

    private companion object {
        const val MINING = 14
        const val FISHING = 10
        const val WORN = 94
    }
}
