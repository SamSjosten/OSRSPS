package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.Silvarea
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Mining
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 7 through the headless client: Silvarea's limestone rocks mined through their three stages
 * and coming back whole, the Odd Old Man handing back a lost ram skull helm and bonesack and then talking about his
 * sorting, his wish-list, the trapdoor to Zemouregal's base and the members' gate in Varrock's east fence. The rl-client
 * script test-runner/examples/silvarea.json does the same on the real map.
 */
class HeadlessSilvareaTest {
    private val oddOldMan = NpcType(Silvarea.ODD_OLD_MAN, "Odd Old Man", size = 2, ops = listOf("Talk-to", null, null, null, null), internalName = "rag_odd_old_man")
    private val sack = NpcType(Silvarea.TALKING_SACK, "Bones", internalName = "talking_sack")

    private fun type(id: Int, name: String, op: String?, multiloc: Boolean = false) =
        LocType(id, name, ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = 1, length = 1, interactType = 0), multiloc = multiloc)

    /** One rock to mine through every stage, one to leave after its first. */
    private val limestone = ZoneLoc(LIMESTONE_1, LocShape.CENTREPIECE_STRAIGHT, 0, 3224, 3222, 0)
    private val limestone2 = ZoneLoc(LIMESTONE_1, LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3222, 0)
    private val wishList = ZoneLoc(Silvarea.WISH_LIST, LocShape.CENTREPIECE_STRAIGHT, 1, 3218, 3216, 0)
    private val baseEntry = ZoneLoc(Silvarea.BASE_ENTRY, LocShape.CENTREPIECE_STRAIGHT, 1, 3216, 3222, 0)
    private val gateLeft = ZoneLoc(Silvarea.GATE_LEFT, LocShape.WALL_STRAIGHT, 2, 3228, 3217, 0)
    private val gateRight = ZoneLoc(Silvarea.GATE_RIGHT, LocShape.WALL_STRAIGHT, 2, 3228, 3216, 0)

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Silvarea.BONESACK + 1)
        objs[LIMESTONE] = ObjType(LIMESTONE, "Limestone")
        objs[RUNE_PICKAXE] = ObjType(RUNE_PICKAXE, "Rune pickaxe")
        objs[Silvarea.RAM_SKULL_HELM] = ObjType(Silvarea.RAM_SKULL_HELM, "Ram skull helm")
        objs[Silvarea.BONESACK] = ObjType(Silvarea.BONESACK, "Bonesack")
        World(
            crcs,
            CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(oddOldMan, sack),
            npcSpawns = listOf(NpcSpawn(oddOldMan, 3220, 3214, source = "test")),
            locTypes = LocTypes(
                listOf(
                    type(LIMESTONE_1, "Limestone rock", "Mine"),
                    type(LIMESTONE_2, "Limestone rock", "Mine"),
                    type(LIMESTONE_3, "Limestone rock", "Mine"),
                    type(Mining.LIMESTONE_EMPTY, "Pile of rock", "Mine"),
                    type(Silvarea.WISH_LIST, "Wish-list", "Read"),
                    type(Silvarea.BASE_ENTRY, "null", null, multiloc = true),
                    type(Silvarea.GATE_LEFT, "Gate", "Open"),
                    type(Silvarea.GATE_LEFT_OPEN, "Gate", "Close"),
                    type(Silvarea.GATE_RIGHT, "Gate", "Open"),
                    type(Silvarea.GATE_RIGHT_OPEN, "Gate", "Close"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(limestone, limestone2, wishList, baseEntry, gateLeft, gateRight).forEach(::add) },
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

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.awaitLoc(loc: ZoneLoc, id: Int) =
        await("$id at ${loc.x}, ${loc.z}", timeoutMillis = 30_000) { if (locAt(loc.x, loc.z) == id) Unit else null }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.awaitSeen(text: String, times: Int = 1) = await("$text x$times") { if (seen(text) >= times) Unit else null }

    private fun HeadlessClient.talk() {
        val npc = await("the Odd Old Man in view") { npcs().firstOrNull { it.id == Silvarea.ODD_OLD_MAN } }
        npcOp(npc.index, Silvarea.TALK_TO)
    }

    @Test
    fun `a limestone rock gives three limestone in three stages and comes back whole 9 ticks after the last`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.command("item $RUNE_PICKAXE")
                client.command("setlevel mining 99")
                val start = client.await("level 99") { client.stat(MINING)?.takeIf { it.level == 99 } }.xp
                for ((stage, next) in listOf(LIMESTONE_1 to LIMESTONE_2, LIMESTONE_2 to LIMESTONE_3, LIMESTONE_3 to Mining.LIMESTONE_EMPTY)) {
                    client.locOp(stage, limestone.x, limestone.z, 1)
                    client.awaitLoc(limestone, next)
                }
                val emptied = client.ticksSeen
                assertEquals(3, client.count(LIMESTONE))
                client.awaitSeen("[mining.get_ore ore=limestone]", 3)
                // 26.5 XP a limestone: 79.5, and the stat packet carries whole XP.
                assertEquals(start + 79, client.await("the XP") { client.stat(MINING)?.takeIf { it.xp >= start + 79 } }.xp)

                // The pile of rock has nothing to mine.
                client.locOp(Mining.LIMESTONE_EMPTY, limestone.x, limestone.z, 1)
                client.awaitSeen("[mining.empty]")
                client.awaitLoc(limestone, LIMESTONE_1)
                val back = client.ticksSeen - emptied
                assertTrue(back in 8..11, "back whole after $back ticks")
            }
        }
    }

    @Test
    fun `a limestone rock left after one stage comes back whole 39 ticks later`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("item $RUNE_PICKAXE")
                client.command("setlevel mining 99")
                client.await("level 99") { client.stat(MINING)?.takeIf { it.level == 99 } }
                client.locOp(LIMESTONE_1, limestone2.x, limestone2.z, 1)
                client.awaitLoc(limestone2, LIMESTONE_2)
                val mined = client.ticksSeen
                client.awaitLoc(limestone2, LIMESTONE_1)
                val back = client.ticksSeen - mined
                assertTrue(back in 38..41, "back whole after $back ticks")
                assertEquals(1, client.count(LIMESTONE))
            }
        }
    }

    @Test
    fun `the Odd Old Man hands back a lost helm, then a lost bonesack, then talks about his sorting`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.talk()
                client.chats("odd_old_man.replace_helm", "odd_old_man.plenty", "odd_old_man.thanks")
                client.awaitClosed()
                assertEquals(1, client.count(Silvarea.RAM_SKULL_HELM))

                client.talk()
                client.chats("odd_old_man.replace_bonesack", "odd_old_man.plenty", "odd_old_man.thanks")
                client.awaitClosed()
                assertEquals(1, client.count(Silvarea.BONESACK))

                client.talk()
                client.chats("odd_old_man.sorting", "odd_old_man.going_well", "odd_old_man.glad", "odd_old_man.mumbling", "odd_old_man.errr")
                val mumble = client.awaitDialogue("the sack") { it is DialogueView.Chat && it.text == "[odd_old_man.sack_mumble]" } as DialogueView.Chat
                assertEquals("[odd_old_man.sack_name]", mumble.name)
                client.continueDialogue()
                client.chats("odd_old_man.what_mumbling", "odd_old_man.lie_down")
                client.awaitClosed()
                assertEquals(listOf(1, 1), listOf(Silvarea.RAM_SKULL_HELM, Silvarea.BONESACK).map { client.count(it) })
            }
        }
    }

    @Test
    fun `the wish-list reads, the base's trapdoor waits for the base, and the gate opens both leaves`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.locOp(wishList.id, wishList.x, wishList.z, Silvarea.READ)
                client.awaitDialogue("the wish-list") { it == DialogueView.Message("[silvarea.wish_list]") }
                client.continueDialogue()
                client.awaitClosed()

                client.locOp(baseEntry.id, baseEntry.x, baseEntry.z, Silvarea.BASE_OP)
                client.awaitSeen("[silvarea.base_later]")

                client.locOp(gateLeft.id, gateLeft.x, gateLeft.z, 1)
                client.await("both leaves open") {
                    if (!server.world.locs.exists(gateLeft) && !server.world.locs.exists(gateRight)) Unit else null
                }
            }
        }
    }

    private companion object {
        const val LIMESTONE_1 = 11382
        const val LIMESTONE_2 = 11383
        const val LIMESTONE_3 = 11384
        const val LIMESTONE = 3211
        const val RUNE_PICKAXE = 1275
        const val MINING = 14
    }
}
