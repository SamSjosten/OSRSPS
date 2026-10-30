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
import osrsps.game.World
import osrsps.game.content.EllamariaLines
import osrsps.game.content.PalaceLibraryLines
import osrsps.game.content.VarrockPalace
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Varrock step 6 through the headless client: every way through King Roald's (with Aeonisig Raispher speaking up),
 * Aeonisig's, Queen Ellamaria's, Ambassador Ferrnook's, Reldo's, Captain Rovin's and the training Captain's talk; Sir
 * Prysin's Silverlight for 1000 coins, not for less or into a full backpack, and not offered to a player who has one;
 * the library's bookcases; and the Captain's shouting. The rl-client script test-runner/examples/varrock-palace.json
 * climbs the real palace and does the same.
 */
class HeadlessVarrockPalaceTest {
    private val coins = ObjType(COINS, "Coins", stackable = true)
    private val silverlight = ObjType(VarrockPalace.SILVERLIGHT, "Silverlight", cost = 50, tradeable = false)
    private val pot = ObjType(POT, "Pot", cost = 1, tradeable = true)

    private val talk = listOf("Talk-to", null, null, null, null)
    private fun npc(id: Int, name: String, internal: String) = NpcType(id, name, ops = talk, internalName = internal)

    private val king = npc(VarrockPalace.KING_ROALD, "King Roald", "king_roald")
    private val aeonisig = npc(VarrockPalace.AEONISIG, "Aeonisig Raispher", "myq3_aeonisig_roalds_advisor")
    private val queen = npc(VarrockPalace.ELLAMARIA, "Queen Ellamaria", "queen_ellamaria")
    private val prysin = npc(VarrockPalace.PRYSIN, "Sir Prysin", "sir_prysin")
    private val rovin = npc(VarrockPalace.ROVIN, "Captain Rovin", "captain_rovin")
    private val reldo = npc(VarrockPalace.RELDO[0], "Reldo", "reldo_normal")
    private val ferrnook = npc(VarrockPalace.FERRNOOK, "Ambassador Ferrnook", "aluft_gnome_diplomat_varrock")
    private val captain = npc(VarrockPalace.GUARD_CAPTAIN, "Captain", "fai_varrock_guard_trainer")
    private val all = listOf(king, aeonisig, queen, prysin, rovin, reldo, ferrnook, captain)

    private fun bookcase(id: Int) = LocType(id, "Bookcase", ops = listOf("Search", null, null, null, null), collision = LocCollisionType(width = 1, length = 1))

