package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameFrame
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.JournalTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.game.content.AntiqueLamps
import osrsps.game.content.DIARY_VARBITS
import osrsps.game.content.DiaryArea
import osrsps.game.content.Mikasi
import osrsps.game.content.VarrockSweepTalkers
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Claiming diary rewards through the headless client: Mikasi gives the easy ring and lamp once every easy task is done,
 * then not again, and gives the highest ring back once it's gone, even with a lower one kept; a hard tier with medium undone isn't offered. Toby turns a
 * claim down without two free spaces (ending the talk), gives the armour and lamp with room, refuses the elite with the
 * medium and hard undone, and "Who are you?" opens the Achievement Diaries tab.
 */
class HeadlessDiaryRewardsTest {
    private fun talker(id: Int, name: String) = NpcType(id, name, ops = listOf("Talk-to", null, null, null, null))

    private val mikasi = talker(Mikasi.ID, "Mikasi")
    private val toby = talker(VarrockSweepTalkers.TOBY, "Toby")
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val objs = ObjTypes.of(
        *(DiaryArea.LUMBRIDGE.rewards + DiaryArea.VARROCK.rewards).map { ObjType(it, "Diary reward", iops = listOf(null, null, null, null, "Drop")) }.toTypedArray(),
        *AntiqueLamps.Lamp.entries.map { ObjType(it.obj, "Antique lamp") }.toTypedArray(),
        ObjType(LOBSTER, "Lobster", cost = 150),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            npcTypes = NpcTypes.of(mikasi, toby, grave), objTypes = objs,
            npcSpawns = listOf(
                NpcSpawn(mikasi, 3224, 3218, wander = 0, source = "test"),
                NpcSpawn(toby, 3220, 3218, wander = 0, source = "test"),
            ),
            varTypes = VarTypes(4_000, DIARY_VARBITS), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.talk(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, Mikasi.TALK_TO)
    }

    private fun HeadlessClient.chat(vararg texts: String) {
        for (text in texts) {
            awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
            continueDialogue()
        }
    }

    private fun HeadlessClient.menu(vararg options: String) {
        val menu = awaitDialogue("the menu ${options.first()}") { it is DialogueView.Menu && (it as DialogueView.Menu).options.first() == options.first() } as DialogueView.Menu
        assertEquals(options.toList(), menu.options)
    }

    private fun HeadlessClient.objbox() {
        awaitDialogue("the item box") { it is DialogueView.Other }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).count { it?.id == id }

    private fun HeadlessClient.awaitCount(id: Int, n: Int) = await("$n of $id") { Unit.takeIf { count(id) == n } }

    private fun HeadlessClient.done(command: String) {
        val before = messages().size
        command(command)
        await("'$command' done") { Unit.takeIf { messages().drop(before).any { "task(s) done" in it.text } } }
    }

    private fun HeadlessClient.fill(n: Int) {
        command("item ${AntiqueLamps.Lamp.ELITE.obj} $n")
        awaitCount(AntiqueLamps.Lamp.ELITE.obj, n)
    }

    private fun HeadlessClient.emptied() {
        command("empty")
        await("an empty pack") { Unit.takeIf { inventory(Inventory.INV).all { it == null } } }
    }

    private fun HeadlessClient.diaryRewardsOnGround() = groundObjs().count { it.id in DiaryArea.LUMBRIDGE.rewards || it.id in DiaryArea.VARROCK.rewards }

    @Test
    fun `Mikasi gives the easy ring and lamp, once, and the ring again when it's lost`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.done("diary complete lumbridge easy all")

                // One free space: the claim is turned down, and nothing is claimed or dropped (exploit review R55A-2).
                client.fill(27)
                client.talk(Mikasi.ID)
                client.chat("[mikasi.claim_easy]", "[mikasi.want_reward]", "[mikasi.yes_please]", "[mikasi.no_space]")
                client.await("the talk over") { Unit.takeIf { client.dialogueView == null } }
                assertEquals(0, client.varp(ACHIEVEMENT_DIARY_REWARDS2), "not claimed")
                assertEquals(0, client.count(DiaryArea.LUMBRIDGE.rewards[0]))
                assertEquals(0, client.diaryRewardsOnGround(), "nothing on the floor")
                client.emptied()

                client.talk(Mikasi.ID)
                client.chat("[mikasi.claim_easy]", "[mikasi.want_reward]", "[mikasi.yes_please]", "[mikasi.ring_easy]", "[mikasi.wow_thanks]", "[mikasi.lose_ring]")
                client.menu("[mikasi.who_are_you]", "[mikasi.my_diary]", "[mikasi.farewell]")
                client.awaitCount(DiaryArea.LUMBRIDGE.rewards[0], 1)
                assertEquals(1, client.count(AntiqueLamps.Lamp.EASY.obj))
                assertEquals(1 shl 4, client.varp(ACHIEVEMENT_DIARY_REWARDS2))
                client.closeModal()

