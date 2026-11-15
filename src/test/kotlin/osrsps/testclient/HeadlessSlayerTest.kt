package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Slayer
import osrsps.game.skill.SlayerData
import osrsps.game.skill.SlayerMasterTask
import osrsps.game.skill.SlayerTask
import osrsps.map.CollisionMap

/**
 * Slayer step 1 through the headless client: a task in live's vars, a kill on task counting down with Slayer XP for the
 * monster's hitpoints, a kill off task giving none, the enchanted gem's Check, and the last kill completing the task with
 * the streak and the master's points.
 */
class HeadlessSlayerTest {
    // The 242 cache's cow: param 50 names the cows task (6). A rat names rats (3).
    private val cow = NpcType(2790, "Cow", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 2, internalName = "cow", params = mapOf(Slayer.PARAM_CATEGORY to 6), stats = NpcStats(hitpoints = 8))
    private val rat = NpcType(2856, "Giant rat", ops = listOf(null, "Attack", null, null, null), combatLevel = 3, internalName = "giantrat", params = mapOf(Slayer.PARAM_CATEGORY to 3), stats = NpcStats(hitpoints = 5))

    private val cows = SlayerTask(6219, 6, "cows", "Cows")
    private val rats = SlayerTask(6216, 3, "rats", "Rats")
    private val data = SlayerData(listOf(cows, rats), listOf(SlayerMasterTask(3, cows, 8, 40, 80), SlayerMasterTask(1, cows, 8, 15, 30)))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(cow, rat)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5),
            // slayer_master, slayer_points and slayer_tasks_completed, as the 242 cache has them.
            varTypes = VarTypes(6_000, mapOf(Slayer.VARBIT_MASTER to VarbitType(661, 2, 5), Slayer.VARBIT_POINTS to VarbitType(661, 6, 22), Slayer.VARBIT_STREAK to VarbitType(1077, 0, 13))),
            objTypes = ObjTypes.of(ObjType(Slayer.ENCHANTED_GEM, "Enchanted gem", iops = listOf("Activate", "Check", "Partner", "Log", "Drop"))),
            npcTypes = types, npcSpawns = listOf(NpcSpawn(cow, 3224, 3218, source = "test"), NpcSpawn(rat, 3218, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), slayerData = data, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.slayerXp() = stat(SLAYER)?.xp ?: 0

    private fun HeadlessClient.killAndWait(name: String, id: Int) {
        commandReply("kill $name") { it.startsWith("Killed") }
        await("the $name gone") { if (npcs().none { it.id == id }) Unit else null }
    }

    @Test
    fun `a kill on task counts down with Slayer XP for its hitpoints, and one off task gives none`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals("Task: 3 cows from vannaka.", client.commandReply("slayer task cows 3 vannaka") { it.startsWith("Task") })
                client.await("the task vars") { if (client.varp(Slayer.VARP_TARGET) == 6 && client.varp(Slayer.VARP_COUNT) == 3) Unit else null }

                client.killAndWait("cow", cow.id)
                client.await("2 left") { if (client.varp(Slayer.VARP_COUNT) == 2) Unit else null }
                // A cow has 8 hitpoints and no XP bonus.
                assertEquals(8, client.slayerXp())

                client.killAndWait("giant rat", rat.id)
                client.awaitTicks(2)
                assertEquals(2, client.varp(Slayer.VARP_COUNT))
                assertEquals(8, client.slayerXp())
                assertEquals("2 cows (task 6) from vannaka; streak 0, 0 points.", client.commandReply("slayer") { it.startsWith("2 cows") })
            }
        }
    }

    @Test
    fun `the enchanted gem's Check tells the task and the streak`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${Slayer.ENCHANTED_GEM}")
                val slot = client.await("the gem") { client.inventory(BACKPACK).indexOfFirst { it?.id == Slayer.ENCHANTED_GEM }.takeIf { it >= 0 } }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, CHECK_OP, slot, Slayer.ENCHANTED_GEM)
                client.seen("[slayer.gem_no_task]")
                client.seen("[slayer.gem_streak streak=0]")

                client.commandReply("slayer task cows 5") { it.startsWith("Task") }
                val from = client.messages().size
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, CHECK_OP, slot, Slayer.ENCHANTED_GEM)
                client.await("the task line") { client.messages().drop(from).firstOrNull { it.text == "[slayer.gem_task task=cows amount=5]" } }
            }
        }
    }

    @Test
    fun `the last kill completes the task, the streak goes up, and a tenth task pays five times the points`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                // A first task: no points before the fifth.
                client.commandReply("slayer task cows 1 vannaka") { it.startsWith("Task") }
                client.killAndWait("cow", cow.id)
                client.seen("[slayer.task_done]")
                client.seen("[slayer.task_done_points streak=1 points=0 total=0]")
                client.await("no task left") { if (client.varp(Slayer.VARP_COUNT) == 0) Unit else null }
                assertEquals("No task; streak 1, 0 points.", client.commandReply("slayer") { it.startsWith("No task") })

                // The tenth from Vannaka: 8 a task, 40 on every 10th.
                client.commandReply("slayer streak 9") { it.startsWith("Streak") }
                client.commandReply("slayer points 100") { it.startsWith("Streak") }
                client.commandReply("slayer task cows 1 vannaka") { it.startsWith("Task") }
                client.await("the cow back") { client.npcs().firstOrNull { it.id == cow.id } }
                client.killAndWait("cow", cow.id)
                client.await("the tenth task done") { if (client.varp(STREAK_VARP) == 10) Unit else null }
                assertEquals("No task; streak 10, 140 points.", client.commandReply("slayer") { it.startsWith("No task") })
            }
        }
    }

    @Test
    fun `when another player dealt the most damage, the XP is split between them and the finisher`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dana").use { dana ->
                server.login("erin").use { erin ->
                    for (client in listOf(dana, erin)) {
                        client.await("the login rebuild") { client.position }
                        client.commandReply("slayer task cows 5 vannaka") { it.startsWith("Task") }
                    }
                    // Erin deals 5 of the cow's 8, Dana the last 3: 8 XP shared, 4 each.
                    erin.commandReply("hit 5 cow") { it.startsWith("Hit") }
                    dana.killAndWait("cow", cow.id)
                    dana.await("dana's half") { if (dana.slayerXp() == 4) Unit else null }
                    erin.await("erin's half") { if (erin.slayerXp() == 4) Unit else null }
                    // The kill counts for the finisher's task.
                    dana.await("dana's count") { if (dana.varp(Slayer.VARP_COUNT) == 4) Unit else null }
                    assertEquals(5, erin.varp(Slayer.VARP_COUNT))

                    // Alone, the finisher gets it all.
                    erin.await("the cow back") { erin.npcs().firstOrNull { it.id == cow.id } }
                    erin.killAndWait("cow", cow.id)
                    erin.await("erin's whole kill") { if (erin.slayerXp() == 12) Unit else null }
                }
            }
        }
    }

    @Test
    fun `a player who logs out and back in is still the top damager, so their own kill gives the whole XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gina").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("slayer task cows 5 vannaka") { it.startsWith("Task") }
                client.commandReply("hit 5 cow") { it.startsWith("Hit") }
                // Away from the cow, which fights back: a hit taken holds off a logout for 10 seconds.
                client.commandReply("tele 3255 3255") { it.startsWith("Teleported") }
                client.awaitTicks(20)
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.awaitNobodyOn()
            server.login("gina").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("tele 3222 3218") { it.startsWith("Teleported") }
                client.killAndWait("cow", cow.id)
                client.await("the whole kill") { if (client.slayerXp() == 8) Unit else null }
            }
        }
    }

    @Test
    fun `a streak past the varbit's top goes back 1,000 so the milestones keep their pace`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fred").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("slayer streak 16383") { it.startsWith("Streak") }
                client.commandReply("slayer task cows 1 vannaka") { it.startsWith("Task") }
                client.killAndWait("cow", cow.id)
                client.await("the streak wrapped") { if (client.varp(STREAK_VARP) and 0x3FFF == 16384 - 1000) Unit else null }
                // 16,384 is no milestone: Vannaka's 8.
                client.seen("[slayer.task_done_points streak=15384 points=8 total=8]")
            }
        }
    }

    private fun TestServer.awaitNobodyOn() {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (world.playerCount != 0) {
            check(System.nanoTime() < deadline) { "Timed out waiting for the world to let the last player go" }
            Thread.sleep(10)
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val SLAYER = 18
        /** slayer_tasks_completed_1, the streak's varp. */
        const val STREAK_VARP = 1077
        /** The gem's iop2, Check, as the client sends it (button op 3). */
        const val CHECK_OP = 3
    }
}
