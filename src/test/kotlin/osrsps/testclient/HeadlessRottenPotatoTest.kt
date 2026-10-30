package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Accounts
import osrsps.game.InventoryTab
import osrsps.game.MemorySaveStore
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.command.Admins
import osrsps.game.content.RottenPotato
import osrsps.game.content.RottenPotatoLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.script.ScriptContext
import osrsps.map.CollisionMap

/** Admin tools: the rotten potato and its menus, through the headless client. */
class HeadlessRottenPotatoTest {
    private val potato = ObjType(RottenPotato.OBJ, "Rotten potato", iops = listOf("Eat", "Slice", "Peel", "Mash", "Drop"), internalName = "rotten_potato")
    private val coins = ObjType(COINS, "Coins", stackable = true)
    private val gift = Scenario.parse("gift", """{ "position": { "x": 3220, "z": 3216 }, "inventory": [ { "item": "$COINS", "count": 100 } ] }""")

    /** Only [ADMIN] is an admin; its name is claimed up front, as a listed admin's name can't be claimed by a login. */
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val accounts = Accounts(MemorySaveStore(), iterations = 1_000).apply { check(ADMIN, "test".toCharArray()) }
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(potato, coins),
            npcTypes = NpcTypes.of(NpcType(GOBLIN, "Goblin", internalName = "goblin")),
            scenarios = Scenarios(listOf(gift)), admins = Admins.parse(ADMIN), accounts = accounts, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.eat() =
        ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, EAT_BUTTON_OP, slotOf(RottenPotato.OBJ), RottenPotato.OBJ)

    private fun HeadlessClient.slice() =
        ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, SLICE_BUTTON_OP, slotOf(RottenPotato.OBJ), RottenPotato.OBJ)

    private fun HeadlessClient.peel() =
        ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, PEEL_BUTTON_OP, slotOf(RottenPotato.OBJ), RottenPotato.OBJ)

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.prompted(script: Int) = await("prompt $script") { clientScripts().lastOrNull()?.takeIf { it.id == script } }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    @Test
    fun `Eat opens live's menu, with Heal me in Setup POH's place`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login(ADMIN).use { boss ->
                boss.ready()
                boss.command("item ${RottenPotato.OBJ}")
                boss.eat()
                val menu = boss.awaitDialogue("the Eat menu") { it is DialogueView.Menu } as DialogueView.Menu
                val words = DialogueText.NONE
                assertEquals(
                    listOf(words[RottenPotatoLines.SET_ALL_STATS], words[RottenPotatoLines.WIPE_INVENTORY], RottenPotato.HEAL_ME,
                        words[RottenPotatoLines.TELEPORT_TO_PLAYER], words[RottenPotatoLines.SPAWN_NPC]),
                    menu.options,
                )
            }
        }
    }

    @Test
    fun `Set all stats sets one skill a tick, then says it has finished`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login(ADMIN).use { boss ->
                boss.ready()
                boss.command("item ${RottenPotato.OBJ}")
                boss.eat()
                boss.awaitDialogue("the Eat menu") { it is DialogueView.Menu }
                boss.chooseOption(1)
                boss.prompted(ScriptContext.MESLAYER_MODE7)
                boss.resumeCount(50)
                boss.seen(DialogueText.NONE[RottenPotatoLines.FINISHED])
                for (skill in Skill.entries) assertEquals(50, boss.stat(skill.ordinal)?.level, skill.displayName)
                val ticks = Skill.entries.map { boss.statTick(it.ordinal)!! }
                assertEquals(ticks.indices.map { ticks[0] + it }, ticks)
                val first = DialogueText.NONE[RottenPotatoLines.SETSTAT, "skill" to "attack", "level" to 50]
                assertTrue(boss.messages().any { it.text == first })
            }
        }
    }

    @Test
    fun `Wipe inventory keeps only the potato`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login(ADMIN).use { boss ->
                boss.ready()
                boss.command("item $COINS 500")
                boss.command("item ${RottenPotato.OBJ}")
                boss.slotOf(RottenPotato.OBJ)
                boss.eat()
                boss.awaitDialogue("the Eat menu") { it is DialogueView.Menu }
                boss.chooseOption(2)
                boss.await("the coins gone") { boss.inventory(BACKPACK).takeIf { inv -> inv.none { it?.id == COINS } } }
                assertEquals(listOf(RottenPotato.OBJ), boss.inventory(BACKPACK).filterNotNull().map { it.id })
            }
        }
    }

    @Test
    fun `Teleport to player lands on them, and Spawn NPC spawns the one named`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login(ADMIN).use { boss ->
                    alice.ready()
                    boss.ready()
                    alice.walk(3225, 3220)
                    alice.await("alice there") { alice.position?.takeIf { it == Coord(3225, 3220) } }
                    boss.command("item ${RottenPotato.OBJ}")
                    boss.eat()
                    boss.awaitDialogue("the Eat menu") { it is DialogueView.Menu }
                    boss.chooseOption(4)
                    boss.prompted(ScriptContext.MESLAYER_MODE9)
                    boss.resumeString("alice")
                    boss.await("the teleport") { boss.position?.takeIf { it == Coord(3225, 3220) } }

                    boss.eat()
                    boss.awaitDialogue("the Eat menu again") { it is DialogueView.Menu }
                    boss.chooseOption(5)
                    boss.prompted(ScriptContext.MESLAYER_MODE9)
                    boss.resumeString("goblin")
                    boss.await("the goblin") { boss.npcs().firstOrNull { it.id == GOBLIN } }
                }
            }
        }
    }

    @Test
    fun `Peel opens the bank, teleports to a tile and spawns an item`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login(ADMIN).use { boss ->
                boss.ready()
                boss.command("item ${RottenPotato.OBJ}")
                boss.peel()
                val menu = boss.awaitDialogue("the Peel menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf(DialogueText.NONE[RottenPotatoLines.BANK_MENU], RottenPotato.TELEPORT_TO_TILE, RottenPotato.SPAWN_ITEM), menu.options)
                boss.chooseOption(1)
                boss.await("the bank") { boss.openInterfaces().takeIf { BankScreen.MAIN in it } }
                boss.closeModal()

                boss.peel()
                boss.awaitDialogue("the Peel menu again") { it is DialogueView.Menu }
                boss.chooseOption(2)
                boss.prompted(ScriptContext.MESLAYER_MODE9)
                boss.resumeString("3226 3221")
                boss.await("the teleport") { boss.position?.takeIf { it == Coord(3226, 3221) } }

                boss.peel()
                boss.awaitDialogue("the Peel menu a third time") { it is DialogueView.Menu }
                boss.chooseOption(3)
                boss.prompted(ScriptContext.MESLAYER_MODE9)
                boss.resumeString("$COINS 250")
                boss.await("the coins") { boss.inventory(BACKPACK).firstOrNull { it?.id == COINS }?.takeIf { it.count == 250 } }
            }
        }
    }

    @Test
    fun `Slice holds the testing tools, god mode, sure hits, the XP rate, a scenario and reset`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login(ADMIN).use { boss ->
                boss.ready()
                boss.command("item ${RottenPotato.OBJ}")
                boss.slice()
                val menu = boss.awaitDialogue("the Slice menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf(RottenPotato.GOD, RottenPotato.SURE, RottenPotato.XP_RATE, RottenPotato.SCENARIO, RottenPotato.RESET), menu.options)
                boss.chooseOption(1)
                boss.awaitMessage("god mode on") { it.startsWith("You can't be hurt now") }
                assertTrue(server.world.playerNamed(ADMIN)!!.god)

                boss.pick(2)
                boss.awaitMessage("sure hits on") { it.startsWith("Your attacks can't miss now") }

                boss.pick(3)
                boss.prompted(ScriptContext.MESLAYER_MODE9)
                boss.resumeString("5")
                boss.awaitMessage("the rate") { it.startsWith("XP rate set to") }

                boss.pick(4)
                boss.prompted(ScriptContext.MESLAYER_MODE9)
                boss.resumeString("gift")
                boss.awaitMessage("the scenario") { it.startsWith("Started scenario gift.") }
                boss.await("the gift's tile") { boss.position?.takeIf { it == Coord(3220, 3216) } }

                boss.pick(5)
                boss.awaitMessage("the reset") { it.startsWith("Back to") }
                boss.await("back on the spawn") { boss.position?.takeIf { it == Coord(3222, 3218) } }
            }
        }
    }

    /** Slice, then option [option] of its menu. */
    private fun HeadlessClient.pick(option: Int) {
        slice()
        awaitDialogue("the Slice menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    @Test
    fun `a non-admin's potato is taken, and nothing opens`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login(ADMIN).use { boss ->
                server.login("mallory").use { mallory ->
                    boss.ready()
                    mallory.ready()
                    // Untradeable, and dropped where only the dropper sees it, but an admin can be taken off the list while
                    // holding one. Given here while the clock is paused, so the world isn't running a tick meanwhile.
                    boss.commandReply("pause") { it.startsWith("Paused") }
                    server.world.playerNamed("mallory")!!.inventory.add(potato, 1)
                    boss.commandReply("resume") { it.startsWith("Resumed") }
                    mallory.eat()
                    mallory.seen("The rotten potato is a testing tool, so only admins can use it here.")
                    mallory.await("the potato gone") { mallory.inventory(BACKPACK).takeIf { inv -> inv.none { it?.id == RottenPotato.OBJ } } }
                    assertTrue(mallory.dialogueView == null)
                }
            }
        }
    }

    private companion object {
        const val ADMIN = "boss"
        const val COINS = 995
        const val GOBLIN = 3029
        const val BACKPACK = 93

        /** iop1 is button op 2 (see InventoryTab.click). */
        const val EAT_BUTTON_OP = 2
        const val SLICE_BUTTON_OP = 3
        const val PEEL_BUTTON_OP = 4
    }
}
