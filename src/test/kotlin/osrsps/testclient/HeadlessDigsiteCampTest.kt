package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.DigsiteCamp
import osrsps.game.content.DigsiteCampLines
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 7 through the headless client: the Digsite's bushes and the teddy's bush, the tent's chest
 * locked and opened with its key, the barrel opened with a trowel and a vial filled at it, and the dungeon's stone
 * tablet. The rl-client script test-runner/examples/digsite-camp.json does the same in the real client.
 */
class HeadlessDigsiteCampTest {
    private val objs = listOf(
        DigsiteCamp.TEDDY to "Teddy", DigsiteCamp.CHEST_KEY to "Chest key", DigsiteCamp.CHEMICAL_POWDER to "Chemical powder",
        DigsiteCamp.TROWEL to "Trowel", DigsiteCamp.VIAL to "Vial", DigsiteCamp.UNIDENTIFIED_LIQUID to "Unidentified liquid",
        DigsiteCamp.STONE_TABLET to "Stone tablet",
    ).map { (id, name) -> ObjType(id, name) }

    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val bush = ZoneLoc(DigsiteCamp.BUSH, LocShape.CENTREPIECE_STRAIGHT, 0, 3355, 3372, 0)
    private val teddyBush = ZoneLoc(DigsiteCamp.TEDDY_BUSH, LocShape.CENTREPIECE_STRAIGHT, 0, 3357, 3372, 0)
    private val chest = ZoneLoc(DigsiteCamp.CHEST, LocShape.CENTREPIECE_STRAIGHT, 0, 3374, 3378, 0)
    private val barrel = ZoneLoc(DigsiteCamp.BARREL, LocShape.CENTREPIECE_STRAIGHT, 0, 3364, 3378, 0)
    private val tablet = ZoneLoc(DigsiteCamp.TABLET, LocShape.GROUND_DECOR, 0, 3373, 9746, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3355 shr 6, 3372 shr 6); markLoaded(3373 shr 6, 9746 shr 6) },
            objTypes = ObjTypes.of(*objs.toTypedArray()),
            varTypes = VarTypes(6_000, mapOf(DigsiteCamp.BARREL_VARBIT to VarbitType(BARREL_VARP, 0, 0), TABLET_VARBIT to VarbitType(BARREL_VARP, 1, 1)), 21_000),
            locTypes = LocTypes(
                listOf(
                    type(DigsiteCamp.BUSH, "Bush", "Search"), type(DigsiteCamp.TEDDY_BUSH, "Bush", "Search"),
                    type(DigsiteCamp.CHEST, "Chest", "Open"), type(DigsiteCamp.CHEST_OPEN, "Chest", "Search", "Close"),
                    LocType(
                        DigsiteCamp.BARREL, "null", List(5) { null }, LocCollisionType(width = 1, length = 1), multiloc = true,
                        multiVarbit = DigsiteCamp.BARREL_VARBIT, multiLocs = listOf(BARREL_SEALED, BARREL_OPEN, -1),
                    ),
                    type(BARREL_SEALED, "Barrel", "Open", "Search"), type(BARREL_OPEN, "Barrel", null, "Search"),
                    LocType(
                        DigsiteCamp.TABLET, "null", List(5) { null }, LocCollisionType(width = 1, length = 1, interactType = 0), multiloc = true,
                        multiVarbit = TABLET_VARBIT, multiLocs = listOf(TABLET_SHOWN, TABLET_FLOOR, -1),
                    ),
                    type(TABLET_SHOWN, "Stone Tablet", "Take"), type(TABLET_FLOOR, "Floor"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(bush, teddyBush, chest, barrel, tablet).forEach(::add) },
            random = GameRandom(3),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.objbox(key: String) {
        awaitDialogue(key) { it is DialogueView.Other }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.slot(id: Int) = await("$id held") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.message(line: osrsps.game.dialogue.Line) = awaitMessage(line.key) { it == DialogueText.NONE[line] }

    @Test
    fun `the bushes, the teddy's bush and the tent's chest`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3355, 3371)
                client.locOp(bush.id, bush.x, bush.z, DigsiteCamp.SEARCH)
                client.message(DigsiteCampLines.BUSH_NOTHING)

                client.locOp(teddyBush.id, teddyBush.x, teddyBush.z, DigsiteCamp.SEARCH)
                client.chat(DigsiteCampLines.TEDDY_FOUND.key)
                client.objbox(DigsiteCampLines.TEDDY_BOX.key)
                client.over()
                assertEquals(1, client.count(DigsiteCamp.TEDDY))
                // A teddy owned: the bush is empty.
                client.locOp(teddyBush.id, teddyBush.x, teddyBush.z, DigsiteCamp.SEARCH)
                client.awaitMessage("bush nothing again") { it == DialogueText.NONE[DigsiteCampLines.BUSH_NOTHING] && client.messages().count { m -> m.text == it } == 2 }
                assertEquals(1, client.count(DigsiteCamp.TEDDY))

                client.tele(3373, 3378)
                client.locOp(chest.id, chest.x, chest.z, DigsiteCamp.OPEN)
                client.message(DigsiteCampLines.CHEST_LOCKED)
                client.command("item ${DigsiteCamp.CHEST_KEY} 1")
                client.slot(DigsiteCamp.CHEST_KEY)
                client.locOp(chest.id, chest.x, chest.z, DigsiteCamp.OPEN)
                client.await("the chest open") { if (server.world.locs.at(chest.x, chest.z, 0).any { it.id == DigsiteCamp.CHEST_OPEN }) Unit else null }
                client.locOp(DigsiteCamp.CHEST_OPEN, chest.x, chest.z, DigsiteCamp.CHEST_SEARCH)
                client.objbox(DigsiteCampLines.CHEST_POWDER.key)
                client.over()
                assertEquals(1, client.count(DigsiteCamp.CHEMICAL_POWDER))
                client.locOp(DigsiteCamp.CHEST_OPEN, chest.x, chest.z, DigsiteCamp.CHEST_SEARCH)
                client.message(DigsiteCampLines.CHEST_EMPTY)
                client.locOp(DigsiteCamp.CHEST_OPEN, chest.x, chest.z, DigsiteCamp.CHEST_CLOSE)
                client.await("the chest shut") { if (server.world.locs.at(chest.x, chest.z, 0).any { it.id == DigsiteCamp.CHEST }) Unit else null }
            }
        }
    }

    @Test
    fun `a chest shut while a player walks over to search it gives no powder`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }
                    alice.tele(3373, 3378)
                    alice.command("item ${DigsiteCamp.CHEST_KEY} 1")
                    alice.slot(DigsiteCamp.CHEST_KEY)
                    alice.locOp(chest.id, chest.x, chest.z, DigsiteCamp.OPEN)
                    alice.await("the chest open") { if (server.world.locs.at(chest.x, chest.z, 0).any { it.id == DigsiteCamp.CHEST_OPEN }) Unit else null }
                    bob.tele(3374, 3380)
                    // Bob sets off to search it from two tiles away as Alice, beside it, shuts it.
                    bob.locOp(DigsiteCamp.CHEST_OPEN, chest.x, chest.z, DigsiteCamp.CHEST_SEARCH)
                    alice.locOp(DigsiteCamp.CHEST_OPEN, chest.x, chest.z, DigsiteCamp.CHEST_CLOSE)
                    alice.await("the chest shut") { if (server.world.locs.at(chest.x, chest.z, 0).any { it.id == DigsiteCamp.CHEST }) Unit else null }
                    val start = server.world.tick
                    bob.await("bob at the chest and a few ticks gone", timeoutMillis = 15_000) {
                        if (server.world.tick >= start + 8) Unit else null
                    }
                    assertEquals(0, bob.count(DigsiteCamp.CHEMICAL_POWDER), "no powder from a shut chest")
                }
            }
        }
    }

    @Test
    fun `the barrel opens with a trowel and fills a vial, and the tablet is taken once`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3363, 3378)
                client.locOp(barrel.id, barrel.x, barrel.z, DigsiteCamp.BARREL_OPEN)
                client.chat(DigsiteCampLines.BARREL_SHUT.key)
                client.over()
                client.locOp(barrel.id, barrel.x, barrel.z, DigsiteCamp.BARREL_SEARCH)
                client.chat(DigsiteCampLines.BARREL_CLOSED.key)
                client.over()
                client.command("item ${DigsiteCamp.TROWEL} 1")
                client.command("item ${DigsiteCamp.VIAL} 1")
                client.locTarget(barrel.id, barrel.x, barrel.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slot(DigsiteCamp.TROWEL), DigsiteCamp.TROWEL)
                client.chat(DigsiteCampLines.BARREL_OPENED.key)
                client.over()
                client.await("the barrel open") { if (client.varp(BARREL_VARP) and 1 == 1) Unit else null }
                client.locOp(barrel.id, barrel.x, barrel.z, DigsiteCamp.BARREL_SEARCH)
                client.chat(DigsiteCampLines.BARREL_BARE_HANDS.key)
                client.over()
                client.locTarget(barrel.id, barrel.x, barrel.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slot(DigsiteCamp.VIAL), DigsiteCamp.VIAL)
                client.objbox(DigsiteCampLines.BARREL_FILL.key)
                client.chat(DigsiteCampLines.BARREL_CAREFUL.key)
                client.message(DigsiteCampLines.BARREL_LID)
                assertEquals(1, client.count(DigsiteCamp.UNIDENTIFIED_LIQUID))
                assertEquals(0, client.count(DigsiteCamp.VIAL))
                client.await("the lid back") { if (client.varp(BARREL_VARP) and 1 == 0) Unit else null }

                client.tele(3373, 9745)
                client.locOp(tablet.id, tablet.x, tablet.z, DigsiteCamp.TAKE)
                client.await("the tablet") { if (client.count(DigsiteCamp.STONE_TABLET) == 1) Unit else null }
                client.locOp(tablet.id, tablet.x, tablet.z, DigsiteCamp.TAKE)
                client.message(DigsiteCampLines.TABLET_HAVE)
                assertEquals(1, client.count(DigsiteCamp.STONE_TABLET))
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val BARREL_VARP = 1500
        const val TABLET_VARBIT = 2548
        const val BARREL_SEALED = 17296
        const val BARREL_OPEN = 17297
        const val TABLET_SHOWN = 17367
        const val TABLET_FLOOR = 17368
    }
}
