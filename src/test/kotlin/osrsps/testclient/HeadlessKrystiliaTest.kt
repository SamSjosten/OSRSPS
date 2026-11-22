package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.EdgevilleRespawn
import osrsps.game.content.SlayerMasters
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Slayer
import osrsps.game.skill.SlayerData
import osrsps.game.skill.SlayerMaster
import osrsps.game.skill.SlayerMasterTask
import osrsps.game.skill.SlayerTask
import osrsps.map.CollisionMap

/**
 * Krystilia through the headless client: she turns away a player with Slayer 1, warns before the first task and gives
 * one with no combat check, her talk about her hat; a kill on her task counts only inside the Wilderness, and completing
 * it adds to the Wilderness streak, not the standard one.
 */
class HeadlessKrystiliaTest {
    private val krystilia = NpcType(SlayerMaster.KRYSTILIA.npc, "Krystilia", ops = listOf("Talk-to", null, "Assignment", "Trade", "Rewards"), internalName = "slayer_master_7")
    private val cow = NpcType(2790, "Cow", ops = listOf(null, "Attack", null, null, null), combatLevel = 2, internalName = "cow", params = mapOf(Slayer.PARAM_CATEGORY to 6), stats = NpcStats(hitpoints = 8))

    // Cows are hers here, with a combat level no new player has, to show she doesn't check it.
    private val cows = SlayerTask(6219, 6, "cows", "Cows", minCombat = 100)
    private val aya = NpcType(SlayerMaster.AYA.npc, "Aya", ops = listOf("Talk-to", null, "Assignment", "Trade", "Rewards"), internalName = "slayer_master_1_aya")
    private val rats = SlayerTask(6220, 7, "rats", "Rats")
    private val data = SlayerData(
        listOf(cows, rats),
        listOf(SlayerMasterTask(SlayerMaster.KRYSTILIA.id, cows, 8, 2, 2), SlayerMasterTask(SlayerMaster.AYA.id, rats, 8, 3, 3)),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(krystilia, cow, aya)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(3222 shr 6, WILDY_Z shr 6); markLoaded(EdgevilleRespawn.X shr 6, EdgevilleRespawn.Z shr 6) }, random = GameRandom(5),
            // The 242 cache's Slayer varbits.
            varTypes = VarTypes(
                6_000,
                mapOf(
                    Slayer.VARBIT_MASTER to VarbitType(661, 2, 5), Slayer.VARBIT_POINTS to VarbitType(661, 6, 22),
                    Slayer.VARBIT_STREAK to VarbitType(1077, 0, 13), Slayer.VARBIT_COMBAT_CHECK_OFF to VarbitType(1077, 29, 29),
                    Slayer.VARBIT_WILDERNESS_STREAK to VarbitType(WILDY_STREAK_VARP, 0, 13),
                    // edgeville_spawn and edgeville_spawn_unlocked, on cws_var and cws_var_2.
                    EdgevilleRespawn.VARBIT_SPAWN to VarbitType(1045, 31, 31), EdgevilleRespawn.VARBIT_UNLOCKED to VarbitType(1046, 0, 0),
                ),
            ),
            npcTypes = types, objTypes = ObjTypes.of(ObjType(EdgevilleRespawn.COINS, "Coins", stackable = true)),
            npcSpawns = listOf(
                NpcSpawn(krystilia, 3222, 3220, wander = 0, source = "test"),
                NpcSpawn(aya, 3220, 3220, wander = 0, source = "test"),
                NpcSpawn(cow, 3224, 3218, wander = 0, source = "test"),
                NpcSpawn(cow, 3224, WILDY_Z, wander = 0, source = "test"),
            ),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), slayerData = data, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.says(text: String) = awaitDialogue(text) { it is DialogueView.Chat && it.text == text }.also { continueDialogue() }

    private fun HeadlessClient.pick(option: Int) {
        awaitDialogue("a menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    private fun HeadlessClient.seen(text: String, from: Int = 0) = await(text) { messages().drop(from).firstOrNull { it.text == text } }

    private fun HeadlessClient.wildernessStreak() = varp(WILDY_STREAK_VARP) and 0x3FFF

    @Test
    fun `Krystilia turns away Slayer 1, warns before her first task, and gives one with no combat check`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("Krystilia in view") { client.npcs().firstOrNull { it.id == krystilia.id } }
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[slayer_master.too_weak]")

                client.commandReply("setlevel slayer 2") { it.contains("set to level") }
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                for (key in listOf("warn_wilderness", "warn_combat", "warn_aya", "warn_loot")) client.says("[krystilia.$key]")
                client.pick(2)
                client.says("[krystilia.no_tasks]")
                assertEquals(0, client.varp(Slayer.VARP_COUNT))

                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                for (key in listOf("warn_wilderness", "warn_combat", "warn_aya", "warn_loot")) client.says("[krystilia.$key]")
                client.pick(1)
                client.says("[slayer_master.new_task amount=2 task=cows]")
                assertEquals(SlayerMaster.KRYSTILIA, server.world.slayer.master(server.world.onlinePlayers.single()))
                client.pick(1)
                client.says("[slayer_master.tips]")
                client.says("[slayer_tip.cows]")
                client.says("[krystilia.tip_wilderness]")
                client.says("[slayer_master.tips_thanks]")

                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[krystilia.still_slaying task=cows amount=2]")
            }
        }
    }

    @Test
    fun `her talk opens with her own greeting and has her hat`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("Krystilia in view") { client.npcs().firstOrNull { it.id == krystilia.id } }
                client.npcOp(npc.index, SlayerMasters.TALK_TO)
                client.says("[krystilia.greeting]")
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[krystilia_respawn.apart]", "[krystilia.hat]"), menu.options.subList(3, 5))
                client.chooseOption(5)
                for (key in listOf("hat_said", "made_it", "arts", "witchcraft", "spare_time", "too_much", "give_away", "chinchompas", "less_dangerous", "hats_too", "perfect")) {
                    client.says("[krystilia.$key]")
                }
            }
        }
    }

    @Test
    fun `a kill on her task counts only in the Wilderness, and completing it adds to the Wilderness streak`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("slayer task cows 1 krystilia") { it.startsWith("Task") }

                // Outside the Wilderness: the message, no count and no XP.
                val from = client.messages().size
                client.commandReply("kill cow") { it.startsWith("Killed") }
                client.seen("[slayer.wilderness_only]", from)
                client.awaitTicks(2)
                assertEquals(1, client.varp(Slayer.VARP_COUNT))
                assertEquals(0, client.stat(SLAYER)?.xp ?: 0)

                client.command("tele 3222 $WILDY_Z")
                client.await("in the Wilderness") { if (client.position == Coord(3222, WILDY_Z)) Unit else null }
                client.awaitTicks(3)
                client.commandReply("kill cow") { it.startsWith("Killed") }
                client.seen("[slayer.task_done]")
                client.await("no task left") { if (client.varp(Slayer.VARP_COUNT) == 0) Unit else null }
                assertEquals(1, client.wildernessStreak())
                assertTrue((client.stat(SLAYER)?.xp ?: 0) > 0, "no Slayer XP for the kill")
                assertEquals("No task; streak 0, 0 points.", client.commandReply("slayer") { it.startsWith("No task") })
            }
        }
    }

    @Test
    fun `Aya warns that swapping a Krystilia task resets the Wilderness streak, and it does`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("slayer task cows 2 krystilia") { it.startsWith("Task") }
                client.commandReply("varbit ${Slayer.VARBIT_WILDERNESS_STREAK} 4") { it.startsWith("Varbit") }
                client.await("the streak set") { if (client.wildernessStreak() == 4) Unit else null }

                val npc = client.await("Aya in view") { client.npcs().firstOrNull { it.id == aya.id } }
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[slayer_master.still_hunting task=cows amount=2]")
                client.says("[aya.not_mine]")
                client.says("[aya.reset_wilderness_streak streak=0 wilderness=4]")
                client.pick(1)
                client.says("[slayer_master.new_task amount=3 task=rats]")
                assertEquals(0, client.wildernessStreak())
                assertEquals(SlayerMaster.AYA, server.world.slayer.master(server.world.onlinePlayers.single()))
            }
        }
    }

    @Test
    fun `she sells the Edgeville respawn for 5 million, and a death then comes back in Edgeville`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                val npc = client.await("Krystilia in view") { client.npcs().firstOrNull { it.id == krystilia.id } }
                fun apart() {
                    client.npcOp(npc.index, SlayerMasters.TALK_TO)
                    client.says("[krystilia.greeting]")
                    client.pick(4)
                    client.says("[krystilia_respawn.apart]")
                    client.says("[krystilia_respawn.quite_a_lot]")
                }
                val deal = listOf("deal", "safe_places", "all_worlds", "fee").map { "[krystilia_respawn.$it]" }

                // Not enough coins: nothing changes.
                apart()
                client.says("[krystilia_respawn.offer]")
                client.pick(1)
                client.says("[krystilia_respawn.tell_me_more]")
                deal.forEach { client.says(it) }
                client.says("[krystilia_respawn.not_carrying]")
                client.says("[krystilia_respawn.scared]")

                client.command("item ${EdgevilleRespawn.COINS} 5000001")
                client.await("the coins") { if (client.inventory(BACKPACK).any { it?.id == EdgevilleRespawn.COINS }) Unit else null }
                apart()
                client.says("[krystilia_respawn.offer]")
                client.pick(1)
                client.says("[krystilia_respawn.tell_me_more]")
                deal.forEach { client.says(it) }
                client.says("[krystilia_respawn.switch_to_edgeville]")
                client.await("the fee paid") { if (client.inventory(BACKPACK).single { it != null }!!.count == 1) Unit else null }
                val player = server.world.onlinePlayers.single()
                assertTrue(EdgevilleRespawn.active(server.world, player) && EdgevilleRespawn.unlocked(server.world, player))

                client.commandReply("hit 99 me") { true }
                client.await("back in Edgeville", timeoutMillis = 20_000) {
                    if (client.position == Coord(EdgevilleRespawn.X, EdgevilleRespawn.Z)) Unit else null
                }

                // Back to Lumbridge for nothing, and to Edgeville again for nothing.
                client.command("tele 3222 3218")
                client.await("back by her") { if (client.position == Coord(3222, 3218)) Unit else null }
                client.awaitTicks(3)
                val again = client.await("Krystilia in view") { client.npcs().firstOrNull { it.id == krystilia.id } }
                client.npcOp(again.index, SlayerMasters.TALK_TO)
                client.says("[krystilia.greeting]")
                client.pick(4)
                client.says("[krystilia_respawn.apart]")
                client.says("[krystilia_respawn.quite_a_lot]")
                client.says("[krystilia_respawn.how_is_it]")
                client.pick(1)
                client.says("[krystilia_respawn.back_to_lumbridge]")
                client.says("[krystilia_respawn.slum]")
                client.pick(1)
                client.says("[krystilia_respawn.yes_lumbridge]")
                client.says("[krystilia_respawn.done]")
                assertTrue(!EdgevilleRespawn.active(server.world, player) && EdgevilleRespawn.unlocked(server.world, player))
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        /** Level 2 of the surface Wilderness, past the ditch and its safe rows. */
        const val WILDY_Z = 3530
        const val WILDY_STREAK_VARP = 1565
        val SLAYER = Skill.SLAYER.ordinal
    }
}
