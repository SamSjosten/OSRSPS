package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.Inventory
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Slayer
import osrsps.game.skill.SlayerData
import osrsps.game.skill.SlayerMasterTask
import osrsps.game.skill.SlayerTask
import osrsps.map.CollisionMap

/**
 * Slayer step 5b through the headless client: a black mask worn makes a punch at a cow 7/6 as accurate and hard hitting
 * while cows are the task, and does nothing once they aren't, read off `::combat`.
 */
class HeadlessSlayerHelmTest {
    private val cow = NpcType(2790, "Cow", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 2, internalName = "cow", params = mapOf(Slayer.PARAM_CATEGORY to 6), stats = NpcStats(hitpoints = 8))
    private val cows = SlayerTask(6219, 6, "cows", "Cows")
    private val data = SlayerData(listOf(cows), listOf(SlayerMasterTask(3, cows, 8, 40, 80)))

    // The 242 cache's uncharged black mask, worn on the head.
    private val mask = ObjType(BLACK_MASK, "Black mask", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.HEAD)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(cow)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5),
            varTypes = VarTypes(6_000, mapOf(Slayer.VARBIT_MASTER to VarbitType(661, 2, 5), Slayer.VARBIT_POINTS to VarbitType(661, 6, 22), Slayer.VARBIT_STREAK to VarbitType(1077, 0, 13))),
            objTypes = ObjTypes.of(mask), npcTypes = types, npcSpawns = listOf(NpcSpawn(cow, 3224, 3218, wander = 0, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), slayerData = data, cycleMillis = millis,
        )
    }

    /** `::combat cow`'s attack roll and max hit. */
    private fun HeadlessClient.numbers(): Pair<Int, Int> {
        val line = commandReply("combat cow") { it.contains("attack roll") && it.contains("max hit") && !it.startsWith("It:") }
        val m = Regex("attack roll (\\d+), max hit (\\d+)").find(line) ?: error("no numbers in $line")
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    @Test
    fun `a black mask makes melee 7 sixths as strong on task and does nothing off it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hana").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel all 60") { it.startsWith("Every skill") }

                client.command("item $BLACK_MASK 1")
                val slot = client.await("the mask in the backpack") { client.inventory(Inventory.INV).indexOfFirst { it?.id == BLACK_MASK }.takeIf { it >= 0 } }
                client.ifButton(INVENTORY, INVENTORY_ITEMS, WEAR_BUTTON, slot, BLACK_MASK)
                client.await("the mask worn") { client.inventory(WORN).firstOrNull { it?.id == BLACK_MASK } }
                // No task yet, so the mask adds nothing.
                val (offRoll, offMax) = client.numbers()

                client.commandReply("slayer task cows 10 vannaka") { it.startsWith("Task") }
                assertEquals(offRoll * 7 / 6 to offMax * 7 / 6, client.numbers())

                client.commandReply("slayer clear") { it.startsWith("No task") }
                assertEquals(offRoll to offMax, client.numbers())
            }
        }
    }

    private companion object {
        const val BLACK_MASK = 8921
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val WORN = 94
        const val WEAR_BUTTON = 3
    }
}