                // Claimed already: straight to her menu. Hard done with medium undone isn't offered either.
                client.done("diary complete lumbridge hard all")
                client.talk(Mikasi.ID)
                client.menu("[mikasi.who_are_you]", "[mikasi.my_diary]", "[mikasi.farewell]")
                client.closeModal()

                client.command("empty")
                client.awaitCount(DiaryArea.LUMBRIDGE.rewards[0], 0)
                // A full pack: another ring is turned down, and none lands on the floor (exploit review R55A-1).
                client.fill(28)
                client.talk(Mikasi.ID)
                client.menu("[mikasi.another_ring]", "[mikasi.who_are_you]", "[mikasi.my_diary]", "[mikasi.farewell]")
                client.chooseOption(1)
                client.chat("[mikasi.another_ring]", "[mikasi.no_space]")
                client.menu("[mikasi.another_ring]", "[mikasi.who_are_you]", "[mikasi.my_diary]", "[mikasi.farewell]")
                assertEquals(0, client.count(DiaryArea.LUMBRIDGE.rewards[0]))
                assertEquals(0, client.diaryRewardsOnGround(), "nothing on the floor")
                client.closeModal()
                client.emptied()
                client.talk(Mikasi.ID)
                client.menu("[mikasi.another_ring]", "[mikasi.who_are_you]", "[mikasi.my_diary]", "[mikasi.farewell]")
                client.chooseOption(1)
                client.chat("[mikasi.another_ring]")
                client.objbox()
                client.awaitCount(DiaryArea.LUMBRIDGE.rewards[0], 1)
                client.menu("[mikasi.who_are_you]", "[mikasi.my_diary]", "[mikasi.farewell]")
                assertEquals(0, client.count(AntiqueLamps.Lamp.EASY.obj), "the lamp isn't given again")
                client.closeModal()

                // A kept lower ring doesn't stop the highest one being given back.
                client.done("diary complete lumbridge medium all")
                client.talk(Mikasi.ID)
                client.chat("[mikasi.claim_medium]", "[mikasi.want_reward]", "[mikasi.yes_please]", "[mikasi.ring_medium]", "[mikasi.wow_thanks]")
                client.menu("[mikasi.who_are_you]", "[mikasi.my_diary]", "[mikasi.farewell]")
                client.awaitCount(DiaryArea.LUMBRIDGE.rewards[1], 1)
                client.closeModal()
                // Hard was done above, so with medium claimed it's claimed next.
                client.talk(Mikasi.ID)
                client.chat("[mikasi.claim_hard]", "[mikasi.want_reward]", "[mikasi.yes_please]", "[mikasi.ring_hard]", "[mikasi.wow_thanks]")
                client.menu("[mikasi.who_are_you]", "[mikasi.my_diary]", "[mikasi.farewell]")
                client.awaitCount(DiaryArea.LUMBRIDGE.rewards[2], 1)
                client.closeModal()
                client.command("empty")
                client.awaitCount(DiaryArea.LUMBRIDGE.rewards[2], 0)
                client.command("item ${DiaryArea.LUMBRIDGE.rewards[0]} 1")
                client.awaitCount(DiaryArea.LUMBRIDGE.rewards[0], 1)
                client.talk(Mikasi.ID)
                client.menu("[mikasi.another_ring]", "[mikasi.who_are_you]", "[mikasi.my_diary]", "[mikasi.farewell]")
                client.chooseOption(1)
                client.chat("[mikasi.another_ring]")
                client.objbox()
                client.awaitCount(DiaryArea.LUMBRIDGE.rewards[2], 1)
                assertEquals(1, client.count(DiaryArea.LUMBRIDGE.rewards[0]), "the easy ring isn't given again")
                assertEquals(0, client.count(DiaryArea.LUMBRIDGE.rewards[1]), "nor the medium one")
                client.closeModal()

