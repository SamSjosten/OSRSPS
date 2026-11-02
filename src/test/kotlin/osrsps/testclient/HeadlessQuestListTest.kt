package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Quest
import osrsps.game.QuestKind
import osrsps.game.QuestVar
import osrsps.game.Quests
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.map.CollisionMap

/**
 * The quest list's footer over the wire: at login the client gets the quest points, the most there are, and the
 * quests finished and in total, so the tab reads "Completed: 2/2" and "Quest Points: 6/6" for these quests rather than
 * "0/0" and "6/0". Bit ranges as the 241 cache has them.
 */
class HeadlessQuestListTest {
    @Test
    fun `login sends the footer's counts and the quest points total`(@TempDir dir: Path) {
        val quests = Quests(
            listOf(
                Quest(17, "A", QuestVar.Varp(29), 2, 1),
                Quest(18, "B", QuestVar.Varp(30), 2, 3),
                Quest(19, "C", QuestVar.Varp(31), 2, 2, QuestKind.SUBQUEST),
                Quest(20, "D", QuestVar.Varp(32), 2, 0, QuestKind.MINIQUEST),
            ),
        )
        val server = TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
                varTypes = VarTypes(4_000, VARBITS), quests = quests, cycleMillis = millis,
            )
        }
        server.use {
            server.login("alice").use { client ->
                client.await("the quest points") { Unit.takeIf { client.varp(Quests.VARP_QUEST_POINTS) == 6 } }
                fun varbit(id: Int): Int {
                    val type = VARBITS.getValue(id)
                    val width = type.endBit - type.startBit + 1
                    return (client.varp(type.baseVar) ushr type.startBit) and ((1 shl width) - 1)
                }
                val expected = mapOf(
                    Quests.VARBIT_QP_MAX to 6,
                    Quests.VARBIT_QUESTS_COMPLETED to 2,
                    Quests.VARBIT_QUESTS_TOTAL to 2,
                    Quests.VARBIT_SUBQUESTS_COMPLETED to 1,
                    Quests.VARBIT_MINIQUESTS_COMPLETED to 1,
                )
                // Each varp arrives in its own packet, so wait for all three before reading any counter.
                client.await("the footer's counters") { Unit.takeIf { expected.keys.all { varbit(it) != 0 } } }
                assertEquals(expected, expected.keys.associateWith { varbit(it) })
            }
        }
    }

    private companion object {
        /** Varps qp_total and qp_total2. */
        const val QP_TOTAL = 904
        const val QP_TOTAL2 = 2920

        val VARBITS = mapOf(
            Quests.VARBIT_QP_MAX to VarbitType(QP_TOTAL, 0, 9),
            Quests.VARBIT_QUESTS_COMPLETED to VarbitType(QP_TOTAL, 20, 28),
            Quests.VARBIT_QUESTS_TOTAL to VarbitType(QP_TOTAL2, 0, 8),
            Quests.VARBIT_SUBQUESTS_COMPLETED to VarbitType(QP_TOTAL2, 17, 25),
            Quests.VARBIT_MINIQUESTS_COMPLETED to VarbitType(2921, 0, 8),
        )
    }
}
