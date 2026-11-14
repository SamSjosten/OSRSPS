package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.content.Kudos
import osrsps.game.content.MUSEUM_VARBITS
import osrsps.game.content.MuseumCleaning
import osrsps.game.content.MuseumFinds
import osrsps.game.content.VarrockMuseum
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Kudos step 1 through the headless client: an archaeologist looks at each kind of find, by talk and by the item used
 * on him; a find he sends to its case goes on display there for 10 Kudos, taking every copy from the backpack and bank;
 * the clean necklace's secret; the clerk's Dig Site answer following the cases filled; the information booth's Kudos
 * List; and the count kept over a new login. The real places are the 242 cache's; here they stand by the spawn.
 */
class HeadlessMuseumKudosTest {
    private val talk = listOf("Talk-to", null, null, null, null)
    private val archaeologist = NpcType(MuseumFinds.ARCHAEOLOGISTS.first(), "Barnabus Hurma", ops = talk, internalName = "vm_archaeologist1_npc_dummy")
    private val clerk = NpcType(VarrockMuseum.CLERK, "Information clerk", ops = talk, internalName = "vm_info_booth_lady")

    private val objs = ObjTypes.of(
        *(MuseumCleaning.COMMON + MuseumCleaning.UNIQUE_FOUR + MuseumCleaning.TOOL_IDS + MuseumCleaning.GEAR_IDS + listOf(MuseumCleaning.CLEAN_NECKLACE, VarrockMuseum.MUSEUM_MAP))
            .map { ObjType(it, "Item $it") }.toTypedArray(),
        ObjType(MuseumFinds.COINS, "Coins", stackable = true),
        ObjType(ROPE, "Rope"),
    )

