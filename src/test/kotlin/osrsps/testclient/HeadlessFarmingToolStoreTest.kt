package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.FarmsRoads
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.farming.ToolStore
import osrsps.map.CollisionMap

/**
 * Farming step 9 through the headless client: a Tool Leprechaun's store (farming_tools 125 and farming_tools_side 126).
 * Exchange and "Yes please." open it; rakes go in by the default quantity and come out by Remove-X; noted compost goes
 * in and comes out as banknotes, past what a pack holds; the store stops at 100 rakes and one watering can; magic
 * secateurs keep plain ones out; a full pack takes nothing; and Deposit-all puts in everything it holds, saying so when
 * there is nothing. What is stored is read off the varbits the client draws it from, at their cache 242 bits. The
 * rl-client script test-runner/examples/farming-tool-store.json does the same in the real client.
 */
class HeadlessFarmingToolStoreTest {
    private val leprechaun = NpcType(FarmsRoads.TOOL_LEPRECHAUN, "Tool Leprechaun", ops = listOf("Talk-to", null, "Exchange", "Deposit-all", null), internalName = "farming_tools_leprechaun")

    private val objs = Array<ObjType?>(OBJS) { ObjType(it, "Obj $it") }.also { objs ->
        // Items with banknotes, the note one id on as in the cache for these.
        for ((id, name) in mapOf(RAKE to "Rake", SPADE to "Spade", SECATEURS to "Secateurs", MAGIC_SECATEURS to "Magic secateurs", COMPOST to "Compost", BUCKET to "Bucket")) {
            objs[id] = ObjType(id, name, tradeable = true, certlink = id + 1)
            objs[id + 1] = ObjType(id + 1, name, stackable = true, tradeable = true, certlink = id, certtemplate = 799)
        }
        for (can in ToolStore.WATERING_CANS) objs[can] = ObjType(can, "Watering can")
    }.let { ObjTypes(it) }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6) }, objTypes = objs, npcTypes = NpcTypes.of(leprechaun),
            npcSpawns = listOf(NpcSpawn(leprechaun, X + 2, Z, source = "test")),
            varTypes = VarTypes(6_000, VARBITS, 21_000),
            random = GameRandom(1),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.give(item: Int, count: Int = 1) {
        val before = count(item)
        command("item $item $count")
        await("item $item") { if (count(item) >= before + count) Unit else null }
    }

    private fun HeadlessClient.awaitCount(id: Int, count: Int) =
        await("$count of item $id (${count(id)})") { if (count(id) == count) Unit else null }

    private fun HeadlessClient.varbit(id: Int): Int {
        val type = VARBITS.getValue(id)
        return (varp(type.baseVar) ushr type.startBit) and type.mask
    }

    private fun HeadlessClient.awaitVarbit(id: Int, value: Int) =
        await("varbit $id at $value (${varbit(id)})") { if (varbit(id) == value) Unit else null }

    private fun HeadlessClient.rakes() = 2 * varbit(ToolStore.VARBIT_EXTRA_RAKES) + varbit(ToolStore.VARBIT_RAKE)

    private fun HeadlessClient.compost() = 256 * varbit(ToolStore.VARBIT_EXTRA_COMPOST) + varbit(ToolStore.VARBIT_COMPOST)

    private fun HeadlessClient.awaitRakes(count: Int) = await("$count rakes stored (${rakes()})") { if (rakes() == count) Unit else null }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.awaitSeen(text: String, times: Int) =
        await("'$text' $times times (${seen(text)})") { if (seen(text) == times) Unit else null }

    private fun HeadlessClient.op(op: Int) {
        val npc = await("the leprechaun in view") { npcs().firstOrNull { it.id == FarmsRoads.TOOL_LEPRECHAUN } }
        npcOp(npc.index, op)
    }

    private fun HeadlessClient.awaitStore() = await("the store open") {
        if (ToolStore.MAIN in openInterfaces() && ToolStore.SIDE in openInterfaces()) Unit else null
    }

    private fun HeadlessClient.setUp() {
        await("the login rebuild") { position }
        command("tele $X $Z")
        await("beside the leprechaun") { position?.takeIf { it.x == X && it.z == Z } }
    }

    private fun HeadlessClient.store(tool: ToolStore.Tool, op: Int = 1) = ifButton(ToolStore.SIDE, tool.sideComponent, op)

    private fun HeadlessClient.remove(tool: ToolStore.Tool, op: Int = 1) = ifButton(ToolStore.MAIN, tool.mainComponent, op)

    private fun HeadlessClient.quantity(mode: Int) {
        ifButton(ToolStore.MAIN, ToolStore.QUANTITY_BUTTONS.entries.first { it.value == mode }.key, 1)
        awaitVarbit(ToolStore.VARBIT_QUANTITY, mode)
    }

    @Test
    fun `rakes go in by the default quantity and come out by Remove-X, and noted compost comes back as banknotes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.setUp()
                client.op(FarmsRoads.EXCHANGE)
                client.awaitStore()

                client.give(RAKE, 6)
                // Default quantity 1: op 1 is Store-1, op 2 Store-5.
                client.store(ToolStore.Tool.RAKE)
                client.awaitRakes(1)
                client.store(ToolStore.Tool.RAKE, 2)
                client.awaitRakes(6)
                client.awaitCount(RAKE, 0)
                // Remove-X is op 3 at quantity 1: 3 come out.
                client.remove(ToolStore.Tool.RAKE, 3)
                client.await("the amount prompt") { client.clientScripts().lastOrNull { it.id == MESLAYER_MODE7 } }
                client.resumeCount(3)
                client.awaitCount(RAKE, 3)
                client.awaitRakes(3)

                // All: 300 noted compost go in, more than a pack holds, and come out noted with Banknotes.
                client.give(COMPOST + 1, 300)
                client.quantity(ToolStore.MODE_ALL)
                client.store(ToolStore.Tool.COMPOST)
                client.await("300 compost stored (${client.compost()})") { if (client.compost() == 300) Unit else null }
                client.awaitCount(COMPOST + 1, 0)
                assertEquals(300 and 255, client.varbit(ToolStore.VARBIT_COMPOST))
                assertEquals(1, client.varbit(ToolStore.VARBIT_EXTRA_COMPOST))
                client.remove(ToolStore.Tool.COMPOST, ToolStore.BANKNOTES)
                client.awaitCount(COMPOST + 1, 300)
                assertEquals(0, client.compost())
            }
        }
    }

    @Test
    fun `the store stops at 100 rakes and one watering can, keeps plain secateurs from magic ones, and a full pack takes nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.setUp()
                client.op(FarmsRoads.EXCHANGE)
                client.awaitStore()
                client.quantity(ToolStore.MODE_ALL)

                client.give(RAKE + 1, 101)
                client.store(ToolStore.Tool.RAKE)
                client.awaitRakes(100)
                client.awaitCount(RAKE + 1, 1)
                client.store(ToolStore.Tool.RAKE)
                client.awaitSeen(FULL, 1)

                // The can with 4 doses is enum 136's fifth; a second can doesn't fit.
                client.give(ToolStore.WATERING_CANS[4])
                client.store(ToolStore.Tool.WATERING_CAN)
                client.awaitVarbit(ToolStore.VARBIT_WATERING_CAN, 5)
                client.awaitCount(ToolStore.WATERING_CANS[4], 0)
                client.give(ToolStore.WATERING_CANS[0])
                client.store(ToolStore.Tool.WATERING_CAN)
                client.awaitSeen(FULL, 2)

                // Magic secateurs first (farming_tools_getcarried), then plain ones are refused.
                client.give(MAGIC_SECATEURS)
                client.give(SECATEURS)
                client.store(ToolStore.Tool.SECATEURS)
                client.awaitVarbit(ToolStore.VARBIT_MAGIC_SECATEURS, 1)
                client.awaitVarbit(ToolStore.VARBIT_SECATEURS, 1)
                client.awaitCount(MAGIC_SECATEURS, 0)
                client.store(ToolStore.Tool.SECATEURS)
                client.awaitSeen(OTHER_KIND, 1)
                assertEquals(1, client.count(SECATEURS))

                // A full pack: Remove takes nothing and says so. The magic secateurs come out as themselves.
                // The noted rake, the empty can and the plain secateurs take 3 slots: 25 spades fill the rest.
                client.give(SPADE, 25)
                client.remove(ToolStore.Tool.WATERING_CAN)
                client.awaitSeen(NO_SPACE, 1)
                assertEquals(5, client.varbit(ToolStore.VARBIT_WATERING_CAN))
                client.command("empty")
                client.awaitCount(SPADE, 0)
                client.remove(ToolStore.Tool.SECATEURS)
                client.awaitCount(MAGIC_SECATEURS, 1)
                client.awaitVarbit(ToolStore.VARBIT_SECATEURS, 0)
                assertEquals(0, client.varbit(ToolStore.VARBIT_MAGIC_SECATEURS))
                client.remove(ToolStore.Tool.WATERING_CAN)
                client.awaitCount(ToolStore.WATERING_CANS[4], 1)
                assertEquals(0, client.varbit(ToolStore.VARBIT_WATERING_CAN))
            }
        }
    }

    @Test
    fun `Yes please opens the store, and Deposit-all puts in everything it holds or says there is nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.setUp()
                client.op(FarmsRoads.TALK_TO)
                client.awaitDialogue("his greeting") { it is DialogueView.Chat && it.text == "[tool_leprechaun.greeting]" }
                client.continueDialogue()
                client.awaitDialogue("his menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.awaitStore()
                client.closeModal()
                client.await("the store closed") { if (ToolStore.MAIN !in client.openInterfaces()) Unit else null }

                client.op(FarmsRoads.DEPOSIT_ALL)
                client.awaitSeen(NOTHING, 1)

                client.give(RAKE, 2)
                client.give(SPADE)
                client.give(BUCKET, 3)
                client.give(BUCKET + 1, 40)
                client.op(FarmsRoads.DEPOSIT_ALL)
                client.awaitRakes(2)
                client.awaitCount(BUCKET + 1, 0)
                assertEquals(0, client.count(SPADE))
                assertEquals(0, client.count(BUCKET))
                assertEquals(1, client.varbit(ToolStore.VARBIT_SPADE))
                // 43 buckets: 11 in the low 5 bits, 1 in the next 3.
                assertEquals(43 and 31, client.varbit(ToolStore.VARBIT_BUCKETS))
                assertEquals(1, client.varbit(ToolStore.VARBIT_EXTRA_BUCKETS))
                assertTrue(ToolStore.MAIN !in client.openInterfaces(), "Deposit-all doesn't open the store")
            }
        }
    }

    private companion object {
        const val OBJS = 25_000
        const val BACKPACK = 93
        const val MESLAYER_MODE7 = 108
        const val X = 3193
        const val Z = 3229
        const val RAKE = 5341
        const val SPADE = 952
        const val SECATEURS = 5329
        const val MAGIC_SECATEURS = 7409
        const val COMPOST = 6032
        const val BUCKET = 1925
        const val FULL = "[tool_store.full]"
        const val OTHER_KIND = "[tool_store.other_kind]"
        const val NO_SPACE = "[tool_store.no_space]"
        const val NOTHING = "[tool_store.nothing]"

        /** The store's varbits at their cache 242 bits. */
        val VARBITS: Map<Int, VarbitType> = mapOf(
            ToolStore.VARBIT_RAKE to VarbitType(615, 0, 0), ToolStore.VARBIT_DIBBER to VarbitType(615, 1, 1),
            ToolStore.VARBIT_SPADE to VarbitType(615, 2, 2), ToolStore.VARBIT_SECATEURS to VarbitType(615, 3, 3),
            ToolStore.VARBIT_WATERING_CAN to VarbitType(615, 4, 7), ToolStore.VARBIT_TROWEL to VarbitType(615, 8, 8),
            ToolStore.VARBIT_BUCKETS to VarbitType(615, 9, 13), ToolStore.VARBIT_COMPOST to VarbitType(615, 14, 21),
            ToolStore.VARBIT_SUPERCOMPOST to VarbitType(615, 22, 29), ToolStore.VARBIT_MAGIC_SECATEURS to VarbitType(615, 30, 30),
            ToolStore.VARBIT_EXTRA_BUCKETS to VarbitType(439, 10, 12), ToolStore.VARBIT_ULTRACOMPOST to VarbitType(967, 2, 11),
            ToolStore.VARBIT_EXTRA2_BUCKETS to VarbitType(967, 12, 13), ToolStore.VARBIT_EXTRA_COMPOST to VarbitType(967, 14, 15),
            ToolStore.VARBIT_EXTRA_SUPERCOMPOST to VarbitType(967, 16, 17), ToolStore.VARBIT_PLANT_CURE to VarbitType(967, 18, 27),
            ToolStore.VARBIT_QUANTITY to VarbitType(675, 14, 15), ToolStore.VARBIT_BOTTOMLESS_TYPE to VarbitType(2084, 0, 2),
            ToolStore.VARBIT_EXTRA_RAKES to VarbitType(2084, 17, 22), ToolStore.VARBIT_EXTRA_DIBBERS to VarbitType(2084, 23, 28),
            ToolStore.VARBIT_EXTRA_SECATEURS to VarbitType(1785, 18, 23), ToolStore.VARBIT_EXTRA_TROWELS to VarbitType(1785, 24, 29),
            ToolStore.VARBIT_EXTRA_SPADES to VarbitType(1704, 17, 22),
        )
    }
}