    private val bookcases = VarrockPalace.QUEST_BOOKCASES + listOf(VarrockPalace.ALCOVE_WEST_BOOKCASE, VarrockPalace.ALCOVE_EAST_BOOKCASE)
    private val locs = bookcases.mapIndexed { i, id -> ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, 0, 3214 + i * 2, 3226, 0) }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(coins, silverlight, pot),
            locTypes = LocTypes(bookcases.associateWith(::bookcase)), staticLocs = StaticLocs().apply { locs.forEach(::add) },
            npcTypes = NpcTypes.of(*all.toTypedArray()),
            npcSpawns = all.mapIndexed { i, type -> NpcSpawn(type, 3212 + i * 2, 3218, wander = 0, source = "test") },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.talkTo(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, VarrockPalace.TALK_TO)
    }

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        val texts = keys.map { "[$it]" }
        awaitDialogue("the menu $texts") { it is DialogueView.Menu && it.options == texts }
        chooseOption(option)
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    /** Talks to [id]: each of [steps] is a key under [prefix] to read on from, or "n:key1,key2" to pick option n. */
    private fun HeadlessClient.walk(id: Int, prefix: String, vararg steps: String) {
        talkTo(id)
        for (step in steps) {
            val menu = step.substringBefore(':', "")
            if (menu.isEmpty()) chat("$prefix.$step") else pick(menu.toInt(), *step.substringAfter(':').split(',').map { "$prefix.$it" }.toTypedArray())
        }
        over()
    }

    @Test
    fun `the King, his advisor, the Queen, the Ambassador and Reldo talk every way`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.walk(king.id, "king_roald", "greeting", "2:morytania,not_really", "not_really", "busy")
                // Aeonisig's two lines in the King's talk come from Aeonisig himself; the keys don't show who says them.
                client.walk(
                    king.id, "king_roald", "greeting", "1:morytania,not_really", "morytania", "friendship", "but", "very_good",
                    "reconsider", "balance", "church", "safe_distance", "wanted", "on_my_way",
                )
                val menu = "who,what,where,how"
                client.walk(aeonisig.id, "aeonisig", "greeting", "5:$menu,fair_enough", "fair_enough")
                client.walk(
                    aeonisig.id, "aeonisig", "greeting", "1:$menu,fair_enough", "who", "who_1", "who_2", "who_3", "who_4",
                    "2:$menu,best_be_off", "what", "what_1", "what_2", "what_3", "3:$menu,best_be_off", "where", "where_1", "where_2",
                    "4:$menu,best_be_off", "how", "how_1", "how_2", "how_3", "how_4", "5:$menu,best_be_off", "best_be_off",
                )
                client.walk(queen.id, "ellamaria", "farming", "refuse", "sorry", "white_tree")
                client.walk(ferrnook.id, "ferrnook", "hello", "sort_of", "not_bothered", "patience", "remember")

                // Reldo greets the player by name.
                for ((option, rest) in listOf(1 to listOf("trade", "knowledge", "price", "joke", "ah_well"), 2 to listOf("going", "goodbye"))) {
                    client.talkTo(reldo.id)
                    client.awaitDialogue("reldo.greeting") { it is DialogueView.Chat && it.text == "[reldo.greeting name=alice]" }
                    client.continueDialogue()
                    client.pick(option, "reldo.trade", "reldo.going")
                    for (key in rest) client.chat("reldo.$key")
                    client.over()
                }

                client.command("item $POT 1")
                client.await("a pot") { if (client.held(POT) == 1) Unit else null }
                val npc = client.await("the Queen in view") { client.npcs().firstOrNull { it.id == queen.id } }
                val slot = client.inventory(Inventory.INV).indexOfFirst { it?.id == POT }
                client.npcTarget(npc.index, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, POT)
                client.chat(EllamariaLines.ITEM.key)
                client.over()
            }
        }
    }

    @Test
    fun `Captain Rovin and the training Captain send the player away every way`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val first = "guard,king,important"
                client.walk(rovin.id, "rovin", "greeting", "1:$first", "guard", "know_all", "1:recruit,surgery", "recruit", "interview", "that_disproves", "out_of_sight")
                client.walk(rovin.id, "rovin", "greeting", "1:$first", "guard", "know_all", "2:recruit,surgery", "surgery", "what_surgery", "still_not")
                client.walk(rovin.id, "rovin", "greeting", "2:$first", "king", "king_may", "not_king")
                client.walk(rovin.id, "rovin", "greeting", "3:$first", "important", "listening", "1:forgot,ale", "forgot", "not_important", "how_know", "go_away")
                client.walk(rovin.id, "rovin", "greeting", "3:$first", "important", "listening", "2:forgot,ale", "ale", "kitchen")

                val menu = "doing,armour,nothing"
                val armour = arrayOf("armour", "only_guards", "become", "really", "yes", "life")
                client.walk(captain.id, "guard_captain", "greeting", "3:$menu", "nothing", "lucky")
                client.walk(captain.id, "guard_captain", "greeting", "2:$menu", *armour, "1:not_worried,forget_it", "not_worried", "failed")
                client.walk(captain.id, "guard_captain", "greeting", "1:$menu", "doing", "training", "1:fit,weak", "fit", "weaklings", "2:armour,weak", "weak", "contradict")
                client.walk(
                    captain.id, "guard_captain", "greeting", "1:$menu", "doing", "training", "2:fit,weak", "weak", "respect", "1:armour,fit",
                    *armour, "2:not_worried,forget_it", "forget_it", "coward",
                )
                // At Thieving 31 he brings up the player's record.
                client.command("setlevel thieving 31")
                client.await("Thieving 31") { client.stat(THIEVING)?.takeIf { it.level == 31 } }
                client.walk(
                    captain.id, "guard_captain", "greeting", "2:$menu", "armour", "only_guards", "become", "record", "repent", "really", "yes",
                    "life", "2:not_worried,forget_it", "forget_it", "coward",
                )

                val shouter = client.await("the Captain in view") { client.npcs().firstOrNull { it.id == captain.id } }
                val shouts = (1..10).map { "[guard_captain.shout_$it]" }
                client.await("the Captain shouting", 30_000) { client.npcSaid().firstOrNull { it.index == shouter.index && it.text in shouts } }
            }
        }
    }

    @Test
    fun `Sir Prysin sells a lost Silverlight for 1000 coins, and only to a player without one`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val borrow = arrayOf("greeting", "2:thats_right,lost", "lost", "recovered", "2:relief,borrow", "borrow", "not_free", "price")
                client.walk(prysin.id, "prysin", "greeting", "1:thats_right,lost", "thats_right", "good_job", "thanks")
                client.walk(prysin.id, "prysin", "greeting", "2:thats_right,lost", "lost", "recovered", "1:relief,borrow", "relief")
                client.walk(prysin.id, "prysin", *borrow, "1:not_worth,pay", "not_worth")

                client.command("item $COINS 999")
                client.await("999 coins") { if (client.held(COINS) == 999) Unit else null }
                client.walk(prysin.id, "prysin", *borrow, "2:not_worth,pay", "pay", "no_money")
                assertEquals(999, client.held(COINS))

                client.command("item $COINS 1")
                client.command("item $POT 27")
                client.await("a full backpack") { if (client.held(POT) == 27 && client.held(COINS) == 1000) Unit else null }
                client.walk(prysin.id, "prysin", *borrow, "2:not_worth,pay", "pay", "no_room")
                assertEquals(1000, client.held(COINS))

                client.command("empty")
                client.command("item $COINS 1000")
                client.await("only 1000 coins") { if (client.held(POT) == 0 && client.held(COINS) == 1000) Unit else null }
                client.walk(prysin.id, "prysin", *borrow, "2:not_worth,pay", "pay", "take_care")
                assertEquals(0, client.held(COINS))
                assertEquals(1, client.held(VarrockPalace.SILVERLIGHT))

                // Holding Silverlight, the player just answers that they stopped the demon.
                client.walk(prysin.id, "prysin", "greeting", "thats_right", "good_job", "thanks")
            }
        }
    }

    @Test
    fun `the library's bookcases each say what they hold`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                val says = VarrockPalace.QUEST_BOOKCASES.map { it to PalaceLibraryLines.NOTHING } +
                    listOf(VarrockPalace.ALCOVE_WEST_BOOKCASE to PalaceLibraryLines.ANTS, VarrockPalace.ALCOVE_EAST_BOOKCASE to PalaceLibraryLines.TITLES)
                for ((id, line) in says) {
                    val loc = locs.first { it.id == id }
                    val text = DialogueText.NONE[line]
                    val seen = client.messages().count { it.text == text }
                    client.locOp(id, loc.x, loc.z, 1)
                    client.await("$id saying $text") { if (client.messages().count { it.text == text } > seen) Unit else null }
                }
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val POT = 1931
        const val THIEVING = 17
    }
}