    /** Each case two tiles apart along z 3216, west of the spawn; the booth north of the clerk. */
    private val cases = MuseumFinds.CASES.mapIndexed { i, case -> ZoneLoc(case.loc, LocShape.CENTREPIECE_STRAIGHT, 0, 3212 + 2 * i, 3214, 0) }
    private val booth = ZoneLoc(Kudos.BOOTH, LocShape.CENTREPIECE_STRAIGHT, 0, 3230, 3222, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs,
            locTypes = LocTypes(
                (MuseumFinds.CASES.map { LocType(it.loc, "Display case", listOf("Study", null, null, null, null), LocCollisionType(width = 1, length = 1)) } +
                    LocType(Kudos.BOOTH, "Information booth", listOf("Look at", "Check Kudos", null, null, null), LocCollisionType(width = 1, length = 1)))
                    .associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { (cases + booth).forEach(::add) },
            npcTypes = NpcTypes.of(archaeologist, clerk),
            npcSpawns = listOf(NpcSpawn(archaeologist, 3224, 3220, wander = 0, source = "test"), NpcSpawn(clerk, 3230, 3220, wander = 0, source = "test")),
            varTypes = VarTypes(6_000, MUSEUM_VARBITS),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.npcIndex(id: Int) = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }.index

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count more of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.chats(vararg keys: String) {
        for (key in keys) {
            awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
            continueDialogue()
        }
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        val texts = keys.map { "[$it]" }
        awaitDialogue("the menu $texts") { it is DialogueView.Menu && it.options == texts }
        chooseOption(option)
    }

    private fun HeadlessClient.objbox(key: String) {
        await(key) { if (dialogueView is DialogueView.Other && text(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_TEXT) == "[$key]") Unit else null }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    /** Talks to the archaeologist (whichever greeting he picks) and says they found something. */
    private fun HeadlessClient.showFind() {
        npcOp(npcIndex(archaeologist.id), MuseumFinds.TALK_TO)
        awaitDialogue("his greeting") { it is DialogueView.Chat && it.text.startsWith("[museum_archaeologist.greeting_") }
        continueDialogue()
        pick(2, "museum_archaeologist.help", "museum_archaeologist.interesting", "museum_archaeologist.no_thanks")
        chats("museum_archaeologist.interesting", "museum_archaeologist.lets_look")
    }

    private fun HeadlessClient.useOnArchaeologist(id: Int) =
        npcTarget(npcIndex(archaeologist.id), InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(id), id)

    private fun HeadlessClient.useOnCase(case: MuseumFinds.Case, obj: Int = case.obj) {
        val loc = cases[MuseumFinds.CASES.indexOf(case)]
        locTarget(loc.id, loc.x, loc.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(obj), obj)
    }

    /** Messages starting with [text]: a line with a placeholder shows its value after the key. */
    private fun HeadlessClient.seen(text: String) = messages().count { it.text.startsWith(text.removeSuffix("]")) }

    private fun HeadlessClient.awaitSeen(text: String, times: Int = 1) = await("$text x$times") { if (seen(text) >= times) Unit else null }

    private val oldSymbol = MuseumFinds.CASES.first { it.obj == MuseumCleaning.OLD_SYMBOL }
    private val pottery = MuseumFinds.CASES.first { it.obj == MuseumCleaning.POTTERY }

    @Test
    fun `an archaeologist sends the old symbol to its case, where it goes on display for 10 Kudos, every copy taken`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                // Nothing to show.
                client.showFind()
                client.chats("museum_archaeologist.lost_it", "museum_archaeologist.never_had")
                client.over()

                // Used on its case before he has seen it, the symbol stays.
                client.give(MuseumCleaning.OLD_SYMBOL)
                client.useOnCase(oldSymbol)
                client.awaitSeen("[museum_case.not_shown]")
                assertEquals(1, client.count(MuseumCleaning.OLD_SYMBOL))

                // The first time: he sends the player to case 36.
                client.showFind()
                client.chats("museum_case_find.old_symbol", "museum_case_find.old_symbol_1", "museum_case_find.old_symbol_2", "museum_case_find.old_symbol_reply")
                client.over()
                // Again before it's in: "Back again?".
                client.showFind()
                client.chats("museum_case_find.old_symbol_back_again", "museum_case_find.old_symbol_go_now")
                client.over()

                // A second copy banked and a third carried: all go when it's put on display.
                val alice = server.world.playerNamed("alice")!!
                alice.bank.add(server.world.objTypes[MuseumCleaning.OLD_SYMBOL]!!, 1)
                client.give(MuseumCleaning.OLD_SYMBOL)
                client.useOnCase(oldSymbol)
                client.awaitSeen("[museum_case.placed")
                client.await("the symbols gone") { if (client.count(MuseumCleaning.OLD_SYMBOL) == 0) Unit else null }
                assertEquals(0L, alice.bank.count(MuseumCleaning.OLD_SYMBOL))
                client.await("10 Kudos") { if (alice.vars.getVarbit(Kudos.VARBIT_KUDOS) == 10) Unit else null }
                assertEquals(1, alice.vars.getVarbit(Kudos.VARBIT_OLD_SYMBOL))

                // Another symbol: on display already, so it's crate fodder, and the case won't take it.
                client.give(MuseumCleaning.OLD_SYMBOL)
                client.showFind()
                client.chats("museum_case_find.old_symbol", "museum_archaeologist.already")
                client.over()
                client.useOnCase(oldSymbol)
                client.awaitSeen("[museum_case.full]")
                assertEquals(1, client.count(MuseumCleaning.OLD_SYMBOL))
                assertEquals(10, alice.vars.getVarbit(Kudos.VARBIT_KUDOS))
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            val gone = System.nanoTime() + 10_000_000_000L
            while (server.world.playerNamed("alice") != null && System.nanoTime() < gone) Thread.sleep(20)
            // The count and the display are saved.
            server.login("alice").use { client ->
                client.ready()
                val alice = server.world.playerNamed("alice")!!
                assertEquals(10, alice.vars.getVarbit(Kudos.VARBIT_KUDOS))
                assertEquals(1, alice.vars.getVarbit(Kudos.VARBIT_OLD_SYMBOL))
            }
        }
    }

