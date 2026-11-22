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
import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.content.randomevents.CountCheckEvent
import osrsps.game.content.randomevents.DrJekyll
import osrsps.game.content.randomevents.DrunkenDwarf
import osrsps.game.content.randomevents.Gifts
import osrsps.game.content.randomevents.MysteriousOldMan
import osrsps.game.content.randomevents.RandomEvent
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.content.randomevents.RickTurpentine
import osrsps.game.content.randomevents.StrangePlant
import osrsps.map.CollisionMap

/**
 * Random events step 2 through the headless client: Count Check's lamp, the Drunken Dwarf's kebab and beer, Rick
 * Turpentine's and the Mysterious Old Man's gifts (the old man's message when it goes on the ground), Dr Jekyll's herb
 * for a potion (the best herb asked for, kept, or none), and the Strange plant that grows, stays put and gives a fruit
 * to eat. The rl-client script test-runner/examples/random-events-talk.json does them in the real game.
 */
class HeadlessRandomEventsTalkTest {
    private val events = listOf("Talk-to", null, null, null, "Dismiss")
    private val npcs = listOf(CountCheckEvent, DrunkenDwarf, RickTurpentine, MysteriousOldMan, DrJekyll).map { NpcType(it.npcId, it.key, ops = events) } +
        NpcType(StrangePlant.npcId, "Strange plant", ops = listOf("Pick", null, null, null, "Dismiss"))