                // Dropped, it lies on the floor still, so it isn't lost and no other is given (exploit review R55A-1).
                val hard = DiaryArea.LUMBRIDGE.rewards[2]
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, DROP_BUTTON_OP, client.inventory(Inventory.INV).indexOfFirst { it?.id == hard }, hard)
                client.await("the ring on the floor") { Unit.takeIf { client.groundObjs().any { it.id == hard } } }
                client.awaitCount(hard, 0)
                client.talk(Mikasi.ID)
                client.menu("[mikasi.who_are_you]", "[mikasi.my_diary]", "[mikasi.farewell]")
            }
        }
    }

    @Test
    fun `Toby needs two spaces, gives the armour, holds the elite back and opens the diary tab`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.done("diary complete varrock easy all")
                client.command("item ${AntiqueLamps.Lamp.ELITE.obj} 27")
                client.awaitCount(AntiqueLamps.Lamp.ELITE.obj, 27)
                client.talk(VarrockSweepTalkers.TOBY)
                client.chat("[toby.claim_easy]", "[toby.well_done]", "[toby.yes_please]", "[toby.no_space]")
                client.await("the talk over") { Unit.takeIf { client.dialogueView == null } }
                assertEquals(0, client.count(DiaryArea.VARROCK.rewards[0]))

                client.command("empty")
                client.awaitCount(AntiqueLamps.Lamp.ELITE.obj, 0)
                client.talk(VarrockSweepTalkers.TOBY)
                client.chat("[toby.claim_easy]", "[toby.well_done]", "[toby.yes_please]", "[toby.armour_easy]", "[toby.wow_thanks]")
                client.menu("[toby.who]", "[toby.question]", "[toby.bye]")
                client.awaitCount(DiaryArea.VARROCK.rewards[0], 1)
                assertEquals(1, client.count(AntiqueLamps.Lamp.EASY.obj))
                assertEquals(1 shl 20, client.varp(ACHIEVEMENT_DIARY_REWARDS))

                client.chooseOption(1)
                client.chat("[toby.who]", "[toby.who_1]", "[toby.who_2]", "[toby.diary_1]")
                client.await("the diary tab") {
                    Unit.takeIf {
                        client.clientScripts().any { it.id == GameFrame.SIDEBUTTON_SWITCH_SCRIPT && it.args == listOf<Any>(GameFrame.SIDE_TAB_QUEST) } &&
                            JournalTab.DIARIES.interfaceId in client.openInterfaces()
                    }
                }
                client.chat("[toby.diary_2]")
                client.closeModal()

                // Lost, with a full pack: another platebody is turned down, and none lands on the floor (exploit review R55A-1).
                client.closeModal()
                client.emptied()
                client.fill(28)
                client.talk(VarrockSweepTalkers.TOBY)
                client.chat("[toby.hello]")
                client.menu("[toby.another_platebody]", "[toby.who]", "[toby.question]", "[toby.bye]")
                client.chooseOption(1)
                client.chat("[toby.another_platebody]", "[toby.no_space]")
                client.menu("[toby.another_platebody]", "[toby.who]", "[toby.question]", "[toby.bye]")
                assertEquals(0, client.count(DiaryArea.VARROCK.rewards[0]))
                assertEquals(0, client.diaryRewardsOnGround(), "nothing on the floor")
                client.closeModal()
                client.emptied()

                client.done("diary complete varrock elite all")
                client.talk(VarrockSweepTalkers.TOBY)
                client.chat("[toby.claim_elite]", "[toby.elite_earlier]")
                // The easy platebody is still lost from above, so he offers another.
                client.menu("[toby.another_platebody]", "[toby.who]", "[toby.question]", "[toby.bye]")
                assertTrue(client.count(DiaryArea.VARROCK.rewards[3]) == 0)
            }
        }
    }

    @Test
    fun `a reward in the player's grave isn't lost, and once the grave is gone another is given`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val carol = server.world.playerNamed("carol")!!
                val armour = DiaryArea.VARROCK.rewards[0]
                client.done("diary complete varrock easy all")
                client.talk(VarrockSweepTalkers.TOBY)
                client.chat("[toby.claim_easy]", "[toby.well_done]", "[toby.yes_please]", "[toby.armour_easy]", "[toby.wow_thanks]")
                client.menu("[toby.who]", "[toby.question]", "[toby.bye]")
                client.awaitCount(armour, 1)
                client.closeModal()

                // Three lobsters are kept on death; the armour, worth less, goes to the grave (exploit review R55A-1).
                client.command("item $LOBSTER 3")
                client.awaitCount(LOBSTER, 3)
                client.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                client.await("the respawn", timeoutMillis = 10_000) { Unit.takeIf { !carol.dead && client.count(LOBSTER) == 3 } }
                client.await("the armour in the grave") { Unit.takeIf { (server.world.deaths.graveOf(carol)?.items?.count(armour) ?: 0L) > 0L } }
                client.awaitCount(armour, 0)
                client.talk(VarrockSweepTalkers.TOBY)
                client.chat("[toby.hello]")
                client.menu("[toby.who]", "[toby.question]", "[toby.bye]")
                client.closeModal()

                // The grave collapses: now the armour is lost and he offers another.
                server.world.deaths.graveOf(carol)!!.ticksLeft = 1
                client.await("the grave gone", timeoutMillis = 10_000) { Unit.takeIf { server.world.deaths.graveOf(carol) == null } }
                client.talk(VarrockSweepTalkers.TOBY)
                client.chat("[toby.hello]")
                client.menu("[toby.another_platebody]", "[toby.who]", "[toby.question]", "[toby.bye]")
            }
        }
    }

    private companion object {
        const val ACHIEVEMENT_DIARY_REWARDS = 1190
        const val LOBSTER = 379
        const val ACHIEVEMENT_DIARY_REWARDS2 = 1191

        /** iop5 (Drop) is button op 7. */
        const val DROP_BUTTON_OP = 7
    }
}