    @Test
    fun `pottery goes in its case one piece at a time, with no message`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.give(MuseumCleaning.POTTERY, 2)
                // Used on him: "Take a look at this..." first.
                client.useOnArchaeologist(MuseumCleaning.POTTERY)
                client.chats(
                    "museum_archaeologist.take_a_look", "museum_case_find.pottery", "museum_case_find.pottery_1", "museum_case_find.pottery_2",
                    "museum_case_find.pottery_reply",
                )
                client.over()
                client.useOnCase(pottery)
                val bob = server.world.playerNamed("bob")!!
                client.await("on display") { if (bob.vars.getVarbit(Kudos.VARBIT_POTTERY) == 1) Unit else null }
                assertEquals(1, client.count(MuseumCleaning.POTTERY))
                assertEquals(10, bob.vars.getVarbit(Kudos.VARBIT_KUDOS))
                assertEquals(0, client.seen("[museum_case.placed"))
            }
        }
    }

    @Test
    fun `the clean necklace teaches its secret and goes, bank copy too, and other finds get their own answers`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                val carol = server.world.playerNamed("carol")!!
                carol.bank.add(server.world.objTypes[MuseumCleaning.CLEAN_NECKLACE]!!, 1)
                client.give(MuseumCleaning.CLEAN_NECKLACE)
                // The necklace comes before anything else carried.
                client.give(MuseumCleaning.ARROWHEADS)
                client.showFind()
                client.chats(*(1..6).map { "museum_archaeologist.necklace_$it" }.toTypedArray())
                client.objbox("museum_archaeologist.necklace_takes")
                client.objbox("museum_archaeologist.necklace_shows_1")
                client.objbox("museum_archaeologist.necklace_shows_2")
                client.chats("museum_archaeologist.necklace_recap", "museum_archaeologist.necklace_well_done")
                client.over()
                assertEquals(0, client.count(MuseumCleaning.CLEAN_NECKLACE))
                assertEquals(0L, carol.bank.count(MuseumCleaning.CLEAN_NECKLACE))
                assertEquals(1, carol.vars.getVarbit(Kudos.VARBIT_NECKLACE))
                assertEquals(0, carol.vars.getVarbit(Kudos.VARBIT_KUDOS))

                client.showFind()
                client.chats("museum_archaeologist.arrowheads", "museum_archaeologist.arrowheads_1", "museum_archaeologist.arrowheads_2", "museum_archaeologist.arrowheads_ok")
                client.over()

                // Jewellery: 5 coins for it.
                client.give(MuseumCleaning.JEWELLERY)
                client.useOnArchaeologist(MuseumCleaning.JEWELLERY)
                client.chats(
                    "museum_archaeologist.take_a_look", "museum_archaeologist.jewellery", "museum_archaeologist.jewellery_1", "museum_archaeologist.jewellery_ok",
                )
                client.objbox("museum_archaeologist.jewellery_coins")
                client.over()
                assertEquals(0, client.count(MuseumCleaning.JEWELLERY))
                assertEquals(5, client.count(MuseumFinds.COINS))

                // Anything else used on him.
                client.give(ROPE)
                client.useOnArchaeologist(ROPE)
                client.chats("museum_archaeologist.take_a_look", "museum_archaeologist.dont_need")
                client.over()

                // Help: not dressed for it.
                client.npcOp(client.npcIndex(archaeologist.id), MuseumFinds.TALK_TO)
                client.awaitDialogue("his greeting") { it is DialogueView.Chat && it.text.startsWith("[museum_archaeologist.greeting_") }
                client.continueDialogue()
                client.pick(1, "museum_archaeologist.help", "museum_archaeologist.interesting", "museum_archaeologist.no_thanks")
                client.chats("museum_archaeologist.help", "museum_archaeologist.need_equipment_1", "museum_archaeologist.need_equipment_2")
                client.over()
            }
        }
    }

    @Test
    fun `the clerk's Dig Site answer follows the cases filled, and the booth shows the Kudos List`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                val dave = server.world.playerNamed("dave")!!
                val topics = "map,dig_site,timeline,natural_history,kudos"
                fun digSite(first: String, second: String) {
                    client.npcOp(client.npcIndex(clerk.id), VarrockMuseum.TALK_TO)
                    client.chats("museum_clerk.welcome")
                    client.pick(2, *topics.split(',').map { "museum_clerk.$it" }.toTypedArray())
                    client.chats("museum_clerk.dig_site_said", "museum_clerk.dig_site_about", "museum_clerk.$first", "museum_clerk.$second")
                    client.pick(2, "museum_clerk.something_else", "museum_clerk.bye")
                    client.chats("museum_clerk.bye_said", "museum_clerk.goodbye")
                    client.over()
                }
                digSite("dig_site_clean_1", "dig_site_clean_2")
                dave.vars.setVarbit(Kudos.VARBIT_ANCIENT_COIN, 1)
                digSite("dig_site_partial_1", "dig_site_partial_2")
                for (case in MuseumFinds.CASES) dave.vars.setVarbit(case.varbit, 1)
                digSite("dig_site_done_1", "dig_site_done_2")

                client.locOp(booth.id, booth.x, booth.z, Kudos.BOOTH_CHECK_KUDOS)
                client.await("the Kudos List") { if (Kudos.KUDOS_LIST in client.openInterfaces()) Unit else null }
            }
        }
    }

    private companion object {
        const val ROPE = 954
    }
}