    private val objs = listOf(
        ObjType(XpLamp.LAMP, "Lamp", iops = listOf("Rub", null, null, null, "Destroy")),
        ObjType(DrunkenDwarf.KEBAB, "Kebab"), ObjType(DrunkenDwarf.BEER, "Beer"),
        ObjType(Gifts.COINS, "Coins", stackable = true),
        ObjType(StrangePlant.FRUIT, "Strange fruit", iops = listOf("Eat", null, null, null, "Drop")),
        ObjType(DrJekyll.SPARE_POTION, "Strength potion(2)"),
        ObjType(GUAM, "Guam leaf"), ObjType(TORSTOL, "Torstol"), ObjType(STAMINA, "Stamina potion(4)"),
    ) + Gifts.TABLE.map { it.id }.distinct().filter { it != Gifts.COINS && it != DrunkenDwarf.KEBAB }.map { ObjType(it, "Gift $it") }

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        command("tele 3222 3218 0")
        await("the teleport") { position?.takeIf { it == Coord(3222, 3218, 0) } }
        commandReply("randomevent off") { it == "Random events off" }
    }

    private fun HeadlessClient.start(event: RandomEvent): NpcView {
        command("randomevent ${event.key}")
        return await("${event.key} in view") { npcs().firstOrNull { it.id == event.npcId } }
    }

    private fun HeadlessClient.chat(text: String) {
        awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
        continueDialogue()
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.gone(id: Int) = await("NPC $id gone") { if (npcs().none { it.id == id }) Unit else null }

    private fun HeadlessClient.has(id: Int) = await("item $id in the backpack") { if (inventory(Inventory.INV).any { it?.id == id }) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).count { it?.id == id }

    @Test
    fun `Count Check gives a lamp and the Drunken Dwarf a kebab and a beer`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                val count = client.start(CountCheckEvent)
                client.await("his greeting") { client.npcSaid().firstOrNull { it.index == count.index && it.text == "[count_check_event.greeting name=alice]" } }
                client.npcOp(count.index, RandomEvents.TALK_TO)
                client.chat("[count_check_event.pass]")
                client.over()
                client.gone(CountCheckEvent.npcId)
                client.has(XpLamp.LAMP)

                val dwarf = client.start(DrunkenDwarf)
                client.npcOp(dwarf.index, RandomEvents.TALK_TO)
                client.chat("[drunken_dwarf.kebab_stories]")
                client.over()
                client.has(DrunkenDwarf.KEBAB)
                client.has(DrunkenDwarf.BEER)
            }
        }
    }

    @Test
    fun `Rick Turpentine gives a gift, and the Mysterious Old Man opens with a waiting line and says when it went on the ground`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                val rick = client.start(RickTurpentine)
                client.npcOp(rick.index, RandomEvents.TALK_TO)
                client.chat("[rick_turpentine.lucky_day]")
                client.over()
                client.gone(RickTurpentine.npcId)
                val gifts = Gifts.TABLE.map { it.id }.toSet()
                client.await("a gift") { client.inventory(Inventory.INV).firstOrNull { it != null && it.id in gifts } }

                client.command("empty")
                client.command("item ${DrunkenDwarf.BEER} 28")
                client.await("a full backpack") { if (client.inventory(Inventory.INV).count { it != null } == 28) Unit else null }
                val old = client.start(MysteriousOldMan)
                val first = setOf("name", "hello", "talk_to_me", "are_you_there", "like_to_talk").map { "[mysterious_old_man.$it name=bob]" }
                client.await("a waiting line first") { client.npcSaid().firstOrNull { it.index == old.index && it.text in first } }
                client.npcOp(old.index, RandomEvents.TALK_TO)
                client.chat("[mysterious_old_man.present]")
                client.over()
                client.await("the ground message") { client.messages().firstOrNull { it.text.startsWith("[mysterious_old_man.on_ground item=") } }
                client.await("the gift on the ground") { client.groundObjs().firstOrNull { it.id in gifts } }
            }
        }
    }

    @Test
    fun `Dr Jekyll asks for the best herb, gives its potion for it, and a spare potion for none or a kept herb`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                server.login("dave").use { other ->
                    client.ready()
                    other.ready()
                    client.command("item $GUAM")
                    client.command("item $TORSTOL")
                    client.has(TORSTOL)
                    var jekyll = client.start(DrJekyll)
                    // Someone else's Talk-to gets his words for them, naming his player.
                    other.await("Dr Jekyll in view") { other.npcs().firstOrNull { it.index == jekyll.index } }
                    other.npcOp(jekyll.index, RandomEvents.TALK_TO)
                    other.chat("[dr_jekyll.someone_else name=carol]")
                    other.over()

                    client.npcOp(jekyll.index, RandomEvents.TALK_TO)
                    client.chat("[dr_jekyll.ask herb=torstol]")
                    client.awaitDialogue("the options") { it is DialogueView.Menu }
                    client.chooseOption(1)
                    client.chat("[dr_jekyll.here_you_are]")
                    // The potion is given with the herb taken, before his thanks is continued.
                    client.awaitDialogue("his thanks") { it is DialogueView.Chat && it.text == "[dr_jekyll.thank_you]" }
                    client.has(STAMINA)
                    // Shut there, he has nothing more: another Talk-to gets nothing, and he goes soon after.
                    client.closeModal()
                    client.over()
                    client.npcOp(jekyll.index, RandomEvents.TALK_TO)
                    client.awaitTicks(2)
                    assertEquals(null, client.dialogueView)
                    client.gone(DrJekyll.npcId)
                    assertEquals(1, client.count(STAMINA))
                    assertEquals(0, client.count(TORSTOL))
                    assertEquals(1, client.count(GUAM))

                    jekyll = client.start(DrJekyll)
                    client.npcOp(jekyll.index, RandomEvents.TALK_TO)
                    client.chat("[dr_jekyll.ask herb=guam leaf]")
                    client.awaitDialogue("the options") { it is DialogueView.Menu }
                    client.chooseOption(2)
                    client.chat("[dr_jekyll.need_it]")
                    client.chat("[dr_jekyll.fair_enough]")
                    client.over()
                    client.has(DrJekyll.SPARE_POTION)
                    assertEquals(1, client.count(GUAM))

                    client.command("empty")
                    client.await("an empty backpack") { if (client.inventory(Inventory.INV).all { it == null }) Unit else null }
                    jekyll = client.start(DrJekyll)
                    client.npcOp(jekyll.index, RandomEvents.TALK_TO)
                    client.chat("[dr_jekyll.ask herb=guam leaf]")
                    client.chat("[dr_jekyll.dont_have]")
                    client.chat("[dr_jekyll.fair_enough]")
                    client.over()
                    client.has(DrJekyll.SPARE_POTION)
                }
            }
        }
    }

    @Test
    fun `the Strange plant grows where it came up, can be picked once grown, and its fruit is eaten`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.ready()
                val plant = client.start(StrangePlant)
                val at = plant.coord
                // Picked at once, it isn't grown yet.
                client.npcOp(plant.index, RandomEvents.TALK_TO)
                client.awaitTicks(2)
                assertTrue(client.npcs().any { it.id == StrangePlant.npcId })
                assertEquals(0, client.count(StrangePlant.FRUIT))
                // It doesn't follow.
                client.command("tele 3225 3218 0")
                client.await("the walk") { client.position?.takeIf { it == Coord(3225, 3218, 0) } }
                client.awaitTicks(StrangePlant.GROW_TICKS)
                assertEquals(at, client.npcs().first { it.id == StrangePlant.npcId }.coord)
                client.npcOp(plant.index, RandomEvents.TALK_TO)
                client.has(StrangePlant.FRUIT)
                // It stays a moment for its pick animation; another Pick then gives nothing.
                client.npcOp(plant.index, RandomEvents.TALK_TO)
                client.gone(StrangePlant.npcId)
                client.awaitTicks(1)
                assertEquals(1, client.count(StrangePlant.FRUIT))

                val slot = client.inventory(Inventory.INV).indexOfFirst { it?.id == StrangePlant.FRUIT }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, EAT_BUTTON_OP, slot, StrangePlant.FRUIT)
                client.await("the message") { client.messages().firstOrNull { it.text == "[strange_plant.eaten]" } }
                client.await("the fruit eaten") { if (client.count(StrangePlant.FRUIT) == 0) Unit else null }
            }
        }
    }

    private companion object {
        const val GUAM = 249
        const val TORSTOL = 269
        const val STAMINA = 12625
        const val EAT_BUTTON_OP = 2
    }
}
